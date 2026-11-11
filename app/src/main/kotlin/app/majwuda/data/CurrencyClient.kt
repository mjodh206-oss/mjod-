package app.majwuda.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Free, key-less exchange rates from open.er-api.com. Returns null when offline/unavailable. */
class CurrencyClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** rates from [base] keyed by currency code (USD, EUR, SAR...). */
    suspend fun latestRates(base: String = "USD"): Map<String, Double>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.er-api.com/v6/latest/${base.uppercase()}")
                .header("user-agent", "majwuda-android/1.0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                if ((root["result"]?.jsonPrimitive?.content ?: root["result"]?.jsonPrimitive?.content) != "success" && root["result"] != null) {
                    // keep parsing anyway; the rates map is present on success
                }
                root["rates"]?.jsonObject?.mapNotNull { (code, element) ->
                    element.jsonPrimitive.doubleOrNull?.let { code.uppercase() to it }
                }?.toMap()
            }
        } catch (_: Exception) {
            null
        }
    }
}
