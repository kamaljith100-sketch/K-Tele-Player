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
import android.webkit.WebSettings

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView

import coil.compose.AsyncImage

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


private const val MALAYALAM_RADIO_URL = "https://radiosindia.com/malayalamradio.html"
private const val MUSIC_SITE_URL = "https://listenfree.in/"

private val MALAYALAM_RADIO_STREAM_EXTENSIONS = listOf(
    ".mp3", ".aac", ".m3u8", ".m3u", ".pls", ".ogg", ".wav", ".flac"
)

private val MALAYALAM_RADIO_STREAM_MARKERS = listOf(
    "stream", "radio", "live", "listen", "audio", "icecast", "shoutcast", "playlist"
)

private fun isMalayalamRadioPageUrl(rawUrl: String): Boolean {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)
    ) return false
    if (host != "radiosindia.com" && host != "www.radiosindia.com") return false

    val path = uri.path?.trimEnd('/').orEmpty()
    return path.isEmpty() || path == "/" ||
        path.endsWith(".html", ignoreCase = true) ||
        path.endsWith(".php", ignoreCase = true)
}

private fun isMalayalamRadioStreamUrl(rawUrl: String): Boolean {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)
    ) return false

    val lowerUrl = rawUrl.lowercase()
    val path = uri.path?.lowercase().orEmpty()
    val hasAudioExtension = MALAYALAM_RADIO_STREAM_EXTENSIONS.any { path.endsWith(it) }
    val hasStreamMarker = MALAYALAM_RADIO_STREAM_MARKERS.any { marker ->
        uri.host?.lowercase()?.contains(marker) == true ||
            path.contains(marker) ||
            lowerUrl.contains("$marker=") ||
            lowerUrl.contains("$marker?")
    }
    return hasAudioExtension || hasStreamMarker
}

private fun isAllowedMalayalamRadioUrl(rawUrl: String): Boolean =
    isMalayalamRadioPageUrl(rawUrl) || isMalayalamRadioStreamUrl(rawUrl)

data class IptvChannel(
    val name: String,
    val category: String,
    val streamUrl: String,
    val userAgent: String? = null,
    val referrer: String? = null
)

private data class MalayalamRadioStation(
    val name: String,
    val frequency: String,
    val imageUrl: String,
    val streamUrls: List<String>,
    val pageUrl: String? = null
)

private fun malayalamRadioFavoriteKey(station: MalayalamRadioStation): String =
    station.pageUrl ?: "${station.name}|${station.frequency}"

private val malayalamRadioStations = listOf(
    MalayalamRadioStation(
        name = "Mirchi Kochi",
        frequency = "98.3 FM",
        imageUrl = "https://radiosindia.com/images/radiomirchi.jpg",
        streamUrls = listOf(
            "https://stream.aiir.com/dbv0rxpwp6ytv",
            "https://sp14.instainternet.com/8050/stream"
        ),
        pageUrl = "https://radiosindia.com/radiomirchimalayalam.html"
    ),
    MalayalamRadioStation(
        name = "Club FM",
        frequency = "94.3 FM",
        imageUrl = "https://radiosindia.com/images/clubfm.jpg",
        streamUrls = listOf(
            "https://listen.openstream.co/4635/audio",
            "https://listen.openstream.co/4626/audio"
        ),
        pageUrl = "https://radiosindia.com/clubfm.html"
    ),
    MalayalamRadioStation(
        name = "Radio Mango",
        frequency = "91.9 FM",
        imageUrl = "https://radiosindia.com/images/radiomango.jpg",
        streamUrls = listOf(
            "https://stream.radiomango.fm/live",
            "https://radiomangoalive1-a.akamaihd.net/9268677ef77949a9b21d33239a55eadd/ap-southeast-1/6034685947001/playlist.m3u8"
        ),
        pageUrl = "https://radiosindia.com/radiomango.html"
    ),
    MalayalamRadioStation(
        name = "Radio Suno",
        frequency = "91.7 FM",
        imageUrl = "https://radiosindia.com/images/radiosuno.jpg",
        streamUrls = listOf("https://playerservices.streamtheworld.com/api/livestream-redirect/SUNO917_SC"),
        pageUrl = "https://radiosindia.com/radiosunomalayalam.html"
    ),
    MalayalamRadioStation(
        name = "Home FM",
        frequency = "Online radio",
        imageUrl = "https://radiosindia.com/images/homefm.jpg",
        streamUrls = listOf("https://centova.aarenworld.com/proxy/922radiokhushi/stream"),
        pageUrl = "https://radiosindia.com/homefm.html"
    )
)

private val malayalamRadioNavigationNames = setOf(
    "home",
    "hindi",
    "akashvani",
    "bollywood",
    "tamil",
    "malayalam",
    "kannada",
    "punjabi"
)

private fun cleanMalayalamRadioStationName(rawName: String): String =
    rawName
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun resolveMalayalamRadioUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        else -> "https://radiosindia.com/${trimmed.trimStart('/')}"
    }
}

/*
 * The directory still serves its station artwork over HTTP. Android can play
 * cleartext streams (the manifest explicitly allows it), but Coil may reject
 * or fail to follow those image redirects on some devices. Prefer HTTPS for
 * artwork while keeping the original stream URL untouched so stations whose
 * audio server only supports HTTP can still play.
 */
private fun resolveMalayalamRadioImageUrl(rawUrl: String): String {
    val resolved = resolveMalayalamRadioUrl(rawUrl)
    return if (resolved.startsWith("http://", ignoreCase = true)) {
        "https://${resolved.substringAfter("://")}"
    } else {
        resolved
    }
}

private fun splitMalayalamRadioStreamCandidates(rawValue: String): List<String> =
    rawValue
        .replace("\\/", "/")
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003d", "=", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .split(Regex("""\s+or\s+|\s*\|\s*""", RegexOption.IGNORE_CASE))
        .map { it.trim().trimEnd('.', ',', ';', ')', ']', '}') }
        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .distinct()

private fun isDirectMalayalamRadioStreamUrl(rawUrl: String): Boolean {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true) &&
        !uri.scheme.equals("https", ignoreCase = true)
    ) return false
    if (host == "radiosindia.com" || host == "www.radiosindia.com") return false

    val path = uri.path?.lowercase().orEmpty()
    val webAssetExtensions = listOf(
        ".html", ".htm", ".php", ".css", ".js", ".jpg", ".jpeg", ".png",
        ".gif", ".webp", ".svg", ".woff", ".woff2", ".ttf", ".ico"
    )
    return webAssetExtensions.none { path.endsWith(it) }
}

private fun directoryMalayalamRadioStation(
    name: String,
    pagePath: String,
    imagePath: String? = null
): MalayalamRadioStation = MalayalamRadioStation(
    name = name,
    frequency = "Online radio",
    imageUrl = imagePath?.let(::resolveMalayalamRadioImageUrl)
        ?: resolveMalayalamRadioImageUrl(
            malayalamRadioImageOverrides[pagePath.substringAfterLast('/')]
                ?: "images/${pagePath.substringAfterLast('/').substringBeforeLast('.')}.jpg"
        ),
    streamUrls = emptyList(),
    pageUrl = resolveMalayalamRadioUrl(pagePath)
)

private val malayalamRadioImageOverrides = mapOf(
    "radiosunobahrain.html" to "images/radiosunobh.jpg",
    "986malayalamradio.html" to "images/986malayalamradio.jpg",
    "ananthapurifm.html" to "images/air.jpg",
    "radiokeralam.html" to "images/radiokeralam.jpg",
    "airmalayalam.html" to "images/air.jpg",
    "kochifm.html" to "images/air.jpg",
    "airdevikulam.html" to "images/air.jpg",
    "airkozhikodefm.html" to "images/airkozhikodefm.jpg",
    "radio90fm.html" to "images/radio90fm1.jpg",
    "radiokerala.html" to "images/keralaradio1.jpg",
    "airkannur.html" to "images/airkannurfm.jpg",
    "manjerifm.html" to "images/air.jpg",
    "akashvanithrissur.html" to "images/airthrissur.jpg",
    "aahafmradio.html" to "images/aahafm.jpg",
    "junefm.html" to "images/junefm.webp"
)

private val malayalamRadioIgnoredNames = setOf(
    "about",
    "about us",
    "help / faq",
    "add radio",
    "contact us",
    "facebook",
    "twitter",
    "terms of use",
    "privacy policy",
    "report abuse/dmca",
    "legal"
)

private val malayalamRadioDirectoryFallback = listOf(
    "Suno Bahrain" to "radiosunobahrain.html",
    "Ananthapuri" to "ananthapurifm.html",
    "Radio Keralam" to "radiokeralam.html",
    "98.6 FM" to "986malayalamradio.html",
    "Live FM 1072" to "livefm1072.html",
    "Radio Lemon" to "radiolemonlive.html",
    "AIR Kerala" to "airmalayalam.html",
    "Kochi FM" to "kochifm.html",
    "AIR Devikulam" to "airdevikulam.html",
    "Kozhikode FM" to "airkozhikodefm.html",
    "Radio 90 FM" to "radio90fm.html",
    "Radio Kerala" to "radiokerala.html",
    "Nammude Radio" to "nammuderadio.html",
    "My Radio FM" to "myradio90fm.html",
    "AIR Kannur" to "airkannur.html",
    "Hello Radio" to "helloradio.html",
    "Benziger FM" to "radiobenziger.html",
    "Aaha FM" to "aahafmradio.html",
    "Manjeri FM" to "manjerifm.html",
    "AIR Thrissur" to "akashvanithrissur.html",
    "Radio Neythal" to "radioneythal.html",
    "Radio Mattoli" to "radiomattoli.html",
    "Ente Radio" to "enteradio.html",
    "Janvani FM" to "janvanifm.html",
    "Radio Macfast" to "radiomacfast.html",
    "Global Radio" to "globalradio.html",
    "Radio Malabar" to "radiomalabar.html",
    "Ahalia FM" to "ahaliafm.html",
    "June FM" to "junefm.html",
    "Radio Mangalam" to "radiomangalam.html",
    "Sargakshetra FM" to "sargakshetrafm.html"
).map { (name, pagePath) -> directoryMalayalamRadioStation(name, pagePath) }

private suspend fun loadMalayalamRadioDirectory(): List<MalayalamRadioStation> =
    withContext(Dispatchers.IO) {
        val connection = URL(MALAYALAM_RADIO_URL).openConnection() as? HttpURLConnection
            ?: return@withContext emptyList()

        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                IPTV_DEFAULT_USER_AGENT
            )
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext emptyList()

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            // Read each station card as one unit. The directory contains a few
            // malformed anchors without a closing </a>; matching anchors globally
            // can then pair one station's link with the next station's image/name.
            val stationCardPattern = Regex(
                """<div\b[^>]*class\s*=\s*["'][^"']*grid_1_of_2[^"']*["'][^>]*>([\s\S]*?)</div>""",
                RegexOption.IGNORE_CASE
            )
            val stationLinkPattern = Regex(
                """<a\b[^>]*href\s*=\s*["']([^"']+\.html?)["']""",
                RegexOption.IGNORE_CASE
            )
            val stationNamePattern = Regex(
                """<p\b[^>]*>([\s\S]*?)</p>""",
                RegexOption.IGNORE_CASE
            )
            val stationImagePattern = Regex(
                """<img\b[^>]*(?:src|data-src|data-lazy-src)\s*=\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            )
            val seenUrls = malayalamRadioStations
                .mapNotNull { it.pageUrl }
                .toMutableSet()

            stationCardPattern.findAll(html).mapNotNull { match ->
                val cardHtml = match.groupValues[1]
                val href = stationLinkPattern.find(cardHtml)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: return@mapNotNull null
                val pageUrl = resolveMalayalamRadioUrl(href)
                val imagePath = stationImagePattern.find(cardHtml)
                    ?.groupValues
                    ?.getOrNull(1)
                val rawName = stationNamePattern.find(cardHtml)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: Regex("""(?:alt|title)\s*=\s*["']([^"']+)["']""")
                        .find(cardHtml)
                        ?.groupValues
                        ?.getOrNull(1)
                val name = rawName?.let(::cleanMalayalamRadioStationName).orEmpty()
                val lowerName = name.lowercase()
                if (
                    name.isBlank() ||
                    lowerName in malayalamRadioNavigationNames ||
                    lowerName in malayalamRadioIgnoredNames ||
                    pageUrl == MALAYALAM_RADIO_URL ||
                    !seenUrls.add(pageUrl)
                ) {
                    null
                } else {
                    directoryMalayalamRadioStation(name, pageUrl, imagePath)
                }
            }.toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

private suspend fun loadMalayalamRadioStationStreams(
    pageUrl: String
): List<String> = withContext(Dispatchers.IO) {
    val connection = URL(pageUrl).openConnection() as? HttpURLConnection
        ?: return@withContext emptyList()

    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty(
            "User-Agent",
            IPTV_DEFAULT_USER_AGENT
        )
        connection.connect()
        if (connection.responseCode !in 200..299) return@withContext emptyList()

        val html = connection.inputStream.bufferedReader().use { it.readText() }
        val candidates = mutableListOf<String>()

        // RadiosIndia uses Playerjs and stores several fallback streams in one
        // value separated by "or". Keep each URL separate so ExoPlayer can try
        // the next stream when the first provider is offline.
        val directValuePattern = Regex(
            """(?:file|contentUrl|urlTemplate|streamUrl|source|src)\s*["']?\s*[:=]\s*["']((?:\\.|[^"'])+)["']""",
            RegexOption.IGNORE_CASE
        )
        directValuePattern.findAll(html).forEach { match ->
            candidates += splitMalayalamRadioStreamCandidates(match.groupValues[1])
        }

        val mediaSourcePattern = Regex(
            """<(?:audio|source)\b[^>]*(?:src|data-src)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        mediaSourcePattern.findAll(html).forEach { match ->
            candidates += splitMalayalamRadioStreamCandidates(
                resolveMalayalamRadioUrl(match.groupValues[1])
            )
        }

        // Also support pages that expose a stream as a plain absolute URL.
        val absoluteUrlPattern = Regex("""https?:\\?/\\?/[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        candidates += absoluteUrlPattern
            .findAll(html)
            .map {
                it.value
                    .replace("\\/", "/")
                    .trimEnd('.', ',', ';', ')', ']', '}')
            }
            .filter(::isMalayalamRadioStreamUrl)
            .toList()

        candidates
            .asSequence()
            .filter(::isDirectMalayalamRadioStreamUrl)
            .distinct()
            .take(5)
            .toList()
    } catch (_: Exception) {
        emptyList()
    } finally {
        connection.disconnect()
    }
}

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

private fun buildMalayalamRadioMediaItem(
    station: MalayalamRadioStation,
    streamIndex: Int
): MediaItem = buildIptvMediaItem(
    IptvChannel(
        name = station.name,
        category = "Malayalam Radio",
        streamUrl = station.streamUrls[streamIndex]
    )
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
private const val VIDEO_MIN_BUFFER_MS = 20_000
private const val VIDEO_MAX_BUFFER_MS = 40_000
private const val VIDEO_START_BUFFER_MS = 500
private const val VIDEO_REBUFFER_BUFFER_MS = 1_000
private const val FALLBACK_START_COUNTDOWN_SECONDS = 3L
private const val TORRENT_PREPARE_BYTES = 512L * 1024L

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
        var rawLink = node.getAttribute('href') || node.getAttribute('data-href') || node.getAttribute('data-url') || '';
        var link = rawLink.toLowerCase().indexOf('magnet:') === 0 ? rawLink : (node.href || rawLink);
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
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adservice.google.com", "adsystem.com", "adnxs.com", "amazon-adsystem.com",
    "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
    "onclickads.net", "trafficjunky.com", "adskeeper.com", "adsterra.com",
    "monetag.com", "clickadu.com", "juicyads.com", "hilltopads.net",
    "ad-maven.com", "adf.ly", "linkvertise.com", "taboola.com", "outbrain.com",
    "revcontent.com", "mgid.com", "criteo.com", "media.net", "quantserve.com",
    "scorecardresearch.com", "sharethrough.com", "bidvertiser.com", "appier.com",
    "googletagmanager.com", "googletagservices.com"
)

private val BLOCKED_AD_PATH_MARKERS = listOf(
    "/adserver", "/adservice", "/ads/", "/ads?", "/banner", "/banners/",
    "/popunder", "/popup", "/adframe", "/ad_iframe", "/advert", "/sponsor",
    "doubleclick", "googlesyndication", "googleadservices", "googletagmanager",
    "googletagservices", "ad_script", "adsbygoogle"
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
    var urlPattern = /(doubleclick|googlesyndication|googleadservices|adservice|adsystem|adnxs|popads|popcash|propellerads|exoclick|onclickads|trafficjunky|adskeeper|adsterra|monetag|clickadu|juicyads|hilltopads|ad-maven|taboola|outbrain|revcontent|mgid|criteo|media\.net|quantserve|scorecardresearch|sharethrough|bidvertiser|googletagmanager|googletagservices)/i;

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

private const val MUSIC_BRANDING_HOOK = """
(function() {
    if (window.__kteleMusicBrandingInstalled) return;
    window.__kteleMusicBrandingInstalled = true;
    var brandPattern = /(?:listen\s*free|free\s*listen)(?:\.in)?/ig;

    function replaceBranding(root) {
        if (!root) return;
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        var node;
        while ((node = walker.nextNode())) {
            if (node.parentElement && node.parentElement.closest('#__kteleBrandBadge')) continue;
            if (brandPattern.test(node.nodeValue || '')) {
                node.nodeValue = node.nodeValue.replace(brandPattern, 'K Universe');
            }
            brandPattern.lastIndex = 0;
        }
        root.querySelectorAll('img, source').forEach(function(media) {
            var label = ((media.getAttribute('alt') || '') + ' ' +
                (media.getAttribute('title') || '') + ' ' +
                (media.getAttribute('src') || '')).toLowerCase();
            if (brandPattern.test(label)) {
                media.style.setProperty('display', 'none', 'important');
            }
            brandPattern.lastIndex = 0;
        });
    }

    function addBrandBadge() {
        if (document.getElementById('__kteleBrandBadge') || !document.body) return;
        var badge = document.createElement('div');
        badge.id = '__kteleBrandBadge';
        badge.innerHTML = '<span style="font-size:18px;line-height:1">✦</span><span>K Universe</span>';
        badge.style.cssText = 'position:fixed;top:8px;left:8px;z-index:2147483647;display:flex;align-items:center;gap:7px;padding:7px 11px;border-radius:18px;background:rgba(5,15,10,.92);color:#8cf5a7;font:700 13px sans-serif;box-shadow:0 2px 12px rgba(0,0,0,.35);pointer-events:none';
        document.body.appendChild(badge);
    }

    replaceBranding(document.documentElement);
    addBrandBadge();
    new MutationObserver(function(records) {
        records.forEach(function(record) {
            record.addedNodes.forEach(function(node) {
                if (node.nodeType === 1) replaceBranding(node);
            });
        });
        addBrandBadge();
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
    private var musicBrowserOpen by mutableStateOf(false)
    private var selectedBrowserUrl by mutableStateOf<String?>(null)
    private var radioOnlyMode by mutableStateOf(false)
    private var malayalamRadioOpen by mutableStateOf(false)
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
                // Start playback after a small initial buffer. TorrentDataSource
                // will request the following pieces as ExoPlayer advances.
                .prepareSize(TORRENT_PREPARE_BYTES)
                .build()

            torrentStream = TorrentStream.init(options).also { stream ->
                stream.addListener(object : TorrentListener {
                    override fun onStreamPrepared(torrent: Torrent?) {
                        torrent?.setInterestedBytes(0L)
                    }

                    override fun onStreamStarted(torrent: Torrent?) {
                        torrentPreparing = true
                        torrentError = ""
                        torrent?.setInterestedBytes(0L)
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
            musicBrowserOpen -> MusicBrowserScreen()
            malayalamRadioOpen -> MalayalamRadioScreen()
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
    private fun MalayalamRadioScreen() {
        val context = LocalContext.current
        val ui = rememberUiMetrics()
        var stations by remember { mutableStateOf(malayalamRadioStations) }
        var directoryLoading by remember { mutableStateOf(true) }
        var selectedStation by remember { mutableStateOf<MalayalamRadioStation?>(null) }
        var streamIndex by remember { mutableStateOf(0) }
        var isPlaying by remember { mutableStateOf(false) }
        var radioError by remember { mutableStateOf("") }
        var loadingStationName by remember { mutableStateOf<String?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var favoritesOnly by remember { mutableStateOf(false) }
        val favoritePreferences = remember(context) {
            context.getSharedPreferences("malayalam_radio_favorites", Context.MODE_PRIVATE)
        }
        var favoriteKeys by remember(favoritePreferences) {
            mutableStateOf(
                favoritePreferences.getStringSet("station_keys", emptySet())?.toSet().orEmpty()
            )
        }
        val scope = rememberCoroutineScope()

        fun toggleFavorite(station: MalayalamRadioStation) {
            val key = malayalamRadioFavoriteKey(station)
            favoriteKeys = if (favoriteKeys.contains(key)) {
                favoriteKeys - key
            } else {
                favoriteKeys + key
            }
            favoritePreferences.edit().putStringSet("station_keys", favoriteKeys).apply()
        }

        val normalizedSearchQuery = searchQuery.trim().lowercase()
        val visibleStations = stations.filter { station ->
            val searchableText = "${station.name} ${station.frequency}".lowercase()
            val matchesSearch = normalizedSearchQuery.isEmpty() ||
                searchableText.contains(normalizedSearchQuery)
            val matchesFavorites = !favoritesOnly ||
                favoriteKeys.contains(malayalamRadioFavoriteKey(station))
            matchesSearch && matchesFavorites
        }

        LaunchedEffect(Unit) {
            val discoveredStations = loadMalayalamRadioDirectory()
            stations = malayalamRadioStations +
                (discoveredStations + malayalamRadioDirectoryFallback)
                    .distinctBy { it.pageUrl ?: it.name }
            directoryLoading = false
        }

        val player = remember(context) {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }
        }

        fun playStation(station: MalayalamRadioStation) {
            selectedStation = station
            streamIndex = 0
            radioError = ""
            player.setMediaItem(buildMalayalamRadioMediaItem(station, streamIndex))
            player.prepare()
            player.play()
            isPlaying = true
        }

        fun startStation(station: MalayalamRadioStation) {
            if (station.streamUrls.isEmpty()) {
                if (loadingStationName == station.name) return
                val pageUrl = station.pageUrl ?: return
                loadingStationName = station.name
                radioError = ""
                scope.launch {
                    val streams = loadMalayalamRadioStationStreams(pageUrl)
                    loadingStationName = null
                    if (streams.isEmpty()) {
                        radioError =
                            "${station.name} stream കണ്ടെത്താനായില്ല. വീണ്ടും Play അമർത്തൂ."
                    } else {
                        val resolvedStation = station.copy(streamUrls = streams)
                        // Keep the resolved URL list in the visible station item.
                        // This avoids downloading the same station page every time
                        // the user pauses and starts that station again.
                        stations = stations.map { current ->
                            if (current.pageUrl == station.pageUrl) resolvedStation else current
                        }
                        playStation(resolvedStation)
                    }
                }
                return
            }

            val sameStation = selectedStation?.name == station.name
            if (sameStation && player.isPlaying) {
                player.pause()
                isPlaying = false
                return
            }
            if (sameStation && player.playbackState != Player.STATE_IDLE) {
                player.play()
                isPlaying = true
                radioError = ""
                return
            }
            playStation(station)
        }

        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: PlaybackException) {
                    val station = selectedStation
                    if (station != null && streamIndex + 1 < station.streamUrls.size) {
                        streamIndex += 1
                        player.setMediaItem(buildMalayalamRadioMediaItem(station, streamIndex))
                        player.prepare()
                        player.play()
                        radioError = ""
                    } else {
                        isPlaying = false
                        radioError = "${station?.name ?: "Radio"} stream ഇപ്പോൾ ലഭ്യമല്ല. മറ്റൊരു station തിരഞ്ഞെടുക്കൂ."
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }

        fun closeRadio() {
            player.stop()
            malayalamRadioOpen = false
            mediaHubOpen = true
        }

        BackHandler { closeRadio() }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(ui.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            userScrollEnabled = true
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AdaptiveLogo()
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Malayalam Radio", style = MaterialTheme.typography.headlineMedium)
                        Text("Malayalam FM radio stations online", color = Color(0xFFB9C2D0))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = {
                                openInAppBrowser(MALAYALAM_RADIO_URL, radioOnly = true)
                            }
                        ) {
                            Text("More")
                        }
                        TextButton(onClick = { closeRadio() }) {
                            Text("Back")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "താഴെയുള്ള ചാനലുകൾ കാണാൻ മുകളിലേക്ക് swipe ചെയ്യുക",
                    color = Color(0xFF13CFF0),
                    style = MaterialTheme.typography.bodySmall
                )
                if (directoryLoading) {
                    Text(
                        "കൂടുതൽ stations load ചെയ്യുന്നു…",
                        color = Color(0xFFB9C2D0),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search radio stations") },
                        singleLine = true
                    )
                    TextButton(
                        onClick = { favoritesOnly = !favoritesOnly },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(if (favoritesOnly) "All Stations" else "Favorites")
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(ui.cardPadding)) {
                        Text(
                            if (selectedStation == null) "ഒരു station തിരഞ്ഞെടുക്കൂ"
                            else "Now playing: ${selectedStation!!.name}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (isPlaying) "● LIVE"
                            else "Play ബട്ടൺ അമർത്തി കേൾക്കാം",
                            color = if (isPlaying) Color(0xFF55E39B) else Color(0xFFB9C2D0)
                        )
                        if (radioError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(radioError, color = Color(0xFFFF6B84))
                        }
                    }
                }
            }

            if (visibleStations.isEmpty()) {
                item {
                    Text(
                        if (favoritesOnly) {
                            "No favorite radio stations yet. Tap ☆ on a station to save it."
                        } else {
                            "No radio stations match \"$searchQuery\"."
                        },
                        color = Color(0xFFB9C2D0),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(visibleStations, key = { it.pageUrl ?: it.name }) { station ->
                    MalayalamRadioStationCard(
                        station = station,
                        isCurrent = selectedStation?.name == station.name,
                        isPlaying = isPlaying,
                        isLoading = loadingStationName == station.name,
                        isFavorite = favoriteKeys.contains(malayalamRadioFavoriteKey(station)),
                        onPlayPause = { startStation(station) },
                        onToggleFavorite = { toggleFavorite(station) }
                    )
                }
            }
        }
    }

    @Composable
    private fun MalayalamRadioStationCard(
        station: MalayalamRadioStation,
        isCurrent: Boolean,
        isPlaying: Boolean,
        isLoading: Boolean,
        isFavorite: Boolean,
        onPlayPause: () -> Unit,
        onToggleFavorite: () -> Unit
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = station.imageUrl,
                    contentDescription = station.name,
                    placeholder = painterResource(id = R.drawable.ktele_player_logo),
                    error = painterResource(id = R.drawable.ktele_player_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 112.dp, height = 78.dp)
                        .background(Color(0xFF20242D), RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium)
                    Text(station.frequency, color = Color(0xFFB9C2D0))
                    if (isCurrent && isPlaying) {
                        Text("Playing now", color = Color(0xFF55E39B), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = onToggleFavorite,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (isFavorite) "★ Favorite" else "☆ Favorite",
                            color = if (isFavorite) Color(0xFFFFD54F) else Color(0xFFB9C2D0)
                        )
                    }
                    Button(onClick = onPlayPause) {
                        Text(
                            when {
                                isLoading -> "Loading"
                                station.streamUrls.isEmpty() -> "Play"
                                isCurrent && isPlaying -> "Pause"
                                else -> "Play"
                            }
                        )
                    }
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

    private fun openMusicInAppBrowser() {
        homeOpen = false
        menuOpen = false
        mediaHubOpen = false
        settingsOpen = false
        iptvOpen = false
        browserOpen = false
        musicBrowserOpen = true
    }


      @Composable
      private fun MusicBrowserScreen() {
          var musicWebView by remember { mutableStateOf<WebView?>(null) }
          var musicTitle by remember { mutableStateOf("K Universe") }
          var isMusicLoading by remember { mutableStateOf(true) }
          var isPlaying by remember { mutableStateOf(false) }
          var isLiked by remember { mutableStateOf(false) }
          var shuffleEnabled by remember { mutableStateOf(false) }

          fun runMusicScript(script: String) {
              musicWebView?.evaluateJavascript(script, null)
          }

          fun closeMusicBrowser() {
              musicWebView?.stopLoading()
              musicWebView?.destroy()
              musicWebView = null
              musicBrowserOpen = false
              mediaHubOpen = true
          }

          fun togglePlayback() {
              isPlaying = !isPlaying
              val action = if (isPlaying) "play()" else "pause()"
              runMusicScript("document.querySelectorAll('audio,video').forEach(function(media){ try { media.$action; } catch(e) {} });")
          }

          BackHandler {
              val view = musicWebView
              if (view?.canGoBack() == true) view.goBack() else closeMusicBrowser()
          }

          DisposableEffect(Unit) {
              onDispose {
                  musicWebView?.stopLoading()
                  musicWebView?.destroy()
              }
          }

          Column(
              modifier = Modifier
                  .fillMaxSize()
                  .background(Color(0xFF101010))
          ) {
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .background(Color(0xFF111111))
                      .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  TextButton(onClick = { closeMusicBrowser() }) {
                      Text("‹", color = Color.White, fontSize = 30.sp)
                  }
                  AppLogo(modifier = Modifier.size(34.dp))
                  Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                      Text(
                          "K Universe",
                          color = Color.White,
                          style = MaterialTheme.typography.titleLarge,
                          fontWeight = FontWeight.Bold
                      )
                      Text(
                          if (isMusicLoading) "Loading music..." else musicTitle,
                          color = Color(0xFFB7B7B7),
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 1
                      )
                  }
                  TextButton(onClick = { musicWebView?.reload() }) {
                      Text("⋮", color = Color.White, fontSize = 24.sp)
                  }
              }

              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .background(Color(0xFF171717))
                      .padding(horizontal = 18.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  AppLogo(modifier = Modifier.size(96.dp))
                  Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                      Text("K Universe Music", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                      Text("Play music inside K-Tele", color = Color(0xFFBDBDBD), fontSize = 14.sp)
                      Spacer(modifier = Modifier.height(10.dp))
                      Box(
                          modifier = Modifier
                              .fillMaxWidth()
                              .height(3.dp)
                              .background(Color(0xFF5E5E5E))
                      ) {
                          Box(
                              modifier = Modifier
                                  .fillMaxWidth(if (isPlaying) 0.34f else 0.08f)
                                  .height(3.dp)
                                  .background(Color.White)
                          )
                      }
                      Row(
                          modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Text(if (isPlaying) "Playing" else "Ready", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                          Text("K Universe", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                      }
                  }
              }

              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .background(Color(0xFF171717))
                      .padding(horizontal = 22.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
              ) {
                  TextButton(onClick = { shuffleEnabled = !shuffleEnabled }) {
                      Text("↝", color = if (shuffleEnabled) Color(0xFF8CF5A7) else Color.White, fontSize = 28.sp)
                  }
                  TextButton(onClick = { musicWebView?.goBack() }) {
                      Text("|‹", color = Color.White, fontSize = 26.sp)
                  }
                  TextButton(
                      onClick = { togglePlayback() },
                      modifier = Modifier.size(62.dp)
                  ) {
                      Text(if (isPlaying) "Ⅱ" else "▶", color = Color.Black, fontSize = 25.sp)
                  }
                  TextButton(onClick = { musicWebView?.reload() }) {
                      Text("›|", color = Color.White, fontSize = 26.sp)
                  }
                  TextButton(onClick = { isLiked = !isLiked }) {
                      Text(if (isLiked) "♥" else "♡", color = if (isLiked) Color(0xFF8CF5A7) else Color.White, fontSize = 28.sp)
                  }
              }

              AndroidView(
                  factory = { viewContext ->
                      WebView(viewContext).apply {
                          setBackgroundColor(android.graphics.Color.rgb(12, 12, 12))
                          settings.javaScriptEnabled = true
                          settings.domStorageEnabled = true
                          settings.javaScriptCanOpenWindowsAutomatically = false
                          settings.setSupportMultipleWindows(false)
                          settings.mediaPlaybackRequiresUserGesture = false
                          settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                          settings.allowContentAccess = true
                          settings.allowFileAccess = true
                          webChromeClient = WebChromeClient()
                          webViewClient = object : WebViewClient() {
                              override fun shouldOverrideUrlLoading(
                                  view: WebView,
                                  request: WebResourceRequest
                              ): Boolean {
                                  val url = request.url.toString()
                                  if (isBlockedAdRequest(url)) return true
                                  val scheme = request.url.scheme.orEmpty().lowercase()
                                  return scheme != "http" && scheme != "https"
                              }

                              @Suppress("DEPRECATION")
                              override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                  if (isBlockedAdRequest(url)) return true
                                  val scheme = Uri.parse(url).scheme.orEmpty().lowercase()
                                  return scheme != "http" && scheme != "https"
                              }

                              override fun shouldInterceptRequest(
                                  view: WebView,
                                  request: WebResourceRequest
                              ): WebResourceResponse? {
                                  if (isBlockedAdRequest(request.url.toString())) {
                                      return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                  }
                                  return super.shouldInterceptRequest(view, request)
                              }

                              @Suppress("DEPRECATION")
                              override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                                  if (isBlockedAdRequest(url)) {
                                      return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                  }
                                  return super.shouldInterceptRequest(view, url)
                              }

                              override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                  isMusicLoading = true
                                  musicTitle = "K Universe"
                              }

                              override fun onPageFinished(view: WebView, url: String) {
                                  isMusicLoading = false
                                  musicTitle = "K Universe"
                                  view.evaluateJavascript(AD_CLEANUP_HOOK, null)
                                  view.evaluateJavascript(MUSIC_BRANDING_HOOK, null)
                              }
                          }
                          loadUrl(MUSIC_SITE_URL)
                          musicWebView = this
                      }
                  },
                  modifier = Modifier
                      .fillMaxWidth()
                      .weight(1f)
                      .padding(horizontal = 10.dp)
              )

              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .background(Color(0xFF101010))
                      .padding(vertical = 4.dp),
                  horizontalArrangement = Arrangement.SpaceEvenly
              ) {
                  TextButton(onClick = { musicWebView?.loadUrl(MUSIC_SITE_URL) }) {
                      Text("⌂  Home", color = Color(0xFF8CF5A7), fontSize = 12.sp)
                  }
                  TextButton(onClick = { musicWebView?.requestFocus() }) {
                      Text("⌕  Browse", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                  }
                  TextButton(onClick = { closeMusicBrowser() }) {
                      Text("‹  Media", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                  }
              }
          }
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
                    Text("Music", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Listen to music online.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { openMusicInAppBrowser() }) {
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
                        "പേര്, ചിത്രം, Play/Pause ബട്ടൺ എന്നിവയോടെ മലയാളം റേഡിയോ കേൾക്കാം.",
                        color = Color(0xFFB9C2D0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = {
                        mediaHubOpen = false
                        malayalamRadioOpen = true
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

        fun persistBookmarks(values: List<String>) {
            bookmarkPreferences.edit()
                .putStringSet("urls", values.toSet())
                .apply()
        }

        fun addBookmark() {
            val value = urlText.trim()
            if (
                (value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)) &&
                !bookmarks.contains(value)
            ) {
                val updated = (bookmarks + value).distinct()
                bookmarks = updated
                persistBookmarks(updated)
            }
        }

        fun deleteBookmark(valueToDelete: String = urlText.trim()) {
            val updated = bookmarks.filterNot { it == valueToDelete }
            bookmarks = updated
            persistBookmarks(updated)
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
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Google") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { openUrl(searchQuery, null) },
                        onDone = { openUrl(searchQuery, null) }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

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
                                    "Open a page and tap Bookmark to save it.",
                                    color = Color(0xFFB9C2D0)
                                )
                            } else {
                                bookmarks.forEach { bookmark ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { openUrl(bookmark, null) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(bookmark, modifier = Modifier.fillMaxWidth())
                                        }
                                        TextButton(onClick = { deleteBookmark(bookmark) }) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
        } else {
            val currentBookmark = urlText.trim()
            val currentIsBookmarked = currentBookmark.isNotEmpty() &&
                bookmarks.contains(currentBookmark)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (currentIsBookmarked) {
                        TextButton(onClick = { deleteBookmark(currentBookmark) }) {
                            Text("Delete Bookmark")
                        }
                    } else {
                        TextButton(onClick = { addBookmark() }) {
                            Text("Bookmark")
                        }
                    }
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
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.allowContentAccess = true
                        settings.allowFileAccess = true
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
                                if (radioOnlyMode) {
                                    view.evaluateJavascript("document.querySelectorAll('audio,video').forEach(function(media){ media.autoplay = true; });", null)
                                }
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
