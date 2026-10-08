package com.monkaydee.tcgcatalogue.grade

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import com.monkaydee.tcgcatalogue.scan.Pixels
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pre-grading of one card side, entirely on the phone: find the card in the photo, flatten it,
 * check the photo, then measure centering and the wear of corners and edges.
 */
object PreGrader {
    /** Longest side the photo is reduced to before searching (keeps memory and time in check). */
    private const val WORK_SIDE = 2400

    data class Side(
        /** The flattened card ([CardRectifier.W] x [CardRectifier.H]). */
        val card: Bitmap,
        val problems: List<PhotoCheck.Problem>,
        val centering: Centering.Result?,
        val wear: Wear.Result,
        /** The photo as measured and the card's outline in it, for adjusting the corners by hand. */
        val photo: Bitmap? = null,
        val quad: Quad? = null,
        /** Width of the original photo ([photo] may be scaled down for measuring). */
        val outlineConfirmed: Boolean = false,
        /** Manual measurements are useful ratios, but are not calibrated model inputs. */
        val manualCentering: Boolean = false,
        val centeringSkipped: Boolean = false,
        val photoWidth: Int = photo?.width ?: 0,
        /** User observations on this rectified image; remeasurement creates a fresh review. */
        val wearFindings: Map<String, Wear.Finding> = emptyMap(),
        /** Lossless source and inspection files; large images stay off the UI heap. */
        val sourceFile: String? = null,
        val inspectionFile: String? = null,
        val inspectionWidth: Int = card.width,
        val inspectionHeight: Int = card.height,
        val surfaceFinding: SurfaceFinding = SurfaceFinding.NOT_REVIEWED,
        val surfacePhotos: List<String> = emptyList(),
        val contours: Map<String, ContourCheck.Evidence> = emptyMap(),
    ) {
        /** A grade estimate must not fill missing measurements with training averages. */
        val usableForCentering: Boolean get() = outlineConfirmed && problems.isEmpty() && centering != null
        val usableForWear: Boolean get() = outlineConfirmed && problems.isEmpty()
        val reportedWear: Boolean get() = wearFindings.values.any { it.damage }
        val usableForGrade: Boolean get() = usableForCentering && !manualCentering && centering?.rotationDegrees == 0.0 && wear.complete && !reportedWear
    }

    sealed interface Outcome {
        data class Ok(val side: Side) : Outcome
        /** No whole card could be found in the photo. */
        data object NoCard : Outcome
    }

    /**
     * Analyses [photo] (upright). [guide] is where the card should be, as fractions of the photo
     * (left, top, right, bottom), e.g. the camera's card guide; null searches the whole photo.
     */
    data class Outline(val photo: Bitmap, val quad: Quad, val photoWidth: Int = photo.width, val detected: Boolean = true)

    /** Full uncropped working photo; a fallback outline is only an editor seed, never grading evidence. */
    fun prepareOutline(photo: Bitmap, guide: FloatArray? = null): Outline {
        require(photo.width > 1 && photo.height > 1)
        val scale = minOf(1.0, WORK_SIDE.toDouble() / max(photo.width, photo.height))
        val work = if (scale < 1) Bitmap.createScaledBitmap(photo, (photo.width * scale).roundToInt(), (photo.height * scale).roundToInt(), true) else photo
        val p = pixels(work)
        val hint = guide?.takeIf { it.size == 4 && it.all { v -> v.isFinite() && v in 0f..1f } && it[0] < it[2] && it[1] < it[3] }?.let { g ->
            fun point(x: Float, y: Float) = Pt((x * (p.width - 1)).toDouble(), (y * (p.height - 1)).toDouble())
            Quad(point(g[0], g[1]), point(g[2], g[1]), point(g[2], g[3]), point(g[0], g[3]))
        }
        val quad = if (hint != null) CardRectifier.findQuadIn(p, hint) else CardRectifier.findQuad(p)
        val height = minOf((p.height - 1) * .8, (p.width - 1) * .8 / .716)
        val width = height * .716
        val x = (p.width - 1 - width) / 2; val y = (p.height - 1 - height) / 2
        val seed = hint ?: Quad(Pt(x, y), Pt(x + width, y), Pt(x + width, y + height), Pt(x, y + height))
        return Outline(work, quad ?: seed, photo.width, quad != null)
    }

    fun analyse(photo: Bitmap, guide: FloatArray? = null, sourceFile: File? = null): Outcome {
        val outline = prepareOutline(photo, guide)
        if (!outline.detected) {
            if (outline.photo !== photo) outline.photo.recycle()
            return Outcome.NoCard
        }
        return Outcome.Ok(measure(outline.photo, pixels(outline.photo), outline.quad,
            outline.photo.width.toDouble() / outline.photoWidth, outline.photoWidth, sourceFile))
    }

    /** Respect the confirmed physical corners exactly: do not snap to artwork or trim the cut again. */
    fun fromOutline(outline: Outline, quad: Quad = outline.quad): Side {
        validateOutline(outline.photo, quad)
        return measure(outline.photo, pixels(outline.photo), quad, outline.photo.width.toDouble() / outline.photoWidth,
            outline.photoWidth, preserveOutline = true).copy(outlineConfirmed = true)
    }

    fun adjust(side: Side, quad: Quad): Side {
        val photo = side.photo ?: return side
        validateOutline(photo, quad)
        return measure(photo, pixels(photo), quad, photo.width.toDouble() / side.photoWidth, side.photoWidth,
            side.sourceFile?.let(::File), preserveOutline = true).copy(outlineConfirmed = true)
    }

    private fun pixels(photo: Bitmap) = Pixels(photo.width, photo.height,
        IntArray(photo.width * photo.height).also { photo.getPixels(it, 0, photo.width, 0, 0, photo.width, photo.height) })

    private fun validateOutline(photo: Bitmap, quad: Quad) {
        require(quad.corners.all { it.x.isFinite() && it.y.isFinite() && it.x >= 0 && it.y >= 0 && it.x < photo.width && it.y < photo.height })
        // Clockwise, convex, non-overlapping corners; reject crossed or collapsed selections.
        val points = quad.corners
        require(points.indices.all { i ->
            val a = points[i]; val b = points[(i + 1) % 4]; val c = points[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 1.0
        })
    }

    private fun measure(work: Bitmap, p: Pixels, quad: Quad, scale: Double, photoWidth: Int = (work.width / scale).roundToInt(), sourceFile: File? = null, preserveOutline: Boolean = false): Side {
        val upright = upright(quad)
        val raw = CardRectifier.warp(p, upright)
        val trim = if (preserveOutline) List(4) { 0 } else "LRTB".map { Centering.cut(raw, it) }
        val flat = if (preserveOutline) raw else trimmed(raw)
        val c = upright.corners
        val widthInPhoto = (hypot(c[1].x - c[0].x, c[1].y - c[0].y) + hypot(c[2].x - c[3].x, c[2].y - c[3].y)) / 2 / scale
        val bitmap = Bitmap.createBitmap(flat.argb, flat.width, flat.height, Bitmap.Config.ARGB_8888)
        val detail = sourceFile?.let { inspection(it, upright, work.width, trim) }
        return Side(
            card = bitmap,
            problems = PhotoCheck.problems(flat, widthInPhoto),
            centering = Centering.measure(flat),
            wear = Wear.measure(flat),
            photo = work,
            quad = quad,
            photoWidth = photoWidth,
            sourceFile = sourceFile?.absolutePath,
            inspectionFile = detail?.first,
            inspectionWidth = detail?.second ?: bitmap.width,
            inspectionHeight = detail?.third ?: bitmap.height,
            contours = ContourCheck.measure(flat),
        )
    }

    private fun inspection(source: File, quad: Quad, workWidth: Int, trim: List<Int>): Triple<String, Int, Int>? = runCatching {
        val original = BitmapFactory.decodeFile(source.absolutePath) ?: return null
        try {
            val q = quad.scaled(original.width.toDouble() / workWidth)
            val nativeWidth = (hypot(q.tr.x - q.tl.x, q.tr.y - q.tl.y) + hypot(q.br.x - q.bl.x, q.br.y - q.bl.y)) / 2
            // Never upscale: preserve up to 2,200 card pixels across for inspection.
            val w = nativeWidth.roundToInt().coerceIn(1, 2200)
            val h = (w * CardRectifier.H.toDouble() / CardRectifier.W).roundToInt().coerceAtLeast(1)
            val high = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val matrix = Matrix()
            val from = q.corners.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray()
            check(matrix.setPolyToPoly(from, 0, floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()), 0, 4))
            Canvas(high).drawBitmap(original, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            // Match the measurement crop rather than measuring from a different physical outline.
            val l = (trim[0] * w.toDouble() / CardRectifier.W).roundToInt()
            val r = (trim[1] * w.toDouble() / CardRectifier.W).roundToInt()
            val t = (trim[2] * h.toDouble() / CardRectifier.H).roundToInt()
            val b = (trim[3] * h.toDouble() / CardRectifier.H).roundToInt()
            val crop = Bitmap.createBitmap(high, l, t, (w - l - r).coerceAtLeast(1), (h - t - b).coerceAtLeast(1))
            try {
                val file = File.createTempFile("inspection-", ".png", source.parentFile)
                file.outputStream().use { check(crop.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                Triple(file.absolutePath, crop.width, crop.height)
            } finally { if (crop !== high) crop.recycle(); high.recycle() }
        } finally { original.recycle() }
    }.getOrNull()

    /** The straightened card with leftover background strips cut off, scaled back to the standard size. */
    private fun trimmed(flat: Pixels): Pixels {
        val l = Centering.cut(flat, 'L')
        val r = Centering.cut(flat, 'R')
        val t = Centering.cut(flat, 'T')
        val b = Centering.cut(flat, 'B')
        if (l + r + t + b == 0) return flat
        val full = Bitmap.createBitmap(flat.argb, flat.width, flat.height, Bitmap.Config.ARGB_8888)
        val cropped = Bitmap.createBitmap(full, l, t, flat.width - l - r, flat.height - t - b)
        val scaled = Bitmap.createScaledBitmap(cropped, flat.width, flat.height, true)
        val px = IntArray(flat.width * flat.height).also { scaled.getPixels(it, 0, flat.width, 0, 0, flat.width, flat.height) }
        listOf(full, cropped, scaled).distinct().forEach { it.recycle() }
        return Pixels(flat.width, flat.height, px)
    }

    /** A card lying on its side is turned upright (its short sides become top and bottom). */
    private fun upright(q: Quad): Quad {
        val c = q.corners
        val top = hypot(c[1].x - c[0].x, c[1].y - c[0].y)
        val left = hypot(c[3].x - c[0].x, c[3].y - c[0].y)
        return if (top > left) Quad(c[3], c[0], c[1], c[2]) else q
    }
}
