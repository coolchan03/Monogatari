package org.koitharu.kotatsu.core.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiServicesTest {

	@Test
	fun everyServiceHasASignupLinkAndFields() {
		for (service in ApiServices.all) {
			assertTrue(service.id, service.signupUrl.startsWith("https://"))
			assertTrue(service.id, service.fields.isNotEmpty())
		}
		assertEquals(ApiServices.all.size, ApiServices.all.map { it.id }.toSet().size)
	}

	@Test
	fun secretsAreMaskedButRecognisable() {
		assertEquals("", ApiServices.mask(null))
		assertEquals("••••", ApiServices.mask("abc"))
		assertEquals("••••wxyz", ApiServices.mask("abcdefghwxyz"))
	}

	@Test
	fun validationTrimsNormalisesAndRequiresEveryField() {
		val jellyfin = ApiServices.byId(ApiServices.JELLYFIN)!!
		val ok = ApiServices.validate(jellyfin, mapOf("server" to " 192.168.1.5:8096/ ", "api_key" to " k ")) as ApiServices.Check.Ok
		assertEquals("http://192.168.1.5:8096", ok.values["server"])
		assertEquals("k", ok.values["api_key"])
		val missing = ApiServices.validate(jellyfin, mapOf("server" to "x.local", "api_key" to " ")) as ApiServices.Check.Problem
		assertEquals("api_key", missing.field)
		val bad = ApiServices.validate(jellyfin, mapOf("server" to "ftp://nope", "api_key" to "k")) as ApiServices.Check.Problem
		assertEquals("server", bad.field)
	}
}
