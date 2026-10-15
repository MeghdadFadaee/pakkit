package meghdad.pakkit.wear

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import meghdad.pakkit.core.Store
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.net.URI

@RunWith(AndroidJUnit4::class)
class LocalReceiveTest {
    @Test fun authenticatedUploadRequiresWatchConsentAndStopClosesListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val c = instrumentation.targetContext
        val store = Store(c)
        store.read("incoming")?.let { store.file(it.getString("id")).delete() }
        store.clear("incoming")
        c.startActivity(Intent(c, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        c.startForegroundService(Intent(c, ReceiveService::class.java))
        val deadline = System.currentTimeMillis() + 30_000
        while (store.prefs.getString("receiveUrl", null) == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val url = URI(requireNotNull(store.prefs.getString("receiveUrl", null)))
        val origin = "http://${url.authority}"
        val before = c.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet()
        fun request(method: String, path: String, auth: Boolean = true, source: String = origin, body: ByteArray? = null): String {
            return Socket(url.host, url.port).use { socket ->
                socket.soTimeout = 10_000
                val headers = "$method $path HTTP/1.1\r\nHost: ${url.authority}\r\n" +
                    (if (auth) "Authorization: Bearer ${url.fragment}\r\n" else "") +
                    "Origin: $source\r\n" + (body?.let { "Content-Type: application/octet-stream\r\nContent-Length: ${it.size}\r\n" } ?: "") + "\r\n"
                socket.getOutputStream().write(headers.toByteArray())
                body?.let { socket.getOutputStream().write(it) }
                socket.getOutputStream().flush()
                val input = socket.getInputStream().buffered()
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) {
                    val next = input.read(); check(next >= 0); head.append(next.toChar())
                }
                val size = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE).find(head)!!.groupValues[1].toInt()
                val bytes = ByteArray(size)
                var position = 0
                while (position < size) { val n = input.read(bytes, position, size - position); check(n > 0); position += n }
                head.toString() + bytes.toString(Charsets.UTF_8)
            }
        }
        try {
            assertTrue(request("GET", "/").contains("Pocket"))
            assertTrue(request("GET", "/api/status", false).startsWith("HTTP/1.1 403"))
            assertTrue(request("POST", "/api/upload", source = "https://evil.example", body = byteArrayOf(1)).startsWith("HTTP/1.1 403"))
            assertTrue(request("POST", "/api/upload", body = "invalid APK".toByteArray()).startsWith("HTTP/1.1 400"))
            assertNull(store.read("incoming"))
            val fixture = instrumentation.context.assets.open("fixture.apk").use { it.readBytes() }
            assertTrue(request("POST", "/api/upload", body = fixture).startsWith("HTTP/1.1 200"))
            val job = requireNotNull(store.read("incoming"))
            assertEquals("ready", job.getString("state"))
            assertEquals("meghdad.pakkit.fixture", job.getJSONObject("apk").getString("package"))
            assertEquals(before, c.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet())
            assertTrue(request("POST", "/api/upload", body = byteArrayOf(1)).startsWith("HTTP/1.1 409"))
            assertTrue(request("GET", "/api/status").contains("ready"))
        } finally {
            c.stopService(Intent(c, ReceiveService::class.java))
            val end = System.currentTimeMillis() + 5000
            while (ReceiveService.active && System.currentTimeMillis() < end) Thread.sleep(50)
            assertNull(store.prefs.getString("receiveUrl", null))
            assertTrue(runCatching { Socket(url.host, url.port).close() }.isFailure)
            store.read("incoming")?.let { store.file(it.getString("id")).delete() }
            store.clear("incoming")
        }
    }
}
