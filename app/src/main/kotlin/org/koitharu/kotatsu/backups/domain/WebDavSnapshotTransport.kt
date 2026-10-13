package org.koitharu.kotatsu.backups.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Credentials stay in memory. No shared app cookie jar or redirect handling. */
internal data class WebDavAuth(val username: String, val appPassword: String)

@Singleton
internal class WebDavSnapshotTransport @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    private fun request(url: HttpUrl, auth: WebDavAuth): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", Credentials.basic(auth.username, auth.appPassword))

    suspend fun list(endpoint: WebDavEndpoint, auth: WebDavAuth): List<String> =
        withContext(Dispatchers.IO) {
            val xml = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            val req = request(endpoint.collection, auth)
                .header("Depth", "1")
                .method("PROPFIND", xml.toRequestBody("application/xml".toMediaType())).build()
            client.newCall(req).execute().use { response ->
                if (response.code == 404) return@withContext emptyList()
                if (response.code != 207) throw IOException("WebDAV listing failed (" + response.code + ")")
                val stream = response.body?.byteStream() ?: throw IOException("Empty WebDAV listing")
                parseMultistatus(readBounded(stream, MAX_XML_BYTES), endpoint)
                    .distinct().take(MAX_DEVICES)
            }
        }
    suspend fun upload(endpoint: WebDavEndpoint, auth: WebDavAuth, id: String, file: File) =
        withContext(Dispatchers.IO) {
            require(file.isFile && file.length() in 1..MAX_ZIP_BYTES) {
                "WebDAV snapshot must be a nonempty ZIP within the size limit"
            }
            val create = request(endpoint.collection, auth)
                .method("MKCOL", ByteArray(0).toRequestBody(null)).build()
            client.newCall(create).execute().use { response ->
                if (response.code != 201 && response.code != 405) {
                    throw IOException("WebDAV folder creation failed (" + response.code + ")")
                }
            }
            val put = request(endpoint.file(id), auth)
                .put(file.asRequestBody("application/zip".toMediaType())).build()
            client.newCall(put).execute().use { response ->
                if (response.code !in setOf(200, 201, 204)) {
                    throw IOException("WebDAV upload failed (" + response.code + ")")
                }
            }
        }

    suspend fun download(endpoint: WebDavEndpoint, auth: WebDavAuth, id: String, target: File) =
        withContext(Dispatchers.IO) {
            val req = request(endpoint.file(id), auth).get().build()
            try {
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("WebDAV download failed (" + response.code + ")")
                    }
                    val source = response.body?.byteStream()
                        ?: throw IOException("Empty WebDAV snapshot")
                    target.outputStream().use { sink ->
                        copyBounded(source, sink, MAX_ZIP_BYTES)
                    }
                }
            } catch (failure: Exception) {
                target.delete()
                throw failure
            }
        }
    companion object {
        private const val MAX_XML_BYTES = 1024 * 1024
        private const val MAX_ZIP_BYTES = 128L * 1024 * 1024
        private const val MAX_DEVICES = 64

        /** Externally referenced entities and DTDs are rejected rather than fetched. */
        internal fun parseMultistatus(xml: ByteArray, endpoint: WebDavEndpoint): List<String> {
            require(xml.size <= MAX_XML_BYTES) { "WebDAV XML response exceeds limit" }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isExpandEntityReferences = false
                isXIncludeAware = false
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val nodes = document.getElementsByTagNameNS("DAV:", "response")
            return buildList {
                for (i in 0 until nodes.length) {
                    val response = nodes.item(i) as? Element ?: continue
                    val href = response.getElementsByTagNameNS("DAV:", "href").item(0)
                        ?.textContent?.trim() ?: continue
                    endpoint.deviceIdFromHref(href)?.let(::add)
                }
            }
        }
        private fun readBounded(input: InputStream, max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            copyBounded(input, out, max.toLong())
            return out.toByteArray()
        }

        private fun copyBounded(input: InputStream, output: java.io.OutputStream, max: Long) {
            val bytes = ByteArray(8192)
            var total = 0L
            while (true) {
                val size = input.read(bytes)
                if (size < 0) return
                total += size
                if (total > max) throw IOException("WebDAV response exceeds size limit")
                output.write(bytes, 0, size)
            }
        }
    }
}
