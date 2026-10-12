package org.koitharu.kotatsu.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostScopedTlsInterceptorTest {
    @Test fun exactHostOnly() {
        val allowed = setOf("example.com")
        assertTrue(HostScopedTlsInterceptor.allowsHost("example.com", allowed))
        assertTrue(HostScopedTlsInterceptor.allowsHost("EXAMPLE.COM", allowed))
        assertFalse(HostScopedTlsInterceptor.allowsHost("cdn.example.com", allowed))
        assertFalse(HostScopedTlsInterceptor.allowsHost("badexample.com", allowed))
        assertFalse(HostScopedTlsInterceptor.allowsHost("example.com.evil.net", allowed))
    }
}
