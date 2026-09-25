package com.ktele.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.delay
import kotlin.math.ceil


private val kTeleColorScheme = darkColorScheme(
    primary = Color(0xFF13CFF0),
    onPrimary = Color(0xFF001117),
    primaryContainer = Color(0xFF082A37),
    onPrimaryContainer = Color(0xFFBDF5FF),
    secondary = Color(0xFFBD45F5),
    onSecondary = Color(0xFF21002E),
    secondaryContainer = Color(0xFF3B0D4D),
    onSecondaryContainer = Color(0xFFF5D8FF),
    background = Color(0xFF05060B),
    onBackground = Color(0xFFF3F5FF),
    surface = Color(0xFF0B0D16),
    onSurface = Color(0xFFF3F5FF),
    surfaceVariant = Color(0xFF171A28),
    onSurfaceVariant = Color(0xFFC0C5D7),
    outline = Color(0xFF3B4155),
    error = Color(0xFFFF6B84),
    onError = Color(0xFF2B0007)
)


data class VideoItem(

    val messageId: Long,
    val title: String,
    val info: String,
    val fileId: Int,
    val size: Long
)

// Match the player load-control thresholds so the displayed ETA has a known target.
private const val VIDEO_MIN_BUFFER_MS = 50_000
private const val VIDEO_MAX_BUFFER_MS = 50_000
private const val VIDEO_START_BUFFER_MS = 1_000
private const val VIDEO_REBUFFER_BUFFER_MS = 2_000

// Fetch only small on-demand ranges; playback reads each range incrementally.
private const val TELEGRAM_STREAM_CHUNK_BYTES = 1024L * 1024L
private const val TELEGRAM_STREAM_READ_BYTES = 256L * 1024L


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

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        if (length == 0) {
            return 0
        }

        if (position >= fileSize) {
            return C.RESULT_END_OF_INPUT
        }

        val requested = minOf(
            length.toLong(),
            TELEGRAM_STREAM_READ_BYTES,
            fileSize - position
        )

        val chunkStart =
            (position / TELEGRAM_STREAM_CHUNK_BYTES) * TELEGRAM_STREAM_CHUNK_BYTES
        val chunkEnd = minOf(
            fileSize,
            chunkStart + TELEGRAM_STREAM_CHUNK_BYTES
        )

        if (position < windowStart || position >= windowEnd) {
            val download = TdApi.DownloadFile()

            download.fileId = fileId
            download.priority = 32
            download.offset = chunkStart
            download.limit = chunkEnd - chunkStart
            download.synchronous = true

            val result = fetch(download)

            if (result == null) {
                throw IOException("Telegram stream chunk timeout")
            }

            if (result is TdApi.Error) {
                throw IOException("Telegram: ${result.message}")
            }

            windowStart = chunkStart
            windowEnd = chunkEnd
        }

        val want = minOf(requested, windowEnd - position)
        if (want <= 0L) {
            throw IOException("No stream data available at offset $position")
        }

        val readPart = TdApi.ReadFilePart()

        readPart.fileId = fileId
        readPart.offset = position
        readPart.count = want

        val result = fetch(readPart)

        if (result is TdApi.Data) {
            val data = result.data

            if (data.isEmpty()) {
                throw IOException("Empty data from Telegram")
            }

            System.arraycopy(
                data,
                0,
                buffer,
                offset,
                data.size
            )

            position += data.size
            bytesTransferred(data.size)

            return data.size
        }

        if (result is TdApi.Error) {
            throw IOException("Telegram: ${result.message}")
        }

        throw IOException("Could not read file")
    }

    override fun getUri(): Uri? {
        return currentUri
    }

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
    private var myUserId by mutableStateOf<Long?>(null)
    private var savedMessagesChatId by mutableStateOf<Long?>(null)

    private var chatIds by mutableStateOf(
        listOf<Long>()
    )

    private val chatTitles =
        mutableStateMapOf<Long, String>()

    private val chatPinnedInMainList =
        mutableStateMapOf<Long, Boolean>()

    private var openChatId by mutableStateOf<Long?>(null)

    private var videos by mutableStateOf(
        listOf<VideoItem>()
    )

    private var playing by mutableStateOf<VideoItem?>(null)


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        startTelegram()

        window.statusBarColor = android.graphics.Color.rgb(5, 6, 11)
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 11)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MaterialTheme(colorScheme = kTeleColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Screen()
                }
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
                Client.ResultHandler { obj ->
                    handleUpdate(obj)
                },
                null,
                null
            )
        } catch (e: Throwable) {
            stage = "error"
            message = "Telegram start error: ${e.message}"
        }
    }


    private fun handleUpdate(
        obj: TdApi.Object
    ) {
        if (obj is TdApi.UpdateAuthorizationState) {
            onAuthState(obj.authorizationState)
        }
    }


    private fun onAuthState(
        state: TdApi.AuthorizationState
    ) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                sendParameters()
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                stage = "phone"
            }

            is TdApi.AuthorizationStateWaitCode -> {
                stage = "code"
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                stage = "password"
            }

            is TdApi.AuthorizationStateReady -> {
                stage = "ready"
                client?.send(
                    TdApi.GetMe(),
                    Client.ResultHandler { result ->
                        if (result is TdApi.User) {
                            myUserId = result.id
                        }
                        loadChats()
                    }
                )
            }

            else -> {
            }
        }
    }


    private fun sendParameters() {
        val parameters = TdApi.SetTdlibParameters()

        parameters.databaseDirectory =
            filesDir.absolutePath + "/tdlib"

        parameters.useFileDatabase = true
        parameters.useChatInfoDatabase = true
        parameters.useMessageDatabase = true
        parameters.useSecretChats = false

        parameters.apiId = BuildConfig.TG_API_ID
        parameters.apiHash = BuildConfig.TG_API_HASH

        parameters.systemLanguageCode = "en"
        parameters.deviceModel = "Android"
        parameters.applicationVersion = "1.0"

        send(parameters)
    }


    private fun send(
        function: TdApi.Function<*>
    ) {
        client?.send(
            function,
            Client.ResultHandler { result ->
                if (result is TdApi.Error) {
                    message = result.message
                } else {
                    message = ""
                }
            }
        )
    }


    private fun sendBlocking(
        function: TdApi.Function<*>
    ): TdApi.Object? {
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<TdApi.Object>(1)

        client?.send(
            function,
            Client.ResultHandler { result ->
                holder[0] = result
                latch.countDown()
            }
        )

        latch.await(120, TimeUnit.SECONDS)

        return holder[0]
    }


    private fun loadChats() {
        val load = TdApi.LoadChats()

        load.chatList = TdApi.ChatListMain()
        load.limit = 100

        client?.send(
            load,
            Client.ResultHandler {
                fetchChats()
            }
        )
    }


    private fun fetchChats() {
        val get = TdApi.GetChats()

        get.chatList = TdApi.ChatListMain()
        get.limit = 100

        client?.send(
            get,
            Client.ResultHandler { result ->
                if (result is TdApi.Chats) {
                    chatIds = result.chatIds.toList()

                    for (id in result.chatIds) {
                        val getChat = TdApi.GetChat()
                        getChat.chatId = id

                        client?.send(
                            getChat,
                            Client.ResultHandler { chatResult ->
                                if (chatResult is TdApi.Chat) {
                                    chatTitles[chatResult.id] =
                                        chatResult.title
                                    val selfUserId = myUserId
                                    val privateChatType =
                                        chatResult.type as? TdApi.ChatTypePrivate
                                    if (selfUserId != null &&
                                        privateChatType?.userId == selfUserId
                                    ) {
                                        savedMessagesChatId = chatResult.id
                                    }
                                    chatPinnedInMainList[chatResult.id] =
                                        chatResult.positions.any { position ->
                                            position.list is TdApi.ChatListMain &&
                                                position.isPinned
                                        }
                                }
                            }
                        )
                    }
                } else if (result is TdApi.Error) {
                    message = result.message
                }
            }
        )
    }


    private fun openChat(
        chatId: Long
    ) {
        openChatId = chatId
        videos = listOf()
        message = ""

        searchInto(
            chatId,
            TdApi.SearchMessagesFilterVideo()
        )

        searchInto(
            chatId,
            TdApi.SearchMessagesFilterDocument()
        )
    }


    private fun searchInto(
        chatId: Long,
        filter: TdApi.SearchMessagesFilter
    ) {
        val search = TdApi.SearchChatMessages()

        search.chatId = chatId
        search.query = ""
        search.fromMessageId = 0
        search.offset = 0
        search.limit = 50
        search.filter = filter

        client?.send(
            search,
            Client.ResultHandler { result ->
                if (result is TdApi.FoundChatMessages) {
                    val found = result.messages.mapNotNull {
                        toItem(it)
                    }

                    videos = (
                        videos + found
                    ).sortedByDescending {
                        it.messageId
                    }
                } else if (result is TdApi.Error) {
                    message = result.message
                }
            }
        )
    }


    private fun toItem(
        message: TdApi.Message
    ): VideoItem? {
        val content = message.content

        if (content is TdApi.MessageVideo) {
            val file = content.video.video

            var title = content.video.fileName ?: ""

            if (title.isBlank()) {
                title = content.caption.text.take(60)
            }

            if (title.isBlank()) {
                title = "Video"
            }

            val bytes = maxOf(
                file.size,
                file.expectedSize
            ).toLong()

            val mb = bytes / 1048576
            val minutes = content.video.duration / 60

            return VideoItem(
                message.id,
                title,
                "$minutes min  |  $mb MB",
                file.id,
                bytes
            )
        }

        if (content is TdApi.MessageDocument) {
            val document = content.document

            val name = document.fileName ?: ""
            val mime = document.mimeType ?: ""
            val lower = name.lowercase()

            val isVideo =
                mime.startsWith("video/") ||
                lower.endsWith(".mkv") ||
                lower.endsWith(".mp4") ||
                lower.endsWith(".avi") ||
                lower.endsWith(".webm")

            if (!isVideo) {
                return null
            }

            val file = document.document

            var title = name

            if (title.isBlank()) {
                title = content.caption.text.take(60)
            }

            if (title.isBlank()) {
                title = "Video file"
            }

            val bytes = maxOf(
                file.size,
                file.expectedSize
            ).toLong()

            val mb = bytes / 1048576

            return VideoItem(
                message.id,
                title,
                "file  |  $mb MB",
                file.id,
                bytes
            )
        }

        return null
    }


    private fun submit(
        text: String
    ) {
        when (stage) {
            "phone" -> {
                send(
                    TdApi.SetAuthenticationPhoneNumber(
                        text.trim(),
                        null
                    )
                )
            }

            "code" -> {
                send(
                    TdApi.CheckAuthenticationCode(
                        text.trim()
                    )
                )
            }

            "password" -> {
                send(
                    TdApi.CheckAuthenticationPassword(
                        text
                    )
                )
            }
        }
    }


    @Composable
    private fun Screen() {
        val currentVideo = playing

        if (currentVideo != null) {
            PlayerScreen(currentVideo)
        } else {
            ListScreen()
        }
    }


    @Composable
    private fun PlayerScreen(
        item: VideoItem
    ) {
        val context = LocalContext.current
        val activity = context as? Activity

        var error by remember {
            mutableStateOf("")
        }

        var playbackState by remember(item.fileId) {
            mutableStateOf(Player.STATE_BUFFERING)
        }

        var estimatedStartSeconds by remember(item.fileId) {
            mutableStateOf<Long?>(null)
        }

        DisposableEffect(Unit) {
            val actionBar = activity?.actionBar
            val restoreActionBar = actionBar?.isShowing == true
            actionBar?.hide()

            activity?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            val controller = activity?.window?.let { window ->
                WindowCompat.getInsetsController(
                    window,
                    window.decorView
                )
            }

            controller?.hide(
                WindowInsetsCompat.Type.systemBars()
            )

            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            onDispose {
                controller?.show(
                    WindowInsetsCompat.Type.systemBars()
                )

                if (restoreActionBar) {
                    actionBar?.show()
                }

                activity?.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        val player = remember(item.fileId) {
            val factory = DataSource.Factory {
                TdFileDataSource(
                    { function ->
                        sendBlocking(function)
                    },
                    item.fileId,
                    item.size
                )
            }

            val uri = Uri.Builder()
                .scheme("td")
                .authority("file")
                .appendPath(item.fileId.toString())
                .appendPath(item.title)
                .build()

            val source =
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(
                        MediaItem.fromUri(uri)
                    )

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    VIDEO_MIN_BUFFER_MS,
                    VIDEO_MAX_BUFFER_MS,
                    VIDEO_START_BUFFER_MS,
                    VIDEO_REBUFFER_BUFFER_MS
                )
                .build()

            val exo = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build()

            exo.addListener(
                object : Player.Listener {
                    override fun onPlayerError(
                        exception: PlaybackException
                    ) {
                        error =
                            "Playback error: " +
                            exception.errorCodeName +
                            " - " +
                            (
                                exception.cause?.message
                                    ?: exception.message
                                    ?: ""
                            )
                    }
                }
            )

            exo.setMediaSource(source)
            exo.prepare()
            exo.playWhenReady = true

            exo
        }

        LaunchedEffect(player) {
            var previousSampleTimeMs = SystemClock.elapsedRealtime()
            var previousBufferedDurationMs =
                player.totalBufferedDuration.coerceAtLeast(0L)
            var bufferedMsPerWallMs = 0.0
            var hasBufferRate = false
            var lastBufferProgressTimeMs = previousSampleTimeMs
            var hasStartedPlayback = false

            while (true) {
                val nowMs = SystemClock.elapsedRealtime()
                val currentState = player.playbackState
                val currentBufferedDurationMs =
                    player.totalBufferedDuration.coerceAtLeast(0L)
                val elapsedMs = nowMs - previousSampleTimeMs
                val bufferedDeltaMs =
                    currentBufferedDurationMs - previousBufferedDurationMs

                if (currentState == Player.STATE_BUFFERING) {
                    if (bufferedDeltaMs < 0L) {
                        bufferedMsPerWallMs = 0.0
                        hasBufferRate = false
                        lastBufferProgressTimeMs = nowMs
                    } else if (elapsedMs > 0L && bufferedDeltaMs > 0L) {
                        val sampleRate =
                            bufferedDeltaMs.toDouble() / elapsedMs.toDouble()
                        bufferedMsPerWallMs = if (hasBufferRate) {
                            bufferedMsPerWallMs * 0.7 + sampleRate * 0.3
                        } else {
                            sampleRate
                        }
                        hasBufferRate = true
                        lastBufferProgressTimeMs = nowMs
                    }

                    if (nowMs - lastBufferProgressTimeMs > 3_000L) {
                        hasBufferRate = false
                    }
                } else {
                    hasBufferRate = false
                    lastBufferProgressTimeMs = nowMs
                }

                if (currentState == Player.STATE_READY && player.playWhenReady) {
                    hasStartedPlayback = true
                }

                playbackState = currentState
                estimatedStartSeconds =
                    if (currentState != Player.STATE_BUFFERING) {
                        null
                    } else {
                        val targetBufferedMs =
                            if (hasStartedPlayback) {
                                VIDEO_REBUFFER_BUFFER_MS.toLong()
                            } else {
                                VIDEO_START_BUFFER_MS.toLong()
                            }
                        val remainingBufferMs =
                            (targetBufferedMs - currentBufferedDurationMs)
                                .coerceAtLeast(0L)

                        when {
                            remainingBufferMs == 0L -> 0L
                            hasBufferRate && bufferedMsPerWallMs > 0.0 -> {
                                ceil(
                                    remainingBufferMs.toDouble() /
                                        bufferedMsPerWallMs /
                                        1_000.0
                                ).toLong().coerceAtLeast(1L)
                            }
                            else -> null
                        }
                    }

                previousSampleTimeMs = nowMs
                previousBufferedDurationMs = currentBufferedDurationMs
                delay(250)
            }
        }

        DisposableEffect(player) {
            onDispose {
                player.release()
            }
        }

        BackHandler {
            playing = null
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        keepScreenOn = true
                        useController = true
                        resizeMode =
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (playbackState == Player.STATE_BUFFERING) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    val etaSeconds = estimatedStartSeconds
                    Text(
                        text = when {
                            etaSeconds == null -> "Estimating start time…"
                            etaSeconds <= 0L -> "Starting soon…"
                            else -> "Starting in about ${etaSeconds}s"
                        }
                    )
                }
            }

            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }
        }
    }


    @Composable
        private fun ListScreen() {
            var input by remember {
                mutableStateOf("")
            }

            BackHandler(enabled = openChatId != null) {
                openChatId = null
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = if (stage == "ready") {
                    Arrangement.Top
                } else {
                    Arrangement.Center
                }
            ) {
                if (stage == "ready") {
                    Text(
                        text = "K-Tele Player",
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ktele_player_logo),
                        contentDescription = "K-Tele Player logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(176.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(
                    modifier = Modifier.height(24.dp)
                )

                when (stage) {
                    "starting" -> {
                        Text("Starting Telegram...")
                    }

                    "error" -> {
                        Text("Something went wrong")
                    }

                    "ready" -> {
                        val current = openChatId

                        if (current == null) {
                            Text(
                                text = "Your chats",
                                style = MaterialTheme.typography.titleMedium
                            )

                            val savedMessagesId = savedMessagesChatId
                            val orderedChatIds =
                                chatIds.filter { it == savedMessagesId } +
                                    chatIds.filter {
                                        it != savedMessagesId &&
                                            chatPinnedInMainList[it] == true
                                    } +
                                    chatIds.filter {
                                        it != savedMessagesId &&
                                            chatPinnedInMainList[it] != true
                                    }

                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                items(orderedChatIds) { id ->
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
                            Button(
                                onClick = { openChatId = null }
                            ) {
                                Text("Back")
                            }

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            Text(
                                text = chatTitles[current] ?: "",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            if (videos.isEmpty()) {
                                Text("Loading videos... (or none found)")
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                items(videos) { video ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { playing = video }
                                            .padding(vertical = 10.dp)
                                    ) {
                                        Text(video.title)
                                        Text(
                                            video.info,
                                            style = MaterialTheme.typography.bodySmall
                                        )
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

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        Button(
                            onClick = {
                                submit(input)
                                input = ""
                            }
                        ) {
                            Text("Next")
                        }
                    }
                }

                if (message.isNotEmpty()) {
                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )
                    Text(message)
                }
            }
        }
    }
