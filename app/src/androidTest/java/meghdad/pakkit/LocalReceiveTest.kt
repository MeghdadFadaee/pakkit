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
    fun watchApprovedBrowserCanUploadAndStoppingRevokesAccess() {
        openWatch()
        context.startForegroundService(Intent(context, ReceiveService::class.java))
        await(30_000) { Receiving.state.url != null }
        val url = URI(requireNotNull(Receiving.state.url))
        val sessions = context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        var browserCredential: String? = null
        fun request(
            method: String,
            path: String,
            credential: String? = browserCredential,
            origin: String = "http://${url.authority}",
            host: String = url.authority,
            body: ByteArray? = null,
            contentLength: Long? = body?.size?.toLong(),
        ): String {
            return Socket(url.host, url.port).use { socket ->
                socket.soTimeout = 10_000
                val headers =
                    "$method $path HTTP/1.1\r\nHost: $host\r\n" +
                        (credential?.let { "Authorization: Bearer $it\r\n" } ?: "") +
                        "Origin: $origin\r\nUser-Agent: Chrome Android\r\n" +
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
        fun assertUploadWakeReleased() {
            await { Receiving.state.total == 0L }
            val dump =
                instrumentation.uiAutomation.executeShellCommand("dumpsys power").use { descriptor
                    ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                }
            assertTrue("Power state must be observable", dump.contains("Wake Locks:"))
            assertFalse(
                "Completed requests must not hold an upload wake lock",
                Regex("(?m)^\\s*PARTIAL_WAKE_LOCK.*pakkit:upload.*$").containsMatchIn(dump),
            )
        }
        assertTrue(request("GET", "/").contains("Pack it."))
        assertTrue(request("GET", "/api/status", credential = null).startsWith("HTTP/1.1 403"))
        assertTrue(request("GET", "/api/status", credential = "wrong").startsWith("HTTP/1.1 403"))
        assertTrue(
            request("GET", "/api/status", host = "attacker.example").startsWith("HTTP/1.1 403")
        )
        assertTrue(
            request(
                    "POST",
                    "/api/upload",
                    origin = "https://attacker.example",
                    body = byteArrayOf(1),
                )
                .startsWith("HTTP/1.1 403")
        )
        val connection = request("POST", "/api/connect")
        browserCredential =
            org.json.JSONObject(connection.substringAfter("\r\n\r\n")).getString("credential")
        assertFalse(connection.contains("Set-Cookie:"))
        assertTrue(request("POST", "/api/upload", body = byteArrayOf(1)).startsWith("HTTP/1.1 403"))
        assertTrue(request("GET", "/api/status").contains("pending"))
        assertTrue(Receiving.answerBrowser(requireNotNull(Receiving.state.browserRequest).id, true))
        assertTrue(request("GET", "/api/status").startsWith("HTTP/1.1 200"))
        assertFalse(request("GET", "/api/status").contains("expiresIn"))
        assertTrue(
            request("POST", "/api/upload", contentLength = Long.MAX_VALUE).contains("storage")
        )
        assertTrue(
            request("POST", "/api/upload", body = "invalid APK".toByteArray())
                .startsWith("HTTP/1.1 400")
        )
        assertNull(store.pending())
        assertTrue(
            request("POST", "/api/upload", body = "short".toByteArray(), contentLength = 100)
                .startsWith("HTTP/1.1 400")
        )
        assertNull(store.pending())
        assertEquals(0L, Receiving.state.total)
        assertEquals("Ready to receive", Receiving.state.detail)
        assertUploadWakeReleased()
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
        assertUploadWakeReleased()
        // A finished install does not require a separate Done tap before the next upload.
        for (terminal in listOf(InstallState.INSTALLED, InstallState.FAILED)) {
            store.update(requireNotNull(store.pending()).id, terminal, "Finished")
            assertTrue(request("POST", "/api/upload", body = fixture).startsWith("HTTP/1.1 200"))
            assertEquals(InstallState.READY, store.pending()?.state)
        }
        context.stopService(Intent(context, ReceiveService::class.java))
        await { !Receiving.state.active }
        assertNull(Receiving.state.url)
        assertTrue(runCatching { Socket(url.host, url.port).close() }.isFailure)
        context.startForegroundService(Intent(context, ReceiveService::class.java))
        await(30_000) { Receiving.state.url != null }
        assertEquals(url.toString(), Receiving.state.url)
        assertTrue(request("GET", "/api/status").startsWith("HTTP/1.1 403"))
    }

    @Test
    fun losingWifiWaitsForReconnectionWithoutEndingReceiving() {
        openWatch()
        context.startForegroundService(Intent(context, ReceiveService::class.java))
        await(30_000) { Receiving.state.url != null }
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                    it.readBytes()
                }
            }
        }
        try {
            shell("cmd wifi set-wifi-enabled disabled")
            await(15_000) { Receiving.state.url == null }
            assertTrue(Receiving.state.active)
            shell("cmd wifi set-wifi-enabled enabled")
            shell("cmd wifi connect-network AndroidWifi open")
            await(30_000) { Receiving.state.url != null }
            assertTrue(Receiving.state.active)
            assertEquals(8787, URI(requireNotNull(Receiving.state.url)).port)
        } finally {
            shell("cmd wifi set-wifi-enabled enabled")
            shell("cmd wifi connect-network AndroidWifi open")
        }
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
