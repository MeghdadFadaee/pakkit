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
    fun ongoingActivityReflectsReviewAndStopActionEndsReceiving() {
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val granted =
            context.checkSelfPermission(permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= 33 && !granted)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        // Keep this optional notification grant on the disposable emulator. Revoking a
        // runtime permission kills the target process and its instrumentation runner.
        run {
            openWatch()
            context.startForegroundService(
                android.content.Intent(context, meghdad.pakkit.transfer.ReceiveService::class.java)
            )
            await(30_000) { meghdad.pakkit.transfer.Receiving.state.url != null }
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            await { androidx.wear.ongoing.OngoingActivity.recoverOngoingActivity(context) != null }
            val ongoing =
                requireNotNull(
                    androidx.wear.ongoing.OngoingActivity.recoverOngoingActivity(context)
                )
            assertTrue(ongoing.touchIntent.isImmutable)
            store.stage(fixture())
            try {
                await {
                    manager.activeNotifications.any {
                        it.id == ongoing.notificationId &&
                            it.notification.extras.getString(
                                android.app.Notification.EXTRA_TITLE
                            ) == "Ready to install"
                    }
                }
            } catch (failure: Exception) {
                throw AssertionError(
                    "Notifications: ${manager.activeNotifications.map { it.id to it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) }}; review=${store.pending()?.state}; receiving=${meghdad.pakkit.transfer.Receiving.state}",
                    failure,
                )
            }
            val notification =
                manager.activeNotifications.single { it.id == ongoing.notificationId }.notification
            notification.actions.single { it.title == "Stop receiving" }.actionIntent.send()
            await { !meghdad.pakkit.transfer.Receiving.state.active }
            await { manager.activeNotifications.none { it.id == ongoing.notificationId } }
        }
    }

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
