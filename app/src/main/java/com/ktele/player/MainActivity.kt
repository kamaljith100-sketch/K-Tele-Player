package com.ktele.player

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

data class VideoItem(
    val messageId: Long,
    val title: String,
    val info: String,
    val fileId: Int
)

class MainActivity : ComponentActivity() {

    private var client: Client? = null
    private var stage by mutableStateOf("starting")
    private var message by mutableStateOf("")
    private var chatIds by mutableStateOf(listOf<Long>())
    private val chatTitles = mutableStateMapOf<Long, String>()
    private var openChatId by mutableStateOf<Long?>(null)
    private var videos by mutableStateOf(listOf<VideoItem>())

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
            val mb = maxOf(f.size, f.expectedSize) / 1048576
            val minutes = c.video.duration / 60
            return VideoItem(m.id, title, "$minutes min  |  $mb MB", f.id)
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
            val mb = maxOf(f.size, f.expectedSize) / 1048576
            return VideoItem(m.id, title, "file  |  $mb MB", f.id)
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
