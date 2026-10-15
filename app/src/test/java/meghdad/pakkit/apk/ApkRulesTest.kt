package meghdad.pakkit.apk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ApkRulesTest {
    @Test
    fun copyComputesKnownDigest() {
        val output = ByteArrayOutputStream()
        val result = ApkRules.copyBounded(ByteArrayInputStream("abc".toByteArray()), output)
        assertEquals(3L, result.first)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result.second,
        )
        assertEquals("abc", output.toString("UTF-8"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun stopsBeforeWritingOverLimit() {
        ApkRules.copyBounded(ByteArrayInputStream(ByteArray(1025)), ByteArrayOutputStream(), 1024)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesEmptyFiles() {
        ApkRules.copyBounded(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
    }

    @Test
    fun rejectsTraversalAndInvalidIdentifiers() {
        assertFalse(ApkRules.validId("../apk"))
        assertFalse(ApkRules.validId("file.apk"))
        assertTrue(ApkRules.validId("05b414ab-a230-42f8-9a6d-1be25dcc3224"))
    }

    @Test
    fun expirationAndClockSkewAreBounded() {
        val now = 1_000_000_000L
        assertTrue(ApkRules.fresh(now - 1000, now))
        assertFalse(ApkRules.fresh(now - ApkRules.TTL_MS, now))
        assertFalse(ApkRules.fresh(now + 120000, now))
    }

    @Test
    fun nativeLibrariesNeedMatchingProcessor() {
        assertTrue(ApkRules.compatibleAbis(emptySet(), setOf("arm64-v8a")))
        assertTrue(ApkRules.compatibleAbis(setOf("armeabi-v7a", "arm64-v8a"), setOf("arm64-v8a")))
        assertFalse(ApkRules.compatibleAbis(setOf("x86_64"), setOf("arm64-v8a")))
    }
}
