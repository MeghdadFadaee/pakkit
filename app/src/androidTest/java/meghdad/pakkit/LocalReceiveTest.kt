package meghdad.pakkit

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.Socket
import java.net.URI
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.transfer.ReceiveService
import meghdad.pakkit.transfer.Receiving
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalReceiveTest : WatchTest() {
    @Test
    fun authenticatedUploadVerifiesPackageAndStopRevokesPrivateLink() {
        openWatch()
        context.startForegroundService(Intent(context, ReceiveService::class.java))
        await(30_000) { Receiving.state.url != null }
        val url = URI(requireNotNull(Receiving.state.url))
        val sessions = context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        fun request(
            method: String,
            path: String,
            token: String? = url.fragment,
            origin: String = "http://${url.authority}",
            host: String = url.authority,
            body: ByteArray? = null,
            contentLength: Long? = body?.size?.toLong(),
        ): String {
            return Socket(url.host, url.port).use { socket ->
                socket.soTimeout = 10_000
                val headers =
                    "$method $path HTTP/1.1\r\nHost: $host\r\n" +
                        (token?.let { "Authorization: Bearer $it\r\n" } ?: "") +
                        "Origin: $origin\r\n" +
                        (contentLength?.let {
                            "Content-Type: application/octet-stream\r\nContent-Length: $it\r\n"
                        } ?: "") +
                        "\r\n"
                socket.getOutputStream().apply {
                    write(headers.toByteArray())
                    body?.let { write(it) }
                    flush()
                }
                socket.shutdownOutput()
                val input = socket.getInputStream().buffered()
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) {
                    val byte = input.read()
                    check(byte >= 0)
                    head.append(byte.toChar())
                }
                val length =
                    Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                        .find(head)!!
                        .groupValues[1]
                        .toInt()
                val bytes = ByteArray(length)
                var position = 0
                while (position < length) {
                    val read = input.read(bytes, position, length - position)
                    check(read > 0)
                    position += read
                }
                head.toString() + bytes.toString(Charsets.UTF_8)
            }
        }
        assertTrue(request("GET", "/").contains("Pocket"))
        assertTrue(request("GET", "/api/status", token = null).startsWith("HTTP/1.1 403"))
        assertTrue(request("GET", "/api/status", token = "wrong").startsWith("HTTP/1.1 403"))
        assertTrue(
            request("GET", "/api/status", host = "attacker.example").startsWith("HTTP/1.1 403"),
        )
        assertTrue(
            request(
                    "POST",
                    "/api/upload",
                    origin = "https://attacker.example",
                    body = byteArrayOf(1),
                )
                .startsWith("HTTP/1.1 403"),
        )
        assertTrue(
            request("POST", "/api/upload", contentLength = 52_428_801).startsWith("HTTP/1.1 400"),
        )
        assertTrue(
            request("POST", "/api/upload", body = "invalid APK".toByteArray())
                .startsWith("HTTP/1.1 400"),
        )
        assertNull(store.pending())
        assertTrue(
            request("POST", "/api/upload", body = "short".toByteArray(), contentLength = 100)
                .startsWith("HTTP/1.1 400"),
        )
        assertNull(store.pending())
        assertEquals(0L, Receiving.state.total)
        assertEquals("Ready to receive", Receiving.state.detail)
        val fixture = instrumentation.context.assets.open("fixture.apk").use { it.readBytes() }
        assertTrue(request("POST", "/api/upload", body = fixture).startsWith("HTTP/1.1 200"))
        assertEquals(InstallState.READY, store.pending()?.state)
        assertEquals("meghdad.pakkit.fixture", store.pending()?.apk?.packageName)
        assertEquals(
            sessions,
            context.packageManager.packageInstaller.mySessions.map { it.sessionId },
        )
        assertTrue(request("POST", "/api/upload", body = byteArrayOf(1)).startsWith("HTTP/1.1 409"))
        assertTrue(request("GET", "/api/status").contains("ready"))
        context.stopService(Intent(context, ReceiveService::class.java))
        await { !Receiving.state.active }
        assertNull(Receiving.state.url)
        assertTrue(runCatching { Socket(url.host, url.port).close() }.isFailure)
    }

    @Test
    fun staleWorkersCannotChangeANewReceivingSession() {
        val old = Any()
        val current = Any()
        try {
            Receiving.begin(old)
            Receiving.begin(current)
            Receiving.update(old) { it.copy(url = "stale link", received = 500) }
            Receiving.end(old, "late shutdown")
            assertTrue(Receiving.state.active)
            assertNull(Receiving.state.url)
            assertEquals(0L, Receiving.state.received)
        } finally {
            Receiving.end(current, "Session ended")
        }
    }
}
