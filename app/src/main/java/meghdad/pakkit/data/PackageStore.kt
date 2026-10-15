package meghdad.pakkit.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.apk.PendingApk
import meghdad.pakkit.apk.UserMessage
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
                )
            )
            true
        }

    fun dismiss(id: String) =
        synchronized(lock) {
            if (pending()?.id != id) return@synchronized
            file(id).delete()
            check(preferences.edit().remove(PENDING_KEY).commit())
        }

    /** Called at process startup, before any transfer can own an uncommitted file. */
    fun clean() =
        synchronized(lock) {
            val keep = pending()?.id
            directory.listFiles()?.forEach { file ->
                if (file.name != "$keep.apk") file.delete()
            }
        }

    private fun persist(apk: PendingApk) {
        val previous = preferences.getString(PENDING_KEY, null)
        if (!preferences.edit().putString(PENDING_KEY, apk.json().toString()).commit()) {
            // SharedPreferences updates memory before its disk write; restore both views on
            // failure.
            if (!preferences.edit().putString(PENDING_KEY, previous).commit()) {
                android.util.Log.w("PakkitStore", "Could not persist review rollback")
            }
            throw UserMessage(
                "Couldn’t save this app. Free some space on your watch and try again."
            )
        }
    }

    companion object {
        const val PENDING_KEY = "incoming"
        private val lock = Any()
    }
}
