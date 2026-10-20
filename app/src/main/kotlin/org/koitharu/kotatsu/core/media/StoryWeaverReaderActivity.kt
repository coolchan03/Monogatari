package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.util.ExternalLinks
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.dp
import org.koitharu.kotatsu.core.ui.headerCard
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.setThemeTextAppearance
import com.google.android.material.R as materialR

/** In-app view of the provider's own illustrated reader. No page scraping, paywall bypass or ad injection. */
class StoryWeaverReaderActivity : AppCompatActivity() {
    companion object {
        private const val MENU_BROWSER = 7401
        const val EXTRA_URL = "storyweaver_story_url"
        const val EXTRA_CREDIT = "storyweaver_story_attribution"
    }
    private lateinit var browser: WebView
    private lateinit var status: TextView
    private var storyUrl = ""
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!StoryWeaverCatalog.officialStoryUrl(url)) { finish(); return }
        storyUrl = url
        title = getString(R.string.storyweaver_reader_title)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // Compact Kitsune card: the provider's credit and the online notice, leaving the page most of the screen.
        val (header, statusView) = headerCard(MascotForm.BOOK, null, getString(R.string.storyweaver_online_notice), mascotSizeDp = 40)
        status = statusView
        val attribution = intent.getStringExtra(EXTRA_CREDIT).orEmpty()
        if (attribution.isNotBlank()) (statusView.parent as? LinearLayout)?.addView(TextView(this).apply {
            text = attribution
            setThemeTextAppearance(materialR.attr.textAppearanceBodySmall, materialR.style.TextAppearance_Material3_BodySmall)
            setTextColor(getThemeColor(materialR.attr.colorOnSurfaceVariant))
            setPadding(0, 0, 0, dp(4))
            isFocusable = true
        }, 0, LinearLayout.LayoutParams(-1, -2))
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            val margin = dp(8)
            setMargins(margin, margin, margin, margin)
        })
        val loading = LinearProgressIndicator(this).apply { isIndeterminate = false; max = 100 }
        content.addView(loading, LinearLayout.LayoutParams(-1, -2))
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loading.progress = newProgress
                    loading.isVisible = newProgress < 100
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (StoryWeaverCatalog.officialHost(request.url.toString())) return false
                    // Embedded frames stay inside the page (web schemes only); they cannot leave the WebView.
                    if (!request.isForMainFrame) return !ExternalLinks.isAllowed(request.url.toString())
                    // Page script cannot launch intent:/custom-scheme links; only http(s) goes to a browser.
                    if (request.isForMainFrame) ExternalLinks.open(this@StoryWeaverReaderActivity, request.url.toString())
                    return true
                }
                @Deprecated("Required on Android 6")
                override fun shouldOverrideUrlLoading(view: WebView?, nextUrl: String): Boolean {
                    if (StoryWeaverCatalog.officialHost(nextUrl)) return false
                    ExternalLinks.open(this@StoryWeaverReaderActivity, nextUrl)
                    return true
                }
            }
        }
        content.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setMonogatariContent(content, MonogatariChrome.LIST_MAX_WIDTH_DP)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (browser.canGoBack()) browser.goBack() else finish() }
        })
        if (savedInstanceState == null || browser.restoreState(savedInstanceState) == null) browser.loadUrl(url)
    }
    // "Open in browser" lives in the toolbar menu so the story gets the whole screen.
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_BROWSER, 0, R.string.storyweaver_open_browser)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != MENU_BROWSER) return super.onOptionsItemSelected(item)
        if (!ExternalLinks.open(this, storyUrl)) status.setText(R.string.link_cannot_open)
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
