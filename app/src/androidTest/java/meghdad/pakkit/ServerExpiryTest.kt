package meghdad.pakkit

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import meghdad.pakkit.transfer.LocalHttpServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerExpiryTest : WatchTest() {
    @Test
    fun expiredSessionRefusesRequestsEvenWithTheCorrectKey() {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        LocalHttpServer(context, address, SystemClock.elapsedRealtime() - 1, Any()).use { server ->
            server.start()
            val url = URI(server.url)
            val response =
                Socket(url.host, url.port).use { socket ->
                    socket.soTimeout = 5000
                    val request =
                        "GET /api/status HTTP/1.1\r\nHost: ${url.authority}\r\nAuthorization: Bearer ${url.fragment}\r\n\r\n"
                    socket.getOutputStream().write(request.toByteArray())
                    socket.getInputStream().bufferedReader().readText()
                }
            assertTrue(response.startsWith("HTTP/1.1 400"))
            assertTrue(response.contains("session ended"))
            assertNull(store.pending())
        }
    }
}
