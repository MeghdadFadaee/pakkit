package meghdad.pakkit.transfer

import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import meghdad.pakkit.apk.requireUser

/** Deliberately small HTTP/1.1 subset: one request per socket, no pipelining or chunked uploads. */
object HttpProtocol {
    data class Request(val method: String, val path: String, val headers: Map<String, String>)

    fun read(input: InputStream, clock: () -> Long = System::nanoTime): Request {
        val started = clock()
        var total = 0
        fun line(): String {
            val out = StringBuilder()
            while (true) {
                require(clock() - started <= 5_000_000_000L) { "Request headers stalled" }
                val b = input.read()
                require(b >= 0 && ++total <= 8192) { "Invalid request headers" }
                if (b == 13) {
                    require(input.read() == 10)
                    total++
                    return out.toString()
                }
                require(b in 32..126 && out.length < 4096) { "Invalid request headers" }
                out.append(b.toChar())
            }
        }
        val first = line().split(' ')
        require(first.size == 3 && first[2] == "HTTP/1.1" && first[0] in setOf("GET", "POST"))
        require(first[1].startsWith('/') && !first[1].startsWith("//"))
        val headers = mutableMapOf<String, String>()
        while (true) {
            val next = line()
            if (next.isEmpty()) break
            val separator = next.indexOf(':')
            require(separator > 0)
            val name = next.substring(0, separator).lowercase()
            require(name.matches(Regex("[a-z0-9-]+")) && name !in headers)
            headers[name] = next.substring(separator + 1).trim()
        }
        require("transfer-encoding" !in headers && "expect" !in headers)
        return Request(first[0], first[1], headers)
    }

    /** Accept only this listener's literal address/port, including equivalent IPv6 spellings. */
    fun sameAuthority(authority: String?, address: InetAddress, port: Int): Boolean = runCatching {
        if (authority == null) return false
        val uri = URI("http://$authority")
        if (
            uri.rawUserInfo != null ||
                !uri.rawPath.isNullOrEmpty() ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                uri.port != port
        )
            return false
        val host = uri.host?.removeSurrounding("[", "]") ?: return false
        val parsed =
            if (':' in host && host.matches(Regex("[0-9a-fA-F:.]+"))) {
                InetAddress.getByName(
                    host
                ) // A colon-containing numeric literal cannot trigger DNS.
            } else {
                val parts = host.split('.')
                if (
                    parts.size != 4 ||
                        parts.any {
                            it.isEmpty() ||
                                !it.all(Char::isDigit) ||
                                (it.toIntOrNull() ?: -1) !in 0..255
                        }
                )
                    return false
                InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray())
            }
        parsed == address
    }
        .getOrDefault(false)

    fun uploadLength(headers: Map<String, String>): Long {
        require(headers["content-type"] == "application/octet-stream") { "Send a complete APK" }
        val raw = headers["content-length"] ?: error("Content length required")
        require(raw.matches(Regex("[0-9]+"))) { "Invalid content length" }
        val size = raw.toLongOrNull() ?: error("Invalid content length")
        requireUser(size > 0) { "This file is empty. Choose another APK." }
        return size
    }
}
