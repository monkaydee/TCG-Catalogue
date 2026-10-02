package com.monkaydee.tcgcatalogue.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume

/** Loads pages like a real browser does, for sites that refuse plain HTTP requests (eBay). */
interface Browser {
    /**
     * Opens [url], waits until the JavaScript expression [ready] is true (or a few seconds have
     * passed) and returns the string result of [script].
     */
    suspend fun evaluate(url: String, script: String, ready: String = "document.readyState === 'complete'"): String?

    suspend fun html(url: String): String? = evaluate(url, "document.documentElement.outerHTML")
}

/** [Browser] backed by an invisible WebView (the phone's Chrome engine). One page at a time. */
class WebViewBrowser(private val context: Context) : Browser {
    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())

    override suspend fun evaluate(url: String, script: String, ready: String): String? = mutex.withLock {
        withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val view = createView()
                    var finished = false
                    fun finish(result: String?) {
                        if (finished) return
                        finished = true
                        main.post { view.stopLoading(); view.destroy() }
                        if (cont.isActive) cont.resume(result)
                    }
                    fun poll(attempt: Int) {
                        if (finished) return
                        view.evaluateJavascript("(function(){try{return !!($ready);}catch(e){return false;}})()") { isReady ->
                            if (isReady == "true" || attempt >= MAX_POLLS) {
                                view.evaluateJavascript("(function(){try{return $script;}catch(e){return null;}})()") { value ->
                                    finish(decode(value))
                                }
                            } else {
                                main.postDelayed({ poll(attempt + 1) }, POLL_MS)
                            }
                        }
                    }
                    var started = false
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView, u: String) {
                            // Bot checks reload the page; start polling once, it survives reloads.
                            if (!started) {
                                started = true
                                main.postDelayed({ poll(0) }, POLL_MS)
                            }
                        }
                    }
                    cont.invokeOnCancellation { main.post { if (!finished) { finished = true; view.destroy() } } }
                    view.loadUrl(url)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView() = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = false
        settings.blockNetworkImage = true
    }

    /** evaluateJavascript returns the value JSON-encoded ("\"text\"" or "null"). */
    private fun decode(value: String?): String? {
        if (value == null || value == "null" || value == "undefined") return null
        return runCatching { (Json.parseToJsonElement(value) as? JsonPrimitive)?.content }.getOrNull()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val POLL_MS = 700L
        const val MAX_POLLS = 12
    }
}
