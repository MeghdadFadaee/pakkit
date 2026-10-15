package meghdad.pakkit.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.apk.PackageInspector
import meghdad.pakkit.apk.PendingApk
import meghdad.pakkit.apk.requireUser
import meghdad.pakkit.apk.userMessage
import meghdad.pakkit.data.PackageStore
import org.json.JSONObject

/** Bounded HTTP endpoint. Wi-Fi acquisition, notifications and power belong to ReceiveService. */
class LocalHttpServer(
    private val context: Context,
    private val address: InetAddress,
    private val owner: Any,
    private val access: BrowserAccess = BrowserAccess(),
) : Closeable {
    private val store = PackageStore(context)
    private val listener =
        ServerSocket().also { socket ->
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(address, PORT), 4)
            } catch (error: Exception) {
                runCatching { socket.close() }
                throw error
            }
        }
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val uploading = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val commitLock = Any()
    private val host =
        address.hostAddress.orEmpty().substringBefore('%').let {
            if (address is Inet6Address) "[$it]" else it
        }
    private val authority = "$host:${listener.localPort}"
    val url = "http://$authority/"

    fun start() {
        Thread(
                {
                    while (!closed.get()) {
                        val socket =
                            try {
                                listener.accept()
                            } catch (_: IOException) {
                                break
                            }
                        socket.soTimeout = 15_000
                        sockets.add(socket)
                        if (closed.get()) {
                            sockets.remove(socket)
                            socket.close()
                            break
                        }
                        try {
                            workers.execute {
                                try {
                                    socket.use { handle(it) }
                                } finally {
                                    sockets.remove(socket)
                                }
                            }
                        } catch (_: RejectedExecutionException) {
                            sockets.remove(socket)
                            socket.close()
                        }
                    }
                },
                "pakkit-http",
            )
            .start()
    }

    private fun handle(socket: Socket) {
        val response = Response(socket)
        try {
            val input = BufferedInputStream(socket.getInputStream(), 8192)
            socket.soTimeout = 5_000
            val request = HttpProtocol.read(input)
            socket.soTimeout = 15_000
            requireUser(!closed.get()) { "Receiving has stopped. Open Pakkit to start again." }
            if (!HttpProtocol.sameAuthority(request.headers["host"], address, PORT)) {
                response.error(403, "Open the address shown on your watch.")
                return
            }
            if (request.method == "GET" && request.path in ASSETS) {
                val asset = ASSETS.getValue(request.path)
                response.send(
                    200,
                    asset.second,
                    context.assets.open("web/${asset.first}").use { it.readBytes() },
                )
                return
            }
            if (request.method == "POST" && !validOrigin(request.headers["origin"])) {
                response.error(403, "Open the address shown on your watch to send an app.")
                return
            }
            val credential = BrowserAccess.credential(request.headers["authorization"])
            if (request.method == "POST" && request.path in setOf("/api/connect", "/api/retry")) {
                if (request.path == "/api/retry") access.retry(credential)
                val connection =
                    access.connect(
                        credential,
                        BrowserAccess.label(request.headers["user-agent"].orEmpty()),
                        socket.inetAddress.hostAddress.orEmpty(),
                    )
                response.json(
                    200,
                    JSONObject().put("connection", connection.state.name.lowercase()).apply {
                        connection.credential?.let { put("credential", it) }
                    },
                )
                return
            }
            val browser = access.state(credential)
            if (browser != BrowserAccess.State.APPROVED) {
                response.json(403, JSONObject().put("connection", browser.name.lowercase()))
                return
            }
            when {
                request.method == "GET" && request.path == "/api/status" ->
                    response.json(200, status())
                request.method == "POST" && request.path == "/api/upload" ->
                    upload(request, input, response)
                else -> response.error(404, "Not found")
            }
        } catch (error: Exception) {
            if (!response.sent)
                runCatching {
                    response.error(
                        400,
                        error.userMessage("Your app didn’t arrive. Choose the file and try again."),
                    )
                }
        }
    }

    private fun validOrigin(origin: String?): Boolean =
        origin?.startsWith("http://") == true &&
            HttpProtocol.sameAuthority(origin.removePrefix("http://"), address, PORT)

    private fun status(): JSONObject {
        val pending = store.pending()
        val live = Receiving.state
        return JSONObject()
            .put("state", pending?.state?.wireValue ?: "waiting")
            .put("detail", pending?.detail ?: live.detail)
            .put("label", pending?.apk?.label.orEmpty())
            .put("received", live.received)
            .put("total", live.total)
            .put("connection", "approved")
    }

    // The read-idle socket timeout and close() bound stalled I/O; no total transfer deadline.
    // Release in finally also covers validation failures, cancellation and running out of space.
    @SuppressLint("WakelockTimeout")
    private fun upload(request: HttpProtocol.Request, input: InputStream, response: Response) {
        val length = HttpProtocol.uploadLength(request.headers)
        if (!uploading.compareAndSet(false, true)) {
            response.error(409, "A transfer is already in progress.")
            return
        }
        var partial: File? = null
        val wake =
            context
                .getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pakkit:upload")
        try {
            store
                .pending()
                ?.takeIf { it.state in setOf(InstallState.INSTALLED, InstallState.FAILED) }
                ?.let { store.dismiss(it.id) }
            if (store.pending() != null) {
                response.error(409, "Finish with the app on your watch before sending another.")
                return
            }
            PackageInspector.requireSpace(length, PackageInspector.free(context))
            wake.acquire()
            try {
                val id = UUID.randomUUID().toString()
                val file = store.file(id)
                partial = file
                updateLive {
                    it.copy(detail = "Your app is on its way…", total = length, received = 0)
                }
                val body =
                    UploadBody(
                        input,
                        length,
                        progress = { received ->
                            updateLive { it.copy(received = received) }
                        },
                    )
                val digest = file.outputStream().use { ApkRules.copyAndHash(body, it).second }
                updateLive { it.copy(detail = "Getting your app ready…") }
                val apk = PackageInspector.inspect(context, file, digest)
                PackageInspector.checkForWatch(
                    apk,
                    Build.VERSION.SDK_INT,
                    Build.SUPPORTED_ABIS.toSet(),
                )
                synchronized(commitLock) {
                    requireUser(!closed.get()) {
                        "Receiving has stopped. Start again to send this app."
                    }
                    store.stage(PendingApk(id, System.currentTimeMillis(), apk))
                }
                partial = null
                updateLive { it.copy(detail = "Ready to install") }
                response.json(200, JSONObject().put("label", apk.label).put("state", "ready"))
            } finally {
                wake.release()
            }
        } finally {
            partial?.delete()
            uploading.set(false)
            updateLive {
                it.copy(
                    received = 0,
                    total = 0,
                    detail =
                        if (store.pending() == null) "Ready to receive" else "Ready to install",
                )
            }
        }
    }

    private fun updateLive(change: (Receiving.State) -> Receiving.State) {
        synchronized(commitLock) {
            if (!closed.get()) Receiving.update(owner, change)
        }
    }

    override fun close() {
        synchronized(commitLock) {
            if (!closed.compareAndSet(false, true)) return
        }
        try {
            listener.close()
        } finally {
            sockets.forEach { runCatching { it.close() } }
            workers.shutdownNow()
        }
    }

    private class Response(private val socket: Socket) {
        var sent = false
            private set

        fun error(code: Int, message: String) = json(code, JSONObject().put("error", message))

        fun json(code: Int, value: JSONObject) =
            send(
                code,
                "application/json; charset=utf-8",
                value.toString().toByteArray(Charsets.UTF_8),
            )

        fun send(code: Int, type: String, bytes: ByteArray) {
            sent = true
            val reason =
                when (code) {
                    200 -> "OK"
                    400 -> "Bad Request"
                    403 -> "Forbidden"
                    404 -> "Not Found"
                    409 -> "Conflict"
                    else -> "Error"
                }
            val headers =
                "HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n" +
                    "Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                    "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n\r\n"
            socket.getOutputStream().apply {
                write(headers.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
    }

    companion object {
        const val PORT = 8787
        private val ASSETS =
            mapOf(
                "/" to ("index.html" to "text/html; charset=utf-8"),
                "/style.css" to ("style.css" to "text/css; charset=utf-8"),
                "/app.js" to ("app.js" to "text/javascript; charset=utf-8"),
            )
    }
}
