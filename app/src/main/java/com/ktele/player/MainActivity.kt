package com.ktele.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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


private data class UiMetrics(
    val screenPadding: Dp,
    val cardPadding: Dp,
    val controlHeight: Dp,
    val logoSize: Dp,
    val mediaButtonHeight: Dp
)

@Composable
private fun rememberUiMetrics(): UiMetrics {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.coerceAtLeast(320)

    return when {
        isTelevisionDevice(context) -> UiMetrics(
            screenPadding = 20.dp,
            cardPadding = 14.dp,
            controlHeight = 52.dp,
            logoSize = 112.dp,
            mediaButtonHeight = 58.dp
        )
        widthDp <= 360 -> UiMetrics(
            screenPadding = 12.dp,
            cardPadding = 12.dp,
            controlHeight = 48.dp,
            logoSize = 82.dp,
            mediaButtonHeight = 52.dp
        )
        widthDp <= 600 -> UiMetrics(
            screenPadding = 16.dp,
            cardPadding = 14.dp,
            controlHeight = 50.dp,
            logoSize = 96.dp,
            mediaButtonHeight = 56.dp
        )
        else -> UiMetrics(
            screenPadding = 24.dp,
            cardPadding = 18.dp,
            controlHeight = 54.dp,
            logoSize = 112.dp,
            mediaButtonHeight = 60.dp
        )
    }
}

@Composable
private fun AdaptiveLogo(
    modifier: Modifier = Modifier,
    sizeOverride: Dp? = null
) {
    val ui = rememberUiMetrics()
    AppLogo(modifier.size(sizeOverride ?: ui.logoSize))
}

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


data class CatalogMovie(
    val title: String,
    val category: String,
    val language: String,
    val quality: String,
    val size: String,
    val license: String,
    val description: String,
    val posterColor: Long
)

private val catalogCategories = listOf(
    "All", "Malayalam", "Tamil", "Hindi", "Hollywood", "Dubbed", "Others"
)

private const val MALAYALAM_RADIO_URL = "https://radiosindia.com/malayalamradio.html"

private val MALAYALAM_RADIO_STREAM_EXTENSIONS = listOf(
    ".mp3", ".aac", ".m3u8", ".pls", ".ogg", ".wav", ".flac"
)

private fun isMalayalamRadioPageUrl(rawUrl: String): Boolean {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return (uri.scheme.equals("http", ignoreCase = true) ||
        uri.scheme.equals("https", ignoreCase = true)) &&
        (host == "radiosindia.com" || host == "www.radiosindia.com") &&
        uri.path?.trimEnd('/') == "/malayalamradio.html"
}

private fun isMalayalamRadioStreamUrl(rawUrl: String): Boolean {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)) return false
    val path = uri.path?.lowercase().orEmpty()
    return MALAYALAM_RADIO_STREAM_EXTENSIONS.any { path.endsWith(it) }
}

private fun isAllowedMalayalamRadioUrl(rawUrl: String): Boolean =
    isMalayalamRadioPageUrl(rawUrl) || isMalayalamRadioStreamUrl(rawUrl)

// Demo entries are open/licensed films. Replace or extend these with your own licensed catalog.
private val catalogMovies = listOf(
    CatalogMovie(
        title = "Sintel",
        category = "Others",
        language = "English",
        quality = "1080p",
        size = "1.2 GB",
        license = "Creative Commons BY 3.0",
        description = "An open movie from the Blender Foundation, available for legal sharing.",
        posterColor = 0xFF4A2F68
    ),
    CatalogMovie(
        title = "Tears of Steel",
        category = "Hollywood",
        language = "English",
        quality = "1080p",
        size = "2.4 GB",
        license = "Creative Commons BY 3.0",
        description = "A science-fiction open movie made with free and open-source tools.",
        posterColor = 0xFF155D72
    ),
    CatalogMovie(
        title = "Big Buck Bunny",
        category = "Others",
        language = "English",
        quality = "1080p",
        size = "780 MB",
        license = "Creative Commons BY 3.0",
        description = "A family-friendly open movie that can be legally shared.",
        posterColor = 0xFF2E7652
    ),
    CatalogMovie(
        title = "Elephants Dream",
        category = "Others",
        language = "English",
        quality = "720p",
        size = "640 MB",
        license = "Creative Commons BY 2.5",
        description = "The first open movie from the Blender Foundation.",
        posterColor = 0xFF7A4B31
    )
)

private data class MovieSite(
    val name: String,
    val url: String
)

private val movieSites = listOf(
    MovieSite("AutoEmbed", "https://watch-v2.autoembed.app/"),
    MovieSite("NetMirror", "https://netmirror.center/"),
    MovieSite("Cineby", "https://cineby.my/movies")
)

data class IptvChannel(
    val name: String,
    val category: String,
    val streamUrl: String,
    val userAgent: String? = null,
    val referrer: String? = null
)

private const val DEFAULT_IPTV_PLAYLIST_URL = "https://iptv-org.github.io/iptv/index.m3u"
private const val IPTV_DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

private val iptvGroupPattern = Regex("""group-title="([^"]*)"""")
private val iptvAttributePattern = Regex("""([\w-]+)="([^"]*)"""")

private fun normalizeIptvCategory(groupTitle: String, channelName: String): String? {
    val searchable = (groupTitle + " " + channelName).lowercase()
    return when {
        "malayalam" in searchable -> "Malayalam"
        "tamil" in searchable -> "Tamil"
        "movie" in searchable || "cinema" in searchable -> "Movies"
        "song" in searchable || "music" in searchable -> "Songs"
        else -> null
    }
}

private fun parseM3uTitle(line: String): String {
    var quoted = false
    for (index in line.indices) {
        when {
            line[index] == '"' -> quoted = !quoted
            line[index] == ',' && !quoted -> return line.substring(index + 1).trim()
        }
    }
    return "Untitled channel"
}

private fun parseIptvPlaylist(contents: String): List<IptvChannel> {
    val channels = mutableListOf<IptvChannel>()
    var pendingName = ""
    var pendingGroup = ""
    var pendingUserAgent: String? = null
    var pendingReferrer: String? = null

    contents.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                val attributes = iptvAttributePattern.findAll(line)
                    .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                pendingName = parseM3uTitle(line)
                pendingGroup = attributes["group-title"].orEmpty()
                pendingUserAgent = attributes["http-user-agent"]
                    ?: attributes["user-agent"]
                pendingReferrer = attributes["http-referrer"]
                    ?: attributes["referrer"]
            }
            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                val option = line.substringAfter(':').trim()
                val key = option.substringBefore('=').trim().lowercase()
                val value = option.substringAfter('=', "").trim().trim('"')
                when (key) {
                    "http-user-agent", "user-agent" -> pendingUserAgent = value
                    "http-referrer", "referrer" -> pendingReferrer = value
                }
            }
            line.isNotEmpty() && !line.startsWith("#") && pendingName.isNotEmpty() -> {
                val category = normalizeIptvCategory(pendingGroup, pendingName) ?: "Other"
                channels += IptvChannel(
                    name = pendingName,
                    category = category,
                    streamUrl = line,
                    userAgent = pendingUserAgent,
                    referrer = pendingReferrer
                )
                pendingName = ""
                pendingGroup = ""
                pendingUserAgent = null
                pendingReferrer = null
            }
        }
    }

    return channels.distinctBy { it.streamUrl }
}

private fun buildIptvMediaItem(channel: IptvChannel): MediaItem {
    val lowerUrl = channel.streamUrl.lowercase()
    val builder = MediaItem.Builder().setUri(channel.streamUrl)
    when {
        ".m3u8" in lowerUrl -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        ".mpd" in lowerUrl -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
    }
    return builder.build()
}

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
private const val VIDEO_MIN_BUFFER_MS = 20_000
private const val VIDEO_MAX_BUFFER_MS = 40_000
private const val VIDEO_START_BUFFER_MS = 500
private const val VIDEO_REBUFFER_BUFFER_MS = 1_000
private const val FALLBACK_START_COUNTDOWN_SECONDS = 3L

// Use a small first range for quick startup, then larger ranges for throughput.
private const val TELEGRAM_STREAM_INITIAL_CHUNK_BYTES = 256L * 1024L
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
    private var input: RandomAccessFile? = null
    private var position = 0L
    private var endPosition = Long.MAX_VALUE
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)

        val startPosition = dataSpec.position.coerceAtLeast(0L)
        val videoFile = torrent.videoFile
        if (!videoFile.exists() || !videoFile.isFile) {
            throw IOException("Torrent video file is not available")
        }

        // Prioritize the target piece before ExoPlayer starts reading. RandomAccessFile
        // avoids walking through every preceding byte after a seek.
        torrent.setInterestedBytes(startPosition)
        val randomAccessFile = RandomAccessFile(videoFile, "r")
        randomAccessFile.seek(startPosition)
        input = randomAccessFile
        position = startPosition
        endPosition = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            startPosition + dataSpec.length
        } else {
            Long.MAX_VALUE
        }
        opened = true
        transferStarted(dataSpec)

        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            val currentLength = maxOf(knownFileSize, videoFile.length())
            if (currentLength > startPosition) {
                currentLength - startPosition
            } else {
                C.LENGTH_UNSET.toLong()
            }
        }
    }

    private fun waitForPiece(pieceOffset: Long): Boolean {
        while (!torrent.hasBytes(pieceOffset)) {
            // Keep the download window following the player as it reads forward.
            torrent.setInterestedBytes(pieceOffset)
            try {
                Thread.sleep(50L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val randomAccessFile = input ?: throw IOException("Torrent stream is not open")
        if (position >= endPosition) return C.RESULT_END_OF_INPUT

        val allowed = minOf(length.toLong(), endPosition - position).toInt()
        val pieceLength = torrent.getTorrentHandle().torrentFile().pieceLength().toLong()
        val untilPieceEnd = if (pieceLength > 0L) {
            pieceLength - (position % pieceLength)
        } else {
            allowed.toLong()
        }
        // Never wait for a whole large ExoPlayer request. Return the first ready
        // piece immediately so Media3 can start rendering while the next pieces load.
        val chunkLength = minOf(allowed.toLong(), untilPieceEnd).toInt()
        val pieceOffset = if (pieceLength > 0L) {
            (position / pieceLength) * pieceLength
        } else {
            position
        }
        if (!waitForPiece(pieceOffset)) return C.RESULT_END_OF_INPUT

        val count = randomAccessFile.read(buffer, offset, chunkLength)
        if (count <= 0) return C.RESULT_END_OF_INPUT

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

private val BLOCKED_AD_HOST_MARKERS = listOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.com",
    "adsystem.com",
    "adnxs.com",
    "amazon-adsystem.com",
    "popads.net",
    "popcash.net",
    "propellerads.com",
    "exoclick.com",
    "onclickads.net",
    "trafficjunky.com"
)

private val BLOCKED_AD_PATH_MARKERS = listOf(
    "/adserver",
    "/adservice",
    "/ads/",
    "/banner",
    "/popunder",
    "doubleclick",
    "googlesyndication",
    "googleadservices"
)

private fun isBlockedAdRequest(rawUrl: String): Boolean {
    val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    val host = parsed.host?.lowercase() ?: return false
    val lowerUrl = rawUrl.lowercase()
    return BLOCKED_AD_HOST_MARKERS.any { host == it || host.endsWith(".$it") } ||
        BLOCKED_AD_PATH_MARKERS.any { lowerUrl.contains(it) }
}

private const val AD_CLEANUP_HOOK = """
(function() {
    if (window.__kteleAdCleanupInstalled) return;
    window.__kteleAdCleanupInstalled = true;

    var markerPattern = /(^|[-_])(?:ad|ads|advert|advertisement|banner|popunder|sponsor)(?:$|[-_])/i;
    var urlPattern = /(doubleclick|googlesyndication|googleadservices|adservice|adsystem|adnxs|popads|popcash|propellerads|exoclick|onclickads|trafficjunky)/i;

    function hideIfAd(node) {
        if (!(node instanceof Element)) return;
        var id = node.id || '';
        var className = typeof node.className === 'string' ? node.className : '';
        var source = node.getAttribute('src') || node.getAttribute('data-src') || '';
        var markerText = id + ' ' + className;
        var hasAdMarker = markerPattern.test(markerText) || urlPattern.test(source);
        var tagIsAdMedia = /^(IFRAME|INS|SCRIPT)$/.test(node.tagName) && (hasAdMarker || urlPattern.test(source));
        var style = window.getComputedStyle(node);
        var rect = node.getBoundingClientRect();
        var floating = (style.position === 'fixed' || style.position === 'absolute') && Number(style.zIndex || 0) > 10;
        var centered = rect.width > 180 && rect.height > 60 && rect.top > 70 && rect.bottom < window.innerHeight - 60;
        var floatingMedia = floating && centered && (
            /^(IFRAME|INS|IMG)$/.test(node.tagName) ||
            !!node.querySelector('iframe, ins, img')
        );

        if (hasAdMarker || tagIsAdMedia || floatingMedia) {
            node.style.setProperty('display', 'none', 'important');
        }
    }

    function cleanAds(root) {
        if (!root || !root.querySelectorAll) return;
        hideIfAd(root);
        root.querySelectorAll('iframe, ins, img, script, [id], [class]').forEach(hideIfAd);
    }

    cleanAds(document.documentElement);
    new MutationObserver(function(records) {
        records.forEach(function(record) {
            record.addedNodes.forEach(function(node) {
                if (node.nodeType === 1) cleanAds(node);
            });
        });
    }).observe(document.documentElement, { childList: true, subtree: true });
})();
"""

@Composable
private fun AppLogo(
    modifier: Modifier = Modifier.size(96.dp)
) {
    Image(
        painter = painterResource(id = R.drawable.ktele_player_logo),
        contentDescription = "K- Univese logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

@Composable
private fun MediaPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = rememberUiMetrics()

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ui.mediaButtonHeight),
        shape = RoundedCornerShape(ui.mediaButtonHeight / 2f),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF13CFF0),
            contentColor = Color(0xFF001117)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 10.dp,
            pressedElevation = 3.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "▶",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Media",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


private fun isTelevisionDevice(context: Context): Boolean {
    val uiModeType = context.resources.configuration.uiMode and
        Configuration.UI_MODE_TYPE_MASK
    return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
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

    private var browserOpen by mutableStateOf(false)
    private var selectedBrowserUrl by mutableStateOf<String?>(null)
    private var radioOnlyMode by mutableStateOf(false)
    private var iptvOpen by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var homeOpen by mutableStateOf(true)
    private var menuOpen by mutableStateOf(false)
    private var mediaHubOpen by mutableStateOf(false)
    private var telegramLoginOpen by mutableStateOf(false)
    private var iptvPlaylistUrl by mutableStateOf(DEFAULT_IPTV_PLAYLIST_URL)
    private var iptvChannels by mutableStateOf<List<IptvChannel>>(emptyList())
    private var iptvLoading by mutableStateOf(false)
    private var iptvError by mutableStateOf("")
    private var selectedCatalogMovie by mutableStateOf<CatalogMovie?>(null)

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

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.rgb(5, 6, 11)
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 11)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
                .prepareSize(2L * 1024L * 1024L)
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


    private fun showTorrentSource(rawUrl: String, assumeTorrent: Boolean = false) {
        val value = if (assumeTorrent) rawUrl.trim() else normalizeTorrentSource(rawUrl) ?: return
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
            homeOpen -> HomeScreen()
            menuOpen -> MainMenuScreen()
            mediaHubOpen -> MediaHubScreen()
            telegramLoginOpen -> TelegramLoginScreen()
            browserOpen -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen()
                    if (torrentSourceUrl != null) {
                        TorrentSourceDialog()
                    }
                }
            }
            settingsOpen -> SettingsScreen()
            iptvOpen -> IptvScreen()
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
        val isTv = isTelevisionDevice(context)
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

                activity?.requestedOrientation = if (isTv) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
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
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                )

            val exo = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .build()

            exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)

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

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                        val torrent = item.torrent ?: return
                        val durationMs = exo.duration
                        if (durationMs <= 0L || item.size <= 0L) return

                        val targetByte = (
                            newPosition.positionMs.toDouble() / durationMs.toDouble() *
                                item.size.toDouble()
                            ).toLong().coerceIn(0L, item.size - 1L)
                        torrent.setInterestedBytes(targetByte)
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
            var bufferingStartedAtMs = previousSampleTimeMs
            var wasBuffering = false

            while (true) {
                val nowMs = SystemClock.elapsedRealtime()
                val currentState = player.playbackState
                val currentBufferedDurationMs =
                    player.totalBufferedDuration.coerceAtLeast(0L)
                val elapsedMs = nowMs - previousSampleTimeMs
                val bufferedDeltaMs =
                    currentBufferedDurationMs - previousBufferedDurationMs

                if (currentState == Player.STATE_BUFFERING) {
                    if (!wasBuffering) {
                        bufferingStartedAtMs = nowMs
                        wasBuffering = true
                    }
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
                    wasBuffering = false
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
                            remainingBufferMs == 0L -> 1L
                            hasBufferRate && bufferedMsPerWallMs > 0.0 -> {
                                ceil(
                                    remainingBufferMs.toDouble() /
                                        bufferedMsPerWallMs /
                                        1_000.0
                                ).toLong().coerceAtLeast(1L)
                            }
                            else -> {
                                (FALLBACK_START_COUNTDOWN_SECONDS -
                                    ((nowMs - bufferingStartedAtMs) / 1_000L))
                                    .coerceAtLeast(1L)
                            }
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
            AppLogo(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(72.dp)
            )

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

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isVideoPlaying) {
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
                                    contentDescription = "K- Univese",
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = "K- Univese",
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
    private fun CatalogScreen() {
        val ui = rememberUiMetrics()
        val selected = selectedCatalogMovie
        if (selected != null) {
            CatalogDetailScreen(selected)
            return
        }

        var selectedCategory by remember { mutableStateOf("All") }
        val visibleMovies = if (selectedCategory == "All") {
            catalogMovies
        } else {
            catalogMovies.filter { it.category == selectedCategory }
        }

        BackHandler { iptvOpen = false }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ui.screenPadding)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ktele_player_logo),
                contentDescription = "K- Univese logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(ui.logoSize)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Movie Catalog", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Open and licensed titles",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB9C2D0)
                    )
                }
                TextButton(onClick = { iptvOpen = false }) {
                    Text("Home")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(catalogCategories) { category ->
                    Button(
                        onClick = { selectedCategory = category },
                        enabled = selectedCategory != category,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(category)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (visibleMovies.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(ui.cardPadding)) {
                        Text("No licensed titles yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add your own Malayalam, Tamil, Hindi or dubbed titles to the catalog data.",
                            color = Color(0xFFB9C2D0)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleMovies.chunked(2)) { rowMovies ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowMovies.forEach { movie ->
                                CatalogPosterCard(
                                    movie = movie,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCatalogMovie = movie }
                                )
                            }
                            if (rowMovies.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CatalogPosterCard(
        movie: CatalogMovie,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Card(
            modifier = modifier
                .padding(6.dp)
                .clickable(onClick = onClick)
                .focusable(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(Color(movie.posterColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            movie.title.take(2).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text("OPEN MOVIE", color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(movie.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(movie.language + " • " + movie.quality, style = MaterialTheme.typography.bodySmall)
                    Text(movie.size, style = MaterialTheme.typography.bodySmall, color = Color(0xFF13CFF0))
                }
            }
        }
    }

    @Composable
    private fun CatalogDetailScreen(movie: CatalogMovie) {
        BackHandler { selectedCatalogMovie = null }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            AdaptiveLogo(Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { selectedCatalogMovie = null }) {
                Text("Back to catalog")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(width = 132.dp, height = 190.dp)
                        .background(Color(movie.posterColor), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        movie.title.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(movie.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(movie.category + " • " + movie.language)
                    Text(movie.quality + " • " + movie.size, color = Color(0xFF13CFF0))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Licensed: " + movie.license, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(movie.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Licensed file", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Add your own licensed file or torrent URL to enable playback/download for this title.",
                        color = Color(0xFFB9C2D0)
                    )
                }
            }
        }
    }

    private suspend fun loadIptvPlaylist(sourceUrl: String) {
        val trimmedUrl = sourceUrl.trim()
        if (trimmedUrl.isEmpty()) {
            iptvError = "Enter an M3U playlist URL in Settings"
            return
        }

        iptvLoading = true
        iptvError = ""
        try {
            val rawPlaylist = withContext(Dispatchers.IO) {
                val connection = URL(trimmedUrl).openConnection() as? HttpURLConnection
                    ?: throw IOException("Unsupported playlist URL")
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", IPTV_DEFAULT_USER_AGENT)
                    connection.connect()
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        throw IOException("Playlist server returned HTTP $status")
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }
            iptvPlaylistUrl = trimmedUrl
            iptvChannels = parseIptvPlaylist(rawPlaylist)
            if (iptvChannels.isEmpty()) {
                iptvError = "No channels found in this playlist"
            }
        } catch (exception: Exception) {
            iptvError = "Could not load playlist: ${exception.message ?: "check the URL"}"
            iptvChannels = emptyList()
        } finally {
            iptvLoading = false
        }
    }

    @Composable
    private fun IptvScreen() {
        val context = LocalContext.current
        val ui = rememberUiMetrics()
        val favoritePreferences = remember(context) {
            context.getSharedPreferences("iptv_favorites", Context.MODE_PRIVATE)
        }
        var searchQuery by remember { mutableStateOf("") }
        var favoritesOnly by remember { mutableStateOf(false) }
        var favoriteUrls by remember(favoritePreferences) {
            mutableStateOf(
                favoritePreferences.getStringSet("urls", emptySet())?.toSet().orEmpty()
            )
        }
        var selectedChannel by remember { mutableStateOf<IptvChannel?>(null) }

        LaunchedEffect(Unit) {
            val savedUrl = favoritePreferences.getString("playlist_url", null)
            if (!savedUrl.isNullOrBlank()) {
                iptvPlaylistUrl = savedUrl
            }
            if (iptvChannels.isEmpty() && !iptvLoading) {
                loadIptvPlaylist(savedUrl ?: iptvPlaylistUrl)
            }
        }

        fun toggleFavorite(channel: IptvChannel) {
            favoriteUrls = if (favoriteUrls.contains(channel.streamUrl)) {
                favoriteUrls - channel.streamUrl
            } else {
                favoriteUrls + channel.streamUrl
            }
            favoritePreferences.edit().putStringSet("urls", favoriteUrls).apply()
        }

        val normalizedQuery = searchQuery.trim()
        val matchingChannels = iptvChannels.filter { channel ->
            val matchesSearch = normalizedQuery.isEmpty() ||
                channel.name.contains(normalizedQuery, ignoreCase = true)
            val matchesFavorites = !favoritesOnly ||
                favoriteUrls.contains(channel.streamUrl)
            matchesSearch && matchesFavorites
        }

        if (selectedChannel != null) {
            IptvPlayerScreen(
                channel = selectedChannel!!,
                onClose = { selectedChannel = null }
            )
            return
        }

        BackHandler { iptvOpen = false }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ui.screenPadding)
        ) {
            AdaptiveLogo(Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("IPTV", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        iptvOpen = false
                        settingsOpen = true
                    }) {
                        Text("Settings")
                    }
                    TextButton(onClick = { iptvOpen = false }) {
                        Text("Home")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search channels") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Favorites", style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = favoritesOnly,
                        onCheckedChange = { favoritesOnly = it }
                    )
                }
            }

            if (iptvError.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(iptvError, color = Color(0xFFFF6B84))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (iptvLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (matchingChannels.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(ui.cardPadding)) {
                        Text(
                            if (searchQuery.trim().isEmpty()) {
                                "No channels loaded"
                            } else {
                                "No channels match \"$searchQuery\""
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (searchQuery.trim().isNotEmpty()) {
                                "Try another channel name or clear the search."
                            } else {
                                "Load channels from Settings to see them here."
                            },
                            color = Color(0xFFB9C2D0)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(matchingChannels, key = { it.streamUrl }) { channel ->
                        IptvChannelRow(
                            channel = channel,
                            isFavorite = favoriteUrls.contains(channel.streamUrl),
                            onToggleFavorite = { toggleFavorite(channel) },
                            onPlay = { selectedChannel = channel }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun IptvChannelRow(
        channel: IptvChannel,
        isFavorite: Boolean,
        onToggleFavorite: () -> Unit,
        onPlay: () -> Unit
    ) {
        var isFocused by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color(0xFF13CFF0) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onPlay)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(channel.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onToggleFavorite) {
                    Text(if (isFavorite) "★" else "☆")
                }
                Button(onClick = onPlay) {
                    Text("Play")
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        val context = LocalContext.current
        val ui = rememberUiMetrics()
        val preferences = remember(context) {
            context.getSharedPreferences("iptv_favorites", Context.MODE_PRIVATE)
        }
        var playlistUrl by remember { mutableStateOf(iptvPlaylistUrl) }
        val scope = rememberCoroutineScope()

        BackHandler { settingsOpen = false }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ui.screenPadding)
        ) {
            AdaptiveLogo(Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { settingsOpen = false }) {
                    Text("Home")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ui.cardPadding)) {
                    Text("IPTV playlist", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Change the M3U source and load channels directly in K- Univese.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = playlistUrl,
                        onValueChange = { playlistUrl = it },
                        label = { Text("M3U playlist URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val trimmedUrl = playlistUrl.trim()
                                iptvPlaylistUrl = trimmedUrl
                                preferences.edit().putString("playlist_url", trimmedUrl).apply()
                                scope.launch { loadIptvPlaylist(trimmedUrl) }
                            },
                            enabled = !iptvLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (iptvLoading) "Loading…" else "Save & load")
                        }
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("IPTV playlist", playlistUrl)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy link")
                        }
                    }
                    if (iptvError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(iptvError, color = Color(0xFFFF6B84))
                    }
                    if (!iptvLoading && iptvChannels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            iptvChannels.size.toString() + " supported channels loaded",
                            color = Color(0xFF13CFF0)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun IptvPlayerScreen(
        channel: IptvChannel,
        onClose: () -> Unit
    ) {
        val context = LocalContext.current
        val activity = context as? Activity
        val isTv = isTelevisionDevice(context)
        var playerError by remember(channel.streamUrl) { mutableStateOf("") }
        var controlsVisible by remember(channel.streamUrl) { mutableStateOf(true) }
        var fillVideo by remember(channel.streamUrl) { mutableStateOf(true) }
        val player = remember(
            channel.streamUrl,
            channel.userAgent,
            channel.referrer
        ) {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent(channel.userAgent ?: IPTV_DEFAULT_USER_AGENT)
            channel.referrer?.takeIf { it.isNotBlank() }?.let { referrer ->
                httpFactory.setDefaultRequestProperties(mapOf("Referer" to referrer))
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, httpFactory)
            )

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    setMediaItem(buildIptvMediaItem(channel))
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            val cause = error.cause?.message ?: error.message.orEmpty()
                            playerError = "Playback error: ${error.errorCodeName}" +
                                if (cause.isBlank()) "" else " - $cause"
                        }
                    })
                    prepare()
                }
        }

        DisposableEffect(player) {
            onDispose { player.release() }
        }

        DisposableEffect(Unit) {
            val window = activity?.window
            val actionBar = activity?.actionBar
            val restoreActionBar = actionBar?.isShowing == true
            val controller = window?.let { currentWindow ->
                WindowCompat.getInsetsController(
                    currentWindow,
                    currentWindow.decorView
                )
            }

            actionBar?.hide()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window?.let { currentWindow ->
                WindowCompat.setDecorFitsSystemWindows(currentWindow, false)
                currentWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                @Suppress("DEPRECATION")
                currentWindow.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            onDispose {
                window?.let { currentWindow ->
                    currentWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    @Suppress("DEPRECATION")
                    currentWindow.decorView.systemUiVisibility = 0
                    WindowCompat.setDecorFitsSystemWindows(currentWindow, true)
                }
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (restoreActionBar) actionBar?.show()
                activity?.requestedOrientation = if (isTv) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }

        BackHandler { onClose() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = true
                        controllerHideOnTouch = true
                        controllerShowTimeoutMs = 4000
                        setControllerVisibilityListener(
                            object : PlayerView.ControllerVisibilityListener {
                                override fun onVisibilityChanged(visibility: Int) {
                                    controlsVisible = visibility == View.VISIBLE
                                }
                            }
                        )
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        resizeMode = if (fillVideo) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.resizeMode = if (fillVideo) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Keep this out of the way during playback. Pause the channel to
            // switch between a cropped full-screen picture and the full frame.
            if (controlsVisible) {
                Button(
                    onClick = { fillVideo = !fillVideo },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                ) {
                    Text(if (fillVideo) "Fit" else "Fill")
                }
            }

            if (playerError.isNotEmpty()) {
                Text(
                    playerError,
                    color = Color(0xFFFF6B84),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }

    private fun openInAppBrowser(url: String, radioOnly: Boolean = false) {
        selectedBrowserUrl = url
        radioOnlyMode = radioOnly
        homeOpen = false
        menuOpen = false
        mediaHubOpen = false
        settingsOpen = false
        iptvOpen = false
        browserOpen = true
    }

    private fun openMovieSite(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(browserIntent)
        } catch (_: Exception) {
            openInAppBrowser(url)
        }
    }

    @Composable
    private fun HomeScreen() {
        val openMenu: () -> Unit = {
            homeOpen = false
            menuOpen = true
        }

        LaunchedEffect(Unit) {
            delay(1400)
            openMenu()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { openMenu() }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ktele_player_logo),
                contentDescription = "K- Univese logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(220.dp)
            )
        }
    }

    @Composable
    private fun MainMenuScreen() {
        val context = LocalContext.current
        val ui = rememberUiMetrics()
        val firstFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            if (isTelevisionDevice(context)) {
                delay(100)
                runCatching { firstFocusRequester.requestFocus() }
            }
        }

        BackHandler {
            menuOpen = false
            homeOpen = true
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ui.screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            AdaptiveLogo(Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(20.dp))

            MediaPlayButton(
                modifier = Modifier.focusRequester(firstFocusRequester),
                onClick = {
                    menuOpen = false
                    mediaHubOpen = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    menuOpen = false
                    telegramLoginOpen = true
                }
            ) {
                Text("Telegram Login")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ടെലിഗ്രാം വീഡിയോസ് കാണുന്നതിനായി ലോഗിൻ ചെയ്യുക",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB9C2D0)
            )
        }
    }

    @Composable
    private fun TelegramLoginScreen() {
        val ui = rememberUiMetrics()
        var input by remember { mutableStateOf("") }

        BackHandler {
            telegramLoginOpen = false
            menuOpen = true
        }

        LaunchedEffect(stage) {
            if (stage == "ready") {
                telegramLoginOpen = false
                mediaHubOpen = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (stage == "ready") Modifier
                    else Modifier.verticalScroll(rememberScrollState())
                )
                .padding(ui.screenPadding),
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ktele_player_logo),
                contentDescription = "K- Univese logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(ui.logoSize)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Telegram Login",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (stage == "starting") {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Starting Telegram...")
            } else if (stage == "error") {
                Text(message.ifBlank { "Something went wrong" })
            } else {
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ടെലിഗ്രാം വീഡിയോസ് കാണുന്നതിനായി ലോഗിൻ ചെയ്യുക",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB9C2D0)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        submit(input)
                        input = ""
                    }
                ) {
                    Text("Next")
                }
            }

            if (message.isNotEmpty() && stage != "error") {
                Spacer(modifier = Modifier.height(16.dp))
                Text(message)
            }
        }
    }

    @Composable
    private fun MediaHubScreen() {
        val ui = rememberUiMetrics()

        BackHandler {
            mediaHubOpen = false
            menuOpen = true
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ui.screenPadding),
            verticalArrangement = Arrangement.Top
        ) {
            AdaptiveLogo(Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Media", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Choose how you want to browse",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB9C2D0)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { mediaHubOpen = false; settingsOpen = true }) {
                        Text("Settings")
                    }
                    TextButton(onClick = {
                        mediaHubOpen = false
                        menuOpen = true
                    }) {
                        Text("Back")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ui.cardPadding)) {
                    Text("IPTV", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Use the native IPTV player with your own playlist.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            mediaHubOpen = false
                            iptvOpen = true
                        }
                    ) {
                        Text("Open IPTV")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ui.cardPadding)) {
                    Text("Movies", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Open a movie site in K- Univese's built-in browser.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        movieSites.forEach { site ->
                            TextButton(
                                onClick = { openMovieSite(site.url) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(site.name, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ui.cardPadding)) {
                    Text("Music", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Listen to music online.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { openMovieSite("https://listenfree.in/") }) {
                        Text("Open Music")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ui.cardPadding)) {
                    Text("Malayalam Radio", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Listen to Malayalam radio stations.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = {
                        openInAppBrowser(MALAYALAM_RADIO_URL, radioOnly = true)
                    }) {
                        Text("Open Malayalam Radio")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ui.cardPadding)) {
                    Text("Telegram Videos", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Login to watch your Telegram videos.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            mediaHubOpen = false
                            telegramLoginOpen = true
                        }
                    ) {
                        Text("Telegram Login")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ui.cardPadding)) {
                    Text("Torrent Video Browser", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Open torrent video websites or search the web inside the app.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            mediaHubOpen = false
                            selectedBrowserUrl = null
                            browserOpen = true
                        }
                    ) {
                        Text("Torrent Video Browser")
                    }
                }
            }
        }
    }

    @Composable
    private fun BrowserScreen() {
        val browserContext = LocalContext.current
        val bookmarkPreferences = remember(browserContext) {
            browserContext.getSharedPreferences("browser_bookmarks", Context.MODE_PRIVATE)
        }
        val initialUrl = selectedBrowserUrl ?: "https://www.google.com"
        var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
        var searchQuery by remember { mutableStateOf("") }
        var browserStartUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
        var browserHome by remember(initialUrl) { mutableStateOf(selectedBrowserUrl == null) }
        var browserView by remember { mutableStateOf<WebView?>(null) }
        var bookmarks by remember(bookmarkPreferences) {
            mutableStateOf(
                bookmarkPreferences.getStringSet("urls", emptySet())?.toList().orEmpty()
            )
        }

        fun handleSpecialUrl(rawUrl: String, view: WebView?): Boolean {
            val trimmed = rawUrl.trim()
            if (
                trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return false
            }

            return try {
                val intent = if (trimmed.startsWith("intent://", ignoreCase = true)) {
                    Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                }
                val fallbackUrl = intent
                    .getStringExtra("browser_fallback_url")
                    ?.let { Uri.decode(it).replace("&amp;", "&") }
                val canOpenExternally = intent.resolveActivity(packageManager) != null

                if (canOpenExternally) {
                    startActivity(intent)
                    true
                } else if (
                    !fallbackUrl.isNullOrBlank() &&
                    (fallbackUrl.startsWith("http://", ignoreCase = true) ||
                        fallbackUrl.startsWith("https://", ignoreCase = true))
                ) {
                    urlText = fallbackUrl
                    browserStartUrl = fallbackUrl
                    browserHome = false
                    view?.loadUrl(fallbackUrl)
                    true
                } else {
                    true
                }
            } catch (_: Exception) {
                true
            }
        }

        fun openUrl(rawUrl: String, view: WebView?) {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return

            if (radioOnlyMode && !isAllowedMalayalamRadioUrl(trimmed)) return

            if (normalizeTorrentSource(trimmed) != null) {
                showTorrentSource(trimmed)
                return
            }

            if (
                trimmed.contains("://") ||
                trimmed.startsWith("mailto:", ignoreCase = true) ||
                trimmed.startsWith("tel:", ignoreCase = true)
            ) {
                if (handleSpecialUrl(trimmed, view)) return
            }

            val encodedQuery = Uri.encode(trimmed)
            val target = if (
                trimmed.startsWith("http://") ||
                trimmed.startsWith("https://")
            ) {
                trimmed
            } else {
                "https://www.google.com/search?q=$encodedQuery"
            }

            urlText = target
            browserStartUrl = target
            browserHome = false
            view?.loadUrl(target)
        }

        fun addBookmark() {
            val value = urlText.trim()
            if (
                (value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)) &&
                !bookmarks.contains(value)
            ) {
                bookmarks = bookmarks + value
            }
        }

        BackHandler {
            val view = browserView
            if (view?.canGoBack() == true) {
                view.goBack()
            } else if (!browserHome) {
                view?.stopLoading()
                view?.destroy()
                browserView = null
                browserHome = true
                urlText = initialUrl
            } else {
                selectedBrowserUrl = null
                radioOnlyMode = false
                browserOpen = false
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                browserView?.stopLoading()
                browserView?.destroy()
            }
        }

        if (browserHome) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF08080B))
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ktele_player_logo),
                        contentDescription = "K- Univese logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("K- Univese", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        selectedBrowserUrl = null
                        browserOpen = false
                    }) { Text("Home") }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(42.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ktele_player_logo),
                        contentDescription = "K- Univese logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(78.dp)
                    )
                    Text("K- Univese", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Google") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { openUrl(searchQuery, null) }) {
                                Text("Go")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Torrent വെബ്സൈറ്റിൽ സന്ദർശിക്കുക. .torrent, 🧲 magnet file ക്ലിക്ക് ചെയ്യുക ഡയറക്റ്റ് വീഡിയോ പ്ലേ ചെയ്യുന്നതാണ്.",
                        color = Color(0xFFB9C2D0),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔖  Bookmarks", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(bookmarks.size.toString(), color = Color(0xFF13CFF0))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (bookmarks.isEmpty()) {
                                Text("No bookmarks yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Search Google and tap Add Bookmark to save it.",
                                    color = Color(0xFFB9C2D0)
                                )
                            } else {
                                bookmarks.forEach { bookmark ->
                                    TextButton(
                                        onClick = { openUrl(bookmark, null) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(bookmark, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }

                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { browserHome = true }) { Text("⌂  Home") }
                    TextButton(onClick = { }) { Text("⇩  Download") }
                    TextButton(onClick = { }) { Text("✓  Completed") }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                AdaptiveLogo(sizeOverride = 54.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        browserView?.stopLoading()
                        browserView?.destroy()
                        browserView = null
                        selectedBrowserUrl = null
                        radioOnlyMode = false
                        browserHome = true
                    }) {
                        Text("Home")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Website or search") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = { openUrl(urlText, browserView) }) {
                        Text("Go")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { browserView?.goBack() }) { Text("Back") }
                    TextButton(onClick = { addBookmark() }) { Text("Bookmark") }
                    TextButton(onClick = { browserView?.goForward() }) { Text("Forward") }
                    TextButton(onClick = { browserView?.reload() }) { Text("Reload") }
                }

            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
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
                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                            val isTorrentDownload =
                                mimeType.equals("application/x-bittorrent", ignoreCase = true) ||
                                    contentDisposition?.contains(".torrent", ignoreCase = true) == true
                            showTorrentSource(url, assumeTorrent = isTorrentDownload)
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val url = request.url.toString()
                                if (radioOnlyMode && !isAllowedMalayalamRadioUrl(url)) return true
                                if (normalizeTorrentSource(url) != null) {
                                    showTorrentSource(url)
                                    return true
                                }
                                if (handleSpecialUrl(url, view)) {
                                    return true
                                }
                                return false
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                url: String
                            ): Boolean {
                                if (radioOnlyMode && !isAllowedMalayalamRadioUrl(url)) return true
                                if (normalizeTorrentSource(url) != null) {
                                    showTorrentSource(url)
                                    return true
                                }
                                if (handleSpecialUrl(url, view)) {
                                    return true
                                }
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val url = request.url.toString()
                                if (isBlockedAdRequest(url)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                                if (normalizeTorrentSource(url) != null) {
                                    runOnUiThread { showTorrentSource(url) }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldInterceptRequest(
                                view: WebView,
                                url: String
                            ): WebResourceResponse? {
                                if (isBlockedAdRequest(url)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                                return super.shouldInterceptRequest(view, url)
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String
                            ) {
                                urlText = url
                                view.evaluateJavascript(TORRENT_LINK_HOOK, null)
                                view.evaluateJavascript(AD_CLEANUP_HOOK, null)
                            }
                        }
                        loadUrl(browserStartUrl)
                        browserView = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
            }
        }
    }


    @Composable
        private fun ListScreen() {
            val ui = rememberUiMetrics()
            var input by remember {
                mutableStateOf("")
            }

            BackHandler(enabled = openChatId != null) {
                openChatId = null
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ui.screenPadding),
                verticalArrangement = if (stage == "ready") {
                    Arrangement.Top
                } else {
                    Arrangement.Center
                }
            ) {
                AppLogo(
                    Modifier
                        .size(ui.logoSize)
                        .align(Alignment.CenterHorizontally)
                )

                if (stage == "ready") {
                    Text(
                        text = "K- Univese",
                        style = MaterialTheme.typography.headlineMedium
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
                            MediaPlayButton(
                                onClick = { mediaHubOpen = true }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Your chats",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(
                                modifier = Modifier.height(12.dp)
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
                                            .focusable()
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
                                            .focusable()
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

                        MediaPlayButton(
                            onClick = { mediaHubOpen = true }
                        )

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
