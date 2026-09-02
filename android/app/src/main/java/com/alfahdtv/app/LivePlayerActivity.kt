package com.alfahdtv.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Resolves a live fixture inside Al Fahd TV, then delegates the protected
 * provider document to Chrome Custom Tabs. The provider rejects Android
 * WebView but accepts Chrome on the same device.
 */
class LivePlayerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URL = "live_url"
        const val EXTRA_TITLE = "live_title"
        private const val SOURCE_HOST = "go4xyz.app"
        private const val CHROME_PACKAGE = "com.android.chrome"
    }

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

        // This applies to the native preparation screen. Chrome then respects
        // the orientation selected by the user/device for the live source.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(createLoadingScreen(title))
        resolveAndOpen(sourceUrl)
    }

    private fun createLoadingScreen(title: String): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10, 11, 16)) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        panel.addView(TextView(this).apply {
            text = title
            textSize = 20f
            maxLines = 1
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-1, dp(36)))
        panel.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(18)
        })
        loadingText = TextView(this).apply {
            text = "جاري تجهيز البث…"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, 0)
        }
        panel.addView(loadingText, LinearLayout.LayoutParams(-1, dp(44)))
        panel.setOnClickListener { resolveAndOpen(intent.getStringExtra(EXTRA_URL).orEmpty()) }
        loading = panel
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun resolveAndOpen(sourceUrl: String) {
        if (!isTrustedSource(sourceUrl)) return
        loading.isEnabled = false
        loadingText.text = "نبحث عن سيرفر بث متاح…"
        Thread {
            val player = LiveSourceResolver.resolve(sourceUrl)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (player == null) {
                    showFailure("تعذر تجهيز البث الآن — اضغط لإعادة المحاولة")
                } else {
                    loadingText.text = "يتم فتح البث…"
                    openInChrome(player.referrerUrl)
                }
            }
        }.start()
    }

    private fun openInChrome(url: String) {
        if (!hasChromeCustomTabs()) {
            showFailure("يتطلب البث متصفح Chrome على الهاتف")
            return
        }
        val customTab = CustomTabsIntent.Builder()
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setToolbarColor(Color.rgb(10, 11, 16))
            .setNavigationBarColor(Color.BLACK)
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .build()
        // Do not fall back to the device's default browser: this provider's
        // compatibility was verified with Chrome on the connected phone.
        customTab.intent.setPackage(CHROME_PACKAGE)
        try {
            customTab.launchUrl(this, Uri.parse(url))
            // The Custom Tab is now visible. Removing this preparation screen
            // makes Back return directly to Al Fahd TV.
            finish()
        } catch (_: Exception) {
            showFailure("تعذر فتح البث الآن — اضغط لإعادة المحاولة")
        }
    }

    private fun showFailure(message: String) {
        loading.isEnabled = true
        loadingText.text = message
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hasChromeCustomTabs(): Boolean {
        val connection = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(CHROME_PACKAGE)
        return packageManager.resolveService(connection, 0) != null
    }

    private fun isTrustedSource(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        uri.scheme == "https" && uri.host.equals(SOURCE_HOST, ignoreCase = true)
    }.getOrDefault(false)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
