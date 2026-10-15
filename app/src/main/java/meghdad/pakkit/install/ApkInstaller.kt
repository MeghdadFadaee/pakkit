package meghdad.pakkit.install

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.apk.PackageInspector
import meghdad.pakkit.apk.PendingApk
import meghdad.pakkit.data.PackageStore

/** Called only after the watch's explicit Install action, on a worker thread. */
class ApkInstaller(private val context: Context) {
    private val store = PackageStore(context)

    fun install(apk: PendingApk) {
        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null
        try {
            PendingApproval.clear()
            context
                .getSystemService(NotificationManager::class.java)
                .cancel(InstallReceiver.NOTIFICATION)
            check(context.packageManager.canRequestPackageInstalls()) {
                "Allow Pakkit to request installs first."
            }
            require(ApkRules.fresh(apk.created, System.currentTimeMillis())) {
                "This APK expired. Upload it again."
            }
            val file = store.file(apk.id)
            val digest = file.inputStream().use { ApkRules.copyBounded(it).second }
            require(digest == apk.apk.sha256) { "This APK changed. Upload it again." }
            val inspected = PackageInspector.inspect(context, file, digest)
            PackageInspector.checkForWatch(
                inspected,
                Build.VERSION.SDK_INT,
                Build.SUPPORTED_ABIS.toSet(),
                PackageInspector.free(context),
            )
            apk.sessionId?.let { runCatching { installer.abandonSession(it) } }
            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply {
                        setAppPackageName(inspected.packageName)
                        setSize(file.length())
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                        if (Build.VERSION.SDK_INT >= 33)
                            setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
                    }
            sessionId = installer.createSession(params)
            check(
                store.update(
                    apk.id,
                    InstallState.INSTALLING,
                    "Waiting for the watch’s system installer.",
                    sessionId,
                ),
            ) {
                "This package review is no longer available."
            }
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output, 64 * 1024) }
                    session.fsync(output)
                }
                val callback = Intent(context, InstallReceiver::class.java).putExtra("job", apk.id)
                val pending =
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        callback,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            sessionId?.let { runCatching { installer.abandonSession(it) } }
            store.update(
                apk.id,
                InstallState.FAILED,
                error.message ?: "Could not open the system installer. Use Google Play.",
            )
        }
    }

    companion object {
        fun settings(context: Context) =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
    }
}
