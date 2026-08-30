package com.alfahdtv.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

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

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10, 11, 16)) }
        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        val topBar = LinearLayout(this).apply {
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
        webView.loadUrl(sourceUrl)
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
            userAgentString = "$userAgentString AlFahdTV/3.7"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) webView.settings.safeBrowsingEnabled = true
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.isForMainFrame && !isTrustedSource(request.url.toString())
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                applyPlayerOnlyLayout()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                applyPlayerOnlyLayout()
                loadingText.text = "يتم الاتصال بسيرفر البث…"
                view.postDelayed({ loading.visibility = View.GONE }, 1800)
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
        loadingText.text = "تعذر تجهيز البث الآن، جرّب سيرفرًا آخر أو أعد المحاولة"
        loading.visibility = View.VISIBLE
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
