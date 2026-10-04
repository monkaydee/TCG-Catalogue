package com.monkaydee.tcgcatalogue

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder

/**
 * Free crash reports without a server: when the app crashes, the stack trace is kept in a file;
 * the next start offers to report it (a prefilled GitHub issue, or the share sheet). Nothing is
 * sent unless the user chooses to.
 */
object CrashReporter {
    private const val FILE = "last-crash.txt"
    private const val ISSUES = "https://github.com/monkaydee/TCG-Catalogue/issues/new"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(report(error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report of the last crash, if one hasn't been dealt with yet. */
    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    /** Opens a new GitHub issue with the report filled in (shortened to fit a URL). */
    fun openIssue(context: Context, report: String) {
        val title = report.lineSequence().firstOrNull { it.startsWith("Exception:") }?.removePrefix("Exception:")?.trim()?.take(80) ?: "Crash report"
        val body = "**What were you doing when it crashed?**\n\n\n**Report**\n```\n${report.take(5500)}\n```"
        val url = "$ISSUES?title=${enc("Crash: $title")}&body=${enc(body)}"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "CardNavo crash report").putExtra(Intent.EXTRA_TEXT, report)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** App version, device and the stack trace — no collection data, no personal data. */
    internal fun report(error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Exception: ${error.javaClass.name}: ${error.message.orEmpty().take(200)}")
            appendLine()
            append(trace.lineSequence().take(60).joinToString("\n"))
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
