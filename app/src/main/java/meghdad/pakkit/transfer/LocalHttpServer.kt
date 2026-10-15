package meghdad.pakkit.transfer

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.PackageInspector
import meghdad.pakkit.apk.PendingApk
import meghdad.pakkit.data.PackageStore
import org.json.JSONObject

/** Bounded HTTP endpoint. Wi-Fi acquisition, notifications and power belong to ReceiveService. */
class LocalHttpServer(
    private val context: Context,
    address: Inet4Address,
    private val deadline: Long,
    private val owner: Any,
) : Closeable {
    private val store = PackageStore(context)
    private val listener =
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(address, 0), 4)
        }
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val uploading = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val token =
        Base64.encodeToString(
            ByteArray(24).also { SecureRandom().nextBytes(it) },
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    private val authority = "${address.hostAddress}:${listener.localPort}"
    val url = "http://$authority/#$token"

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
            val request = HttpProtocol.read(input)
            check(!closed.get() && SystemClock.elapsedRealtime() < deadline) {
                "The receiving session ended. Start a new one."
            }
            if (request.headers["host"] != authority) {
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
            if (!HttpProtocol.authorized(request.headers["authorization"], token)) {
                response.error(403, "Open the private link or scan the QR code on your watch.")
                return
            }
            when {
                request.method == "GET" && request.path == "/api/status" ->
                    response.json(200, status())
                request.method == "POST" && request.path == "/api/upload" -> {
                    if (request.headers["origin"] != "http://$authority")
                        response.error(403, "Use Pakkit’s local page.")
                    else upload(request, input, response)
                }
                else -> response.error(404, "Not found")
            }
        } catch (error: Exception) {
            if (!response.sent)
                runCatching {
                    response.error(
                        400,
                        error.message?.take(180) ?: "Transfer interrupted. Try again.",
                    )
                }
        }
    }

    private fun status(): JSONObject {
        val pending = store.pending()
        val live = Receiving.state
        return JSONObject()
            .put("state", pending?.state?.wireValue ?: "waiting")
            .put("detail", pending?.detail ?: live.detail)
            .put("label", pending?.apk?.label.orEmpty())
            .put("received", live.received)
            .put("total", live.total)
            .put("expiresIn", ((deadline - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0))
    }

    private fun upload(request: HttpProtocol.Request, input: InputStream, response: Response) {
        val length = HttpProtocol.uploadLength(request.headers)
        if (!uploading.compareAndSet(false, true)) {
            response.error(409, "A transfer is already in progress.")
            return
        }
        var partial: File? = null
        try {
            if (store.pending() != null) {
                response.error(409, "Finish or dismiss the current app on your watch first.")
                return
            }
            PackageInspector.requireSpace(length, PackageInspector.free(context))
            val id = UUID.randomUUID().toString()
            val file = store.file(id)
            partial = file
            Receiving.update(owner) {
                it.copy(detail = "Receiving APK…", total = length, received = 0)
            }
            val body =
                UploadBody(
                    input,
                    length,
                    progress = { received ->
                        Receiving.update(owner) { it.copy(received = received) }
                    },
                )
            val digest = file.outputStream().use { ApkRules.copyBounded(body, it).second }
            Receiving.update(owner) { it.copy(detail = "Checking APK…") }
            val apk = PackageInspector.inspect(context, file, digest)
            PackageInspector.checkForWatch(
                apk,
                Build.VERSION.SDK_INT,
                Build.SUPPORTED_ABIS.toSet(),
                PackageInspector.free(context),
            )
            check(!closed.get() && SystemClock.elapsedRealtime() < deadline) {
                "The receiving session ended. Try again."
            }
            store.stage(PendingApk(id, System.currentTimeMillis(), apk))
            partial = null
            Receiving.update(owner) { it.copy(detail = "Ready to install") }
            response.json(200, JSONObject().put("label", apk.label).put("state", "ready"))
        } finally {
            partial?.delete()
            uploading.set(false)
            Receiving.update(owner) {
                it.copy(
                    received = 0,
                    total = 0,
                    detail =
                        if (store.pending() == null) "Ready to receive" else "Ready to install",
                )
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
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
        private val ASSETS =
            mapOf(
                "/" to ("index.html" to "text/html; charset=utf-8"),
                "/style.css" to ("style.css" to "text/css; charset=utf-8"),
                "/app.js" to ("app.js" to "text/javascript; charset=utf-8"),
            )
    }
}
