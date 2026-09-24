package com.ktele.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class VideoItem(
    val messageId: Long,
    val title: String,
    val info: String,
    val fileId: Int,
    val size: Long
)

class TdFileDataSource(
    private val fetch: (TdApi.Function<*>) -> TdApi.Object?,
    private val fileId: Int,
    private val fileSize: Long
) : BaseDataSource(true) {

    private var currentUri: Uri? = null
    private var position = 0L
    private var windowStart = 0L
    private var windowEnd = 0L
    private var isOpen = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)
        position = dataSpec.position
        windowStart = position
        windowEnd = position
        isOpen = true
        transferStarted(dataSpec)
        val remaining = maxOf(0L, fileSize - position)
        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(dataSpec.length, remaining)
        } else {
            remaining
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= fileSize) return C.RESULT_END_OF_INPUT

        val want = minOf(length.toLong(), 262144L, fileSize - position)

        if (position < windowStart || position + want > windowEnd) {
            val d = TdApi.DownloadFile()
            d.fileId = fileId
            d.priority = 32
            d.offset = position
            d.limit = 8L * 1024L * 1024L
            d.synchronous = true
            val r = fetch(d)
            if (r == null) throw IOException("Telegram download timeout")
            if (r is TdApi.Error) throw IOException("Telegram: " + r.message)
            windowStart = position
            windowEnd = position + d.limit
        }

        val rp = TdApi.ReadFilePart()
        rp.fileId = fileId
        rp.offset = position
        rp.count = want
        val res = fetch(rp)
        if (res is TdApi.Data) {
            val data = res.data
            if (data.isEmpty()) throw IOException("Empty data from Telegram")
            System.arraycopy(data, 0, buffer, offset, data.size)
            position += data.size
            bytesTransferred(data.size)
            return data.size
        }
        if (res is TdApi.Error) throw IOException("Telegram: " + res.message)
        throw IOException("Could not read file")
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (isOpen) {
            isOpen = false
            transferEnded()
        }
        currentUri = null
    }
}

class MainActivity : ComponentActivity() {

    private var client: Client? = null
    private var stage by mutableStateOf("starting")
    private var message by mutableStateOf("")
    private var chatIds by mutableStateOf(listOf<Long>())
    private val chatTitles = mutableStateMapOf<Long, String>()
    private var openChatId by mutableStateOf<Long?>(null)
    private var videos by mutableStateOf(listOf<VideoItem>())
    private var playing by mutableStateOf<VideoItem?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTelegram()
        setContent {
            MaterialTheme {
                Screen()
            }
        }
    }

    private fun startTelegram() {
        if (BuildConfig.TG_API_ID == 0) {
            stage = "error"
            message = "API keys missing in this build"
            return
        }
        try {
            System.loadLibrary("tdjni")
            client = Client.create(
                Client.ResultHandler { obj -> handleUpdate(obj) },
                null,
                null
            )
        } catch (e: Throwable) {
            stage = "error"
            message = "Telegram start error: " + e.message
        }
    }

    private fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            onAuthState(obj.authorizationState)
        }
    }

    private fun onAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> stage = "phone"
            is TdApi.AuthorizationStateWaitCode -> stage = "code"
            is TdApi.AuthorizationStateWaitPassword -> stage = "password"
            is TdApi.AuthorizationStateReady -> {
                stage = "ready"
                loadChats()
            }
            else -> {}
        }
    }

    private fun sendParameters() {
        val p = TdApi.SetTdlibParameters()
        p.databaseDirectory = filesDir.absolutePath + "/tdlib"
        p.useFileDatabase = true
        p.useChatInfoDatabase = true
        p.useMessageDatabase = true
        p.useSecretChats = false
        p.apiId = BuildConfig.TG_API_ID
        p.apiHash = BuildConfig.TG_API_HASH
        p.systemLanguageCode = "en"
        p.deviceModel = "Android"
        p.applicationVersion = "1.0"
        send(p)
    }

    private fun send(f: TdApi.Function<*>) {
        client?.send(f, Client.ResultHandler { r ->
            if (r is TdApi.Error) {
                message = r.message
            } else {
                message = ""
            }
        })
    }

    private fun sendBlocking(f: TdApi.Function<*>): TdApi.Object? {
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<TdApi.Object>(1)
        client?.send(f, Client.ResultHandler { r ->
            holder[0] = r
            latch.countDown()
        })
        latch.await(120, TimeUnit.SECONDS)
        return holder[0]
    }

    private fun loadChats() {
        val load = TdApi.LoadChats()
        load.chatList = TdApi.ChatListMain()
        load.limit = 100
        client?.send(load, Client.ResultHandler { fetchChats() })
    }

    private fun fetchChats() {
        val get = TdApi.GetChats()
        get.chatList = TdApi.ChatListMain()
        get.limit = 100
        client?.send(get, Client.ResultHandler { r ->
            if (r is TdApi.Chats) {
                chatIds = r.chatIds.toList()
                for (id in r.chatIds) {
                    val gc = TdApi.GetChat()
                    gc.chatId = id
                    client?.send(gc, Client.ResultHandler { c ->
                        if (c is TdApi.Chat) {
                            chatTitles[c.id] = c.title
                        }
                    })
                }
            } else if (r is TdApi.Error) {
                message = r.message
            }
        })
    }

    private fun openChat(chatId: Long) {
        openChatId = chatId
        videos = listOf()
        message = ""
        searchInto(chatId, TdApi.SearchMessagesFilterVideo())
        searchInto(chatId, TdApi.SearchMessagesFilterDocument())
    }

    private fun searchInto(chatId: Long, filter: TdApi.SearchMessagesFilter) {
        val s = TdApi.SearchChatMessages()
        s.chatId = chatId
        s.query = ""
        s.fromMessageId = 0
        s.offset = 0
        s.limit = 50
        s.filter = filter
        client?.send(s, Client.ResultHandler { r ->
            if (r is TdApi.FoundChatMessages) {
                val found = r.messages.mapNotNull { toItem(it) }
                videos = (videos + found).sortedByDescending { it.messageId }
            } else if (r is TdApi.Error) {
                message = r.message
            }
        })
    }

    private fun toItem(m: TdApi.Message): VideoItem? {
        val c = m.content
        if (c is TdApi.MessageVideo) {
            val f = c.video.video
            var title = c.video.fileName ?: ""
            if (title.isBlank()) title = c.caption.text.take(60)
            if (title.isBlank()) title = "Video"
            val bytes = maxOf(f.size, f.expectedSize).toLong()
            val mb = bytes / 1048576
            val minutes = c.video.duration / 60
            return VideoItem(m.id, title, "$minutes min  |  $mb MB", f.id, bytes)
        }
        if (c is TdApi.MessageDocument) {
            val d = c.document
            val name = d.fileName ?: ""
            val mime = d.mimeType ?: ""
            val lower = name.lowercase()
            val isVideo = mime.startsWith("video/") ||
                lower.endsWith(".mkv") || lower.endsWith(".mp4") ||
                lower.endsWith(".avi") || lower.endsWith(".webm")
            if (!isVideo) return null
            val f = d.document
            var title = name
            if (title.isBlank()) title = c.caption.text.take(60)
            if (title.isBlank()) title = "Video file"
            val bytes = maxOf(f.size, f.expectedSize).toLong()
            val mb = bytes / 1048576
            return VideoItem(m.id, title, "file  |  $mb MB", f.id, bytes)
        }
        return null
    }

    private fun submit(text: String) {
        when (stage) {
            "phone" -> send(TdApi.SetAuthenticationPhoneNumber(text.trim(), null))
            "code" -> send(TdApi.CheckAuthenticationCode(text.trim()))
            "password" -> send(TdApi.CheckAuthenticationPassword(text))
        }
    }

    @Composable
    private fun Screen() {
        val p = playing
        if (p != null) {
            PlayerScreen(p)
        } else {
            ListScreen()
        }
    }

    @Composable
    private fun PlayerScreen(item: VideoItem) {
        val context = LocalContext.current
        var error by remember { mutableStateOf("") }

        val player = remember(item.fileId) {
            val factory = DataSource.Factory {
                TdFileDataSource({ f -> sendBlocking(f) }, item.fileId, item.size)
            }
            val uri = Uri.Builder()
                .scheme("td")
                .authority("file")
                .appendPath(item.fileId.toString())
                .appendPath(item.title)
                .build()
            val source = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(uri))
            val exo = ExoPlayer.Builder(context).build()
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    error = "Playback error: " + e.errorCodeName + " - " +
                        (e.cause?.message ?: e.message ?: "")
                }
            })
            exo.setMediaSource(source)
            exo.prepare()
            exo.playWhenReady = true
            exo
        }

        DisposableEffect(player) {
            onDispose { player.release() }
        }

        BackHandler { playing = null }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Button(onClick = { playing = null }) {
                Text("Back")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(item.title)
            Spacer(modifier = Modifier.height(12.dp))
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
            if (error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(error)
            }
        }
    }

    @Composable
    private fun ListScreen() {
        var input by remember { mutableStateOf("") }

        BackHandler(enabled = openChatId != null) {
            openChatId = null
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = if (stage == "ready") Arrangement.Top else Arrangement.Center
        ) {
            Text("K-Tele Player", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            when (stage) {
                "starting" -> Text("Starting Telegram...")
                "error" -> Text("Something went wrong")
                "ready" -> {
                    val current = openChatId
                    if (current == null) {
                        Text("Your chats", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(chatIds) { id ->
                                Text(
                                    text = chatTitles[id] ?: "...",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openChat(id) }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    } else {
                        Button(onClick = { openChatId = null }) {
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            chatTitles[current] ?: "",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (videos.isEmpty()) {
                            Text("Loading videos... (or none found)")
                        }
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(videos) { v ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { playing = v }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(v.title)
                                    Text(v.info, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                else -> {
                    val label = when (stage) {
                        "phone" -> "Phone number (with country code, e.g. +91...)"
                        "code" -> "Login code from Telegram"
                        else -> "Two-step verification password"
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        submit(input)
                        input = ""
                    }) {
                        Text("Next")
                    }
                }
            }

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(message)
            }
        }
    }
}
