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
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/** In-app view of the provider's own illustrated reader. No page scraping, paywall bypass or ad injection. */
class StoryWeaverReaderActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "storyweaver_story_url"
        const val EXTRA_CREDIT = "storyweaver_story_attribution"
    }
    private lateinit var browser: WebView
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!StoryWeaverCatalog.officialStoryUrl(url)) { finish(); return }
        title = getString(R.string.storyweaver_reader_title)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val attribution = intent.getStringExtra(EXTRA_CREDIT).orEmpty()
        if (attribution.isNotBlank()) content.addView(TextView(this).apply {
            text = attribution
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(16, 8, 16, 8)
            isFocusable = true
        })
        val loading = LinearProgressIndicator(this).apply { isIndeterminate = false; max = 100 }
        content.addView(loading, LinearLayout.LayoutParams(-1, -2))
        val status = TextView(this).apply {
            setText(R.string.storyweaver_online_notice)
            setPadding(16, 8, 16, 8)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, -2))
        val fallback = MaterialButton(this).apply {
            setText(R.string.storyweaver_open_browser)
            setOnClickListener { if (!ExternalLinks.open(this@StoryWeaverReaderActivity, url)) status.setText(R.string.link_cannot_open) }
        }
        content.addView(fallback, LinearLayout.LayoutParams(-1, -2))
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
