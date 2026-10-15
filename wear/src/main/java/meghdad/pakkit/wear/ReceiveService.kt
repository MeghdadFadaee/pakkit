package meghdad.pakkit.wear

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.*
import android.os.*
import meghdad.pakkit.core.*
import org.json.JSONObject
import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** A user-started, ten-minute local session. No auto start, discovery broadcast, or cloud traffic. */
class ReceiveService : Service() {
    companion object {
        const val DURATION = 10 * 60 * 1000L
        @Volatile var active = false
        const val STOP = "stop"
        const val NOTICE = 81
    }
    private val handler = Handler(Looper.getMainLooper())
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4), ThreadPoolExecutor.AbortPolicy())
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val uploading = AtomicBoolean(false)
    private var listener: ServerSocket? = null
    private var network: Network? = null
    private var registered = false
    private var ended = false
    private var authority = ""
    private var token = ""
    private var expires = 0L
    private var wake: PowerManager.WakeLock? = null
    private lateinit var store: Store
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            handler.post { if (!ended && this@ReceiveService.network == null && properties.linkAddresses.isNotEmpty()) open(network, properties) }
        }
        override fun onLost(network: Network) { handler.post { if (network == this@ReceiveService.network) finish("Wi-Fi disconnected. Start a new session.") } }
        override fun onUnavailable() { handler.post { finish("Connect your watch to Wi-Fi, then try again.") } }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() { super.onCreate(); active = true; store = Store(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish("Session ended"); return START_NOT_STICKY }
        if (registered) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("receive", "Receiving sessions", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ReceiveService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "receive").setSmallIcon(meghdad.pakkit.core.R.drawable.pakkit_mark)
            .setContentTitle("Pakkit is ready to receive").setContentText("Private Wi-Fi session · ends in 10 minutes")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "End session", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTICE, notification)
        store.prefs.edit().remove("receiveUrl").putString("receiveState", "Connecting to Wi-Fi…").apply()
        expires = SystemClock.elapsedRealtime() + DURATION
        handler.postDelayed({ finish("Session expired. Start a new session.") }, DURATION)
        try {
            registered = true
            connectivity.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback, 25_000)
        } catch (_: Exception) { finish("Wi-Fi is unavailable. Check watch settings.") }
        return START_NOT_STICKY
    }
    private fun open(net: Network, properties: LinkProperties) {
        try {
            val address = properties.linkAddresses.map { it.address }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress } ?: error("A local Wi-Fi address is required")
            network = net
            val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(address, 0), 4) }
            listener = server
            token = android.util.Base64.encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) }, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            authority = "${address.hostAddress}:${server.localPort}"
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pakkit:receive").apply { acquire((expires - SystemClock.elapsedRealtime()).coerceAtLeast(1)) }
            store.prefs.edit().putString("receiveUrl", "http://$authority/#$token")
                .putString("receiveState", "Ready to receive").putLong("receiveExpires", System.currentTimeMillis() + (expires - SystemClock.elapsedRealtime())).apply()
            Thread({
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: IOException) { break }
                    socket.soTimeout = 15_000
                    sockets.add(socket)
                    try { workers.execute { socket.use { handle(it) }; sockets.remove(socket) } }
                    catch (_: RejectedExecutionException) { sockets.remove(socket); socket.close() }
                }
            }, "pakkit-listener").start()
        } catch (e: Exception) { android.util.Log.w("PakkitReceive", "Cannot open receiving session", e); finish("Could not open local Wi-Fi. Check the network and try again.") }
    }
    private fun handle(socket: Socket) {
        var replied = false
        fun respond(code: Int, type: String, bytes: ByteArray) {
            replied = true
            val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 409 -> "Conflict"; 404 -> "Not Found"; else -> "Error" }
            val headers = "HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n\r\n"
            socket.getOutputStream().apply { write(headers.toByteArray()); write(bytes); flush() }
        }
        fun json(code: Int, value: JSONObject) = respond(code, "application/json; charset=utf-8", value.toString().toByteArray())
        try {
            val input = BufferedInputStream(socket.getInputStream(), 8192)
            val request = HttpProtocol.read(input)
            require(!ended && SystemClock.elapsedRealtime() < expires)
            if (request.headers["host"] != authority) { json(403, JSONObject().put("error", "Open the address shown on your watch.")); return }
            if (request.method == "GET" && request.path in setOf("/", "/style.css", "/app.js")) {
                val file = when (request.path) { "/" -> "index.html"; else -> request.path.drop(1) }
                val type = when (file) { "index.html" -> "text/html; charset=utf-8"; "style.css" -> "text/css"; else -> "text/javascript" }
                respond(200, type, assets.open("web/$file").use { it.readBytes() }); return
            }
            if (!HttpProtocol.authorized(request.headers["authorization"], token)) { json(403, JSONObject().put("error", "Open the private link or scan the QR code on your watch.")); return }
            if (request.method == "GET" && request.path == "/api/status") {
                val job = store.read("incoming")
                val status = JSONObject().put("state", job?.optString("state") ?: "waiting")
                    .put("detail", job?.optString("detail") ?: "Your watch is ready.")
                    .put("label", job?.optJSONObject("apk")?.optString("label") ?: "")
                    .put("received", store.prefs.getLong("received", 0)).put("total", store.prefs.getLong("receiveTotal", 0))
                    .put("expiresIn", ((expires - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0))
                json(200, status); return
            }
            if (request.method != "POST" || request.path != "/api/upload") { json(404, JSONObject().put("error", "Not found")); return }
            if (request.headers["origin"] != "http://$authority") { json(403, JSONObject().put("error", "Use Pakkit’s local page.")); return }
            val length = HttpProtocol.uploadLength(request.headers)
            if (!uploading.compareAndSet(false, true)) { json(409, JSONObject().put("error", "A transfer is already in progress.")); return }
            var destination: File? = null
            try {
                if (store.read("incoming") != null) { json(409, JSONObject().put("error", "Finish or dismiss the current app on your watch first.")); return }
                require(Packages.free(this) > length * 3 + 20 * 1024 * 1024) { "Free some storage on your watch first." }
                val id = UUID.randomUUID().toString()
                val file = store.file(id); destination = file
                store.prefs.edit().putLong("receiveTotal", length).putLong("received", 0).putString("receiveState", "Receiving APK…").apply()
                var received = 0L
                var lastUpdate = 0L
                val limited = object : InputStream() {
                    override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 255 }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (received == length) return -1
                        val n = input.read(b, off, minOf(len.toLong(), length - received).toInt())
                        require(n > 0) { "Transfer interrupted. Choose the file again." }
                        received += n
                        if (SystemClock.elapsedRealtime() - lastUpdate > 250 || received == length) {
                            lastUpdate = SystemClock.elapsedRealtime()
                            store.prefs.edit().putLong("received", received).apply()
                        }
                        return n
                    }
                }
                val digest = file.outputStream().use { TransferRules.copyBounded(limited, it).second }
                store.prefs.edit().putString("receiveState", "Checking APK…").apply()
                val apk = Packages.inspect(this, file, digest)
                Packages.checkForWatch(apk, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toSet(), Packages.free(this))
                require(!ended) { "The receiving session ended. Try again." }
                store.write("incoming", JSONObject().put("id", id).put("created", System.currentTimeMillis())
                    .put("source", "local-browser").put("apk", apk.json()).put("state", "ready")
                    .put("detail", "Review this app and approve installation on your watch."))
                destination = null
                store.prefs.edit().putString("receiveState", "Ready to install").apply()
                json(200, JSONObject().put("label", apk.label).put("state", "ready"))
            } finally {
                destination?.delete(); uploading.set(false)
                store.prefs.edit().putLong("received", 0).putLong("receiveTotal", 0).apply()
            }
        } catch (e: Exception) {
            store.prefs.edit().putString("receiveState", "Ready to receive").apply()
            if (!replied) runCatching { json(400, JSONObject().put("error", e.message?.take(180) ?: "Transfer interrupted. Try again.")) }
        }
    }
    private fun finish(message: String) {
        if (ended) return
        ended = true
        store.prefs.edit().remove("receiveUrl").putString("receiveState", message).apply()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        ended = true
        active = false
        handler.removeCallbacksAndMessages(null)
        listener?.close()
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
        wake?.let { if (it.isHeld) it.release() }
        store.prefs.edit().remove("receiveUrl").putLong("received", 0).putLong("receiveTotal", 0).apply()
        super.onDestroy()
    }
}
