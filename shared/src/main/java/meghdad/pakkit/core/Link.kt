package meghdad.pakkit.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Link {
    const val ROOT = "/pakkit/"
    const val CAPABILITY = "pakkit_watch_v1"
    fun isWatch(c: Context) = c.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    fun settings(c: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
    fun installer(c: Context): Boolean = Intent(Intent.ACTION_INSTALL_PACKAGE)
        .setDataAndType(Uri.parse("content://meghdad.pakkit/probe.apk"), "application/vnd.android.package-archive")
        .resolveActivity(c.packageManager) != null
    fun capability(c: Context, nonce: String = "") {
        val node = Tasks.await(Wearable.getNodeClient(c).localNode, 15, TimeUnit.SECONDS)
        val request = PutDataMapRequest.create("${ROOT}cap/${node.id}")
        request.dataMap.apply {
            putString("nonce", nonce); putLong("time", System.currentTimeMillis()); putInt("sdk", Build.VERSION.SDK_INT)
            putStringArrayList("abis", ArrayList(Build.SUPPORTED_ABIS.toList()))
            putLong("free", Packages.free(c)); putBoolean("installer", installer(c))
            putBoolean("settings", settings(c).resolveActivity(c.packageManager) != null)
            putBoolean("allowed", c.packageManager.canRequestPackageInstalls())
        }
        Tasks.await(Wearable.getDataClient(c).putDataItem(request.asPutDataRequest().setUrgent()), 20, TimeUnit.SECONDS)
    }
    @Synchronized
    fun status(c: Context, job: JSONObject, state: String, detail: String, persist: Boolean = true) {
        val store = Store(c)
        val revision = store.prefs.getLong("statusRevision", 0) + 1
        store.prefs.edit().putLong("statusRevision", revision).commit()
        val updated = JSONObject(job.toString()).put("state", state).put("detail", detail).put("revision", revision)
        if (persist) Store(c).write("incoming", updated)
        Store(c).history(job.getString("id"), job.getJSONObject("apk").getString("label"), state, detail)
        val req = PutDataMapRequest.create("${ROOT}status/${job.getString("id")}")
        req.dataMap.apply {
            putString("id", job.getString("id")); putString("target", job.getString("source"))
            putString("state", state); putString("detail", detail.take(400)); putLong("time", System.currentTimeMillis()); putLong("revision", revision)
        }
        // A durable worker retries publication if connectivity or Play services is temporarily unavailable.
        androidx.work.WorkManager.getInstance(c).enqueueUniqueWork("status-${job.getString("id")}",
            androidx.work.ExistingWorkPolicy.REPLACE, androidx.work.OneTimeWorkRequestBuilder<StatusWorker>()
                .setInputData(androidx.work.workDataOf("path" to req.uri.path, "map" to req.dataMap.toByteArray())).build())
    }
    fun delete(c: Context, uri: Uri) = Wearable.getDataClient(c).deleteDataItems(uri)
}

class StatusWorker(c: Context, p: androidx.work.WorkerParameters) : androidx.work.Worker(c, p) {
    override fun doWork(): Result { return try {
        val req = PutDataMapRequest.create(requireNotNull(inputData.getString("path")))
        req.dataMap.putAll(DataMap.fromByteArray(requireNotNull(inputData.getByteArray("map"))))
        val current = Store(applicationContext).read("incoming")
        if (current != null && current.optString("id") == req.dataMap.getString("id") && current.optLong("revision") > req.dataMap.getLong("revision")) return Result.success()
        Tasks.await(Wearable.getDataClient(applicationContext).putDataItem(req.asPutDataRequest().setUrgent()), 30, TimeUnit.SECONDS)
        Result.success()
    } catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }
}
