package com.monkaydee.tcgcatalogue.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import kotlin.math.max

/**
 * Finds a card by its picture alone, for cards whose number can't be read (worn, old, at an angle).
 * A small image model (downloaded once, about 11 MB) turns the picture into a fingerprint, which
 * is compared with the weekly picture index of every card (scripts/build_embeddings.py). Runs fully
 * on the phone.
 */
object PictureSearch {
    private const val BASE = "https://raw.githubusercontent.com/monkaydee/TCG-Catalogue/embeddings"
    private const val WEEK = 7 * 24 * 60 * 60 * 1000L

    /** Games the picture index can cover (the weekly build lists the ones it has in its meta file). */
    val GAMES: Set<Game> = Game.entries.toSet()

    /**
     * A card found by picture: its id as the game's lookup uses it ("sv03.5-025", Magic "dmu/107",
     * a TCGplayer product id for the indexed games), for One Piece with the printing
     * ("OP01-077|OP01-077_p1").
     */
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
    private val http by lazy { Http() }

    /**
     * The parts of [picture] that may be the card: the usual crop (the middle of the camera's card
     * guide, or a card-shaped centre crop of a photo) and the card-shaped areas [CardLocator]
     * finds. A card lying on its side is tried turned both ways. Picture search keeps whichever
     * crop matches a card best, so a card that is small or off-centre in a photo is still found.
     */
    fun crops(picture: Bitmap, fromCamera: Boolean): List<Bitmap> {
        val w = picture.width
        val h = picture.height
        val usual = if (fromCamera) {
            Bitmap.createBitmap(picture, (w * 0.115f).toInt(), (h * 0.143f).toInt(), (w * 0.77f).toInt(), (h * 0.714f).toInt())
        } else {
            val ch = minOf(h.toFloat(), w / 0.716f) * 0.92f
            val cw = ch * 0.716f
            Bitmap.createBitmap(picture, ((w - cw) / 2).toInt(), ((h - ch) / 2).toInt(), cw.toInt(), ch.toInt())
        }
        val scale = CardLocator.SIDE.toFloat() / max(w, h)
        val small = Bitmap.createScaledBitmap(picture, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
        val pixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val found = CardLocator.locate(Pixels(small.width, small.height, pixels))
        if (small !== picture) small.recycle()
        val located = found.flatMap { f ->
            val x = (f.rect.x / scale).toInt().coerceIn(0, w - 1)
            val y = (f.rect.y / scale).toInt().coerceIn(0, h - 1)
            val cw = (f.rect.w / scale).toInt().coerceIn(1, w - x)
            val ch = (f.rect.h / scale).toInt().coerceIn(1, h - y)
            if (!f.lying) {
                listOf(Bitmap.createBitmap(picture, x, y, cw, ch))
            } else {
                listOf(90f, 270f).map { Bitmap.createBitmap(picture, x, y, cw, ch, Matrix().apply { postRotate(it) }, true) }
            }
        }
        return listOf(usual) + located
    }

    /** True once the model and index are on the phone (the first use downloads them). */
    fun isReady(context: Context): Boolean = File(dir(context), "model.tflite").exists() && File(dir(context), "EMBED_PCA.bin").exists()

    /**
     * The [k] best matches among [games] for the card in [crops] (see [crops]): every crop is
     * searched and the one whose best match is closest wins.
     */
    suspend fun find(context: Context, crops: List<Bitmap>, games: Set<Game>, k: Int = 8): List<Found> = lock.withLock {
        val wanted = games.filter { it in GAMES }.ifEmpty { return@withLock emptyList() }
        prepare(context, wanted)
        val model = interpreter ?: return@withLock emptyList()
        val pca = projection ?: return@withLock emptyList()
        withContext(Dispatchers.Default) {
            crops.map { crop ->
                val query = pca.project(fingerprint(model, crop))
                wanted.flatMap { g -> indexes[g]?.search(query, k)?.map { Found(g, it.id, it.score) }.orEmpty() }
                    .sortedByDescending { it.score }
                    .take(k)
            }.maxByOrNull { it.firstOrNull()?.score ?: -1f }.orEmpty()
        }
    }

    /**
     * Downloads what is missing. The projection and every game's index must come from the same
     * weekly build, so when a new build is out, everything is fetched again together.
     */
    private suspend fun prepare(context: Context, games: List<Game>) = withContext(Dispatchers.IO) {
        val dir = dir(context).apply { mkdirs() }
        val metaFile = File(dir, "EMBED_META.json")
        val local = metaFile.takeIf { it.exists() }?.let { runCatching { Json.parseToJsonElement(it.readText()) as JsonObject }.getOrNull() }
        val listed = local?.get("games")?.let { it as? JsonObject }?.keys
        val missing = !File(dir, "EMBED_PCA.bin").exists() ||
            games.any { g -> (listed == null || g.name in listed) && !File(dir, "EMBED_${g.name}.bin").exists() }
        val old = local == null || System.currentTimeMillis() - metaFile.lastModified() > WEEK
        var meta = local
        if (old || missing) {
            val remote = runCatching { http.getBytes("$BASE/EMBED_META.json") }.getOrNull()
            val parsed = remote?.let { runCatching { Json.parseToJsonElement(String(it)) as JsonObject }.getOrNull() }
            if (parsed != null) {
                val newBuild = parsed["updated"]?.jsonPrimitive?.content != local?.get("updated")?.jsonPrimitive?.content
                if (newBuild) {
                    // Drop everything of the old build; it is fetched again below as needed.
                    dir.listFiles { f -> f.name.startsWith("EMBED_") && f.name != "EMBED_META.json" }?.forEach { it.delete() }
                    projection = null
                    indexes.clear()
                }
                write(metaFile, remote)
                meta = parsed
            } else if (local != null) {
                metaFile.setLastModified(System.currentTimeMillis() - WEEK + 60 * 60 * 1000L) // try again in an hour
            }
        }
        meta ?: error("picture index not available")
        input = meta["input"]?.jsonPrimitive?.content?.toIntOrNull() ?: 224
        meta["art"]?.jsonArray?.map { it.jsonPrimitive.content.toFloat() }?.takeIf { it.size == 4 }?.let { art = it.toFloatArray() }
        val dims = meta["dims"]?.jsonPrimitive?.content?.toIntOrNull() ?: 256
        val available = (meta["games"] as? JsonObject)?.keys.orEmpty()
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
        val files = listOf("EMBED_PCA.bin") + games.filter { it.name in available }.flatMap { listOf("EMBED_${it.name}.bin", "EMBED_${it.name}.json") }
        for (name in files) {
            val f = File(dir, name)
            if (!f.exists()) write(f, http.getBytes("$BASE/$name"))
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
