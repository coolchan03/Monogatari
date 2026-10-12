package org.koitharu.kotatsu.jsext.source

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedCookieJarTest {
    private class MemoryJar : CookieJar {
        var reads = 0
        var writes = 0
        override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
            reads++
            return listOf(Cookie.Builder().name("session").value("abc").hostOnlyDomain("example.com").build())
        }
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
            writes++
        }
    }

    @Test fun subdomainsOnly() {
        val delegate = MemoryJar()
        val jar = ScopedCookieJar(delegate, setOf("example.com"))
        assertTrue(jar.allows("example.com"))
        assertTrue(jar.allows("www.example.com"))
        assertTrue(jar.allows("cdn.example.com"))
        assertFalse(jar.allows("badexample.com"))
        assertFalse(jar.allows("example.com.evil.net"))
        assertEquals(0, jar.loadForRequest("https://badexample.com/".toHttpUrl()).size)
        assertEquals(0, delegate.reads)
        assertEquals(1, jar.loadForRequest("https://example.com/".toHttpUrl()).size)
        assertEquals(1, delegate.reads)
        jar.saveFromResponse("https://badexample.com/".toHttpUrl(), emptyList())
        assertEquals(0, delegate.writes)
        jar.saveFromResponse("https://example.com/".toHttpUrl(), emptyList())
        assertEquals(1, delegate.writes)
    }
}
