package meghdad.pakkit.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import meghdad.pakkit.core.Store
import org.json.JSONObject

object LocalInstall {
    fun settings(c: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
    @Synchronized fun status(c: Context, job: JSONObject, state: String, detail: String) {
        val updated = JSONObject(job.toString()).put("state", state).put("detail", detail)
        Store(c).write("incoming", updated)
        Store(c).history(job.getString("id"), job.getJSONObject("apk").getString("label"), state, detail)
    }
}
