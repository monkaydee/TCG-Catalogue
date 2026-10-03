package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, message: String) : IOException(message)

class Http(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /** GET [url] and parse it as JSON. Returns null on HTTP 404. */
    suspend fun getJson(url: String): JsonElement? =
        getText(url, accept = "application/json")?.takeIf { it.isNotBlank() }?.let(json::parseToJsonElement)

    /** GET [url] as text. Returns null on HTTP 404. */
    suspend fun getText(url: String, accept: String = "*/*", userAgent: String = "TCG-Catalogue-Android/1.0"): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", accept).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext null
                // The message is shown to the user when a search fails.
                if (!response.isSuccessful) throw HttpException(response.code, AppStrings.get(R.string.data_http_error, response.code, url))
                response.body?.string().orEmpty()
            }
        }
}

/** Like runCatching, but lets coroutine cancellation through instead of treating it as a failure. */
inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

// Small helpers for navigating loosely-typed API responses.
fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.content == "true"
