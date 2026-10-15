package meghdad.pakkit.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("pakkit", Context.MODE_PRIVATE)
    val directory = File(context.filesDir, "packages").apply { mkdirs() }
    fun file(id: String): File { require(TransferRules.validId(id)); return File(directory, "$id.apk") }
    fun read(key: String): JSONObject? = runCatching { JSONObject(prefs.getString(key, null) ?: return null) }.getOrNull()
    fun write(key: String, value: JSONObject) { check(prefs.edit().putString(key, value.toString()).commit()) }
    fun clear(key: String) { prefs.edit().remove(key).commit() }
    fun history(id: String, label: String, state: String, detail: String) = synchronized(Store::class.java) {
        val old = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray().put(JSONObject().put("id", id).put("label", label).put("state", state)
            .put("detail", detail).put("time", System.currentTimeMillis()))
        for (i in 0 until old.length()) if (old.getJSONObject(i).optString("id") != id && next.length() < 20) next.put(old.getJSONObject(i))
        prefs.edit().putString("history", next.toString()).commit()
    }
    fun clean() {
        val keep = listOfNotNull(read("outgoing")?.optString("id"), read("incoming")?.optString("id"))
        directory.listFiles()?.forEach {
            if (it.name.substringBefore('.') !in keep && System.currentTimeMillis() - it.lastModified() > TransferRules.TTL_MS) it.delete()
        }
    }
}
