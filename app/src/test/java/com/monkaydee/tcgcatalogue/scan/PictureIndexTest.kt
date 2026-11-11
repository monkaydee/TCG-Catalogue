package com.monkaydee.tcgcatalogue.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class PictureIndexTest {
    private fun file(rows: List<FloatArray>, dims: Int): ByteArray {
        val scale = FloatArray(dims) { d -> rows.maxOf { kotlin.math.abs(it[d]) } / 127f + 1e-9f }
        val buf = ByteBuffer.allocate(12 + 4 * dims + rows.size * dims).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("TCGE".toByteArray()).putInt(rows.size).putInt(dims)
        scale.forEach { buf.putFloat(it) }
        rows.forEach { r -> r.forEachIndexed { d, x -> buf.put(Math.round(x / scale[d]).coerceIn(-127, 127).toByte()) } }
        return buf.array()
    }

    private fun randomUnit(r: Random, dims: Int) = FloatArray(dims) { r.nextFloat() - 0.5f }.also { normalise(it) }

    @Test fun findsTheClosestCards() {
        val r = Random(3)
        val dims = 32
        val rows = List(200) { randomUnit(r, dims) }
        val index = PictureIndex.parse(file(rows, dims), List(200) { "card$it" })
        // A slightly disturbed copy of card 42 finds card 42 first.
        val query = FloatArray(dims) { rows[42][it] + (r.nextFloat() - 0.5f) * 0.05f }.also { normalise(it) }
        val found = index.search(query, 5)
        assertEquals("card42", found.first().id)
        assertEquals(5, found.size)
        assertTrue(found.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test fun projectionReducesAndNormalises() {
        val n = 6
        val dims = 2
        val mean = FloatArray(n) { 0.1f }
        val buf = ByteBuffer.allocate(4 * n + 2 * n * dims).order(ByteOrder.LITTLE_ENDIAN)
        mean.forEach { buf.putFloat(it) }
        // Components: first dimension reads input 0, second reads input 1 (float16 1.0 = 0x3C00).
        for (i in 0 until n) for (d in 0 until dims) buf.putShort(if (i == d) 0x3C00.toShort() else 0)
        val p = PictureProjection.parse(buf.array(), dims)
        val out = p.project(floatArrayOf(3.1f, 4.1f, 9f, 9f, 9f, 9f))
        assertEquals(0.6f, out[0], 1e-4f)
        assertEquals(0.8f, out[1], 1e-4f)
    }
}
