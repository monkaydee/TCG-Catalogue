package com.monkaydee.tcgcatalogue.data.remote

import com.monkaydee.tcgcatalogue.data.db.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The app's own free price server (a Cloudflare Worker, see worker/ and docs/CLOUDFLARE.md). It
 * holds the API keys of the price providers, so none are inside the app, and shares every answer
 * between all users. It gives graded prices, checks PSA cert numbers and identifies a card from a
 * photo when the phone can't (only when the user asks).
 *
 * [server] returns the address and the app key, or null when no server is set up.
 */
class PriceServerApi(private val server: suspend () -> Pair<String, String>?) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
    private val http = Http(client)

    /** The server answered, but configured upstream providers could not supply data. */
    class ProvidersUnavailableException : IOException("Price providers unavailable")

    /** One graded price: "PSA" "10" 412.00 USD from JustTCG. */
    data class Graded(
        val grader: String, val grade: String, val price: Double, val currency: String, val source: String, val date: String?, val sales: Int?,
        /** Asking prices: how many listings, and their lowest and highest price. */
        val listings: Int? = null, val low: Double? = null, val high: Double? = null,
        val qualifier: String? = null,
        val fetchedAt: Long? = null, val stale: Boolean = false, val evidence: String? = null,
    )

    /** A raw price from the server: amount, where from, currency, and for asking prices the listings behind it. */
    data class Raw(val amount: Double, val source: String, val currency: String, val listings: Int?, val low: Double?, val high: Double?, val fetchedAt: Long? = null, val stale: Boolean = false)

    /** A card PSA graded: what the label says, and how many copies PSA graded the same or higher. */
    data class Cert(
        val number: String,
        val description: String?,
        val grade: String?,
        val year: String?,
        val set: String?,
        val cardNumber: String?,
        val population: Int?,
        val higher: Int?,
    )

    /** A card the server's image recognition (Ximilar) found in a photo. */
    data class Identified(
        val name: String,
        val set: String?,
        val setCode: String?,
        val number: String?,
        val outOf: String?,
        val game: Game?,
        val tcgplayerId: Long?,
        val confidence: Double?,
    )

    suspend fun isSetUp(): Boolean = server() != null

    /** True when the server answers with this app key. */
    suspend fun reachable(): Boolean = runCatching { get("/v1/status") != null }.getOrDefault(false)

    /** The price server's raw (ungraded) price of a printing: NM or a blended market price, in USD, and its source. */
    suspend fun raw(game: Game, cardId: String, name: String, setName: String, number: String, tcgplayerId: Long?, printing: String?, language: String = "EN", localName: String? = null, condition: String = "NM", setAliases: List<String> = emptyList(), releaseYear: String? = null, market: String? = null, printingUnique: Boolean = false): Raw? {
        val body = buildJsonObject {
            put("schemaVersion", 2)
            putJsonArray("cards") {
                add(
                    buildJsonObject {
                        put("game", game.name)
                        put("id", cardId)
                        put("name", name)
                        put("set", setName)
                        if (printingUnique) put("printingUnique", true)
                        market?.let { put("market", it) }
                        putJsonArray("setAliases") { setAliases.forEach { add(it) } }
                        releaseYear?.let { put("releaseYear", it) }
                        put("number", number)
                        tcgplayerId?.let { put("tcgplayerId", it) }
                        printing?.let { put("printing", it) }
                        if (language != "EN") put("language", language)
                        localName?.let { put("localName", it) }
                    },
                )
            }
        }
        val result = post("/v1/prices", body.toString().toRequestBody(JSON))?.get("results").arr()?.firstOrNull() ?: return null
        if (result["reason"].str() == "unavailable") throw ProvidersUnavailableException()
        val amount = (result["conditions"]?.let { it as? kotlinx.serialization.json.JsonObject }?.get(condition).dbl())
            ?.takeIf { it > 0 } ?: return null
        val evidence = result["conditionEvidence"]?.let { it as? kotlinx.serialization.json.JsonObject }?.get(condition)
        return Raw(amount, result["source"].str().orEmpty(), result["currency"].str() ?: "USD", evidence?.get("listings").int() ?: result["listings"].int(), evidence?.get("low").dbl() ?: result["low"].dbl(), evidence?.get("high").dbl() ?: result["high"].dbl(), timestamp(result["fetchedAt"].str()), result["stale"].str() == "true")
    }

    /** Graded prices of a card (TCGplayer product [tcgplayerId]), or an empty list when there are none. */
    suspend fun graded(game: Game, cardId: String, name: String, setName: String, number: String, tcgplayerId: Long?, printing: String? = null, language: String = "EN", localName: String? = null, setAliases: List<String> = emptyList(), releaseYear: String? = null, market: String? = null, printingUnique: Boolean = false, grader: String? = null, grade: String? = null): List<Graded> {
        val body = buildJsonObject {
            put("schemaVersion", 2)
            putJsonArray("cards") {
                add(
                    buildJsonObject {
                        put("game", game.name)
                        put("id", cardId)
                        put("name", name)
                        put("set", setName)
                        if (printingUnique) put("printingUnique", true)
                        market?.let { put("market", it) }
                        putJsonArray("setAliases") { setAliases.forEach { add(it) } }
                        releaseYear?.let { put("releaseYear", it) }
                        put("number", number)
                        tcgplayerId?.let { put("tcgplayerId", it) }
                        printing?.let { put("printing", it) }
                        if (language != "EN") put("language", language)
                        localName?.let { put("localName", it) }
                        put("graded", true)
                        put("gradedOnly", true)
                        grader?.let { put("grader", it) }
                        grade?.let { put("grade", it.replace(',', '.')) }
                    },
                )
            }
        }
        val result = post("/v1/prices", body.toString().toRequestBody(JSON))?.get("results").arr()?.firstOrNull() ?: return emptyList()
        if (result["gradedReason"].str() == "unavailable") throw ProvidersUnavailableException()
        return result["graded"].arr().orEmpty().mapNotNull { g ->
            Graded(
                grader = g["grader"].str() ?: return@mapNotNull null,
                grade = g["grade"].str() ?: return@mapNotNull null,
                price = g["price"].dbl()?.takeIf { it > 0 } ?: return@mapNotNull null,
                currency = g["currency"].str() ?: "USD",
                source = g["source"].str().orEmpty(),
                date = g["date"].str(),
                sales = g["sales"].int(),
                listings = g["listings"].int(),
                low = g["low"].dbl(),
                high = g["high"].dbl(),
                qualifier = g["qualifier"].str(),
                fetchedAt = timestamp(g["fetchedAt"].str() ?: result["gradedFetchedAt"].str()),
                stale = g["stale"].str() == "true" || result["gradedStale"].str() == "true",
                evidence = g["evidence"].str(),
            )
        }
    }

    data class SealedQuote(val amount: Double, val currency: String, val source: String, val fetchedAt: Long?, val stale: Boolean, val listings: Int?)
    data class SealedResult(val quote: SealedQuote?, val reason: String?)
    suspend fun sealed(product: SealedProduct): SealedResult {
        val body = buildJsonObject {
            put("game", product.game.name); put("productId", product.productId.toString())
            put("name", product.name); put("language", product.language)
            product.market?.let { put("market", it) }
            putJsonArray("aliases") { product.aliases.forEach { add(it) } }
        }
        val result = post("/v1/sealed/price", body.toString().toRequestBody(JSON)) ?: return SealedResult(null, "unavailable")
        val p = result["price"]
        val amount = p?.get("amount").dbl()?.takeIf { it.isFinite() && it > 0 }
        val currency = p?.get("currency").str()?.takeIf { it in setOf("USD", "EUR") }
        if (amount == null || currency == null) return SealedResult(null, result["reason"].str() ?: "not_found")
        return SealedResult(SealedQuote(amount, currency, p["source"].str().orEmpty(),
            p["fetchedAt"].str()?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }, p["stale"].str() == "true", p["listings"].int()), result["reason"].str())
    }

    /** PSA's record for a cert number, or null when PSA doesn't know it. */
    suspend fun cert(number: String): Cert? {
        val digits = number.filter(Char::isDigit).ifEmpty { return null }
        val c = get("/v1/cert/$digits")?.get("cert") ?: return null
        return Cert(
            number = c["certNumber"].str() ?: digits,
            description = c["description"].str() ?: c["subject"].str(),
            grade = c["gradeDescription"].str() ?: c["grade"].str(),
            year = c["year"].str(),
            set = c["set"].str(),
            cardNumber = c["cardNumber"].str(),
            population = c["population"]["total"].int(),
            higher = c["population"]["higher"].int(),
        )
    }

    /** Cards the server recognises in [jpeg] (a photo of one card), best first. The photo is not kept. */
    suspend fun identify(jpeg: ByteArray, game: Game?): List<Identified> {
        val r = post("/v1/identify", jpeg.toRequestBody("image/jpeg".toMediaType()), game?.let { "X-Game" to it.name }) ?: return emptyList()
        return r["matches"].arr().orEmpty().mapNotNull { m ->
            Identified(
                name = m["name"].str() ?: m["fullName"].str() ?: return@mapNotNull null,
                set = m["set"].str(),
                setCode = m["setCode"].str(),
                number = m["number"].str(),
                outOf = m["outOf"].str(),
                game = m["game"].str()?.let { g -> Game.entries.firstOrNull { it.name.equals(g, ignoreCase = true) } },
                tcgplayerId = m["tcgplayerId"].str()?.toLongOrNull(),
                confidence = m["confidence"].dbl(),
            )
        }
    }

    private suspend fun get(path: String): JsonElement? = call(path) { it.get() }

    private suspend fun post(path: String, body: okhttp3.RequestBody, header: Pair<String, String>? = null): JsonElement? =
        call(path) { b -> b.post(body).also { if (header != null) it.header(header.first, header.second) } }

    private suspend fun call(path: String, build: (Request.Builder) -> Request.Builder): JsonElement? = withContext(Dispatchers.IO) {
        val (base, key) = server() ?: throw IOException("no price server")
        val request = build(Request.Builder().url(base.trimEnd('/') + path).header("X-App-Key", key).header("User-Agent", "TCG-Catalogue-Android/1.0")).build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> response.body?.string()?.takeIf { it.isNotBlank() }?.let(http.json::parseToJsonElement)
                else -> throw HttpException(response.code, "price server: HTTP ${response.code}")
            }
        }
    }

    private fun timestamp(value: String?): Long? = value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
