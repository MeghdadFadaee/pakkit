package meghdad.pakkit.transfer

import org.junit.Assert.*
import org.junit.Test

class HttpProtocolTest {
    private fun request(value: String) = HttpProtocol.read(value.byteInputStream())

    @Test
    fun authorityChecksNumericAddressesWithoutAssumingPrivateIpv4() {
        val v6 = java.net.InetAddress.getByName("fd00::1")
        assertTrue(HttpProtocol.sameAuthority("[fd00:0:0:0:0:0:0:1]:8787", v6, 8787))
        assertTrue(HttpProtocol.sameAuthority("[fd00::1]:8787", v6, 8787))
        val v4 = java.net.InetAddress.getByName("100.64.1.2")
        assertTrue(HttpProtocol.sameAuthority("100.64.1.2:8787", v4, 8787))
        for (host in
            listOf(
                "evil.example:8787",
                "100.64.1.2:9999",
                "evil@100.64.1.2:8787",
                "100.64.1.2:8787/path",
                "100.64.1.2:8787?q=x",
            )) {
            assertFalse(host, HttpProtocol.sameAuthority(host, v4, 8787))
        }
    }

    @Test
    fun acceptsBoundedRawUpload() {
        val r =
            request(
                "POST /api/upload HTTP/1.1\r\nHost: 192.168.1.4:8080\r\nContent-Length: 42\r\nContent-Type: application/octet-stream\r\n\r\n"
            )
        assertEquals(42L, HttpProtocol.uploadLength(r.headers))
        assertEquals("/api/upload", r.path)
    }

    @Test
    fun rejectsAmbiguousAndOversizedHeaders() {
        for (headers in
            listOf(
                "Content-Length: 1\r\nContent-Length: 2",
                "Transfer-Encoding: chunked",
                "Expect: 100-continue",
                "Host: x\nBad: x",
                "Long: " + "a".repeat(9000),
            )) {
            assertTrue(
                runCatching { request("POST /api/upload HTTP/1.1\r\n$headers\r\n\r\n") }.isFailure
            )
        }
    }

    @Test
    fun rejectsInvalidLengthsAndTypes() {
        for (length in listOf("-1", "+1", "1,1", "0", "wat", "9223372036854775808")) {
            assertTrue(
                runCatching {
                    HttpProtocol.uploadLength(
                        mapOf(
                            "content-type" to "application/octet-stream",
                            "content-length" to length,
                        )
                    )
                }
                    .isFailure
            )
        }
        assertTrue(
            runCatching {
                HttpProtocol.uploadLength(
                    mapOf("content-type" to "text/plain", "content-length" to "42")
                )
            }
                .isFailure
        )
    }

    @Test
    fun slowHeadersCannotHoldAWorkerIndefinitely() {
        var time = 0L
        val result = runCatching {
            HttpProtocol.read("GET / HTTP/1.1\r\n\r\n".byteInputStream()) {
                time += 1_000_000_000
                time
            }
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun acceptsLargeContentLengthsWithoutIntegerTruncation() {
        for (size in listOf(52_428_801L, 4L * 1024 * 1024 * 1024, Long.MAX_VALUE)) {
            assertEquals(
                size,
                HttpProtocol.uploadLength(
                    mapOf("content-type" to "application/octet-stream", "content-length" to "$size")
                ),
            )
        }
    }
}
