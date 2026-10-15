package meghdad.pakkit.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TransferRulesTest {
    @Test fun copyComputesKnownDigest() {
        val output=ByteArrayOutputStream()
        val result=TransferRules.copyBounded(ByteArrayInputStream("abc".toByteArray()),output)
        assertEquals(3L,result.first)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",result.second)
        assertEquals("abc",output.toString("UTF-8"))
    }
    @Test(expected=IllegalArgumentException::class) fun stopsBeforeWritingOverLimit() {
        TransferRules.copyBounded(ByteArrayInputStream(ByteArray(1025)),ByteArrayOutputStream(),1024)
    }
    @Test(expected=IllegalArgumentException::class) fun refusesEmptyFiles() {
        TransferRules.copyBounded(ByteArrayInputStream(byteArrayOf()),ByteArrayOutputStream())
    }
    @Test fun rejectsTraversalAndInvalidIdentifiers() {
        assertFalse(TransferRules.validId("../apk")); assertFalse(TransferRules.validId("file.apk"))
        assertTrue(TransferRules.validId("05b414ab-a230-42f8-9a6d-1be25dcc3224"))
    }
    @Test fun expirationAndClockSkewAreBounded() {
        val now=1_000_000_000L
        assertTrue(TransferRules.fresh(now-1000,now))
        assertFalse(TransferRules.fresh(now-TransferRules.TTL_MS,now))
        assertFalse(TransferRules.fresh(now+120000,now))
    }
    @Test fun nativeLibrariesNeedMatchingProcessor() {
        assertTrue(TransferRules.compatibleAbis(emptySet(),setOf("arm64-v8a")))
        assertTrue(TransferRules.compatibleAbis(setOf("armeabi-v7a","arm64-v8a"),setOf("arm64-v8a")))
        assertFalse(TransferRules.compatibleAbis(setOf("x86_64"),setOf("arm64-v8a")))
    }
}
