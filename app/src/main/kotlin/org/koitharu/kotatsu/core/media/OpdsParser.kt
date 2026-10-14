package org.koitharu.kotatsu.core.media

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import okio.ByteString.Companion.encodeUtf8

/** Pure OPDS parsing: Atom (OPDS 1.x) and JSON (OPDS 2.0) feeds, paging, search templates and basic-auth URLs. */
internal object OpdsParser {

	/** One page of a catalog: its entries, the next page (if any) and a search URL template with {searchTerms}. */
	data class Page(val entries: List<OpdsCatalog.Entry>, val next: String?, val searchTemplate: String?, val searchDescription: String? = null)

	fun isWeb(url: String) = url.startsWith("https://") || url.startsWith("http://")

	private fun resolve(base: URI, href: String): String? =
		runCatching { base.resolve(href.trim()).toString() }.getOrNull()?.takeIf(::isWeb)

	fun parse(body: String, base: URI, contentType: String? = null): Page =
		if (contentType?.contains("json", true) == true || body.trimStart().startsWith("{")) parseJson(body, base) else parseAtom(body, base)

	fun parseAtom(xml: String, base: URI): Page {
		val doc = Jsoup.parse(xml, "", Parser.xmlParser())
		val entries = doc.select("entry").mapNotNull { entry ->
			val title = entry.selectFirst("title")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
			val author = entry.selectFirst("author > name")?.text().orEmpty()
			val links = entry.select("link[rel]")
			val acquisition = links.firstOrNull { it.attr("rel").contains("acquisition") }
			val navigation = links.firstOrNull { it.attr("rel").contains("subsection") }
				?: links.firstOrNull { it.attr("type").contains("atom+xml") }
			val selected = acquisition ?: navigation ?: return@mapNotNull null
			val href = resolve(base, selected.attr("href")) ?: return@mapNotNull null
			val cover = links.firstOrNull { it.attr("rel").let { r -> r.endsWith("/image") || r.endsWith("/image/thumbnail") || r.endsWith("/thumbnail") } }
				?.let { resolve(base, it.attr("href")) }?.takeIf { it.startsWith("https://") }
			OpdsCatalog.Entry(title, author, href, acquisition != null, selected.attr("type").takeIf { it.isNotBlank() }, cover)
		}
		val feedLinks = doc.select("feed > link[rel]")
		val next = feedLinks.firstOrNull { it.attr("rel") == "next" }?.let { resolve(base, it.attr("href")) }
		val searchLink = feedLinks.firstOrNull { it.attr("rel") == "search" }
		val href = searchLink?.attr("href").orEmpty()
		val template = if ("{searchTerms}" in href) resolveTemplate(base, href) else null
		val description = if (template == null && searchLink != null && searchLink.attr("type").contains("opensearchdescription", true)) resolve(base, href) else null
		return Page(entries, next, template, description)
	}

	/** OPDS 2.0: "navigation" and "publications" arrays with typed links. */
	fun parseJson(json: String, base: URI): Page {
		val root = runCatching { JSONObject(json) }.getOrNull() ?: return Page(emptyList(), null, null)
		val entries = ArrayList<OpdsCatalog.Entry>()
		root.optJSONArray("navigation")?.forEachObject { nav ->
			val title = nav.optString("title").takeIf { it.isNotBlank() } ?: return@forEachObject
			val href = resolve(base, nav.optString("href")) ?: return@forEachObject
			entries += OpdsCatalog.Entry(title, "", href, false, nav.optString("type").takeIf { it.isNotBlank() } ?: "application/opds+json")
		}
		root.optJSONArray("publications")?.forEachObject { pub ->
			val meta = pub.optJSONObject("metadata") ?: return@forEachObject
			val title = meta.optString("title").takeIf { it.isNotBlank() } ?: return@forEachObject
			val author = when (val a = meta.opt("author")) {
				is JSONObject -> a.optString("name")
				is JSONArray -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") ?: a.optString(it) }.joinToString(", ")
				is String -> a
				else -> ""
			}
			val links = pub.optJSONArray("links") ?: return@forEachObject
			var chosen: JSONObject? = null
			links.forEachObject { l ->
				val rel = l.optString("rel")
				if (chosen == null && rel.contains("acquisition")) chosen = l
			}
			val acquisition = chosen ?: return@forEachObject
			val href = resolve(base, acquisition.optString("href")) ?: return@forEachObject
			val image = pub.optJSONArray("images")?.optJSONObject(0)?.optString("href")?.let { resolve(base, it) }?.takeIf { it.startsWith("https://") }
			entries += OpdsCatalog.Entry(title, author, href, true, acquisition.optString("type").takeIf { it.isNotBlank() }, image)
		}
		var next: String? = null
		var template: String? = null
		root.optJSONArray("links")?.forEachObject { l ->
			val rels = when (val r = l.opt("rel")) { is JSONArray -> (0 until r.length()).map { r.optString(it) }; is String -> listOf(r); else -> emptyList() }
			val href = l.optString("href")
			if ("next" in rels) next = resolve(base, href)
			if ("search" in rels && "{" in href) template = resolveTemplate(base, href.replace("{?query}", "?query={searchTerms}").replace("{query}", "{searchTerms}"))
		}
		return Page(entries, next, template)
	}

	/** Resolves a search template against the catalog URL while keeping the {searchTerms} placeholder intact. */
	private fun resolveTemplate(base: URI, template: String): String? {
		val token = "SEARCHTERMSPLACEHOLDER"
		return resolve(base, template.replace("{searchTerms}", token))?.replace(token, "{searchTerms}")
	}

	/** The Atom search URL template inside an OpenSearch description document, if any. */
	fun searchTemplateFromDescription(xml: String, base: URI): String? {
		val doc = Jsoup.parse(xml, "", Parser.xmlParser())
		val url = doc.select("Url").firstOrNull { it.attr("template").contains("{searchTerms}") && (it.attr("type").isBlank() || it.attr("type").contains("atom")) }
			?: return null
		return resolveTemplate(base, url.attr("template"))
	}

	fun searchUrl(template: String, query: String): String =
		template.replace("{searchTerms}", java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20"))
			.replace(Regex("\\{[^}]*\\}"), "")

	/**
	 * Catalogs behind a login: `https://user:password@host/opds` becomes a clean URL plus a Basic Authorization header
	 * (OkHttp ignores user info in URLs). Returns the URL unchanged and a null header when there are no credentials.
	 */
	fun splitCredentials(url: String): Pair<String, String?> {
		val uri = runCatching { URI(url) }.getOrNull() ?: return url to null
		val info = uri.rawUserInfo ?: return url to null
		val decoded = java.net.URLDecoder.decode(info, "UTF-8")
		val clean = URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
		return clean to "Basic " + decoded.encodeUtf8().base64()
	}

	private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
		for (i in 0 until length()) optJSONObject(i)?.let(block)
	}
}
