package com.alfahdtv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.WindowCompat

/**
 * A small, isolated fallback browser for the approved emergency source.  It is
 * intentionally not a generic browser: the initial page is validated and only
 * the known source/player hosts may replace the main frame.  New windows are
 * declined so a source popup cannot eject a viewer from AlFahd TV.
 */
class EmergencyWebPlayerActivity : Activity() {
    private var webView: WebView? = null
    private var progress: ProgressBar? = null
    private var status: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val sourcePage = intent.getStringExtra(EXTRA_SOURCE_PAGE).orEmpty()
        if (!isApprovedSourcePage(sourcePage)) {
            finish()
            return
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val player = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return request.isForMainFrame && !isApprovedNavigation(request.url.toString())
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !isApprovedNavigation(url)

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    this@EmergencyWebPlayerActivity.progress?.visibility = View.VISIBLE
                    this@EmergencyWebPlayerActivity.status?.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView, url: String) {
                    this@EmergencyWebPlayerActivity.progress?.visibility = View.GONE
                    this@EmergencyWebPlayerActivity.status?.visibility = View.GONE
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
            }
        }
        webView = player
        root.addView(player, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        progress = ProgressBar(this).also {
            root.addView(it, FrameLayout.LayoutParams(54, 54, Gravity.CENTER))
        }
        status = TextView(this).apply {
            text = "جاري تجهيز المشاهدة…"
            setTextColor(Color.WHITE)
            textSize = 14f
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
            gravity = Gravity.CENTER
        }.also {
            root.addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
                bottomMargin = 34
            })
        }
        setContentView(root)
        player.loadUrl(sourcePage)
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val active = webView
        if (active?.canGoBack() == true) active.goBack() else finish()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOURCE_PAGE = "emergency_source_page"
        const val EXTRA_TITLE = "emergency_source_title"

        fun isApprovedSourcePage(value: String): Boolean = runCatching {
            val uri = Uri.parse(value)
            uri.scheme.equals("https", true) &&
                uri.host.equals(CIMA_HOST, true) &&
                uri.path.equals("/watch.php", true) &&
                !uri.getQueryParameter("vid").isNullOrBlank()
        }.getOrDefault(false)

        private fun isApprovedNavigation(value: String): Boolean = runCatching {
            val uri = Uri.parse(value)
            if (!uri.scheme.equals("https", true)) return@runCatching false
            val host = uri.host.orEmpty().lowercase()
            host == CIMA_HOST || host.endsWith(".$CIMA_HOST") || host == PLAYER_HOST || host.endsWith(".$PLAYER_HOST")
        }.getOrDefault(false)

        private const val CIMA_HOST = "e.cimalight.co"
        private const val PLAYER_HOST = "elif.news"
    }
}
