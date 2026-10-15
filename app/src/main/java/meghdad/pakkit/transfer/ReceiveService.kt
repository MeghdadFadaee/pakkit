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
import android.os.PowerManager
import android.os.SystemClock
import java.net.Inet4Address
import meghdad.pakkit.R
import meghdad.pakkit.ui.WatchActivity

/** Owns the user-started session and releases all network/power resources on every exit path. */
class ReceiveService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private var server: LocalHttpServer? = null
    private var network: Network? = null
    private var address: Inet4Address? = null
    private var registered = false
    private var ended = false
    private var deadline = 0L
    private var wake: PowerManager.WakeLock? = null
    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                main.post {
                    if (ended) return@post
                    if (this@ReceiveService.network == null) {
                        if (properties.linkAddresses.isNotEmpty()) open(network, properties)
                    } else if (
                        this@ReceiveService.network == network &&
                            properties.linkAddresses.none { it.address == address }
                    ) {
                        finish("Wi-Fi address changed. Start a new session.")
                    }
                }
            }

            override fun onLost(network: Network) {
                main.post {
                    if (network == this@ReceiveService.network)
                        finish("Wi-Fi disconnected. Start a new session.")
                }
            }

            override fun onUnavailable() {
                main.post { finish("Connect your watch to Wi-Fi, then try again.") }
            }
        }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            finish("Session ended")
            return START_NOT_STICKY
        }
        if (registered || ended) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "receive",
                "Receiving sessions",
                NotificationManager.IMPORTANCE_LOW,
            ),
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
                .setContentTitle("Pakkit is ready to receive")
                .setContentText("Private Wi-Fi session · ends in 10 minutes")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "End session", stop).build())
                .build()
        startForeground(
            NOTIFICATION,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        Receiving.begin(this)
        deadline = SystemClock.elapsedRealtime() + DURATION_MS
        main.postDelayed({ finish("Session expired. Start a new session.") }, DURATION_MS)
        try {
            registered = true
            connectivity.requestNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
                25_000,
            )
        } catch (_: Exception) {
            finish("Wi-Fi is unavailable. Check watch settings.")
        }
        return START_NOT_STICKY
    }

    private fun open(network: Network, properties: LinkProperties) {
        try {
            val address =
                properties.linkAddresses
                    .map { it.address }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress }
                    ?: error("A private IPv4 Wi-Fi address is required")
            this.network = network
            this.address = address
            val listener = LocalHttpServer(applicationContext, address, deadline, this)
            server = listener
            wake =
                getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pakkit:receive")
                    .apply { acquire((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1)) }
            listener.start()
            Receiving.update(this) { it.copy(url = listener.url, detail = "Ready to receive") }
        } catch (error: Exception) {
            android.util.Log.w("PakkitReceive", "Cannot open receiving session", error)
            finish("Could not open local Wi-Fi. Check the network and try again.")
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
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        Receiving.end(this, message)
    }

    override fun onDestroy() {
        ended = true
        release("Session ended")
        super.onDestroy()
    }

    companion object {
        const val DURATION_MS = 10 * 60 * 1000L
        const val STOP = "stop"
        private const val NOTIFICATION = 81
    }
}
