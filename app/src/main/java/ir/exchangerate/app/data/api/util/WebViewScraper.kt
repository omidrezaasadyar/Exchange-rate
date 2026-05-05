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
 * prices are populated by JavaScript (bonbast.com, alanchand.com, navasan.tech).
 *
 * Calls are serialized through a mutex because there is exactly one WebView
 * instance and it can only run a single navigation at a time.
 */
class WebViewScraper(private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var webView: WebView? = null

    suspend fun fetchRenderedHtml(
        url: String,
        settleDelayMs: Long = 3500L,
        timeoutMs: Long = 18_000L,
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
                        if (!pageLoaded.isCompleted) {
                            pageLoaded.completeExceptionally(
                                RuntimeException("WebView error $errorCode: $description")
                            )
                        }
                    }
                }

                view.stopLoading()
                view.clearHistory()
                view.loadUrl(url)

                pageLoaded.await()
                delay(settleDelayMs)

                val html = CompletableDeferred<String>()
                val bridge = HtmlBridge(html)
                view.removeJavascriptInterface("AndroidHtmlBridge")
                view.addJavascriptInterface(bridge, "AndroidHtmlBridge")

                view.evaluateJavascript(
                    "AndroidHtmlBridge.receive(document.documentElement.outerHTML);",
                    null,
                )

                val raw = html.await()
                raw
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val view = WebView(context.applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                loadsImagesAutomatically = false
                blockNetworkImage = true
                userAgentString = MOBILE_UA
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
                mediaPlaybackRequiresUserGesture = true
            }
            setBackgroundColor(0)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        webView = view
        return view
    }

    private class HtmlBridge(private val deferred: CompletableDeferred<String>) {
        @JavascriptInterface
        fun receive(html: String) {
            if (!deferred.isCompleted) deferred.complete(html)
        }
    }

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
