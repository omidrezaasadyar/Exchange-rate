package ir.exchangerate.app.data.api.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Single shared WebView used to fetch the fully-rendered HTML of pages whose
 * prices are populated by JavaScript (alanchand.com, donya-e-eqtesad.com).
 *
 * fetchRenderedHtml polls a "ready" JS expression every 250ms after the page
 * finishes loading; once it returns true (or the maximum wait elapses) it
 * dumps the rendered HTML back via a JS bridge.
 */
class WebViewScraper(private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var webView: WebView? = null

    suspend fun fetchRenderedHtml(
        url: String,
        readyJsExpression: String = "document.body && document.body.innerText.length > 100",
        minDelayMs: Long = 1500L,
        maxWaitAfterLoadMs: Long = 15_000L,
        timeoutMs: Long = 30_000L,
    ): String = mutex.withLock {
        withTimeout(timeoutMs) {
            withContext(Dispatchers.Main) {
                val view = ensureWebView()
                val pageLoaded = CompletableDeferred<Unit>()

                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView?, finishedUrl: String?) {
                        if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                    }

                    override fun onReceivedError(
                        v: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?,
                    ) {
                        if (!pageLoaded.isCompleted && failingUrl == url) {
                            pageLoaded.completeExceptionally(
                                RuntimeException("WebView error $errorCode: $description")
                            )
                        }
                    }
                }

                val htmlBridge = HtmlBridge()
                view.removeJavascriptInterface(BRIDGE_NAME)
                view.addJavascriptInterface(htmlBridge, BRIDGE_NAME)

                view.stopLoading()
                view.clearHistory()
                view.loadUrl(url)

                pageLoaded.await()

                delay(minDelayMs)
                pollUntilReady(view, readyJsExpression, maxWaitAfterLoadMs)

                htmlBridge.reset()
                view.evaluateJavascript(
                    "$BRIDGE_NAME.receive(document.documentElement.outerHTML);",
                    null,
                )
                htmlBridge.await()
            }
        }
    }

    private suspend fun pollUntilReady(
        view: WebView,
        readyJsExpression: String,
        maxWaitMs: Long,
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            val ready = CompletableDeferred<Boolean>()
            view.evaluateJavascript("(function(){try{return !!($readyJsExpression);}catch(e){return false;}})();") {
                val v = it?.trim()?.removeSurrounding("\"")
                ready.complete(v == "true")
            }
            if (ready.await()) return
            delay(300)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val view = WebView(context.applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = false
                blockNetworkImage = true
                userAgentString = MOBILE_UA
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
                mediaPlaybackRequiresUserGesture = true
                javaScriptCanOpenWindowsAutomatically = false
                allowFileAccess = false
                allowContentAccess = false
            }
            setBackgroundColor(0)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        webView = view
        return view
    }

    private class HtmlBridge {
        private var deferred: CompletableDeferred<String> = CompletableDeferred()

        fun reset() { deferred = CompletableDeferred() }

        suspend fun await(): String = deferred.await()

        @JavascriptInterface
        fun receive(html: String) {
            if (!deferred.isCompleted) deferred.complete(html)
        }
    }

    companion object {
        private const val BRIDGE_NAME = "AndroidHtmlBridge"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
