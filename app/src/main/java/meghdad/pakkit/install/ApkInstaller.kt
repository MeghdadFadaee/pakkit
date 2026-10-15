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
import meghdad.pakkit.apk.requireUser
import meghdad.pakkit.apk.userMessage
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
            requireUser(context.packageManager.canRequestPackageInstalls()) {
                "Allow Pakkit to install apps, then try again."
            }
            requireUser(ApkRules.fresh(apk.created, System.currentTimeMillis())) {
                "This file has expired. Send it again to install."
            }
            val file = store.file(apk.id)
            val digest = file.inputStream().use { ApkRules.copyBounded(it).second }
            requireUser(digest == apk.apk.sha256) {
                "This file has changed. Send a fresh copy to install."
            }
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
                    "Getting ready to install…",
                    sessionId,
                )
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
            android.util.Log.w("PakkitInstall", "Installation preparation failed", error)
            sessionId?.let { runCatching { installer.abandonSession(it) } }
            store.update(
                apk.id,
                InstallState.FAILED,
                error.userMessage("Couldn’t start installation. Try again."),
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
