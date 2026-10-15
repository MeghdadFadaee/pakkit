package meghdad.pakkit.transfer

import java.io.InputStream
import java.security.MessageDigest
import meghdad.pakkit.apk.ApkRules

/** Deliberately small HTTP/1.1 subset: one request per socket, no pipelining or chunked uploads. */
object HttpProtocol {
    data class Request(val method: String, val path: String, val headers: Map<String, String>)

    fun read(input: InputStream): Request {
        var total = 0
        fun line(): String {
            val out = StringBuilder()
            while (true) {
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

    fun authorized(header: String?, token: String): Boolean =
        MessageDigest.isEqual(
            (header ?: "").toByteArray(Charsets.UTF_8),
            "Bearer $token".toByteArray(Charsets.UTF_8),
        )

    fun uploadLength(headers: Map<String, String>): Long {
        require(headers["content-type"] == "application/octet-stream") { "Send a complete APK" }
        val raw = headers["content-length"] ?: error("Content length required")
        require(raw.matches(Regex("[0-9]+"))) { "Invalid content length" }
        val size = raw.toLongOrNull() ?: error("Invalid content length")
        require(size in 1..ApkRules.MAX_BYTES) { "Choose an APK up to 50 MiB" }
        return size
    }
}
