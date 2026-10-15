package meghdad.pakkit.apk

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object ApkRules {
    fun validId(id: String) =
        Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(id)

    fun compatibleAbis(packageAbis: Set<String>, deviceAbis: Set<String>) =
        packageAbis.isEmpty() || packageAbis.any { it in deviceAbis }

    fun copyAndHash(
        input: InputStream,
        output: OutputStream? = null,
    ): Pair<Long, String> {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            hash.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        requireUser(total > 0) { "This file is empty. Choose another APK." }
        return total to hash.digest().joinToString("") { "%02x".format(it) }
    }
}
