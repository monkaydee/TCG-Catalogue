package com.monkaydee.tcgcatalogue.scan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * The reduction of a full picture fingerprint (2 × 1280 numbers from the image model) to the
 * index's 256 numbers: subtract [mean], project on [components] (PCA, fitted by
 * scripts/build_embeddings.py).
 */
class PictureProjection(private val mean: FloatArray, private val components: FloatArray, val dims: Int) {
    val inputSize: Int get() = mean.size

    /** [v] reduced and normalised to length 1. */
    fun project(v: FloatArray): FloatArray {
        require(v.size == mean.size)
        val out = FloatArray(dims)
        for (i in v.indices) {
            val x = v[i] - mean[i]
            if (x == 0f) continue
            val row = i * dims
            for (d in 0 until dims) out[d] += x * components[row + d]
        }
        normalise(out)
        return out
    }

    companion object {
        /** EMBED_PCA.bin: float32 mean[n], float16 components[n][dims], little-endian. */
        fun parse(bytes: ByteArray, dims: Int): PictureProjection {
            val n = bytes.size / (4 + 2 * dims)
            require(n * (4 + 2 * dims) == bytes.size) { "unexpected size" }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val mean = FloatArray(n) { buf.float }
            val components = FloatArray(n * dims) { halfToFloat(buf.short) }
            return PictureProjection(mean, components, dims)
        }

        private fun halfToFloat(h: Short): Float {
            val bits = h.toInt() and 0xFFFF
            val sign = (bits ushr 15) and 1
            val exp = (bits ushr 10) and 0x1F
            val frac = bits and 0x3FF
            val value = when (exp) {
                0 -> frac / 1024f * 6.1035156e-5f
                31 -> if (frac == 0) Float.POSITIVE_INFINITY else Float.NaN
                else -> (1 + frac / 1024f) * Math.scalb(1f, exp - 15)
            }
            return if (sign == 1) -value else value
        }
    }
}

/** One game's picture index: a fingerprint per card, stored as bytes, searched by similarity. */
class PictureIndex(val ids: List<String>, private val dims: Int, private val scale: FloatArray, private val rows: ByteArray) {
    data class Match(val id: String, val score: Float)

    init {
        require(rows.size == ids.size * dims)
    }

    /** The [k] cards whose fingerprint is most like [query] (already projected), best first. */
    fun search(query: FloatArray, k: Int = 8): List<Match> {
        require(query.size == dims)
        val q = FloatArray(dims) { query[it] * scale[it] }
        val best = ArrayList<Match>(k + 1)
        for (r in ids.indices) {
            var dot = 0f
            val base = r * dims
            for (d in 0 until dims) dot += q[d] * rows[base + d]
            if (best.size < k || dot > best.last().score) {
                val m = Match(ids[r], dot)
                val at = best.indexOfFirst { it.score < dot }.let { if (it < 0) best.size else it }
                best.add(at, m)
                if (best.size > k) best.removeAt(best.size - 1)
            }
        }
        return best
    }

    companion object {
        /** EMBED_<GAME>.bin ("TCGE", count, dims, float32 scale[dims], int8 rows) and its id list. */
        fun parse(bytes: ByteArray, ids: List<String>): PictureIndex {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            require(String(magic) == "TCGE") { "not a picture index" }
            val count = buf.int
            val dims = buf.int
            require(count == ids.size) { "index and id list don't match" }
            val scale = FloatArray(dims) { buf.float }
            val rows = ByteArray(count * dims).also { buf.get(it) }
            return PictureIndex(ids, dims, scale, rows)
        }
    }
}

internal fun normalise(v: FloatArray) {
    var sum = 0.0
    for (x in v) sum += x * x
    val len = sqrt(sum).toFloat().takeIf { it > 0f } ?: return
    for (i in v.indices) v[i] /= len
}
