package meghdad.pakkit.core

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import java.util.concurrent.TimeUnit

class SendWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val store = Store(applicationContext)
        val job = store.read("outgoing") ?: return Result.failure()
        if (job.optString("id") != inputData.getString("id") || job.optString("state") != "sending") return Result.success()
        return try {
            require(TransferRules.fresh(job.getLong("created"), System.currentTimeMillis())) { "This transfer expired. Choose the file again." }
            val req = PutDataMapRequest.create("/pakkit/offer/${job.getString("id")}")
            req.dataMap.apply {
                putString("id", job.getString("id")); putString("target", job.getString("target"))
                putLong("created", job.getLong("created")); putString("apk", job.getJSONObject("apk").toString())
                putAsset("file", Asset.createFromUri(Uri.fromFile(store.file(job.getString("id")))))
            }
            val item = Tasks.await(Wearable.getDataClient(applicationContext).putDataItem(req.asPutDataRequest().setUrgent()), 45, TimeUnit.SECONDS)
            // Do not overwrite an acknowledgement that arrived while publication was in flight.
            synchronized(Store::class.java) {
                val latest = store.read("outgoing")
                if (latest?.optString("id") == job.getString("id")) {
                    latest.put("offer", item.uri.toString())
                    if (latest.optString("state") == "sending") latest.put("state", "syncing")
                        .put("detail", "Syncing to your watch. Open Pakkit there to continue.")
                    store.write("outgoing", latest)
                }
            }
            WorkManager.getInstance(applicationContext).enqueueUniqueWork("expire-${job.getString("id")}", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ExpireWorker>().setInitialDelay(24, TimeUnit.HOURS)
                    .setInputData(workDataOf("id" to job.getString("id"), "uri" to item.uri.toString())).build())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else {
                synchronized(Store::class.java) {
                    val latest = store.read("outgoing")
                    if (latest?.optString("id") == job.getString("id") && latest.optString("state") == "sending")
                        store.write("outgoing", latest.put("state", "failed").put("detail", "Could not sync. Check your connection and choose the file again."))
                }
                Result.failure()
            }
        }
    }
}
class ExpireWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val store = Store(applicationContext)
        val id = inputData.getString("id") ?: return Result.failure()
        return try {
            inputData.getString("uri")?.let { Tasks.await(Link.delete(applicationContext, Uri.parse(it)), 30, TimeUnit.SECONDS) }
            store.file(id).delete()
            for (key in listOf("incoming", "outgoing")) {
                val job = store.read(key) ?: continue
                if (job.optString("id") == id && job.optString("state") !in setOf("installed", "failed", "declined")) {
                    if (key == "incoming") {
                        job.optInt("session", -1).takeIf { it >= 0 }?.let { runCatching { applicationContext.packageManager.packageInstaller.abandonSession(it) } }
                        applicationContext.getSystemService(android.app.NotificationManager::class.java).cancel(42)
                    }
                    if (key == "incoming") Link.status(applicationContext, job, "failed", "This APK expired. Send it again from your phone.")
                    else store.write(key, job.put("state", "failed").put("detail", "This transfer expired. Choose the file again."))
                }
            }
            store.clean()
            Result.success()
        } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
