package com.alfahdtv.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

/**
 * Keeps the live-source compatibility surface inside Al Fahd TV while the
 * surrounding experience (navigation, loading, and error state) stays native.
 */
class LivePlayerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URL = "live_url"
        const val EXTRA_TITLE = "live_title"
        private const val SOURCE_HOST = "go4xyz.app"
    }

    private lateinit var webView: WebView
    private lateinit var loading: View
    private lateinit var loadingText: TextView
    private lateinit var topBar: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val sourceUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "بث مباشر" }
        if (!isTrustedSource(sourceUrl)) {
            finish()
            return
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10, 11, 16)) }
        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(44), dp(12), dp(10))
            setBackgroundColor(0xE60C0D12.toInt())
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "رجوع"
            setOnClickListener { finish() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(42), dp(46)))
        topBar.addView(TextView(this).apply {
            text = title
            textSize = 16f
            maxLines = 1
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(100), Gravity.TOP))

        loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xDD0A0B10.toInt())
            addView(ProgressBar(this@LivePlayerActivity).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(42), dp(42)))
            loadingText = TextView(this@LivePlayerActivity).apply {
                text = "نجهّز البث بأفضل جودة…"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(15), 0, 0)
            }
            addView(loadingText, LinearLayout.LayoutParams(-1, dp(45)))
        }
        root.addView(loading, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        configureBrowser()
        loading.setOnClickListener { resolveAndLoad(sourceUrl) }
        resolveAndLoad(sourceUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowser() {
        webView.setBackgroundColor(Color.rgb(10, 11, 16))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString AlFahdTV/3.7.4"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) webView.settings.safeBrowsingEnabled = true
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.isForMainFrame

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                applyPlayerOnlyLayout()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                loadingText.text = "يتم الاتصال بسيرفر البث…"
                view.postDelayed({
                    loading.visibility = View.GONE
                    topBar.visibility = View.GONE
                    WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                }, 1800)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return if (isAdvertisingResource(request.url.host.orEmpty())) emptyResponse() else super.shouldInterceptRequest(view, request)
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                if (failingUrl == view.url) showFailure()
            }
        }
    }

    private fun applyPlayerOnlyLayout() {
        val script = """
            (function(){
              var hide=['.header','.chat-section','#matchInfoCard','#aboutStreamCard','.footer','.modal','.SiteOverlay'];
              hide.forEach(function(selector){document.querySelectorAll(selector).forEach(function(node){node.style.setProperty('display','none','important');});});
              var container=document.querySelector('.container');
              var wrapper=document.querySelector('.player-chat-wrapper');
              var section=document.querySelector('.player-section');
              var player=document.getElementById('playerContainer');
              window.open=function(){return null;};
              document.querySelectorAll('iframe').forEach(function(frame){if(player && !player.contains(frame))frame.remove();});
              if(!window.__alfahdPopupGuard){
                window.__alfahdPopupGuard=true;
                new MutationObserver(function(records){records.forEach(function(record){record.addedNodes.forEach(function(node){if(node.tagName==='IFRAME' && player && !player.contains(node))node.remove();});});}).observe(document.documentElement,{childList:true,subtree:true});
                document.addEventListener('click',function(event){var link=event.target.closest&&event.target.closest('a[target="_blank"]');if(link && (!player || !player.contains(link))){event.preventDefault();event.stopPropagation();}},true);
              }
              if(container){container.style.cssText+='max-width:none!important;width:100%!important;margin:0!important;padding:0!important;';}
              if(wrapper){wrapper.style.cssText+='display:block!important;margin:0!important;padding:0!important;';}
              if(section){section.style.cssText+='width:100%!important;margin:0!important;padding:0!important;';}
              if(player){player.style.cssText+='height:100vh!important;width:100%!important;border-radius:0!important;background:#0a0b10!important;';}
              document.documentElement.style.background='#0a0b10'; document.body.style.margin='0'; document.body.style.background='#0a0b10';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun showFailure() {
        if (::topBar.isInitialized) topBar.visibility = View.VISIBLE
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        loadingText.text = "تعذر تجهيز البث الآن — اضغط لإعادة المحاولة"
        loading.visibility = View.VISIBLE
    }

    private fun resolveAndLoad(matchPage: String) {
        loading.visibility = View.VISIBLE
        topBar.visibility = View.VISIBLE
        loadingText.text = "نبحث عن سيرفر بث متاح…"
        Thread {
            val player = LiveSourceResolver.resolve(matchPage)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (player == null) {
                    showFailure()
                } else {
                    // Giving the frame its original page as base keeps normal
                    // provider referrer checks intact without exposing its ads UI.
                    webView.loadDataWithBaseURL(
                        player.referrerUrl,
                        playerShell(player.embedUrl),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        }.start()
    }

    private fun playerShell(embedUrl: String): String = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>html,body,#playerContainer,iframe{margin:0;width:100%;height:100%;overflow:hidden;background:#0a0b10;border:0}</style>
        </head><body><main id="playerContainer"><iframe src="$embedUrl" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen></iframe></main></body></html>
    """.trimIndent()

    private fun emptyResponse(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

    private fun isAdvertisingResource(host: String): Boolean {
        val lowered = host.lowercase()
        return listOf("doubleclick", "googlesyndication", "googleadservices", "adservice", "adnxs", "exoclick", "popads", "popcash", "propellerads", "adsterra", "trafficjunky", "onclickads", "ksks2.sportsonline", "histats", "whos.amung.us", "xstats.st").any { lowered.contains(it) }
    }

    private fun isTrustedSource(value: String): Boolean = runCatching {
        val uri = android.net.Uri.parse(value)
        uri.scheme == "https" && uri.host.equals(SOURCE_HOST, ignoreCase = true)
    }.getOrDefault(false)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }
}
