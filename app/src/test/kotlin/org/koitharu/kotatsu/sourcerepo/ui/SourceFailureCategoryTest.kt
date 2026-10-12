package org.koitharu.kotatsu.sourcerepo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceFailureCategoryTest {
    @Test fun recognizesDnsFailures() {
        assertEquals("DNS", sourceFailureCategory("UnknownHostException: Unable to resolve host \"api.comick.fun\""))
        assertEquals("DNS", sourceFailureCategory("Unable to resolve host \"mangapark.net\""))
    }

    @Test fun separatesTlsFromCertificateErrors() {
        assertEquals("TLS", sourceFailureCategory("SSLException: Unable to parse TLS packet header"))
        assertEquals("CERTIFICATE", sourceFailureCategory("SSLPeerUnverifiedException: Hostname readcomiconline.li not verified"))
    }

    @Test fun recognizesScriptAndNetworkFailures() {
        assertEquals("CLOUDFLARE", sourceFailureCategory("CloudFlareProtectedException"))
        assertEquals("EXTENSION_SCRIPT", sourceFailureCategory("TypeError: cannot read property of null"))
        assertEquals("HTTP_404", sourceFailureCategory("NotFoundException: Status=404"))
        assertEquals("HTTP_403", sourceFailureCategory("HttpStatusException: Status=403"))
        assertEquals("RATE_LIMIT", sourceFailureCategory("Too many requests, retry after"))
        assertEquals("TIMEOUT", sourceFailureCategory("getPopular timed out"))
    }
}
