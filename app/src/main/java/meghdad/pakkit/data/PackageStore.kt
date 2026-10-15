package meghdad.pakkit.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.apk.PendingApk
import org.json.JSONObject

/** One private package and its review, stored atomically under a stable on-disk schema. */
class PackageStore(context: Context) {
    val preferences: SharedPreferences =
        context.getSharedPreferences("pakkit", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "packages").apply { mkdirs() }

    fun file(id: String): File {
        require(ApkRules.validId(id))
        return File(directory, "$id.apk")
    }

    fun pending(): PendingApk? =
        preferences.getString(PENDING_KEY, null)?.let { value ->
            runCatching { PendingApk.from(JSONObject(value)) }.getOrNull()
        }

    fun stage(apk: PendingApk) =
        synchronized(lock) {
            check(pending() == null) { "Finish or dismiss the current app on your watch first." }
            persist(apk)
        }

    fun update(id: String, state: InstallState, detail: String, sessionId: Int? = null): Boolean =
        synchronized(lock) {
            val current = pending()?.takeIf { it.id == id } ?: return false
            persist(
                current.copy(
                    state = state,
                    detail = detail,
                    sessionId = sessionId ?: current.sessionId,
                ),
            )
            true
        }

    fun dismiss(id: String) =
        synchronized(lock) {
            if (pending()?.id != id) return@synchronized
            file(id).delete()
            check(preferences.edit().remove(PENDING_KEY).commit())
        }

    /** Expiry prevents installation; disk cleanup is intentionally lazy, on the next app launch. */
    fun clean(now: Long = System.currentTimeMillis()) =
        synchronized(lock) {
            pending()?.takeUnless { ApkRules.fresh(it.created, now) }?.let { dismiss(it.id) }
            val keep = pending()?.id
            directory.listFiles()?.forEach { file ->
                if (file.name != "$keep.apk" && now - file.lastModified() >= ApkRules.TTL_MS)
                    file.delete()
            }
        }

    private fun persist(apk: PendingApk) {
        check(preferences.edit().putString(PENDING_KEY, apk.json().toString()).commit())
    }

    companion object {
        const val PENDING_KEY = "incoming"
        private val lock = Any()
    }
}
