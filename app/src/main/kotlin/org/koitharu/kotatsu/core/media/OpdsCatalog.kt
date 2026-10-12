package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.koitharu.kotatsu.core.network.BaseHttpClient
import javax.inject.Inject

/** OPDS 1.x Atom catalog reader. Links remain restricted to HTTP(S). */
class OpdsCatalog @Inject constructor(@BaseHttpClient private val client: OkHttpClient) {
    data class Entry(val title: String, val author: String, val href: String, val acquisition: Boolean, val mediaType: String? = null)

    suspend fun load(url: String): List<Entry> = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Use an HTTP(S) catalog URL" }
        val request = Request.Builder().url(url).build()
        val xml = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("OPDS HTTP ${it.code}")
            it.body?.string() ?: error("Empty OPDS response")
        }
        val base = java.net.URI(url)
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        doc.select("entry").mapNotNull { entry ->
            val title = entry.selectFirst("title")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val author = entry.selectFirst("author > name")?.text().orEmpty()
            val links = entry.select("link[rel]")
            val acquisition = links.firstOrNull { it.attr("rel").contains("acquisition") }
            val navigation = links.firstOrNull { it.attr("rel").contains("subsection") }
                ?: links.firstOrNull { it.attr("type").contains("atom+xml") }
            val selected = acquisition ?: navigation ?: return@mapNotNull null
            val href = runCatching { base.resolve(selected.attr("href")).toString() }.getOrNull()
                ?: return@mapNotNull null
            if (!href.startsWith("https://") && !href.startsWith("http://")) return@mapNotNull null
            Entry(title, author, href, acquisition != null, selected.attr("type").takeIf { it.isNotBlank() })
        }
    }
}
