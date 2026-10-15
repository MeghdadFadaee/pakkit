package meghdad.pakkit.core

import android.net.Uri
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class LinkService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (Link.isWatch(this) && event.path == "/pakkit/probe" && event.data.size <= 100) {
            runCatching { Link.capability(this, event.data.toString(Charsets.UTF_8)) }
        }
    }
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            when {
                Link.isWatch(this) && item.uri.path?.startsWith("/pakkit/offer/") == true -> {
                    WorkManager.getInstance(this).enqueueUniqueWork("receive-${item.uri.lastPathSegment}", ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<ReceiveWorker>().setInputData(workDataOf("uri" to item.uri.toString())).build())
                }
                !Link.isWatch(this) && item.uri.path?.startsWith("/pakkit/status/") == true -> acceptStatus(item)
            }
        }
    }
    private fun acceptStatus(item: DataItem) = synchronized(Store::class.java) {
        runCatching {
            val map = DataMapItem.fromDataItem(item).dataMap
            val store = Store(this)
            val job = store.read("outgoing") ?: return@runCatching
            if (map.getString("id") != job.optString("id") || item.uri.host != job.optString("target")) return@runCatching
            val revision = map.getLong("revision")
            if (revision <= job.optLong("revision", -1)) return@runCatching
            val state = map.getString("state") ?: return@runCatching
            if (state !in setOf("receiving", "ready", "confirm", "installing", "installed", "failed", "declined")) return@runCatching
            job.put("revision", revision).put("state", state).put("detail", map.getString("detail").orEmpty().take(400))
            store.write("outgoing", job)
            store.history(job.getString("id"), job.getJSONObject("apk").getString("label"), state, job.getString("detail"))
            if (state in setOf("installed", "failed", "declined")) {
                store.file(job.getString("id")).delete()
                job.optString("offer").takeIf { it.isNotEmpty() }?.let { Link.delete(this, Uri.parse(it)) }
                Link.delete(this, item.uri)
            }
        }
    }

}

class ReceiveWorker(c: android.content.Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val c = applicationContext
        val store = Store(c)
        var job: JSONObject? = null
        var part: File? = null
        var itemUri: Uri? = null
        return try {
            if (!Link.isWatch(c)) return Result.failure()
            val uri = Uri.parse(requireNotNull(inputData.getString("uri")))
            itemUri = uri
            val item = Tasks.await(Wearable.getDataClient(c).getDataItem(uri), 30, TimeUnit.SECONDS) ?: return Result.success()
            val map = DataMapItem.fromDataItem(item).dataMap
            val local = Tasks.await(Wearable.getNodeClient(c).localNode, 15, TimeUnit.SECONDS)
            if (map.getString("target") != local.id) return Result.success()
            val id = requireNotNull(map.getString("id"))
            require(TransferRules.validId(id) && uri.lastPathSegment == id)
            val expected = Apk.from(JSONObject(requireNotNull(map.getString("apk"))))
            job = JSONObject().put("id", id).put("source", uri.host).put("apk", expected.json()).put("created", map.getLong("created"))
            if (store.prefs.getStringSet("received", emptySet()).orEmpty().contains(id)) return Result.success()
            val old = store.read("incoming")
            if (old?.optString("id") == id && old.optString("state") !in setOf("receiving")) return Result.success()
            if (old != null && old.optString("id") != id && old.optString("state") !in setOf("installed", "failed", "declined")) {
                Link.status(c, job, "failed", "Finish or dismiss the current APK on your watch first.", persist = false)
                Link.delete(c, uri)
                return Result.success()
            }
            require(TransferRules.fresh(map.getLong("created"), System.currentTimeMillis())) { "This transfer expired. Send it again from your phone." }
            require(expected.bytes in 1..TransferRules.MAX_BYTES)
            require(Link.installer(c) && (c.packageManager.canRequestPackageInstalls() || Link.settings(c).resolveActivity(c.packageManager) != null)) { "This watch does not expose the system installation controls. Use Google Play on your watch." }
            Packages.checkForWatch(expected, android.os.Build.VERSION.SDK_INT, android.os.Build.SUPPORTED_ABIS.toSet(), Packages.free(c))
            WorkManager.getInstance(c).enqueueUniqueWork("expire-$id", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ExpireWorker>().setInitialDelay((map.getLong("created") + TransferRules.TTL_MS - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("id" to id)).build())
            Link.status(c, job, "receiving", "Verifying the APK on your watch")
            part = File(store.directory, "$id.part")
            val asset = requireNotNull(map.getAsset("file"))
            val response = Tasks.await(Wearable.getDataClient(c).getFdForAsset(asset), 90, TimeUnit.SECONDS)
            val result = try {
                requireNotNull(response.inputStream).use { input -> part.outputStream().use { output -> TransferRules.copyBounded(input, output) } }
            } finally { response.release() }
            require(result.first == expected.bytes && result.second == expected.sha256) { "The file did not pass its integrity check. Send it again." }
            val actual = Packages.inspect(c, part, result.second)
            require(actual.packageName == expected.packageName) { "Package details did not match." }
            Packages.checkForWatch(actual, android.os.Build.VERSION.SDK_INT, android.os.Build.SUPPORTED_ABIS.toSet(), Packages.free(c))
            require(TransferRules.fresh(map.getLong("created"), System.currentTimeMillis())) { "This transfer expired. Send it again." }
            require(part.renameTo(store.file(id))) { "Could not save this APK." }
            job.put("apk", actual.json())
            Link.status(c, job, "ready", "Ready. Review and install on your watch.")
            store.prefs.edit().putStringSet("received", (store.prefs.getStringSet("received", emptySet()).orEmpty().toList().takeLast(99) + id).toSet()).commit()

            Link.delete(c, uri)
            store.clean()
            Result.success()
        } catch (e: Exception) {
            part?.delete()
            if (job != null && e !is IllegalArgumentException && e !is IllegalStateException && runAttemptCount < 3) {
                Result.retry()
            } else if (job != null) {
                runCatching { Link.status(c, job, "failed", e.message ?: "Could not receive this APK. Try again.") }
                itemUri?.let { Link.delete(c, it) }
                Result.failure()
            } else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
