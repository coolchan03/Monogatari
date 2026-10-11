package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent non-POST HTTP methods from silently being mapped to POST. */
class JsHttpShimMethodsTest {
    @Test fun requestMethodsUseMatchingHostCalls() {
        val shim = requireNotNull(javaClass.getResource("/jsext/http.js"))
            .readText()
        for (method in listOf("post", "put", "delete", "patch")) {
            val methodBlock = shim.substringAfter("async $method(", missingDelimiterValue = "")
                .substringBefore("return JSON.parse(result);")
            assertTrue("$method handler missing", methodBlock.isNotEmpty())
            assertTrue("$method routed incorrectly", methodBlock.contains("\"http_$method\""))
        }
    }
}
