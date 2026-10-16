package org.koitharu.kotatsu.core.media

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/**
 * The provider's own website in an ordinary WebView.
 * This does NOT masquerade as a parser, evade bot challenges, scrape results or bypass sign-in.
 * Manual sign-in/challenges stay under the provider's control and external browser is always available.
 */
class OfficialFictionBrowserActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "official_fiction_browser_url"
    }
    private lateinit var browser: WebView
    private lateinit var allowedSite: OfficialMediaSites.Site

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringExtra(EXTRA_URL).orEmpty()
        val provider = OfficialMediaSites.fictionSite(initial) ?: run { finish(); return }
        allowedSite = provider
        title = when (provider) {
            OfficialMediaSites.Site.FICTIONPRESS -> "FictionPress"
            OfficialMediaSites.Site.FANFICTION -> "FanFiction.net"
            else -> "Official fiction website"
        }
        val unit = resources.displayMetrics.density
        val padding = (12 * unit).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val progress = LinearProgressIndicator(this).apply { max = 100; isIndeterminate = false }
        root.addView(progress, LinearLayout.LayoutParams(-1, -2))
        val notice = TextView(this).apply {
            setText(R.string.official_fiction_disclosure)
            setPadding(padding, padding / 2, padding, padding / 2)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        }
        root.addView(notice)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(MaterialButton(this).apply {
            setText(R.string.official_fiction_reload)
            setOnClickListener { if (::browser.isInitialized) browser.reload() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(MaterialButton(this).apply {
            setText(R.string.official_fiction_external)
            setOnClickListener {
                val current = browser.url?.takeIf { OfficialMediaSites.fictionSite(it) == allowedSite } ?: initial
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, current.toUri())) }
                    .onFailure { notice.setText(R.string.official_fiction_no_browser) }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.progress = newProgress
                    progress.isVisible = newProgress < 100
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                    handleNavigation(request.url, request.isForMainFrame)
                @Deprecated("Android 6 compatibility")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean =
                    handleNavigation(Uri.parse(url), true)
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        notice.setText(R.string.official_fiction_connection_failed)
                    }
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: android.webkit.WebResourceResponse?) {
                    if (request?.isForMainFrame == true && response != null &&
                        (response.statusCode == 403 || response.statusCode == 429 || response.statusCode == 503)) {
                        notice.setText(R.string.official_fiction_access_limited)
                    }
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    notice.setText(R.string.official_fiction_disclosure)
                }
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setMonogatariContent(root, MonogatariChrome.LIST_MAX_WIDTH_DP)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (browser.canGoBack()) browser.goBack() else finish()
            }
        })
        if (savedInstanceState == null || browser.restoreState(savedInstanceState) == null) browser.loadUrl(initial)
    }

    private fun handleNavigation(url: Uri, mainFrame: Boolean): Boolean {
        val location = url.toString()
        if (OfficialMediaSites.fictionSite(location) == allowedSite) return false
        if (mainFrame && url.scheme == "https") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
        }
        // No deep-link or untrusted URL can be opened silently from provider-controlled page script.
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::browser.isInitialized) browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::browser.isInitialized) {
            browser.stopLoading()
            (browser.parent as? android.view.ViewGroup)?.removeView(browser)
            browser.destroy()
        }
        super.onDestroy()
    }
}
