package com.monkaydee.tcgcatalogue.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.data.remote.CardCandidate
import com.monkaydee.tcgcatalogue.data.remote.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Pseudonymous opt-in reports. Full originals, costs and account identifiers never leave the phone. */
object SharedLearning {
    const val PHOTOS_ENABLED = false // Re-enable only with server-side content and identity moderation.
    internal fun readHash(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(normalizeRead(text).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun normalizeRead(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]"""), "").take(300)
    @SuppressLint("StaticFieldLeak") private var app: Context? = null // application context only
    val state = MutableStateFlow(State())
    data class State(val text: Boolean = false, val photos: Boolean = false, val pending: Int = 0, val error: String? = null)
    private val client = OkHttpClient()
    private val lock = Mutex()
    private fun prefs() = requireNotNull(app).getSharedPreferences("recognition-learning", Context.MODE_PRIVATE)
    private fun dir() = File(requireNotNull(app).filesDir, "recognition-learning").apply { mkdirs() }
    fun initialize(context: Context, repo: CardRepository) {
        app = context.applicationContext
        val p = prefs()
        if (!p.contains("install")) p.edit().putString("install", UUID.randomUUID().toString()).putString("token", UUID.randomUUID().toString()).apply()
        if (p.getBoolean("photos", false)) p.edit().putBoolean("deletePending", true).apply()
        p.edit().putBoolean("photos", false).apply()
        dir().listFiles()?.filter { it.extension == "jpg" }?.forEach { it.delete() }
        state.value = State(p.getBoolean("text", false), false, dir().listFiles()?.count { it.extension == "json" } ?: 0)
        com.monkaydee.tcgcatalogue.work.LearningWorker.schedule(context)
    }
    suspend fun consent(repo: CardRepository, text: Boolean, photos: Boolean) {
        // Save consent first, so revocation stops all future background transfers even offline.
        prefs().edit().putBoolean("text", text).putBoolean("photos", photos && text && PHOTOS_ENABLED).apply()
        state.value = state.value.copy(text = text, photos = photos && text && PHOTOS_ENABLED)
        if (!text) { dir().listFiles()?.forEach { it.delete() }; state.value = state.value.copy(pending = 0); deleteRemote(repo) }
        else if (!photos) withContext(Dispatchers.IO) { dir().listFiles()?.filter { it.extension == "jpg" }?.forEach { it.delete() } }
    }
    private fun identity(): JSONObject = JSONObject().put("install", prefs().getString("install", "")).put("token", prefs().getString("token", ""))
    suspend fun record(repo: CardRepository, row: Long, card: CardCandidate, variant: Variant, language: String, grade: com.monkaydee.tcgcatalogue.scan.GradeInfo? = null) = withContext(Dispatchers.IO) {
        if (app == null || !state.value.text) return@withContext
        val id = UUID.randomUUID().toString()
        val body = identity().put("id", id).put("row", row).put("kind", "recognition").put("humanConfirmed", true).put("readHash", readHash(card.name + " " + card.number)).put("created", System.currentTimeMillis())
            .put("card", JSONObject().put("game", card.game.name).put("id", card.cardId).put("name", card.name).put("set", card.setName).put("number", card.number).put("language", language).put("printing", variant.key))
        if (grade != null) body.put("selectedGrade", JSONObject().put("grader", grade.grader).put("grade", grade.grade).put("qualifier", grade.qualifier))
        File(dir(), "$id.json").writeText(body.toString())
        state.value = state.value.copy(pending = state.value.pending + 1)
    }
    suspend fun markAutomatic(row: Long) = withContext(Dispatchers.IO) {
        if (app == null || !state.value.text) return@withContext
        val file = dir().listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.firstOrNull { runCatching { JSONObject(it.readText()).optLong("row") == row }.getOrDefault(false) } ?: return@withContext
        file.writeText(JSONObject(file.readText()).put("humanConfirmed", false).toString())
    }
    suspend fun enrich(row: Long, texts: List<String>, suggested: String?, picture: Bitmap?) = withContext(Dispatchers.IO) {
        if (app == null || !state.value.text) return@withContext
        val file = dir().listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.firstOrNull { runCatching { JSONObject(it.readText()).optLong("row") == row }.getOrDefault(false) } ?: return@withContext
        val body = JSONObject(file.readText())
        body.put("readHash", readHash(texts.joinToString(" ")))
        body.remove("read"); body.remove("suggested")
        file.writeText(body.toString())
        val crop = if (PHOTOS_ENABLED && state.value.photos && picture != null) runCatching {
            val pixels = com.monkaydee.tcgcatalogue.scan.Pixels(picture.width, picture.height, IntArray(picture.width * picture.height).also { picture.getPixels(it, 0, picture.width, 0, 0, picture.width, picture.height) })
            com.monkaydee.tcgcatalogue.grade.CardRectifier.findQuad(pixels)?.let { quad -> com.monkaydee.tcgcatalogue.grade.CardRectifier.warp(pixels, quad).let { flat -> Bitmap.createBitmap(flat.argb, flat.width, flat.height, Bitmap.Config.ARGB_8888) } }
        }.getOrNull() else null
        if (crop != null) {
            val factor = minOf(1.0, 800.0 / maxOf(crop.width, crop.height))
            val small = Bitmap.createScaledBitmap(crop, (crop.width * factor).toInt(), (crop.height * factor).toInt(), true)
            File(dir(), "${body.getString("id")}.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
    }
    fun crops(): List<File> = if (app != null && state.value.photos) dir().listFiles()?.filter { it.extension == "jpg" && File(dir(), it.nameWithoutExtension + ".json").exists() }.orEmpty() else emptyList()
    suspend fun decline(file: File) = withContext(Dispatchers.IO) { file.delete() }
    suspend fun approve(file: File) = withContext(Dispatchers.IO) {
        if (!PHOTOS_ENABLED || !state.value.text || !state.value.photos) return@withContext
        val report = File(dir(), file.nameWithoutExtension + ".json")
        val body = JSONObject(report.readText()).put("photoApproved", true)
        report.writeText(body.toString())
    }
    private suspend fun request(repo: CardRepository, method: String, body: JSONObject, explicitPriceReport: Boolean = false) = withContext(Dispatchers.IO) {
        val s = repo.settings.current()
        check(s.hasServer) { "Price server is not configured" }
        check(s.serverUrl.startsWith("https://"))
        check(method != "POST" || explicitPriceReport || state.value.text) { "Recognition sharing is disabled" }
        client.newCall(Request.Builder().url(s.serverUrl.trimEnd('/') + "/v1/feedback").header("X-App-Key", s.serverKey).method(method, body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { response ->
            check(response.isSuccessful) { "Recognition reports: HTTP ${response.code}" }
        }
    }
    suspend fun flush(repo: CardRepository) = lock.withLock { withContext(Dispatchers.IO) {
        if (app == null || !state.value.text) return@withContext
        if (prefs().getBoolean("deletePending", false)) return@withContext // Finish withdrawal before submitting again.
        val result = runCatching {
            for (file in dir().listFiles()?.filter { it.extension == "json" }.orEmpty()) {
                if (!state.value.text) break
                val body = JSONObject(file.readText())
                if (body.has("read")) body.put("readHash", readHash(body.optString("read")))
                body.remove("read"); body.remove("suggested"); body.remove("image"); body.remove("photoApproved")
                body.optJSONObject("card")?.apply { remove("name"); remove("set") }
                val image = File(dir(), file.nameWithoutExtension + ".jpg")
                if (System.currentTimeMillis() - body.optLong("created", file.lastModified()) > 90 * 86400000L) { file.delete(); image.delete(); continue }
                if (!PHOTOS_ENABLED) image.delete()
                body.remove("row"); body.remove("created")
                request(repo, "POST", body)
                file.delete(); image.delete()
            }
        }
        state.value = state.value.copy(pending = dir().listFiles()?.count { it.extension == "json" } ?: 0, error = result.exceptionOrNull()?.message)
    } }
    suspend fun deleteRemote(repo: CardRepository) = lock.withLock { withContext(Dispatchers.IO) {
        if (app == null) return@withContext
        val result = runCatching { request(repo, "DELETE", identity()) }
        prefs().edit().putBoolean("deletePending", result.isFailure).apply()
        state.value = state.value.copy(error = result.exceptionOrNull()?.message)
    } }
    suspend fun retryDeletion(repo: CardRepository) { if (app != null && prefs().getBoolean("deletePending", false)) deleteRemote(repo) }
    /** Daily moderated hints can reorder existing candidates; they never create a match or override printed evidence. */
    suspend fun rank(repo: CardRepository, candidates: List<CardCandidate>, texts: List<String>): List<CardCandidate> = withContext(Dispatchers.IO) {
        if (app == null || candidates.size < 2) return@withContext candidates
        runCatching {
            val p = prefs(); val s = repo.settings.current()
            val cache = File(dir(), "rules.cache")
            if (s.hasServer && s.serverUrl.startsWith("https://") && (System.currentTimeMillis() - p.getLong("rulesAt", 0) > 86400000L || !cache.exists())) {
                client.newCall(Request.Builder().url(s.serverUrl.trimEnd('/') + "/v1/feedback/rules").header("X-App-Key", s.serverKey).build()).execute().use { response ->
                    check(response.isSuccessful)
                    val body = JSONObject(response.body!!.string())
                    val payload = body.getString("payload")
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
                    check(digest == body.getString("sha256"))
                    val temporary = File(dir(), "rules.tmp"); temporary.writeText(payload); check(temporary.renameTo(cache))
                    p.edit().putLong("rulesAt", System.currentTimeMillis()).apply()
                }
            }
            if (!cache.exists() || System.currentTimeMillis() - p.getLong("rulesAt", 0) > 7 * 86400000L) return@runCatching candidates
            val read = readHash(texts.joinToString(" "))
            val rules = org.json.JSONArray(cache.readText())
            val language = com.monkaydee.tcgcatalogue.scan.CardTextParser.detectLanguage(texts) ?: "EN"
            val context = candidates.first().game.name.lowercase(java.util.Locale.ROOT) + "|" + language + "|" + read
            val rule = (0 until rules.length()).map { rules.getJSONObject(it) }.firstOrNull { it.getString("context") == context }
            val selected = rule?.optString("card_id") ?: return@runCatching candidates
            if (candidates.none { it.cardId == selected }) return@runCatching candidates
            candidates.sortedByDescending { it.cardId == selected }.map { it.copy(warning = it.warning ?: com.monkaydee.tcgcatalogue.ui.AppStrings.get(com.monkaydee.tcgcatalogue.R.string.picture_check)) }
        }.getOrDefault(candidates)
    }

    suspend fun reportPrice(repo: CardRepository, card: OwnedCard) {
        check(app != null)
        val body = identity().put("id", UUID.randomUUID().toString()).put("kind", "price").put("read", card.priceSource.orEmpty() + " " + card.priceNote.orEmpty())
            .put("card", JSONObject().put("game", card.game.name).put("id", card.cardId).put("name", card.name).put("set", card.setName).put("number", card.number).put("language", card.language).put("printing", card.variant))
        body.put("evidence", JSONObject().put("amount", card.price).put("currency", card.priceCurrency).put("source", card.priceSource)
            .put("grader", card.grader).put("grade", card.grade).put("qualifier", card.gradeQualifier).put("quotedAt", card.priceUpdatedAt))
        body.put("readHash", readHash(body.optString("read"))); body.remove("read")
        body.optJSONObject("card")?.apply { remove("name"); remove("set") }
        request(repo, "POST", body, explicitPriceReport = true)
    }
}
