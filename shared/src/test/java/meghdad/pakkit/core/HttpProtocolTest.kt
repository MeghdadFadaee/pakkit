package meghdad.pakkit.core
import org.junit.Assert.*
import org.junit.Test

class HttpProtocolTest {
    private fun request(value: String) = HttpProtocol.read(value.byteInputStream())
    @Test fun acceptsBoundedRawUpload() {
        val r = request("POST /api/upload HTTP/1.1\r\nHost: 192.168.1.4:8080\r\nContent-Length: 42\r\nContent-Type: application/octet-stream\r\n\r\n")
        assertEquals(42L, HttpProtocol.uploadLength(r.headers))
        assertEquals("/api/upload", r.path)
    }
    @Test fun rejectsAmbiguousAndOversizedHeaders() {
        for (headers in listOf("Content-Length: 1\r\nContent-Length: 2", "Transfer-Encoding: chunked", "Expect: 100-continue", "Host: x\nBad: x", "Long: " + "a".repeat(9000))) {
            assertTrue(runCatching { request("POST /api/upload HTTP/1.1\r\n$headers\r\n\r\n") }.isFailure)
        }
    }
    @Test fun rejectsInvalidLengthsAndTypes() {
        for (length in listOf("-1", "0", "52428801", "wat", "9223372036854775808")) {
            assertTrue(runCatching { HttpProtocol.uploadLength(mapOf("content-type" to "application/octet-stream", "content-length" to length)) }.isFailure)
        }
        assertTrue(runCatching { HttpProtocol.uploadLength(mapOf("content-type" to "text/plain", "content-length" to "42")) }.isFailure)
    }
    @Test fun requiresExactBearerToken() {
        assertTrue(HttpProtocol.authorized("Bearer secret", "secret"))
        assertFalse(HttpProtocol.authorized(null, "secret"))
        assertFalse(HttpProtocol.authorized("Bearer secretX", "secret"))
        assertFalse(HttpProtocol.authorized("bearer secret", "secret"))
    }
}
