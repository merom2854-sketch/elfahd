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
data class Actor(val name: String, val image: String)

data class ContentDetail(
    val title: String,
    val description: String,
    val mediaUrl: String,
    val episodes: List<Episode>,
    val actors: List<Actor>,
)

class NativeCatalogRepository {
    companion object {
        private const val WORKER = "https://akwam-stream-fetcher.meroo3292.workers.dev/"
        private const val METADATA_API = "https://al-fahd-api-production.up.railway.app/v1/metadata"
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
                val href = workerUrl(value.optString("href"))
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
                val href = workerUrl(value.optString("href"))
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
        val parsed = parseDetail(request("$WORKER?action=series&series=${encode(workerUrl(item.href))}"), item.title)
        val playable = if (parsed.mediaUrl.isBlank() && item.kind == CatalogKind.MOVIE) resolveMediaUrl(item.href) else parsed.mediaUrl
        val resolved = if (playable == parsed.mediaUrl) parsed else parsed.copy(mediaUrl = playable)
        if (resolved.actors.isNotEmpty()) resolved else resolved.copy(actors = metadataActors(item.title, item.kind))
    }

    suspend fun episode(link: String, fallbackTitle: String): ContentDetail = withContext(Dispatchers.IO) {
        val parsed = parseDetail(request("$WORKER?action=series&series=${encode(workerUrl(link))}"), fallbackTitle)
        if (parsed.mediaUrl.isNotBlank()) parsed else parsed.copy(mediaUrl = resolveMediaUrl(link))
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
        val actors = buildList {
            val actorsJson = payload.optJSONArray("actors")
            if (actorsJson != null) for (index in 0 until actorsJson.length()) {
                val actor = actorsJson.optString(index).trim()
                if (actor.isNotBlank() && actor.length <= 60 && !actor.contains("الموسم") && !actor.contains("الحلقة") && !actor.contains("أبطال بلباس النوم")) add(Actor(actor, ""))
            }
        }.distinctBy { it.name }.take(12)
        return ContentDetail(
            title = rawTitle.replace(Regex("^مشاهدة\\s+(فيلم|مسلسل)\\s+"), "").trim().ifBlank { fallbackTitle },
            description = payload.optString("description").trim(),
            mediaUrl = compatibleMediaUrl(payload.optString("media_src")),
            episodes = episodes,
            actors = actors,
        )
    }

    private fun request(url: String): JSONObject {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 18_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("User-Agent", "AlFahdTV/3.0 Android")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("HTTP $code")
                return JSONObject(text)
            } catch (error: Exception) {
                lastError = error
                if (attempt < 2) Thread.sleep(350L * (attempt + 1))
            } finally {
                connection.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("Empty response")
    }

    private fun metadataActors(title: String, kind: CatalogKind): List<Actor> {
        return runCatching {
            val type = if (kind == CatalogKind.MOVIE) "movie" else "tv"
            val payload = request("$METADATA_API?title=${encode(title)}&kind=$type")
            val values = payload.optJSONObject("data")?.optJSONArray("actors") ?: return@runCatching emptyList()
            buildList { for (index in 0 until values.length()) { val actor = values.optJSONObject(index); if (actor != null) add(Actor(actor.optString("name").trim(), actor.optString("image").trim())) } }.filter { it.name.isNotBlank() }.distinctBy { it.name }.take(12)
        }.getOrDefault(emptyList())
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun workerUrl(value: String): String = value.trim().replaceFirst(Regex("^https://ak\\.sv/", RegexOption.IGNORE_CASE), "https://akwam.it/")
    private fun resolveMediaUrl(value: String): String = runCatching {
        val page = fetchPage(workerUrl(value))
        val watch = Regex("href\\s*=\\s*[\\\"'](https?://(?:ak\\.sv|akwam\\.it)/watch/[^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE).find(page)?.groupValues?.get(1) ?: return@runCatching ""
        val watchPage = fetchPage(watch)
        val source = Regex("<source[^>]+src\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(watchPage)?.groupValues?.get(1) ?: return@runCatching ""
        compatibleMediaUrl(source)
    }.getOrDefault("")
    private fun fetchPage(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }
    private fun isNoise(title: String): Boolean = title.equals("اكوام", true) || title.contains("web stats", true) || title.contains("إشعارات اكوام")
    private fun highResolutionPoster(value: String): String = value.replace(Regex("/thumb/\\d+x\\d+/"), "/")
    private fun compatibleMediaUrl(value: String): String {
        val clean = value.trim()
        return if (clean.startsWith("https://") && Regex("^https://(?:[^/]+\\.)?downet\\.net/", RegexOption.IGNORE_CASE).containsMatchIn(clean)) clean.replaceFirst("https://", "http://") else clean.takeIf { it.startsWith("https://") } ?: ""
    }
}
