package meghdad.pakkit.transfer

import org.junit.Assert.*
import org.junit.Test

class BrowserAccessTest {
    @Test
    fun approvingAnotherBrowserDoesNotEvictEarlierApprovals() {
        var request: BrowserAccess.Request? = null
        val access = BrowserAccess { request = it }
        val first = access.connect(null, "Browser", "ip").credential
        access.answer(requireNotNull(request).id, true)
        repeat(64) {
            access.connect(null, "Browser", "ip")
            access.answer(requireNotNull(request).id, true)
        }
        assertEquals(BrowserAccess.State.APPROVED, access.state(first))
    }

    @Test
    fun onlyTheWatchCanApproveAndDecisionsAreSessionScoped() {
        var pending: BrowserAccess.Request? = null
        val access = BrowserAccess { pending = it }
        val connection = access.connect(null, "Chrome", "192.168.1.2")
        val credential = requireNotNull(connection.credential)
        assertEquals(BrowserAccess.State.PENDING, access.state(credential))
        assertFalse(access.answer("forged", true))
        assertTrue(access.answer(requireNotNull(pending).id, true))
        repeat(100) { assertEquals(BrowserAccess.State.APPROVED, access.state(credential)) }
        assertNull(access.connect(credential, "Chrome", "192.168.1.2").credential)
        access.close()
        assertEquals(BrowserAccess.State.UNKNOWN, access.state(credential))
    }

    @Test
    fun unknownPeersCannotReplacePendingRequestsOrAutoRetryDenial() {
        var pending: BrowserAccess.Request? = null
        val access = BrowserAccess { pending = it }
        val first = access.connect(null, "Chrome", "ip")
        val id = requireNotNull(pending).id
        repeat(1000) {
            assertEquals(BrowserAccess.State.BUSY, access.connect(null, "Unknown", "ip").state)
        }
        assertEquals(id, pending?.id)
        access.answer(id, false)
        assertEquals(
            BrowserAccess.State.DENIED,
            access.connect(first.credential, "Chrome", "ip").state,
        )
        access.retry(first.credential)
        assertEquals(
            BrowserAccess.State.PENDING,
            access.connect(first.credential, "Chrome", "ip").state,
        )
    }

    @Test
    fun credentialsArePrivateOpaqueValuesWithExactHeaderFraming() {
        val credential = requireNotNull(BrowserAccess().connect(null, "Browser", "ip").credential)
        assertEquals(credential, BrowserAccess.credential("Bearer $credential"))
        assertNull(BrowserAccess.credential("Bearer $credential extra"))
        assertNull(BrowserAccess.credential("bearer $credential"))
        assertNull(BrowserAccess.credential("Bearer guessed"))
        assertEquals("Chrome on Android", BrowserAccess.label("Mozilla Android Chrome Safari"))
    }
}
