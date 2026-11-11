package com.monkaydee.tcgcatalogue.widget

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.ui.AppStrings
import kotlin.math.min

data class WidgetModel(
    val title: String, val value: String, val change: String?, val up: Boolean,
    val cards: String, val sealed: String, val missing: String, val coverage: String,
    val updated: String,
) {
    val description: String get() = listOfNotNull(title, value, change, cards, sealed, missing, coverage, updated).joinToString(". ")
}

/** Shared by the actual launcher widget and its live preview, including the bundled Inter font. */
object WidgetRenderer {
    fun render(context: Context, model: WidgetModel, appearance: WidgetAppearance, width: Int, height: Int): Bitmap {
        val w = width.coerceIn(100, 600).toFloat()
        val h = height.coerceIn(48, 600).toFloat()
        val bitmap = Bitmap.createBitmap((w * 2).toInt(), (h * 2).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(2f, 2f) }
        val painter = Painter(context, canvas, appearance, w, h)
        painter.draw(model)
        return bitmap
    }

    private class Painter(context: Context, val c: Canvas, val a: WidgetAppearance, val w: Float, val h: Float) {
        private val regular = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.create("sans-serif", Typeface.NORMAL)
        private val bold = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.create("sans-serif", Typeface.BOLD)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fg = a.textColor
        private val mint = Color.rgb(105, 232, 208)
        private val pad = if (w < 180) 12f else 18f
        private val content = w - pad * 2
        private val compact = h < 120
        private val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 1.5f)

        private fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float = 0f) {
            paint.apply { this.color = color; shader = null; style = Paint.Style.FILL }
            c.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
        }

        // Fit value and coverage to actual launcher bounds; never ellipsize the money amount.
        private fun text(value: String, x: Float, baseline: Float, size: Float, width: Float = content, strong: Boolean = false, color: Int = fg) {
            paint.apply { shader = null; style = Paint.Style.FILL; this.color = color; typeface = if (strong) bold else regular; textSize = min(size * fontScale, size + 3f) }
            val measured = paint.measureText(value)
            if (measured > width) paint.textSize *= width / measured
            c.drawText(value, x, baseline, paint)
        }

        private fun muted() = Color.argb(210, Color.red(fg), Color.green(fg), Color.blue(fg))
        private fun line(y: Float) = rect(pad, y, w - pad, y + 1, Color.argb(50, Color.red(fg), Color.green(fg), Color.blue(fg)))
        private fun header(m: WidgetModel) {
            if (h < 80) return
            text("CARDNAVO", pad, pad + 9, 9f, content - 30, true)
            if (!compact) text(m.title, pad, pad + 29, 11f, color = muted())
        }

        fun draw(m: WidgetModel) {
            if (!a.transparent) {
                paint.shader = LinearGradient(0f, 0f, w, h,
                    when (a.style) {
                        WidgetStyle.VALUE -> intArrayOf(0xFF15272A.toInt(), 0xFF10191E.toInt())
                        WidgetStyle.DASHBOARD -> intArrayOf(0xFF19212E.toInt(), 0xFF111923.toInt())
                        WidgetStyle.COLLECTOR -> intArrayOf(0xFF243533.toInt(), 0xFF14201F.toInt())
                    }, null, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(0f, 0f, w, h), 24f, 24f, paint)
                paint.shader = null
            }
            when (a.style) {
                WidgetStyle.VALUE -> focus(m)
                WidgetStyle.DASHBOARD -> dashboard(m)
                WidgetStyle.COLLECTOR -> collector(m)
            }
        }

        private fun focus(m: WidgetModel) {
            header(m)
            if (compact) {
                text(m.value, pad, h - 23, min(30f, h * .38f), content - if (h < 80) 30f else 0f, true)
                text(m.coverage, pad, h - 8, 9f, color = muted())
                return
            }
            val valueY = (h * .51f).coerceAtLeast(76f)
            text(m.value, pad, valueY, if (w < 200) 33f else 40f, content, true)
            text(m.change ?: m.coverage, pad, valueY + 22, 11f, color = muted())
            line(h - 49)
            text(m.cards, pad, h - 29, 10f, content * .58f)
            text(m.missing, pad + content * .62f, h - 29, 10f, content * .38f, color = muted())
            text(m.updated, pad, h - 13, 9f, color = muted())
        }

        private fun dashboard(m: WidgetModel) {
            header(m)
            if (h < 160) {
                text(m.value, pad, h - 26, min(27f, h * .34f), content * .64f, true)
                if (h >= 80) text(m.cards, pad + content * .68f, h - 26, 10f, content * .32f)
                text(m.coverage, pad, h - 9, 9f, color = muted())
                return
            }
            text(m.value, pad, h * .41f + 9, if (h < 180) 25f else if (w < 200) 28f else 35f, content, true)
            text(m.coverage, pad, h * .41f + 27, 10f, color = muted())
            val top = h - 64f
            val gap = 6f
            val cell = (content - gap * 2) / 3
            val labels = listOf(R.string.widget_short_cards, R.string.widget_short_sealed, R.string.widget_short_missing)
            val values = listOf(m.cards.substringBefore(' '), m.sealed.substringBefore(' '), m.missing.substringBefore(' '))
            labels.forEachIndexed { i, label ->
                val x = pad + i * (cell + gap)
                if (!a.transparent) rect(x, top, x + cell, h - 27, 0xFF27323E.toInt(), 10f)
                text(values[i], x + 6, top + 18, 16f, cell - 12, true)
                text(AppStrings.get(label), x + 6, top + 32, 8f, cell - 12, color = muted())
            }
            text(m.updated, pad, h - 11, 9f, color = muted())
        }

        private fun collector(m: WidgetModel) {
            header(m)
            if (h < 80) {
                rect(pad, 12f, pad + 2, h - 23, if (a.transparent) fg else mint, 1f)
                text(m.value, pad + 7, h - 26, 24f, content - 37, true)
                text(m.coverage, pad, h - 9, 9f, color = muted())
                return
            }
            val artWidth = if (compact) 23f else (content * .28f).coerceAtMost(60f)
            val artHeight = artWidth * 1.4f
            val artTop = if (compact) h - artHeight - 13 else (h - artHeight) / 2 + 5
            c.save()
            c.rotate(-9f, pad + artWidth / 2, artTop + artHeight / 2)
            outline(pad - 3, artTop + 3, pad + artWidth - 3, artTop + artHeight + 3, muted(), 5f)
            c.restore()
            if (!a.transparent) rect(pad, artTop, pad + artWidth, artTop + artHeight, 0xFF1A4140.toInt(), 5f)
            outline(pad, artTop, pad + artWidth, artTop + artHeight, if (a.transparent) fg else mint, 5f)
            outline(pad + 5, artTop + 7, pad + artWidth - 5, artTop + artHeight - 7, muted(), 2f)
            val cx = pad + artWidth / 2
            val cy = artTop + artHeight / 2
            val star = Path().apply { moveTo(cx, cy - 8); lineTo(cx + 6, cy); lineTo(cx, cy + 8); lineTo(cx - 6, cy); close() }
            paint.apply { color = if (a.transparent) fg else mint; style = Paint.Style.FILL }
            c.drawPath(star, paint)
            val x = pad + artWidth + 12
            val width = w - pad - x
            val y = if (compact) h - 30 else h * .53f
            text(m.value, x, y, if (compact) 24f else 31f, width, true)
            text(if (compact) m.coverage else m.cards, x, y + 17, 10f, width, color = muted())
            if (!compact) {
                line(h - 48)
                text(m.change ?: m.coverage, pad, h - 29, 10f, color = muted())
                text(m.updated, pad, h - 13, 9f, color = muted())
            }
        }

        private fun outline(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float) {
            paint.apply { shader = null; this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.2f }
            c.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
