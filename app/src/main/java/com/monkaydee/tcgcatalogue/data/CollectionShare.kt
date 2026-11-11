package com.monkaydee.tcgcatalogue.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shares cards as one self-contained web page (a file sent through the share sheet): pictures,
 * names, sets, slabs and, if wanted, prices. Nothing is uploaded or hosted by the app; the page
 * loads card pictures from the catalogues' image servers when opened.
 */
object CollectionShare {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun html(title: String, subtitle: String, cards: List<OwnedCard>, prices: ((OwnedCard) -> String)?, total: String?): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>").append(esc(title)).append("</title><style>")
        append("body{font-family:system-ui,sans-serif;margin:0;padding:16px;background:#111318;color:#eceef4}")
        append("h1{font-size:22px;margin:0 0 4px}p.s{color:#9aa0ad;margin:0 0 16px}")
        append(".g{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px}")
        append(".c{background:#1c1f26;border-radius:10px;padding:8px}.c img{width:100%;aspect-ratio:63/88;object-fit:cover;border-radius:6px;background:#2a2e37}")
        append(".n{font-weight:600;font-size:14px;margin-top:6px}.m{color:#9aa0ad;font-size:12px}.b{display:inline-block;background:#3b4a7a;border-radius:6px;padding:1px 6px;font-size:12px;font-weight:700;margin-top:4px}")
        append(".p{font-weight:700;margin-top:4px}footer{color:#6b7280;font-size:11px;margin-top:20px}</style></head><body>")
        append("<h1>").append(esc(title)).append("</h1><p class=\"s\">").append(esc(subtitle))
        total?.let { append(" · ").append(esc(it)) }
        append("</p><div class=\"g\">")
        for (c in cards) {
            append("<div class=\"c\">")
            c.imageUrl?.takeIf { it.startsWith("https://") }?.let { append("<img loading=\"lazy\" alt=\"\" src=\"").append(esc(it)).append("\">") }
            append("<div class=\"n\">").append(esc(c.name)).append("</div>")
            append("<div class=\"m\">").append(esc(listOf(c.setName, c.number, c.language).filter { it.isNotBlank() }.joinToString(" · ")))
            if (c.quantity > 1) append(" · ×").append(c.quantity)
            append("</div>")
            val slab = CollectionList.slab(c)
            append("<div class=\"b\">").append(esc(slab ?: c.condition)).append("</div>")
            prices?.let { append("<div class=\"p\">").append(esc(it(c))).append("</div>") }
            append("</div>")
        }
        append("</div><footer>CardNavo</footer></body></html>")
    }

    suspend fun share(context: Context, title: String, subtitle: String, cards: List<OwnedCard>, prices: ((OwnedCard) -> String)?, total: String?) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "cardnavo-cards.html").apply { writeText(html(title, subtitle, cards, prices, total)) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
