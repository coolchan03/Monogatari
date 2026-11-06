package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.backups.domain.NearbySyncProtocol.Message
import org.koitharu.kotatsu.backups.domain.NearbyTrustHandshake.State

class NearbyTrustTest {

    private val secret = ByteArray(32) { (it + 1).toByte() }
    private val idA = "11111111-1111-1111-1111-111111111111"
    private val idB = "22222222-2222-2222-2222-222222222222"
    private val nonceA = ByteArray(16) { 0x0A }
    private val nonceB = ByteArray(16) { 0x0B }

    private fun device(id: String, endpoint: String, seen: Long = 1L) = NearbyTrust.Device(
        id, "Phone $id".take(20), endpoint, NearbyTrust.toHex(secret), pairedAt = 1L, lastSeen = seen,
    )

    /** Initiator = A (requested the connection), responder = B. */
    private fun pair(
        secretA: ByteArray = secret,
        secretB: ByteArray = secret,
        peerIdSeenByA: String = idB,
    ) = NearbyTrustHandshake(secretA, idA, peerIdSeenByA, initiator = true, localNonce = nonceA) to
        NearbyTrustHandshake(secretB, idB, idA, initiator = false, localNonce = nonceB)

    /** Delivers every message both ways until nothing is left to say. */
    private fun run(a: NearbyTrustHandshake, b: NearbyTrustHandshake) {
        val toA = ArrayDeque<Message>()
        val toB = ArrayDeque<Message>()
        toB.addLast(a.hello())
        toA.addLast(b.hello())
        while (toA.isNotEmpty() || toB.isNotEmpty()) {
            if (toA.isNotEmpty()) a.receive(toA.removeFirst()).forEach { toB.addLast(it) }
            if (toB.isNotEmpty()) b.receive(toB.removeFirst()).forEach { toA.addLast(it) }
        }
    }

    @Test fun hexRoundTripAndRejectsGarbage() {
        val bytes = byteArrayOf(0, 1, 127, -1, -128)
        assertEquals("00017fff80", NearbyTrust.toHex(bytes))
        assertTrue(bytes.contentEquals(NearbyTrust.fromHex("00017fff80")))
        assertThrows(IllegalArgumentException::class.java) { NearbyTrust.fromHex("abc") }
        assertThrows(IllegalArgumentException::class.java) { NearbyTrust.fromHex("zz") }
    }

    @Test fun proofDependsOnSecretRoleAndBothNonces() {
        val base = NearbyTrust.proof(secret, "A", nonceA, nonceB)
        assertEquals(32, base.size)
        assertTrue(NearbyTrust.verify(base, NearbyTrust.proof(secret, "A", nonceA, nonceB)))
        assertFalse(NearbyTrust.verify(base, NearbyTrust.proof(secret, "B", nonceA, nonceB)))
        assertFalse(NearbyTrust.verify(base, NearbyTrust.proof(secret, "A", nonceB, nonceA)))
        assertFalse(NearbyTrust.verify(base, NearbyTrust.proof(ByteArray(32), "A", nonceA, nonceB)))
        assertFalse(NearbyTrust.verify(base, base.copyOf(31)))
    }

    @Test fun matchingSecretsVerifyBothSides() {
        val (a, b) = pair()
        run(a, b)
        assertEquals(State.VERIFIED, a.state)
        assertEquals(State.VERIFIED, b.state)
    }

    @Test fun proofDependsOnLinkBinding() {
        val base = NearbyTrust.proof(secret, "A", nonceA, nonceB, "1234")
        assertTrue(NearbyTrust.verify(base, NearbyTrust.proof(secret, "A", nonceA, nonceB, "1234")))
        assertFalse(NearbyTrust.verify(base, NearbyTrust.proof(secret, "A", nonceA, nonceB, "4321")))
        assertFalse(NearbyTrust.verify(base, NearbyTrust.proof(secret, "A", nonceA, nonceB)))
    }

    @Test fun relayedHandshakeOverTwoLinksFails() {
        // A relay between two links sees different digits on each, so the proofs it forwards do not verify.
        val a = NearbyTrustHandshake(secret, idA, idB, initiator = true, localNonce = nonceA, binding = "1111")
        val b = NearbyTrustHandshake(secret, idB, idA, initiator = false, localNonce = nonceB, binding = "2222")
        run(a, b)
        assertEquals(State.FAILED, a.state)
        assertEquals(State.FAILED, b.state)
    }

    @Test fun sameLinkBindingVerifiesBothSides() {
        val a = NearbyTrustHandshake(secret, idA, idB, initiator = true, localNonce = nonceA, binding = "1111")
        val b = NearbyTrustHandshake(secret, idB, idA, initiator = false, localNonce = nonceB, binding = "1111")
        run(a, b)
        assertEquals(State.VERIFIED, a.state)
        assertEquals(State.VERIFIED, b.state)
    }

    @Test fun differentSecretsFailBothSides() {
        val (a, b) = pair(secretB = ByteArray(32) { 9 })
        run(a, b)
        assertEquals(State.FAILED, a.state)
        assertEquals(State.FAILED, b.state)
    }

    @Test fun peerClaimingAnotherDeviceIdFails() {
        val (a, b) = pair(peerIdSeenByA = "33333333-3333-3333-3333-333333333333")
        run(a, b)
        assertEquals(State.FAILED, a.state)
    }

    @Test fun reflectingOurOwnProofBackDoesNotVerify() {
        val a = NearbyTrustHandshake(secret, idA, idB, initiator = true, localNonce = nonceA)
        // The attacker echoes A's own nonce and A's own proof (which uses role A) instead of a role-B proof.
        val reflectedNonce = NearbyTrust.toHex(nonceA)
        val out = a.receive(Message.Challenge(idB, reflectedNonce))
        val ownProof = (out.single() as Message.Response).mac
        a.receive(Message.Response(ownProof))
        assertEquals(State.FAILED, a.state)
    }

    @Test fun responseMayArriveBeforeChallenge() {
        val a = NearbyTrustHandshake(secret, idA, idB, initiator = true, localNonce = nonceA)
        val proofOfB = NearbyTrust.toHex(NearbyTrust.proof(secret, "B", nonceA, nonceB))
        a.receive(Message.Response(proofOfB))
        assertEquals(State.PENDING, a.state)
        a.receive(Message.Challenge(idB, NearbyTrust.toHex(nonceB)))
        assertEquals(State.VERIFIED, a.state)
    }

    @Test fun repeatedOrUnexpectedMessagesFail() {
        val first = NearbyTrustHandshake(secret, idA, idB, true, nonceA)
        first.receive(Message.Challenge(idB, NearbyTrust.toHex(nonceB)))
        first.receive(Message.Challenge(idB, NearbyTrust.toHex(nonceB)))
        assertEquals(State.FAILED, first.state)

        val second = NearbyTrustHandshake(secret, idA, idB, true, nonceA)
        second.receive(Message.Batch(2, 10))
        assertEquals(State.FAILED, second.state)

        val third = NearbyTrustHandshake(secret, idA, idB, true, nonceA)
        third.receive(Message.TrustFail)
        assertEquals(State.FAILED, third.state)
    }

    @Test fun failedHandshakeStaysFailed() {
        val (a, b) = pair(secretB = ByteArray(32) { 9 })
        run(a, b)
        a.receive(Message.Response(NearbyTrust.toHex(NearbyTrust.proof(secret, "B", nonceA, nonceB))))
        assertEquals(State.FAILED, a.state)
    }

    @Test fun mergeReplacesSameIdAndKeepsNewestFirst() {
        val list = listOf(device(idA, "x", seen = 5), device(idB, "y", seen = 9))
        val merged = NearbyTrust.merge(list, device(idA, "z", seen = 20))
        assertEquals(listOf(idA, idB), merged.map { it.id })
        assertEquals("z", merged.first().endpointName)
    }

    @Test fun mergeIsBounded() {
        var list = emptyList<NearbyTrust.Device>()
        for (i in 0 until NearbyTrust.MAX_DEVICES + 5) {
            list = NearbyTrust.merge(list, device("id-$i", "n$i", seen = i.toLong()))
        }
        assertEquals(NearbyTrust.MAX_DEVICES, list.size)
        assertEquals("id-${NearbyTrust.MAX_DEVICES + 4}", list.first().id)
    }

    @Test fun forgetAndTouch() {
        val list = listOf(device(idA, "x"), device(idB, "y"))
        assertEquals(listOf(idB), NearbyTrust.forget(list, idA).map { it.id })
        assertEquals(list, NearbyTrust.forget(list, "missing"))
        val touched = NearbyTrust.touch(list, idB, "renamed", 99L)
        assertEquals("renamed", touched.single { it.id == idB }.endpointName)
        assertEquals(99L, touched.single { it.id == idB }.lastSeen)
        assertEquals(1L, touched.single { it.id == idA }.lastSeen)
    }

    @Test fun endpointNameMustMatchExactlyOneDevice() {
        val list = listOf(device(idA, "Monogatari - Pixel"), device(idB, "Monogatari - Galaxy"))
        assertEquals(idB, NearbyTrust.byEndpointName(list, "Monogatari - Galaxy")?.id)
        assertNull(NearbyTrust.byEndpointName(list, "monogatari - galaxy"))
        assertNull(NearbyTrust.byEndpointName(list, "Unknown"))
        val twins = list + device("44444444-4444-4444-4444-444444444444", "Monogatari - Galaxy")
        assertNull(NearbyTrust.byEndpointName(twins, "Monogatari - Galaxy"))
    }

    @Test fun listEncodingRoundTripsAndDropsDamagedEntries() {
        val list = listOf(device(idA, "x", seen = 7), device(idB, "y", seen = 3))
        assertEquals(list, NearbyTrust.decode(NearbyTrust.encode(list)))
        assertEquals(emptyList<NearbyTrust.Device>(), NearbyTrust.decode(null))
        assertEquals(emptyList<NearbyTrust.Device>(), NearbyTrust.decode("not json"))
        val damaged = """[{"id":"a","secret":"short"},{"id":"$idA","name":"ok","endpoint":"e",
            "secret":"${NearbyTrust.toHex(secret)}","paired":1,"seen":2},{"name":"no id"}]"""
        assertEquals(listOf(idA), NearbyTrust.decode(damaged).map { it.id })
    }

    @Test fun deviceToStringNeverPrintsTheSecret() {
        assertFalse(device(idA, "x").toString().contains(NearbyTrust.toHex(secret)))
    }
}
