package com.alfahdtv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveFixture(
    val id: String,
    val home: String,
    val away: String,
    val league: String,
    val time: String,
    val status: Int,
) {
    val isLive get() = status == 1
    val statusLabel get() = if (isLive) "مباشر الآن" else "يبدأ $time"
    val playbackPage get() = "https://go4xyz.app/?m=$id&lang=ar"
}

/** Public match schedule only. Playback stays inside [LivePlayerActivity]. */
object LiveScheduleRepository {
    private const val SCHEDULE_ORIGIN = "https://ws.kora-api.space"

    suspend fun fixtures(): List<LiveFixture> = withContext(Dispatchers.IO) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val payload = request("$SCHEDULE_ORIGIN/api/matches/$date/1")
        val matches = JSONObject(payload).optJSONArray("matches") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until matches.length()) {
                val item = matches.optJSONObject(index) ?: continue
                if (item.optInt("active") != 1 || item.optInt("has_channels") != 1) continue
                val status = item.optInt("status")
                if (status == 2) continue
                val id = item.optString("id").trim()
                if (id.isBlank()) continue
                val home = item.optString("home").ifBlank { item.optString("home_en") }.trim()
                val away = item.optString("away").ifBlank { item.optString("away_en") }.trim()
                if (home.isBlank() || away.isBlank()) continue
                add(
                    LiveFixture(
                        id = id,
                        home = home,
                        away = away,
                        league = item.optString("league").ifBlank { item.optString("league_en") }.trim(),
                        time = item.optString("time").trim(),
                        status = status,
                    ),
                )
            }
        }.sortedWith(compareByDescending<LiveFixture> { it.isLive }.thenBy { it.time })
    }

    private fun request(value: String): String {
        val connection = (URL(value).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 16_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AlFahdTV/3.7 Android")
        }
        return try {
            if (connection.responseCode !in 200..299) error("Schedule unavailable")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
