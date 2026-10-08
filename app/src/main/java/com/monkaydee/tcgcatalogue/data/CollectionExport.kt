package com.monkaydee.tcgcatalogue.data

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.request.SuccessResult
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.db.CostLot
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Local exports never send acquisition costs to the price or learning server. */
object CollectionExport {
    fun cell(value: Any?): String {
        var text = value?.toString().orEmpty()
        if (text.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) text = "'" + text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }
    suspend fun csv(context: Context, uri: Uri, cards: List<OwnedCard>, lots: List<CostLot>, settings: AppSettings) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { out ->
            out.write("card_id,game,name,set,number,printing,language,condition,grader,grade,certificate,quantity,lot_id,lot_quantity,purchase_per_copy,grading_per_copy,shipping_per_copy,tax_per_copy,cost_currency,quote_per_copy,quote_currency,quote_source,quote_time,quote_note,image_url,unrealized_pnl,display_currency\n")
            cards.forEach { c ->
                val owned = lots.filter { it.cardRowId == c.id }
                val pnl = CostLedger.pnl(c, owned, settings.currency, settings.usdToEur)
                val entries: List<CostLot?> = owned.ifEmpty { listOf(null) }
                entries.forEach { l -> out.write(listOf(c.cardId, c.game, c.name, c.setName, c.number, c.variantLabel, CardLanguage.displayCode(c.language), c.condition, c.grader, c.grade, c.certNumber, c.quantity, l?.id, l?.quantity, l?.purchase, l?.grading, l?.shipping, l?.tax, l?.currency, c.manualPrice ?: c.price, c.manualCurrency ?: c.priceCurrency, if (c.manualPrice != null) "manual" else c.priceSource, c.priceUpdatedAt, c.priceNote, c.imageUrl, pnl, settings.currency).joinToString(",") { cell(it) } + "\n") }
            }
        }
    }
    suspend fun pdf(context: Context, uri: Uri, cards: List<OwnedCard>, lots: List<CostLot>, settings: AppSettings) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val unknown = context.getString(R.string.tools_unknown)
        try {
            cards.chunked(7).ifEmpty { listOf(emptyList()) }.forEachIndexed { index, pageCards ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val canvas = page.canvas
                paint.textSize = 18f
                canvas.drawText(context.getString(R.string.tools_export), 30f, 34f, paint)
                paint.textSize = 10f
                canvas.drawText(DateFormat.getDateTimeInstance().format(Date()), 30f, 55f, paint)
                var y = 88f
                pageCards.forEach { card ->
                    val thumbnail = card.imageUrl?.let { url ->
                        (context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false)
                            .networkCachePolicy(CachePolicy.DISABLED).size(80, 112).build()) as? SuccessResult)?.drawable?.toBitmap()
                    }
                    if (thumbnail != null) canvas.drawBitmap(thumbnail, null, android.graphics.RectF(30f, y - 12f, 90f, y + 72f), paint)
                    val entries = lots.filter { it.cardRowId == card.id }
                    val basis = entries.map { CostLedger.basis(it, settings.currency, settings.usdToEur) }
                    val total = if (entries.sumOf { it.quantity } == card.quantity && basis.all { it != null }) basis.filterNotNull().sum() else null
                    val lines = listOf("${card.name} · ×${card.quantity}", "${card.setName} · ${card.number} · ${CardLanguage.displayCode(card.language)} · ${card.variantLabel}", listOfNotNull(card.condition, card.certNumber).joinToString(" · "),
                        "${context.getString(R.string.tools_costs)}: ${total?.let { Money.format(it, settings.currency) } ?: unknown}",
                        "${card.priceSource.orEmpty()} · ${Money.unitOrNull(card, settings.currency, settings.usdToEur)?.let { Money.format(it, settings.currency) } ?: unknown} · ${card.priceUpdatedAt?.let { DateFormat.getDateInstance().format(Date(it)) }.orEmpty()}")
                    lines.forEach { canvas.drawText(it.take(80), 110f, y, paint); y += 15f }
                    y += 22f
                }
                document.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri, "wt")!!.use { document.writeTo(it) }
        } finally { document.close() }
    }
}
