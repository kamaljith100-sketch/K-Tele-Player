package com.ktele.player

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.delay
import kotlin.math.ceil


private const val SUBTITLE_PREFERENCES = "subtitle_preferences"
private const val SUBTITLE_COLOR_KEY = "subtitle_color"
private const val DEFAULT_SUBTITLE_COLOR_ID = "white"

private data class SubtitleColorPreset(
    val id: String,
    val label: String,
    val color: Long
)

private val subtitleColorPresets = listOf(
    SubtitleColorPreset("white", "White", 0xFFFFFFFF),
    SubtitleColorPreset("yellow", "Yellow", 0xFFFFD54F),
    SubtitleColorPreset("cyan", "Cyan", 0xFF4DD0E1),
    SubtitleColorPreset("green", "Green", 0xFF8BC34A)
)

private fun subtitleCaptionStyle(foregroundColor: Int) =
    CaptionStyleCompat(
        foregroundColor,
        AndroidColor.TRANSPARENT,
        AndroidColor.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        AndroidColor.BLACK,
        null
    )


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
    val size: Long,
    val localPath: String? = null,
    val torrent: Torrent? = null
)

// Keep player startup and rebuffer thresholds explicit.
private const val VIDEO_MIN_BUFFER_MS = 50_000
private const val VIDEO_MAX_BUFFER_MS = 50_000
private const val VIDEO_START_BUFFER_MS = 1_000
private const val VIDEO_REBUFFER_BUFFER_MS = 2_000

// Use a small first range for quick startup, then larger ranges for throughput.
private const val TELEGRAM_STREAM_INITIAL_CHUNK_BYTES = 512L * 1024L
private const val TELEGRAM_STREAM_CHUNK_BYTES = 4L * 1024L * 1024L
private const val TELEGRAM_STREAM_READ_BYTES = 1024L * 1024L


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

        val chunkStart = if (position < TELEGRAM_STREAM_INITIAL_CHUNK_BYTES) {
            0L
        } else {
            TELEGRAM_STREAM_INITIAL_CHUNK_BYTES +
                (
                    (position - TELEGRAM_STREAM_INITIAL_CHUNK_BYTES) /
                        TELEGRAM_STREAM_CHUNK_BYTES
                ) * TELEGRAM_STREAM_CHUNK_BYTES
        }
        val chunkSize = if (chunkStart == 0L) {
            TELEGRAM_STREAM_INITIAL_CHUNK_BYTES
        } else {
            TELEGRAM_STREAM_CHUNK_BYTES
        }
        val chunkEnd = minOf(
            fileSize,
            chunkStart + chunkSize
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


class TorrentDataSource(
    private val torrent: Torrent,
    private val knownFileSize: Long
) : BaseDataSource(true) {

    private var currentUri: Uri? = null
    private var input: InputStream? = null
    private var position = 0L
    private var endPosition = Long.MAX_VALUE
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)

        // TorrentStream exposes a blocking stream. Do not use File.length() as
        // the EOF boundary: during streaming it can describe only the sparse
        // portion already written, while the torrent stream is still growing.
        val stream = torrent.getVideoStream()
        var toSkip = dataSpec.position
        while (toSkip > 0L) {
            val skipped = stream.skip(toSkip)
            if (skipped > 0L) {
                toSkip -= skipped
            } else if (stream.read() == -1) {
                throw IOException("Could not seek in torrent video")
            } else {
                toSkip--
            }
        }

        input = stream
        position = dataSpec.position
        endPosition = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.position + dataSpec.length
        } else {
            Long.MAX_VALUE
        }
        opened = true
        transferStarted(dataSpec)

        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            return dataSpec.length
        }

        // Report the best known length when available, but let read() decide
        // EOF so a stale/incomplete File.length() cannot stop playback.
        val currentLength = maxOf(knownFileSize, torrent.videoFile.length())
        return if (currentLength > dataSpec.position) {
            currentLength - dataSpec.position
        } else {
            C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val stream = input ?: throw IOException("Torrent stream is not open")
        if (position >= endPosition) return C.RESULT_END_OF_INPUT

        val allowed = minOf(length.toLong(), endPosition - position).toInt()
        val count = stream.read(buffer, offset, allowed)
        if (count == -1) return C.RESULT_END_OF_INPUT

        position += count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        input?.close()
        input = null
        currentUri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

private const val TORRENT_LINK_HOOK = """
(function() {
    if (window.__kteleTorrentHook) return;
    window.__kteleTorrentHook = true;
    document.addEventListener('click', function(event) {
        var node = event.target.closest && event.target.closest('a,button,[data-href],[data-url]');
        if (!node) return;
        var link = node.getAttribute('href') || node.getAttribute('data-href') || node.getAttribute('data-url') || node.href || '';
        var lowerLink = link.toLowerCase();
        if (link && (lowerLink.indexOf('magnet:') === 0 || lowerLink.indexOf('.torrent') >= 0 || lowerLink.indexOf('intent://') === 0)) {
            event.preventDefault();
            event.stopPropagation();
            if (window.KTeleTorrent) window.KTeleTorrent.openTorrent(link);
        }
    }, true);
})();
"""

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

    private var browserOpen by mutableStateOf(false)

    private var torrentStream: TorrentStream? = null
    private var torrentSourceUrl by mutableStateOf<String?>(null)
    private var torrentSourceTitle by mutableStateOf("")
    private var torrentSourceSize by mutableStateOf("Torrent source")
    private var torrentPreparing by mutableStateOf(false)
    private var torrentDownloadMode by mutableStateOf(false)
    private var torrentProgress by mutableStateOf(0)
    private var torrentError by mutableStateOf("")


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        startTelegram()
        initTorrentStream()

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


    private fun initTorrentStream() {
        if (torrentStream != null) return

        val saveDirectory = File(filesDir, "torrents")
        try {
            if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
                throw IOException("Could not create torrent storage directory")
            }

            val options = TorrentOptions.Builder()
                .saveLocation(saveDirectory)
                .removeFilesAfterStop(false)
                .maxConnections(500)
                .maxActiveDHT(200)
                .prepareSize(4L * 1024L * 1024L)
                .build()

            torrentStream = TorrentStream.init(options).also { stream ->
                stream.addListener(object : TorrentListener {
                    override fun onStreamPrepared(torrent: Torrent?) {
                    }

                    override fun onStreamStarted(torrent: Torrent?) {
                        torrentPreparing = true
                        torrentError = ""
                    }

                    override fun onStreamError(torrent: Torrent?, e: Exception?) {
                        torrentPreparing = false
                        torrentError = e?.let { error ->
                            "Torrent error: ${error.message ?: error.javaClass.simpleName}"
                        } ?: "Could not start torrent"
                    }

                    override fun onStreamReady(torrent: Torrent?) {
                        try {
                            torrentPreparing = false

                            val readyTorrent = torrent
                            if (readyTorrent == null) {
                                torrentError = "Torrent finished without a playable file"
                                return
                            }

                            val videoFile = readyTorrent.videoFile
                            if (!videoFile.exists() || !videoFile.isFile) {
                                torrentError = "Torrent video file is not available"
                                return
                            }

                            if (!torrentDownloadMode) {
                                val bytes = videoFile.length()
                                playing = VideoItem(
                                    messageId = 0L,
                                    title = torrentSourceTitle.ifBlank { videoFile.name },
                                    info = "Torrent  |  ${bytes / 1048576} MB",
                                    fileId = -1,
                                    size = bytes,
                                    localPath = videoFile.absolutePath,
                                    torrent = readyTorrent
                                )
                                torrentSourceUrl = null
                            } else {
                                torrentError = "Download started: ${videoFile.name}"
                            }
                        } catch (e: Throwable) {
                            torrentPreparing = false
                            torrentError = "Could not prepare torrent video: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }

                    override fun onStreamProgress(
                        torrent: Torrent?,
                        status: StreamStatus?
                    ) {
                        torrentProgress = status?.bufferProgress ?: 0
                    }

                    override fun onStreamStopped() {
                        torrentPreparing = false
                    }
                })
            }
        } catch (e: Throwable) {
            torrentStream = null
            torrentPreparing = false
            torrentError = "Could not initialize torrent player: ${e.message ?: e.javaClass.simpleName}"
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


    private fun normalizeTorrentSource(rawUrl: String): String? {
        var decoded = rawUrl.trim()
        repeat(3) {
            val next = Uri.decode(decoded).replace("&amp;", "&")
            if (next == decoded) return@repeat
            decoded = next
        }

        if (isTorrentSource(decoded)) {
            return decoded
        }

        if (decoded.startsWith("intent://", ignoreCase = true)) {
            val marker = "s.browser_fallback_url="
            val start = decoded.lowercase().indexOf(marker)
            if (start >= 0) {
                val fallback = decoded.substring(start + marker.length).substringBefore(";")
                val candidate = Uri.decode(fallback).replace("&amp;", "&")
                if (isTorrentSource(candidate)) {
                    return candidate
                }
            }
        }

        return null
    }


    private fun isTorrentSource(rawUrl: String): Boolean {
        val value = rawUrl.trim().replace("&amp;", "&")
        return value.startsWith("magnet:", ignoreCase = true) ||
            value.substringBefore("?").substringBefore("#")
                .endsWith(".torrent", ignoreCase = true) ||
            value.contains(".torrent?", ignoreCase = true)
    }


    private fun magnetQueryParameter(rawUrl: String, name: String): String? {
        val query = rawUrl.substringAfter("?", "").substringBefore("#")
        return query.split("&").asSequence()
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = try {
                    URLDecoder.decode(part.substring(0, separator), "UTF-8")
                } catch (_: Exception) {
                    part.substring(0, separator)
                }
                if (key != name) return@mapNotNull null
                try {
                    URLDecoder.decode(part.substring(separator + 1), "UTF-8")
                } catch (_: Exception) {
                    part.substring(separator + 1)
                }
            }
            .firstOrNull()
    }


    private fun torrentTitle(rawUrl: String): String {
        val value = Uri.decode(rawUrl.trim())
        if (value.startsWith("magnet:", ignoreCase = true)) {
            val displayName = magnetQueryParameter(value, "dn")
            if (!displayName.isNullOrBlank()) return displayName
            return "Magnet torrent"
        }

        return value.substringAfterLast("/")
            .substringBefore("?")
            .substringBefore("#")
            .ifBlank { "Torrent file" }
    }


    private fun showTorrentSource(rawUrl: String) {
        val value = normalizeTorrentSource(rawUrl) ?: return
        torrentSourceUrl = value
        torrentSourceTitle = torrentTitle(value)
        torrentSourceSize = if (value.startsWith("magnet:", ignoreCase = true)) {
            val bytes = magnetQueryParameter(value, "xl")?.toLongOrNull()
            if (bytes != null) {
                String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824.0)
            } else {
                "Magnet torrent"
            }
        } else {
            "Torrent file"
        }
        torrentError = ""
        torrentProgress = 0
    }


    private fun startTorrent(download: Boolean) {
        val source = torrentSourceUrl ?: return
        torrentDownloadMode = download
        torrentPreparing = true
        torrentError = ""
        torrentProgress = 0
        try {
            if (torrentStream == null) initTorrentStream()
            val stream = torrentStream ?: throw IllegalStateException("Torrent engine is not initialized")
            stream.startStream(source)
        } catch (e: Throwable) {
            torrentPreparing = false
            torrentError = "Could not start torrent: ${e.message ?: e.javaClass.simpleName}"
        }
    }


    private fun stopTorrent() {
        try {
            torrentStream?.stopStream()
        } catch (e: Throwable) {
            torrentError = "Could not stop torrent: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            torrentPreparing = false
        }
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

        when {
            currentVideo != null -> PlayerScreen(currentVideo)
            browserOpen -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen()
                    if (torrentSourceUrl != null) {
                        TorrentSourceDialog()
                    }
                }
            }
            torrentSourceUrl != null -> TorrentSourceDialog()
            else -> ListScreen()
        }
    }


    @Composable
    private fun PlayerScreen(
        item: VideoItem
    ) {
        val context = LocalContext.current
        val preferences = remember(context) {
            context.getSharedPreferences(
                SUBTITLE_PREFERENCES,
                Context.MODE_PRIVATE
            )
        }
        var subtitleColorId by remember(preferences) {
            mutableStateOf(
                preferences.getString(
                    SUBTITLE_COLOR_KEY,
                    DEFAULT_SUBTITLE_COLOR_ID
                ) ?: DEFAULT_SUBTITLE_COLOR_ID
            )
        }
        var showSubtitleColorOptions by remember {
            mutableStateOf(false)
        }
        val selectedSubtitlePreset = subtitleColorPresets.firstOrNull {
            it.id == subtitleColorId
        } ?: subtitleColorPresets.first()
        val activity = context as? Activity

        var error by remember {
            mutableStateOf("")
        }

        var playbackState by remember(item.fileId) {
            mutableStateOf(Player.STATE_BUFFERING)
        }

        var isVideoPlaying by remember(item.fileId) {
            mutableStateOf(false)
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

        val player = remember(item.fileId, item.localPath, item.torrent) {
            val torrent = item.torrent
            val source = if (torrent != null) {
                val factory = DataSource.Factory {
                    TorrentDataSource(torrent, item.size)
                }
                val uri = Uri.fromFile(File(item.localPath ?: torrent.videoFile.absolutePath))
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(uri))
            } else if (item.localPath != null) {
                ProgressiveMediaSource.Factory(
                    DefaultDataSource.Factory(context)
                ).createMediaSource(
                    MediaItem.fromUri(Uri.fromFile(File(item.localPath)))
                )
            } else {
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

                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(
                        MediaItem.fromUri(uri)
                    )
            }

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    VIDEO_MIN_BUFFER_MS,
                    VIDEO_MAX_BUFFER_MS,
                    VIDEO_START_BUFFER_MS,
                    VIDEO_REBUFFER_BUFFER_MS
                )
                .build()

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                )

            val exo = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .build()

            exo.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )

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
                isVideoPlaying = player.isPlaying
                if (isVideoPlaying) {
                    showSubtitleColorOptions = false
                }
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
                if (item.localPath != null && !torrentDownloadMode) {
                    stopTorrent()
                }
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
                        subtitleView?.setStyle(
                            subtitleCaptionStyle(selectedSubtitlePreset.color.toInt())
                        )
                        keepScreenOn = true
                        useController = true
                        resizeMode =
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { playerView ->
                    playerView.subtitleView?.setStyle(
                        subtitleCaptionStyle(selectedSubtitlePreset.color.toInt())
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!isVideoPlaying) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showSubtitleColorOptions = !showSubtitleColorOptions
                        }
                    ) {
                        Text("Subtitle: ${selectedSubtitlePreset.label}")
                    }
    
                    if (showSubtitleColorOptions) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Subtitle color")
                                subtitleColorPresets.forEach { preset ->
                                    TextButton(
                                        onClick = {
                                            subtitleColorId = preset.id
                                            preferences.edit()
                                                .putString(SUBTITLE_COLOR_KEY, preset.id)
                                                .apply()
                                            showSubtitleColorOptions = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (preset.id == subtitleColorId) {
                                                "✓ ${preset.label}"
                                            } else {
                                                preset.label
                                            },
                                            color = Color(preset.color)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (playbackState == Player.STATE_BUFFERING) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Starting in",
                        color = Color(0xFFFF3B4D),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val etaSeconds = estimatedStartSeconds
                    Text(
                        text = etaSeconds?.let { "${it}s" } ?: "…",
                        color = Color.Red,
                        style = MaterialTheme.typography.headlineMedium
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
    private fun TorrentSourceDialog() {
        val source = torrentSourceUrl ?: return
        val isMagnet = source.startsWith("magnet:", ignoreCase = true)

        BackHandler {
            torrentSourceUrl = null
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            color = Color(0xD9000000)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF1B1B1F)
                ) {
                    Column {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF08A9E3)
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ktele_player_logo),
                                    contentDescription = "K-Tele Player",
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = "K-fast Downloader",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(22.dp)) {
                            Text(
                                text = torrentSourceTitle,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            Text(
                                text = "Select Download Source",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF202126)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(58.dp),
                                        shape = RoundedCornerShape(29.dp),
                                        color = Color(0xFF78C842)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "µ",
                                                color = Color.White,
                                                fontSize = 42.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.size(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Torrent", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(torrentSourceSize, color = Color(0xFFC0C5D7))
                                    }
                                    Text(
                                        text = "◉",
                                        color = Color(0xFF00D9FF),
                                        fontSize = 30.sp
                                    )
                                }
                            }

                            if (torrentError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(torrentError, color = Color(0xFFFF6B84))
                            }
                            if (torrentPreparing) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Column {
                                        Text("Finding peers and preparing video…", color = Color.White)
                                        if (torrentProgress > 0) {
                                            Text("Buffered: ${torrentProgress}%", color = Color(0xFFB9C2D0))
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (torrentPreparing) stopTorrent()
                                    torrentSourceUrl = null
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("CLOSE", color = Color(0xFF00CFFF)) }
                            TextButton(
                                onClick = { startTorrent(download = false) },
                                enabled = !torrentPreparing,
                                modifier = Modifier.weight(1f)
                            ) { Text("PLAY", color = Color(0xFF00CFFF)) }
                            TextButton(
                                onClick = { startTorrent(download = true) },
                                enabled = !torrentPreparing,
                                modifier = Modifier.weight(1f)
                            ) { Text("DOWNLOAD", color = Color(0xFF00CFFF)) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BrowserScreen() {
        val initialUrl = "https://www.google.com"
        var urlText by remember { mutableStateOf(initialUrl) }
        var browserView by remember { mutableStateOf<WebView?>(null) }

        fun openUrl(rawUrl: String, view: WebView?) {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) {
                return
            }

            if (normalizeTorrentSource(trimmed) != null) {
                showTorrentSource(trimmed)
                return
            }

            val target = if (
                trimmed.startsWith("http://") ||
                trimmed.startsWith("https://")
            ) {
                trimmed
            } else {
                "https://www.google.com/search?q=" + Uri.encode(trimmed)
            }

            urlText = target
            view?.loadUrl(target)
        }

        BackHandler {
            val view = browserView
            if (view?.canGoBack() == true) {
                view.goBack()
            } else {
                browserOpen = false
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                browserView?.stopLoading()
                browserView?.destroy()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { browserOpen = false }
                ) {
                    Text("Home")
                }

                Spacer(
                    modifier = Modifier.size(8.dp)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Website or search") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Spacer(
                    modifier = Modifier.size(8.dp)
                )

                Button(
                    onClick = { openUrl(urlText, browserView) }
                ) {
                    Text("Go")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { browserView?.goBack() }
                ) {
                    Text("Back")
                }

                TextButton(
                    onClick = { browserView?.goForward() }
                ) {
                    Text("Forward")
                }

                TextButton(
                    onClick = { browserView?.reload() }
                ) {
                    Text("Reload")
                }
            }

            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webChromeClient = WebChromeClient()
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun openTorrent(url: String) {
                                runOnUiThread { showTorrentSource(url) }
                            }
                        }, "KTeleTorrent")
                        setDownloadListener { url, _, _, _, _ ->
                            showTorrentSource(url)
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val url = request.url.toString()
                                if (normalizeTorrentSource(url) != null) {
                                    showTorrentSource(url)
                                    return true
                                }
                                return false
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                url: String
                            ): Boolean {
                                if (normalizeTorrentSource(url) != null) {
                                    showTorrentSource(url)
                                    return true
                                }
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): android.webkit.WebResourceResponse? {
                                val url = request.url.toString()
                                if (normalizeTorrentSource(url) != null) {
                                    runOnUiThread { showTorrentSource(url) }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String
                            ) {
                                urlText = url
                                view.evaluateJavascript(TORRENT_LINK_HOOK, null)
                            }
                        }
                        loadUrl(initialUrl)
                        browserView = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
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

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            Button(
                                onClick = { browserOpen = true }
                            ) {
                                Text("Open Browser")
                            }

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

                        Button(
                            onClick = { browserOpen = true }
                        ) {
                            Text("Open Browser")
                        }

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

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
