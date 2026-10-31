package com.nikhilbhatt.telly.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds real logos: app icons from the app's Google Play listing, channel logos from Wikipedia.
 * Images are downloaded into the logo folder so they keep working offline.
 */
class LogoFinder(private val logoDir: () -> File) {

    /** The app's Play Store icon, saved locally. Returns the file path, or null if not found. */
    suspend fun appIcon(pkg: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val page = get("https://play.google.com/store/apps/details?id=${enc(pkg)}&hl=en", BROWSER_UA)
            val icon = OG_IMAGE.find(page)?.groupValues?.get(1) ?: return@runCatching null
            // Play image URLs take a size suffix after '='.
            download(icon.substringBefore('=') + "=w256")
        }.getOrNull()
    }

    /** The logo from the channel's Wikipedia article, saved locally. */
    suspend fun channelLogo(name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val query = enc("$name TV channel India")
            val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search" +
                "&gsrsearch=$query&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=256&pilicense=any"
            val pages = Json.parseToJsonElement(get(url, APP_UA)).jsonObject["query"]?.jsonObject?.get("pages") as? JsonObject
            val thumb = pages?.values?.firstOrNull()?.jsonObject?.get("thumbnail")?.jsonObject?.get("source")?.jsonPrimitive?.content
            thumb?.let(::download)
        }.getOrNull()
    }

    private fun download(url: String): String? {
        val conn = open(url, APP_UA)
        return try {
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size < 100) return null
            File(logoDir(), newId() + ".img").apply { writeBytes(bytes) }.absolutePath
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String, userAgent: String): String {
        val conn = open(url, userAgent)
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, userAgent: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 10_000
        setRequestProperty("User-Agent", userAgent)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val APP_UA = "Telly/1.0 (Android TV remote app)"
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val OG_IMAGE = Regex("""<meta property="og:image" content="([^"]+)"""")

        /** Apps launched by link still have a package we can look the icon up by. */
        private val LINK_PACKAGES = listOf(
            "netflix.com" to "com.netflix.ninja",
            "youtube.com" to "com.google.android.youtube.tv",
            "primevideo.com" to "com.amazon.amazonvideo.livingroom",
            "hotstar.com" to "in.startv.hotstar",
            "spotify.com" to "com.spotify.tv.android",
        )

        fun iconPackage(shortcut: Shortcut): String? {
            shortcut.steps.firstNotNullOfOrNull { (it as? Step.App)?.pkg }?.let { return it }
            val url = shortcut.steps.firstNotNullOfOrNull { (it as? Step.Link)?.url } ?: return null
            return LINK_PACKAGES.firstOrNull { (domain, _) -> domain in url }?.second
        }
    }
}
