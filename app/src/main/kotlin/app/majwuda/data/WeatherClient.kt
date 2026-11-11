package app.majwuda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class WeatherSnapshot(
    val place: String,
    val temperature: Double,
    val feelsLike: Double?,
    val description: String,
    val humidity: Int?,
    val windKmh: Double?,
    val precipitationMm: Double?,
    val high: Double?,
    val low: Double?,
)

/**
 * Fetches current weather from wttr.in (no API key required). Degrades gracefully offline.
 */
class WeatherClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun currentWeather(place: String): WeatherSnapshot? = withContext(Dispatchers.IO) {
        val query = place.trim().ifEmpty { "" }
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://wttr.in/$encoded?format=j1&lang=ar"
        try {
            val request = Request.Builder()
                .url(url)
                .header("user-agent", "majwuda-android/1.0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val current = root["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@withContext null
                val weather = root["weather"]?.jsonArray?.firstOrNull()?.jsonObject
                val area = root["nearest_area"]?.jsonArray?.firstOrNull()?.jsonObject
                fun areaText(key: String): String? =
                    area?.get(key)?.jsonArray?.firstOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.content

                val desc = current["lang_ar"]?.jsonArray?.firstOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.content
                    ?: current["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.content
                    ?: "—"
                WeatherSnapshot(
                    place = areaText("areaName") ?: areaText("region") ?: query.ifEmpty { "موقعك" },
                    temperature = current["temp_C"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    feelsLike = current["FeelsLikeC"]?.jsonPrimitive?.doubleOrNull,
                    description = desc,
                    humidity = current["humidity"]?.jsonPrimitive?.intOrNull,
                    windKmh = current["windspeedKmph"]?.jsonPrimitive?.doubleOrNull,
                    precipitationMm = current["precipMM"]?.jsonPrimitive?.doubleOrNull,
                    high = weather?.get("maxtempC")?.jsonPrimitive?.doubleOrNull,
                    low = weather?.get("mintempC")?.jsonPrimitive?.doubleOrNull,
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
