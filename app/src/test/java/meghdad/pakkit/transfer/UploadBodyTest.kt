package meghdad.pakkit.transfer

import org.junit.Assert.*
import org.junit.Test

class UploadBodyTest {
    @Test
    fun readsExactlyDeclaredLengthWithoutConsumingExtraBytes() {
        val input = "abcdef".byteInputStream()
        val events = mutableListOf<Long>()
        val body = UploadBody(input, 3, { events.add(it) })
        assertArrayEquals("abc".toByteArray(), body.readBytes())
        assertEquals('d'.code, input.read())
        assertEquals(listOf(3L), events)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesTruncatedBodies() {
        UploadBody("ab".byteInputStream(), 3, {}).readBytes()
    }

    @Test
    fun zeroLengthReadDoesNotAdvanceOrReportProgress() {
        val body = UploadBody("abc".byteInputStream(), 3, { fail("No progress expected") })
        assertEquals(0, body.read(ByteArray(1), 0, 0))
        assertEquals('a'.code, body.read())
    }

    @Test
    fun progressIsThrottledAndAlwaysReportsCompletion() {
        var now = 0L
        val events = mutableListOf<Long>()
        val body = UploadBody("abc".byteInputStream(), 3, { events.add(it) }, { now })
        body.read()
        now = 250_000_000
        body.read()
        body.read()
        assertEquals(listOf(2L, 3L), events)
    }
}
