package meghdad.pakkit.apk

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object ApkRules {
    const val MAX_BYTES = 50L * 1024 * 1024
    const val TTL_MS = 24L * 60 * 60 * 1000

    fun validId(id: String) =
        Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(id)

    fun fresh(created: Long, now: Long) = created <= now + 60_000 && now - created < TTL_MS

    fun compatibleAbis(packageAbis: Set<String>, deviceAbis: Set<String>) =
        packageAbis.isEmpty() || packageAbis.any { it in deviceAbis }

    fun copyBounded(
        input: InputStream,
        output: OutputStream? = null,
        max: Long = MAX_BYTES,
    ): Pair<Long, String> {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            requireUser(total <= max) { "Choose an APK up to 50 MB." }
            hash.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        requireUser(total > 0) { "This file is empty. Choose another APK." }
        return total to hash.digest().joinToString("") { "%02x".format(it) }
    }
}
