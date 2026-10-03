package com.monkaydee.tcgcatalogue.scan

import android.content.Context
import android.graphics.Bitmap
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.remote.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Finds a card by its picture alone, for cards whose number can't be read (worn, old, at an angle).
 * A small image model (downloaded once, about 11 MB) turns the picture into a fingerprint, which
 * is compared with the weekly picture index of every card (scripts/build_embeddings.py). Runs fully
 * on the phone.
 */
object PictureSearch {
    private const val BASE = "https://raw.githubusercontent.com/monkaydee/TCG-Catalogue/embeddings"
    private const val WEEK = 7 * 24 * 60 * 60 * 1000L

    /** Games in the picture index. */
    val GAMES = setOf(Game.POKEMON, Game.ONE_PIECE)

    /** A card found by picture: its id ("sv03.5-025", or "OP01-077|OP01-077_p1" with the printing). */
    data class Found(val game: Game, val id: String, val score: Float) {
        val cardId: String get() = id.substringBefore('|')
        val printing: String? get() = id.substringAfter('|', "").ifEmpty { null }
    }

    private val lock = Mutex()
    private var interpreter: Interpreter? = null
    private var projection: PictureProjection? = null
    private val indexes = HashMap<Game, PictureIndex>()
    private var input = 224
    private var art = floatArrayOf(0.08f, 0.10f, 0.92f, 0.55f)

    /**
     * The middle of a picture taken around the camera's card guide (the card fills about this
     * part of the analysed area, see TextAnalyzer), or a card-shaped centre crop of a photo.
     */
    fun cardCrop(picture: Bitmap, fromCamera: Boolean): Bitmap {
        val w = picture.width
        val h = picture.height
        return if (fromCamera) {
            Bitmap.createBitmap(picture, (w * 0.115f).toInt(), (h * 0.143f).toInt(), (w * 0.77f).toInt(), (h * 0.714f).toInt())
        } else {
            val ch = minOf(h.toFloat(), w / 0.716f) * 0.92f
            val cw = ch * 0.716f
            Bitmap.createBitmap(picture, ((w - cw) / 2).toInt(), ((h - ch) / 2).toInt(), cw.toInt(), ch.toInt())
        }
    }

    /** True once the model and index are on the phone (the first use downloads them). */
    fun isReady(context: Context): Boolean = File(dir(context), "model.tflite").exists() && File(dir(context), "EMBED_PCA.bin").exists()

    /** The [k] best matches for [picture] (a photo of one card, roughly filling it) among [games]. */
    private val http by lazy { Http() }

    suspend fun find(context: Context, picture: Bitmap, games: Set<Game>, k: Int = 6): List<Found> = lock.withLock {
        val wanted = games.filter { it in GAMES }.ifEmpty { return@withLock emptyList() }
        prepare(context, wanted)
        val model = interpreter ?: return@withLock emptyList()
        val pca = projection ?: return@withLock emptyList()
        val query = withContext(Dispatchers.Default) { pca.project(fingerprint(model, picture)) }
        wanted.flatMap { g -> indexes[g]?.search(query, k)?.map { Found(g, it.id, it.score) }.orEmpty() }
            .sortedByDescending { it.score }
            .take(k)
    }

    private suspend fun prepare(context: Context, games: List<Game>) = withContext(Dispatchers.IO) {
        val dir = dir(context).apply { mkdirs() }
        val metaFile = File(dir, "EMBED_META.json")
        val stale = !metaFile.exists() || System.currentTimeMillis() - metaFile.lastModified() > WEEK
        if (stale) runCatching { http.getBytes("$BASE/EMBED_META.json") }.getOrNull()?.let { write(metaFile, it) }
        if (!metaFile.exists()) error("picture index not available")
        val meta = Json.parseToJsonElement(metaFile.readText()) as JsonObject
        input = meta["input"]?.jsonPrimitive?.content?.toIntOrNull() ?: 224
        meta["art"]?.jsonArray?.map { it.jsonPrimitive.content.toFloat() }?.takeIf { it.size == 4 }?.let { art = it.toFloatArray() }
        val dims = meta["dims"]?.jsonPrimitive?.content?.toIntOrNull() ?: 256
        // The model is fetched again only if the index was built with another one.
        val modelUrl = meta["model"]?.jsonPrimitive?.content ?: error("no model in the index")
        val modelFile = File(dir, "model.tflite")
        val modelUrlFile = File(dir, "model.url")
        if (!modelFile.exists() || modelUrlFile.takeIf { it.exists() }?.readText() != modelUrl) {
            write(modelFile, http.getBytes(modelUrl))
            modelUrlFile.writeText(modelUrl)
            interpreter?.close()
            interpreter = null
        }
        val files = listOf("EMBED_PCA.bin") + games.flatMap { listOf("EMBED_${it.name}.bin", "EMBED_${it.name}.json") }
        for (name in files) {
            val f = File(dir, name)
            if (!f.exists() || stale) {
                runCatching { http.getBytes("$BASE/$name") }.getOrNull()?.let { write(f, it) }
                if (name == "EMBED_PCA.bin") projection = null
                indexes.remove(games.firstOrNull { name.contains(it.name) })
            }
        }
        if (interpreter == null) interpreter = Interpreter(modelFile, Interpreter.Options().setNumThreads(2))
        if (projection == null) projection = PictureProjection.parse(File(dir, "EMBED_PCA.bin").readBytes(), dims)
        for (g in games) {
            if (indexes[g] != null) continue
            val bin = File(dir, "EMBED_${g.name}.bin").takeIf { it.exists() } ?: continue
            val ids = Json.parseToJsonElement(File(dir, "EMBED_${g.name}.json").readText()).jsonArray.map { it.jsonPrimitive.content }
            indexes[g] = PictureIndex.parse(bin.readBytes(), ids)
        }
    }

    /** The card's fingerprint: the whole card and its artwork window, as the index was built. */
    private fun fingerprint(model: Interpreter, card: Bitmap): FloatArray {
        val w = card.width
        val h = card.height
        val artBitmap = Bitmap.createBitmap(
            card, (w * art[0]).toInt(), (h * art[1]).toInt(),
            ((art[2] - art[0]) * w).toInt().coerceAtLeast(1), ((art[3] - art[1]) * h).toInt().coerceAtLeast(1),
        )
        val v = run(model, card) + run(model, artBitmap)
        if (artBitmap !== card) artBitmap.recycle()
        normalise(v)
        return v
    }

    private fun run(model: Interpreter, picture: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(picture, input, input, true)
        val pixels = IntArray(input * input).also { scaled.getPixels(it, 0, input, 0, 0, input, input) }
        if (scaled !== picture) scaled.recycle()
        val buf = ByteBuffer.allocateDirect(4 * 3 * input * input).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buf.putFloat(((p shr 16) and 0xFF) / 255f)
            buf.putFloat(((p shr 8) and 0xFF) / 255f)
            buf.putFloat((p and 0xFF) / 255f)
        }
        buf.rewind()
        val size = model.getOutputTensor(0).shape().last()
        val out = Array(1) { FloatArray(size) }
        model.run(buf, out)
        return out[0].also { normalise(it) }
    }

    private fun dir(context: Context) = File(context.filesDir, "picture-index")

    private fun write(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
    }
}
