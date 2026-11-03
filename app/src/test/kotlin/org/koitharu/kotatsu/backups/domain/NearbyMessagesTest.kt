package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.backups.domain.NearbySyncProtocol.Message

class NearbyMessagesTest {

    private val id = "11111111-1111-1111-1111-111111111111"
    private val secret = "ab".repeat(32)
    private val nonce = "cd".repeat(16)
    private val mac = "ef".repeat(32)

    private fun roundTrip(message: Message) =
        NearbySyncProtocol.decodeMessage(NearbySyncProtocol.encodeMessage(message))

    @Test fun everyMessageKindRoundTrips() {
        val messages = listOf(
            Message.Batch(3, 1234L),
            Message.BatchEnd,
            Message.PairOffer(id, "Monogatari - Pixel", secret),
            Message.PairAck(id, "Monogatari - Galaxy"),
            Message.PairDecline,
            Message.Challenge(id, nonce),
            Message.Response(mac),
            Message.TrustFail,
        )
        messages.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test fun transferHeadersKeepTheirOriginalLayout() {
        val header = NearbySyncProtocol.Header(7L, NearbySyncProtocol.Kind.FILE, "book.epub", 10L, "a".repeat(64))
        val bytes = NearbySyncProtocol.encodeMessage(Message.Transfer(header))
        assertTrue(bytes.contentEquals(NearbySyncProtocol.encode(header)))
        assertEquals(Message.Transfer(header), NearbySyncProtocol.decodeMessage(bytes))
        // Older builds decode the same bytes with decode().
        assertEquals(header, NearbySyncProtocol.decode(bytes))
    }

    @Test fun trustMessagesStaySmall() {
        val biggest = NearbySyncProtocol.encodeMessage(Message.PairOffer(id, "x".repeat(60), secret))
        assertTrue(biggest.size < 512)
    }

    @Test fun malformedMessagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Message.PairOffer("not-a-uuid", "n", secret) }
        assertThrows(IllegalArgumentException::class.java) { Message.PairOffer(id, "n", "00") }
        assertThrows(IllegalArgumentException::class.java) { Message.Challenge(id, "zz".repeat(16)) }
        assertThrows(IllegalArgumentException::class.java) { Message.Response("ab") }
        assertThrows(IllegalArgumentException::class.java) { Message.Batch(1, 10) }
        assertThrows(IllegalArgumentException::class.java) { Message.Batch(NearbySyncProtocol.MAX_BATCH_FILES + 1, 10) }
        assertThrows(IllegalArgumentException::class.java) {
            Message.Batch(2, NearbySyncProtocol.MAX_BATCH_BYTES + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbySyncProtocol.decodeMessage("""{"protocol":1,"type":"mystery"}""".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbySyncProtocol.decodeMessage("""{"protocol":2,"type":"batch_end"}""".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) { NearbySyncProtocol.decodeMessage(ByteArray(2049) { 65 }) }
        assertThrows(Exception::class.java) { NearbySyncProtocol.decodeMessage("nope".toByteArray()) }
    }

    @Test fun displayNamesAreCleaned() {
        val raw = """{"protocol":1,"type":"pair_ack","device":"$id","name":"  Bad\u0000 Name\n  "}"""
        val ack = NearbySyncProtocol.decodeMessage(raw.toByteArray()) as Message.PairAck
        assertEquals("Bad Name", ack.name)
        assertEquals("Unknown device", NearbySyncProtocol.cleanDisplayName("\u0001 "))
        assertEquals(60, NearbySyncProtocol.cleanDisplayName("x".repeat(200)).length)
    }
}
