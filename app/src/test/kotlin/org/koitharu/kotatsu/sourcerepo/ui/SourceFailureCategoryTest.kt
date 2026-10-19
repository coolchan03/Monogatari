package org.koitharu.kotatsu.sourcerepo.ui

import org.jsoup.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

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

    @Test fun networkEvidenceWinsOverLaterScriptError() {
        // A Mangayomi script that receives a 404 page fails with a TypeError; the website is at fault.
        assertEquals("HTTP_404", sourceFailureCategory("cause=http_status=404 error=JsExtensionException: TypeError: x is undefined"))
        assertEquals("DNS", sourceFailureCategory("cause=network=UnknownHostException: nope error=TypeError"))
        assertEquals("CLOUDFLARE", sourceFailureCategory("cause=http_status=403 cf-mitigated error=TypeError"))
    }

    @Test fun recognizesFormerlyUnclassifiedFailures() {
        assertEquals("CONNECTION", sourceFailureCategory("ConnectException: Failed to connect to example.org/1.2.3.4:443"))
        assertEquals("SERVER_ERROR", sourceFailureCategory("HttpStatusException: Status=522"))
        assertEquals("HTTP_404", sourceFailureCategory("HttpStatusException: Status=410"))
        assertEquals("RATE_LIMIT", sourceFailureCategory("HttpStatusException: Status=429"))
        assertEquals("AUTH", sourceFailureCategory("HttpStatusException: Status=401"))
        assertEquals("PARSER", sourceFailureCategory("ParseException: Cannot find chapter list"))
        assertEquals("PARSER", sourceFailureCategory("JsExtensionException: X: getDetail did not return an object"))
        assertEquals("JS_RUNTIME_GAP", sourceFailureCategory("JsExtensionException: Host call 'foo' is not supported yet"))
        assertEquals("OTHER", sourceFailureCategory("Something odd"))
    }

    @Test fun describeKeepsStatusAndRootCause() {
        val http = describeSourceFailure(HttpStatusException("", 503, "https://example.org/list"))
        assertTrue(http, http.contains("Status=503"))
        assertEquals("SERVER_ERROR", sourceFailureCategory(http))
        val wrapped = describeSourceFailure(IOException("wrapper", UnknownHostException("Unable to resolve host \"x\"")))
        assertEquals("DNS", sourceFailureCategory(wrapped))
        val refused = describeSourceFailure(ConnectException("Connection refused"))
        assertEquals("CONNECTION", sourceFailureCategory(refused))
    }

    @Test fun recategorizesOldSavedLines() {
        val old = "FAIL | KOTATSU | FOO | category=OTHER | HttpStatusException: Status=502"
        assertEquals("FAIL | KOTATSU | FOO | category=SERVER_ERROR | HttpStatusException: Status=502", recategorizeSavedLine(old))
        val known = "FAIL | KOTATSU | FOO | category=DNS | UnknownHostException"
        assertEquals(known, recategorizeSavedLine(known))
        val pass = "PASS | KOTATSU | FOO | popular=20"
        assertEquals(pass, recategorizeSavedLine(pass))
    }

    @Test fun mapsCategoriesToResponsibleParty() {
        assertEquals(SourceFailureCause.UNREACHABLE, sourceFailureCause("DNS"))
        assertEquals(SourceFailureCause.ACCESS_RESTRICTED, sourceFailureCause("CLOUDFLARE"))
        assertEquals(SourceFailureCause.MOVED_CONTENT, sourceFailureCause("HTTP_404"))
        assertEquals(SourceFailureCause.CODE_DEFECT, sourceFailureCause("PARSER"))
        assertEquals(SourceFailureCause.SECURE_CONNECTION, sourceFailureCause("TLS"))
        assertEquals(SourceFailureCause.UNKNOWN, sourceFailureCause("OTHER"))
    }

    @Test fun reportGroupsFailuresByCause() {
        val report = formatSourceReport(
            "title",
            listOf(
                "PASS | KOTATSU | A | popular=10",
                "PARTIAL | KOTATSU | B | category=EMPTY_RESULTS | popular=0",
                "FAIL | KOTATSU | C | category=DNS | x",
                "FAIL | KOTATSU | D | category=TIMEOUT | x",
                "FAIL | KOTATSU | E | category=PARSER | x",
            ),
        )
        assertTrue(report, report.contains("PASS=1 PARTIAL=1 FAIL=3"))
        assertTrue(report, report.contains("- Website unreachable: 2 ("))
        assertTrue(report, report.contains("- Parser or extension defect: 1 (PARSER=1)"))
        assertTrue(report, report.contains("Only the 1 \"Parser or extension defect\" failures"))
    }
}
