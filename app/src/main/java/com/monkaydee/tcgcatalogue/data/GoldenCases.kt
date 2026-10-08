package com.monkaydee.tcgcatalogue.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

/**
 * Recognition test cases collected on the phone: a photo and the card it really shows. Saved when
 * the user corrects a recognised card (always) or confirms one (when "collect test cases" is on).
 * Nothing leaves the phone until the user exports the cases (a zip for the golden set, see
 * docs/GOLDEN_SET.md).
 */
object GoldenCases {
    private const val MAX_SIDE = 2400
    private const val HEADER = "file,cardId,language,grader,grade,source"

    private fun dir(context: Context) = File(context.filesDir, "golden").apply { mkdirs() }

    /** How many cases are stored. */
    fun count(context: Context): Int = File(dir(context), "cases.csv").takeIf { it.exists() }?.readLines()?.drop(1)?.count { it.isNotBlank() } ?: 0

    /** Stores [photo] as a case showing [cardId]; [source] is "correction" or "confirmed". */
    suspend fun save(context: Context, photo: Uri, cardId: String, language: String?, grader: String?, grade: String?, source: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = dir(context)
            val name = "${System.currentTimeMillis()}_${cardId.replace(Regex("[^A-Za-z0-9-]"), "_")}.jpg"
            val bitmap = context.contentResolver.openInputStream(photo)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching
            val scale = MAX_SIDE.toDouble() / max(bitmap.width, bitmap.height)
            val out = if (scale < 1) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
            File(dir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val csv = File(dir, "cases.csv")
            if (!csv.exists()) csv.writeText("$HEADER\n")
            csv.appendText(listOf(name, cardId, language.orEmpty(), grader.orEmpty(), grade.orEmpty(), source).joinToString(",") + "\n")
        }
    }

    /** All cases as one zip, offered through the share sheet. Returns false when there are none. */
    suspend fun share(context: Context): Boolean {
        val zip = withContext(Dispatchers.IO) {
            val dir = dir(context)
            val files = dir.listFiles().orEmpty().filter { it.isFile }
            if (files.none { it.name == "cases.csv" }) return@withContext null
            val shared = File(context.cacheDir, "shared").apply { mkdirs() }
            val zip = File(shared, "cardnavo-test-cases.zip")
            ZipOutputStream(zip.outputStream()).use { z ->
                files.forEach { f -> z.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
            zip
        } ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Deletes all stored cases (after exporting them). */
    fun clear(context: Context) {
        dir(context).deleteRecursively()
    }
}
