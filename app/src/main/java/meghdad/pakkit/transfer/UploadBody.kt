package meghdad.pakkit.transfer

import java.io.InputStream
import meghdad.pakkit.apk.requireUser

/** Exact-length stream: truncation is an error, and extra bytes cannot become another request. */
internal class UploadBody(
    private val input: InputStream,
    private val length: Long,
    private val progress: (Long) -> Unit,
    private val clock: () -> Long = System::nanoTime,
) : InputStream() {
    private var received = 0L
    private var lastUpdate = clock()

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 255
    }

    override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
        require(offset >= 0 && count >= 0 && offset <= bytes.size - count)
        if (count == 0) return 0
        if (received == length) return -1
        val read = input.read(bytes, offset, minOf(count.toLong(), length - received).toInt())
        requireUser(read > 0) { "The transfer stopped early. Choose the file and try again." }
        received += read
        val now = clock()
        if (now - lastUpdate >= 250_000_000 || received == length) {
            lastUpdate = now
            progress(received)
        }
        return read
    }
}
