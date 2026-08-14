package com.alfahdtv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class CatalogKind { MOVIE, SERIES, ANIME }

data class CatalogItem(
    val title: String,
    val href: String,
    val image: String,
    val kind: CatalogKind,
)

data class Episode(val number: String, val link: String)

data class ContentDetail(
    val title: String,
    val description: String,
    val mediaUrl: String,
    val episodes: List<Episode>,
)

class NativeCatalogRepository {
    companion object {
        private const val WORKER = "https://akwam-stream-fetcher.meroo3292.workers.dev/"
        const val MOVIES = "https://akwam.it/movies"
        const val SERIES = "https://akwam.it/series"
        const val ANIME_MOVIES = "https://akwam.it/movies?category=30&section=0"
        const val ANIME_SERIES = "https://akwam.it/series?category=30&section=0"
    }

    suspend fun catalog(source: String, kind: CatalogKind, limit: Int = 30): List<CatalogItem> = withContext(Dispatchers.IO) {
        val payload = request("$WORKER?action=genre&genre=${encode(source)}")
        val data = payload.optJSONArray("data") ?: return@withContext emptyList()
        val seen = HashSet<String>()
        buildList {
            for (index in 0 until data.length()) {
                val value = data.optJSONObject(index) ?: continue
                val title = value.optString("title").trim()
                val href = value.optString("href").trim()
                val image = value.optString("img").trim()
                if (title.isBlank() || href.isBlank() || !href.startsWith("https://") || !seen.add(href) || isNoise(title)) continue
                add(CatalogItem(title, href, highResolutionPoster(image), kind))
                if (size >= limit) break
            }
        }
    }

    suspend fun anime(limit: Int = 30): List<CatalogItem> {
        val movies = catalog(ANIME_MOVIES, CatalogKind.ANIME, limit)
        val series = catalog(ANIME_SERIES, CatalogKind.ANIME, limit)
        return (movies + series).distinctBy { it.href }.take(limit)
    }

    suspend fun search(query: String, limit: Int = 30): List<CatalogItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val payload = request("$WORKER?action=search&q=${encode(clean)}&p=1")
        val data = payload.optJSONArray("data") ?: return@withContext emptyList()
        val seen = HashSet<String>()
        buildList {
            for (index in 0 until data.length()) {
                val value = data.optJSONObject(index) ?: continue
                val title = value.optString("title").trim()
                val href = value.optString("href").trim()
                val image = value.optString("img").trim()
                if (title.isBlank() || !href.startsWith("https://") || !seen.add(href) || isNoise(title)) continue
                val kind = when {
                    href.contains("/series/") -> CatalogKind.SERIES
                    title.contains("أنمي", ignoreCase = true) -> CatalogKind.ANIME
                    else -> CatalogKind.MOVIE
                }
                add(CatalogItem(title, href, highResolutionPoster(image), kind))
                if (size >= limit) break
            }
        }
    }

    suspend fun detail(item: CatalogItem): ContentDetail = withContext(Dispatchers.IO) {
        parseDetail(request("$WORKER?action=series&series=${encode(item.href)}"), item.title)
    }

    suspend fun episode(link: String, fallbackTitle: String): ContentDetail = withContext(Dispatchers.IO) {
        parseDetail(request("$WORKER?action=series&series=${encode(link)}"), fallbackTitle)
    }

    private fun parseDetail(payload: JSONObject, fallbackTitle: String): ContentDetail {
        val episodesJson = payload.optJSONArray("episodes")
        val episodes = buildList {
            if (episodesJson != null) for (index in 0 until episodesJson.length()) {
                val value = episodesJson.optJSONObject(index) ?: continue
                val link = value.optString("link")
                if (link.startsWith("https://")) add(Episode(value.optString("num", "${index + 1}"), link))
            }
        }
        val rawTitle = payload.optString("movie_title", fallbackTitle)
        return ContentDetail(
            title = rawTitle.replace(Regex("^مشاهدة\\s+(فيلم|مسلسل)\\s+"), "").trim().ifBlank { fallbackTitle },
            description = payload.optString("description").trim(),
            mediaUrl = compatibleMediaUrl(payload.optString("media_src")),
            episodes = episodes,
        )
    }

    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AlFahdTV/3.0 Android")
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun isNoise(title: String): Boolean = title.equals("اكوام", true) || title.contains("web stats", true) || title.contains("إشعارات اكوام")
    private fun highResolutionPoster(value: String): String = value.replace(Regex("/thumb/\\d+x\\d+/"), "/")
    private fun compatibleMediaUrl(value: String): String {
        val clean = value.trim()
        return if (clean.startsWith("https://") && Regex("^https://(?:[^/]+\\.)?downet\\.net/", RegexOption.IGNORE_CASE).containsMatchIn(clean)) clean.replaceFirst("https://", "http://") else clean.takeIf { it.startsWith("https://") } ?: ""
    }
}
