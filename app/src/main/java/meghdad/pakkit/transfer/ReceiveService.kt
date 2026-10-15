package meghdad.pakkit.transfer

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
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import meghdad.pakkit.R
import meghdad.pakkit.data.PackageStore
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
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var ongoing: OngoingActivity
    private var notificationState = ""
    private val store by lazy { PackageStore(this) }
    private val notificationUpdate = Runnable { updateNotification() }
    private val sessionObserver: () -> Unit = { scheduleNotification() }
    private val reviewObserver =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PackageStore.PENDING_KEY || key == null) main.post { scheduleNotification() }
        }
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
        if (registered || ended) {
            if (!ended) scheduleNotification()
            return START_NOT_STICKY
        }
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
        notification =
            NotificationCompat.Builder(this, "receive")
                .setSmallIcon(R.drawable.pakkit_mark)
                .setContentTitle("Receiving is on")
                .setContentText("Open Pakkit to connect your browser.")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, getString(R.string.watch_end_session), stop)
        ongoing =
            OngoingActivity.Builder(this, NOTIFICATION, notification)
                .setStaticIcon(R.drawable.pakkit_mark)
                .setTouchIntent(open)
                .build()
        ongoing.apply(this)
        startForeground(
            NOTIFICATION,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        Receiving.observe(sessionObserver)
        store.preferences.registerOnSharedPreferenceChangeListener(reviewObserver)
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

    private fun scheduleNotification() {
        // Coalesce quick phase changes and stay below the system's notification rate limit.
        // No repeating idle timer; the latest state is read only after an actual change.
        if (!ended && !main.hasCallbacks(notificationUpdate))
            main.postDelayed(notificationUpdate, 1000)
    }

    private fun updateNotification() {
        if (ended || !::ongoing.isInitialized) return
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
            return
        val live = Receiving.state
        val job = store.pending()
        val (title, detail) =
            when {
                live.browserRequest != null ->
                    "Connect browser?" to "Open Pakkit to approve this browser."
                live.total > 0 -> "Your app is on its way" to "Open Pakkit to follow the transfer."
                job != null ->
                    when (job.state) {
                        meghdad.pakkit.apk.InstallState.READY ->
                            "Ready to install" to "Review ${job.apk.label} on your watch."
                        meghdad.pakkit.apk.InstallState.CONFIRM ->
                            "Ready for your approval" to "Open Pakkit to continue."
                        meghdad.pakkit.apk.InstallState.INSTALLING ->
                            "Getting ready" to "Your watch will ask you to approve installation."
                        meghdad.pakkit.apk.InstallState.INSTALLED ->
                            "All yours." to "${job.apk.label} is ready to wear."
                        meghdad.pakkit.apk.InstallState.FAILED ->
                            "Couldn’t install" to "Open Pakkit to try again."
                    }
                live.url == null ->
                    "Waiting for Wi-Fi" to "Receiving will resume when your watch reconnects."
                else -> "Receiving is on" to "Open Pakkit to connect your browser."
            }
        val state = "$title:$detail"
        if (notificationState == state) return
        notificationState = state
        notification.setContentTitle(title).setContentText(detail)
        ongoing.update(this, Status.forPart(Status.TextPart(detail)))
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
        Receiving.removeObserver(sessionObserver)
        store.preferences.unregisterOnSharedPreferenceChangeListener(reviewObserver)
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
