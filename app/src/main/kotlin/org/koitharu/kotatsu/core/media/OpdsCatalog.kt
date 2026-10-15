package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.network.BaseHttpClient
import javax.inject.Inject

/** OPDS 1.x (Atom) and 2.0 (JSON) catalog reader with paging, search and basic-auth catalogs. Links stay HTTP(S). */
class OpdsCatalog @Inject constructor(@BaseHttpClient private val client: OkHttpClient) {
    data class Entry(
        val title: String, val author: String, val href: String, val acquisition: Boolean,
        val mediaType: String? = null, val cover: String? = null,
    )

    suspend fun load(url: String): List<Entry> = loadPage(url).entries

    internal suspend fun loadPage(url: String): OpdsParser.Page = withContext(Dispatchers.IO) {
        require(OpdsParser.isWeb(url)) { "Use an HTTP(S) catalog URL" }
        val (clean, auth) = OpdsParser.splitCredentials(url)
        val request = Request.Builder().url(clean)
            .header("Accept", "application/opds+json, application/atom+xml, application/xml;q=0.9, */*;q=0.5")
            .apply { if (auth != null) header("Authorization", auth) }
            .build()
        client.newCall(request).execute().use {
            if (it.code == 401) error("This catalog needs a login: add it as https://user:password@host/path")
            if (!it.isSuccessful) error("OPDS HTTP ${it.code}")
            val body = it.body?.string() ?: error("Empty OPDS response")
            OpdsParser.parse(body, java.net.URI(clean), it.header("Content-Type"))
        }
    }

    /** Resolves the catalog's search URL template (directly, or via its OpenSearch description). */
    internal suspend fun searchTemplate(page: OpdsParser.Page, base: String): String? {
        page.searchTemplate?.let { return it }
        val description = page.searchDescription ?: return null
        val (clean, auth) = OpdsParser.splitCredentials(description)
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(clean).apply { if (auth != null) header("Authorization", auth) }.build()
                client.newCall(request).execute().use {
                    if (!it.isSuccessful) return@use null
                    OpdsParser.searchTemplateFromDescription(it.body?.string().orEmpty(), java.net.URI(base))
                }
            }.getOrNull()
        }
    }
}
