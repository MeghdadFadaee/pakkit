package meghdad.pakkit

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import meghdad.pakkit.transfer.LocalHttpServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiverLifecycleTest : WatchTest() {
    @Test
    fun ipv6ListenerAcceptsCanonicalBrowserAuthority() {
        LocalHttpServer(context, InetAddress.getByName("::1"), Any()).use { server ->
            server.start()
            val response =
                java.net.Socket(InetAddress.getByName("::1"), 8787).use { socket ->
                    socket.soTimeout = 5000
                    socket
                        .getOutputStream()
                        .write("GET / HTTP/1.1\r\nHost: [::1]:8787\r\n\r\n".toByteArray())
                    socket.getInputStream().bufferedReader().readText()
                }
            assertTrue(response.startsWith("HTTP/1.1 200"))
        }
    }

    @Test
    fun portIsFixedAndBusyPortsNeverSilentlyChangeTheAddress() {
        val address = InetAddress.getByName("127.0.0.1") as Inet4Address
        LocalHttpServer(context, address, Any()).use { server ->
            assertEquals("http://127.0.0.1:8787/", server.url)
            val failure = runCatching {
                LocalHttpServer(context, address, Any()).close()
            }
                .exceptionOrNull()
            assertTrue(failure is BindException)
        }
        LocalHttpServer(context, address, Any()).use {
            assertEquals("http://127.0.0.1:8787/", it.url)
        }
    }
}
