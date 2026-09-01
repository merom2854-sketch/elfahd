package com.alfahdtv.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves the currently available player frame for a match.  The match page is
 * deliberately not used as the playback surface: it has become unreliable and
 * can return a provider-side "blocked" page.  We only use the source's public
 * channel manifest, then render its actual player inside Al Fahd's own shell.
 */
object LiveSourceResolver {
    private const val CHANNELS_API = "https://ws.kora-api.top/api/matche"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"

    data class ResolvedPlayer(
        val embedUrl: String,
        val referrerUrl: String,
    )

    fun resolve(matchPage: String): ResolvedPlayer? = runCatching {
        val matchId = Uri.parse(matchPage).getQueryParameter("m").orEmpty()
        require(matchId.matches(Regex("\\d+")))
        val payload = JSONObject(request("$CHANNELS_API/$matchId/ar"))
        val channels = payload.optJSONArray("channels") ?: return null

        orderedChannels(channels).forEach { channel ->
            val pageUrl = channel.optString("mobile_link").ifBlank { channel.optString("link") }
            if (!isHttps(pageUrl)) return@forEach
            val page = runCatching { request(pageUrl) }.getOrNull() ?: return@forEach
            val embedUrl = frameUrl(page) ?: return@forEach
            if (isHttps(embedUrl)) return ResolvedPlayer(embedUrl, pageUrl)
        }
        null
    }.getOrNull()

    private fun orderedChannels(channels: JSONArray): List<JSONObject> {
        val values = buildList {
            for (index in 0 until channels.length()) channels.optJSONObject(index)?.let(::add)
        }
        // Arabic channels are preferred, but a verified fallback is better than
        // showing a dead player when that channel's edge is temporarily down.
        return values.sortedBy { if (it.optString("language").equals("Ar", true)) 0 else 1 }
    }

    private fun frameUrl(page: String): String? {
        val match = Regex("""<iframe[^>]+src\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE).find(page)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun isHttps(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun request(value: String): String {
        val connection = (URL(value).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
        }
        return try {
            require(connection.responseCode in 200..299)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
