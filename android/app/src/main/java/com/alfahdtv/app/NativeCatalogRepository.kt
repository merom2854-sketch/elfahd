package com.alfahdtv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.HashMap

enum class CatalogKind { MOVIE, SERIES, ANIME }

data class CatalogItem(
    val title: String,
    val href: String,
    val image: String,
    val kind: CatalogKind,
)

data class Episode(val number: String, val link: String)
data class Actor(val name: String, val image: String)
data class SourceCategory(val id: String, val title: String, val url: String)

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
        // This data is hosted with the public site, not on a Railway runtime. The GitHub
        // copy keeps the manual catalogue available even while the site is being redeployed.
        private const val MANUAL_API = "https://elfahd-tv.vercel.app/data/manual-content.json"
        private const val MANUAL_FALLBACK_API = "https://raw.githubusercontent.com/merom2854-sketch/elfahd/main/data/manual-content.json"
        const val MOVIES = "https://akwam.it/movies"
        const val SERIES = "https://akwam.it/series"
        const val ANIME_MOVIES = "https://akwam.it/movies?category=30&section=0"
        const val ANIME_SERIES = "https://akwam.it/series?category=30&section=0"
        private const val CACHE_TTL_MS = 5L * 60L * 1000L
        private val responseCache = HashMap<String, Pair<Long, String>>()
    }

    suspend fun catalog(source: String, kind: CatalogKind, limit: Int = 30): List<CatalogItem> = withContext(Dispatchers.IO) {
        val payload = runCatching { request("$WORKER?action=genre&genre=${encode(source)}") }.getOrNull()
        val data = payload?.optJSONArray("data")
        if (data == null || data.length() == 0) return@withContext fallbackCatalog(source, kind, limit)
        return@withContext itemsFromJson(data, kind, limit)
    }

    private fun itemsFromJson(data: org.json.JSONArray, kind: CatalogKind, limit: Int): List<CatalogItem> {
        val seen = HashSet<String>()
        return buildList {
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

    suspend fun manualContent(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val data = listOf(MANUAL_API, MANUAL_FALLBACK_API)
            .asSequence()
            .mapNotNull { endpoint -> runCatching { request("$endpoint?ts=${System.currentTimeMillis()}").optJSONArray("data") }.getOrNull() }
            .firstOrNull()
            ?: return@withContext emptyList()
        buildList {
            for (index in 0 until data.length()) {
                val value = data.optJSONObject(index) ?: continue
                val title = value.optString("title").trim()
                val href = workerUrl(value.optString("href"))
                val image = highResolutionPoster(value.optString("image", value.optString("img")).trim())
                val kind = when (value.optString("kind").lowercase()) {
                    "series", "tv" -> CatalogKind.SERIES
                    "anime" -> CatalogKind.ANIME
                    else -> CatalogKind.MOVIE
                }
                if (title.isNotBlank() && href.startsWith("https://")) add(CatalogItem(title, href, image, kind))
            }
        }
    }

    suspend fun categories(source: String): List<SourceCategory> = withContext(Dispatchers.IO) {
        runCatching {
            val base = source.substringBefore('?')
            val html = fetchPage(base)
            Regex("""<option\s+value=[\"'](\d+)[\"'][^>]*>([\s\S]*?)</option>""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .map { SourceCategory(it.groupValues[1], plainText(it.groupValues[2]), "$base?category=${it.groupValues[1]}&section=0") }
                .filter { it.id != "0" && it.title.isNotBlank() }
                .distinctBy { it.id }
                .toList()
        }.getOrDefault(emptyList())
    }

    suspend fun search(query: String, limit: Int = 30): List<CatalogItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val payload = runCatching { request("$WORKER?action=search&q=${encode(clean)}&p=1") }.getOrNull()
        val data = payload?.optJSONArray("data")
        if (data != null && data.length() > 0) return@withContext itemsFromJson(data, CatalogKind.MOVIE, limit)
        return@withContext fallbackCatalog("https://akwam.it/search?q=${encode(clean)}", CatalogKind.MOVIE, limit)
    }

    suspend fun detail(item: CatalogItem): ContentDetail = withContext(Dispatchers.IO) {
        val workerParsed = runCatching {
            parseDetail(request("$WORKER?action=series&series=${encode(workerUrl(item.href))}"), item.title)
        }.getOrElse { fallbackDetail(item.href, item.title) }
        // The source sometimes returns a successful JSON shell with no media or
        // episodes after a backend change. Fall back to parsing the public page
        // instead of showing a broken detail screen.
        val parsed = if (workerParsed.mediaUrl.isBlank() || (item.kind != CatalogKind.MOVIE && workerParsed.episodes.isEmpty())) {
            runCatching { fallbackDetail(item.href, item.title) }.getOrDefault(workerParsed)
        } else workerParsed
        val playable = if (parsed.mediaUrl.isBlank() && item.kind == CatalogKind.MOVIE) resolveMediaUrl(item.href) else secureMediaUrl(parsed.mediaUrl)
        val resolved = if (playable == parsed.mediaUrl) parsed else parsed.copy(mediaUrl = playable)
        if (resolved.actors.isNotEmpty()) resolved else resolved.copy(actors = metadataActors(item.title, item.kind))
    }

    suspend fun episode(link: String, fallbackTitle: String): ContentDetail = withContext(Dispatchers.IO) {
        val parsed = runCatching {
            parseDetail(request("$WORKER?action=series&series=${encode(workerUrl(link))}"), fallbackTitle)
        }.getOrElse { fallbackDetail(link, fallbackTitle) }
        if (parsed.mediaUrl.isNotBlank()) parsed.copy(mediaUrl = secureMediaUrl(parsed.mediaUrl)) else parsed.copy(mediaUrl = resolveMediaUrl(link))
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
        val now = System.currentTimeMillis()
        synchronized(responseCache) {
            val cached = responseCache[url]
            if (cached != null && cached.first > now) return JSONObject(cached.second)
            if (cached != null) responseCache.remove(url)
        }
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
                synchronized(responseCache) {
                    responseCache[url] = System.currentTimeMillis() + CACHE_TTL_MS to text
                    while (responseCache.size > 32) responseCache.remove(responseCache.keys.first())
                }
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

    private fun fallbackCatalog(source: String, kind: CatalogKind, limit: Int): List<CatalogItem> {
        return runCatching { parseCatalogPage(fetchPage(source), kind, limit) }.getOrDefault(emptyList())
    }

    private fun parseCatalogPage(html: String, kind: CatalogKind, limit: Int): List<CatalogItem> {
        val cards = Regex(
            """<a\s+href=[\"'](https?://(?:ak\.sv|akwam\.it)/(?:movie|series)/[^\"']+)[\"'][^>]*class=[\"'][^\"']*\bbox\b[^\"']*[\"'][\s\S]*?</h3>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val seen = HashSet<String>()
        return buildList {
            for (match in cards.findAll(html)) {
                val href = workerUrl(match.groupValues[1])
                val title = Regex("""<h3[^>]*>\s*<a[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
                    .find(match.value)?.groupValues?.get(1)?.let(::plainText).orEmpty()
                val image = Regex("""(?:data-src|src|xlink:href)=[\"'](https?://[^\"']+)[\"']""", RegexOption.IGNORE_CASE)
                    .findAll(match.value).map { it.groupValues[1] }
                    .firstOrNull { !it.contains("placeholder", true) && !it.contains("logo", true) }.orEmpty()
                if (title.isNotBlank() && href.isNotBlank() && seen.add(href) && !isNoise(title)) {
                    add(CatalogItem(title, href, highResolutionPoster(image), kind))
                    if (size >= limit) break
                }
            }
        }
    }

    private fun fallbackDetail(url: String, fallbackTitle: String): ContentDetail {
        val html = fetchPage(workerUrl(url))
        val title = Regex("""<h1[^>]*>([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::plainText)?.ifBlank { fallbackTitle } ?: fallbackTitle
        val description = Regex("""<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']*)[\"']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::plainText).orEmpty()
        val episodes = buildList {
            val seen = HashSet<String>()
            for (match in Regex("""href=[\"'](https?://(?:ak\.sv|akwam\.it)/episode/[^\"']+)[\"']""", RegexOption.IGNORE_CASE).findAll(html)) {
                val link = workerUrl(match.groupValues[1])
                if (seen.add(link)) add(Episode("${size + 1}", link))
            }
        }
        return ContentDetail(title, description, "", episodes, emptyList())
    }

    private fun plainText(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
        .replace(Regex("\\s+"), " ").trim()

    // The source parser already supplies actor names when available. Do not make a content
    // page depend on a separate metadata server merely to enrich those names with photos.
    @Suppress("UNUSED_PARAMETER")
    private fun metadataActors(title: String, kind: CatalogKind): List<Actor> = emptyList()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun workerUrl(value: String): String = value.trim().replaceFirst(Regex("^https://(?:ak\\.sv|akwam\\.ss)/", RegexOption.IGNORE_CASE), "https://akwam.it/")
    private fun resolveMediaUrl(value: String): String = runCatching {
        val page = fetchPage(workerUrl(value))
        val watch = Regex("href\\s*=\\s*[\\\"'](https?://(?:ak\\.sv|akwam\\.it|akwam\\.ss)/watch/[^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE).find(page)?.groupValues?.get(1) ?: return@runCatching ""
        val watchPage = fetchPage(workerUrl(watch))
        val source = Regex("<source[^>]+src\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(watchPage)?.groupValues?.get(1) ?: return@runCatching ""
        secureMediaUrl(source)
    }.getOrDefault("")
    private fun fetchPage(url: String): String {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 18_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                val code = connection.responseCode
                if (code !in 200..299) error("HTTP $code")
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (error: Exception) {
                lastError = error
                if (attempt < 2) Thread.sleep(350L * (attempt + 1))
            } finally { connection.disconnect() }
        }
        throw lastError ?: IllegalStateException("Empty page")
    }
    private fun isNoise(title: String): Boolean = title.equals("اكوام", true) || title.contains("web stats", true) || title.contains("إشعارات اكوام")
    private fun highResolutionPoster(value: String): String = value.replace(Regex("/thumb/\\d+x\\d+/"), "/")
    private fun compatibleMediaUrl(value: String): String {
        val clean = value.trim().replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        return clean.takeIf { it.startsWith("https://") } ?: ""
    }
    private fun secureMediaUrl(value: String): String {
        val clean = compatibleMediaUrl(value)
        if (clean.isBlank() || clean.startsWith(WORKER)) return clean
        return runCatching {
            val signed = request("$WORKER?action=sign&url=${encode(clean)}").optString("url").trim()
            signed.takeIf { it.startsWith("https://") } ?: clean
        }.getOrDefault(clean)
    }
}
