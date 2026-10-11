package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertTrue
import org.junit.Test

/** Every video extractor shim must be registered on the async host dispatch path. */
class JsExtractorDispatchTest {
    @Test fun allDeclaredExtractorsUseAsyncHostCalls() {
        val shim = requireNotNull(javaClass.getResource("/jsext/extractors.js"))
            .readText()
        val source = requireNotNull(JsExtension::class.java.getResource(
            "/jsext/extractors.js"
        )).readText()
        assertTrue(source.isNotBlank())
        val names = Regex("async function (\\w+Extractor)\\(").findAll(shim)
            .map { it.groupValues[1] }.toSet()
        assertTrue(names.isNotEmpty())
        val bridgeSource = java.io.File("src/main/kotlin/org/koitharu/kotatsu/jsext/JsExtension.kt")
            .takeIf { it.isFile }?.readText()
            ?: java.io.File("app/src/main/kotlin/org/koitharu/kotatsu/jsext/JsExtension.kt").readText()
        names.forEach { name ->
            assertTrue("$name missing async host registration", bridgeSource.contains("\"$name\""))
        }
    }
}
