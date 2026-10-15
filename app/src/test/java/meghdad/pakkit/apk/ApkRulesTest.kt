package meghdad.pakkit.apk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ApkRulesTest {
    @Test
    fun copyComputesKnownDigest() {
        val output = ByteArrayOutputStream()
        val result = ApkRules.copyAndHash(ByteArrayInputStream("abc".toByteArray()), output)
        assertEquals(3L, result.first)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result.second,
        )
        assertEquals("abc", output.toString("UTF-8"))
    }

    @Test
    fun streamsBeyondThePreviousFileLimitWithABoundedBuffer() {
        val length = 64L * 1024 * 1024
        var remaining = length
        val input =
            object : java.io.InputStream() {
                override fun read(): Int = error("Bulk reads only")

                override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
                    assertTrue("Copy buffer stays bounded", bytes.size <= 64 * 1024)
                    if (remaining == 0L) return -1
                    val size = minOf(count.toLong(), remaining).toInt()
                    bytes.fill(0, offset, offset + size)
                    remaining -= size
                    return size
                }
            }
        assertEquals(length, ApkRules.copyAndHash(input).first)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesEmptyFiles() {
        ApkRules.copyAndHash(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
    }

    @Test
    fun rejectsTraversalAndInvalidIdentifiers() {
        assertFalse(ApkRules.validId("../apk"))
        assertFalse(ApkRules.validId("file.apk"))
        assertTrue(ApkRules.validId("05b414ab-a230-42f8-9a6d-1be25dcc3224"))
    }

    @Test
    fun nativeLibrariesNeedMatchingProcessor() {
        assertTrue(ApkRules.compatibleAbis(emptySet(), setOf("arm64-v8a")))
        assertTrue(ApkRules.compatibleAbis(setOf("armeabi-v7a", "arm64-v8a"), setOf("arm64-v8a")))
        assertFalse(ApkRules.compatibleAbis(setOf("x86_64"), setOf("arm64-v8a")))
    }
}
