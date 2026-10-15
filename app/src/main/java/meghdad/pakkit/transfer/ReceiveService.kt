package meghdad.pakkit.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import meghdad.pakkit.R
import meghdad.pakkit.ui.WatchActivity

/** User-controlled receiving; network loss suspends the listener, never the user's intent. */
class ReceiveService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private var server: LocalHttpServer? = null
    private var network: Network? = null
    private var address: InetAddress? = null
    private var registered = false
    private var ended = false
    private val access = BrowserAccess { request ->
        Receiving.update(this) { it.copy(browserRequest = request) }
    }
    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                main.post {
                    if (ended) return@post
                    val candidates =
                        properties.linkAddresses
                            .map { it.address }
                            .filter {
                                !it.isLoopbackAddress &&
                                    !it.isAnyLocalAddress &&
                                    !it.isMulticastAddress
                            }
                    val ipv4 =
                        candidates.firstOrNull { it is Inet4Address }
                            ?: candidates.firstOrNull { !it.isLinkLocalAddress }
                    if (ipv4 == null) {
                        if (network == this@ReceiveService.network) suspendListener()
                        return@post
                    }
                    if (server != null && network == this@ReceiveService.network && ipv4 == address)
                        return@post
                    open(network, ipv4)
                }
            }

            override fun onLost(network: Network) {
                main.post {
                    if (!ended && network == this@ReceiveService.network) suspendListener()
                }
            }

            override fun onUnavailable() {
                main.post { if (!ended) suspendListener() }
            }
        }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            finish("Receiving stopped")
            return START_NOT_STICKY
        }
        if (registered || ended) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("receive", "App transfers", NotificationManager.IMPORTANCE_LOW)
        )
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, WatchActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, ReceiveService::class.java).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification.Builder(this, "receive")
                .setSmallIcon(R.drawable.pakkit_mark)
                .setContentTitle("Receiving is on")
                .setContentText("Open Pakkit to connect your browser.")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop receiving", stop).build())
                .build()
        startForeground(
            NOTIFICATION,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        Receiving.begin(this, access)
        try {
            registered = true
            connectivity.requestNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        } catch (error: Exception) {
            android.util.Log.w("PakkitReceive", "Cannot request Wi-Fi", error)
            finish("Can’t reach Wi-Fi. Check your watch’s connection.")
        }
        // A killed process does not silently reopen network access. The user starts it again.
        return START_NOT_STICKY
    }

    private fun open(network: Network, address: InetAddress) {
        suspendListener()
        this.network = network
        this.address = address
        try {
            val listener = LocalHttpServer(applicationContext, address, this, access)
            server = listener
            listener.start()
            Receiving.update(this) { it.copy(url = listener.url, detail = "Ready to receive") }
        } catch (error: Exception) {
            android.util.Log.w("PakkitReceive", "Cannot open receiver", error)
            finish(
                if (error is BindException)
                    "Port ${LocalHttpServer.PORT} is in use. Close the other receiver and try again."
                else "Couldn’t connect. Check Wi-Fi and try again."
            )
        }
    }

    private fun suspendListener() {
        server?.close()
        server = null
        network = null
        address = null
        Receiving.update(this) {
            it.copy(url = null, received = 0, total = 0, detail = "Waiting for Wi-Fi…")
        }
    }

    private fun finish(message: String) {
        if (ended) return
        ended = true
        release(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release(message: String) {
        main.removeCallbacksAndMessages(null)
        server?.close()
        server = null
        if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
        registered = false
        access.close()
        Receiving.end(this, message)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        finish("Your watch paused receiving. Open Pakkit to start again.")
    }

    override fun onDestroy() {
        ended = true
        release("Receiving stopped")
        super.onDestroy()
    }

    companion object {
        const val STOP = "stop"
        private const val NOTIFICATION = 81
    }
}
