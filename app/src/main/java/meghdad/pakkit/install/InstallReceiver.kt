package meghdad.pakkit.install

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import meghdad.pakkit.R
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.data.PackageStore
import meghdad.pakkit.ui.WatchActivity
import meghdad.pakkit.ui.WatchDesign

/** Explicit mutable installer callback, kept non-exported and matched to the active session. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = PackageStore(context)
        val apk = store.pending() ?: return
        if (
            apk.sessionId == null ||
                intent.getStringExtra("job") != apk.id ||
                intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != apk.sessionId
        )
            return
        if (apk.state !in setOf(InstallState.INSTALLING, InstallState.CONFIRM)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        when (
            val status =
                intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        ) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation == null) {
                    store.update(
                        apk.id,
                        InstallState.FAILED,
                        "The system did not provide an approval screen.",
                    )
                    return
                }
                PendingApproval.id = apk.id
                PendingApproval.intent = confirmation
                store.update(
                    apk.id,
                    InstallState.CONFIRM,
                    "Continue the system approval on your watch.",
                )
                manager.createNotificationChannel(
                    NotificationChannel(
                        "installation",
                        "Installation approval",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
                if (
                    Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                ) {
                    val open =
                        Intent(context, WatchActivity::class.java)
                            .putExtra("approval", confirmation)
                            .putExtra("job", apk.id)
                    val pending =
                        PendingIntent.getActivity(
                            context,
                            apk.sessionId,
                            open,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    manager.notify(
                        NOTIFICATION,
                        Notification.Builder(context, "installation")
                            .setSmallIcon(R.drawable.pakkit_mark)
                            .setColor(WatchDesign.PURPLE)
                            .setContentTitle("Approve installation")
                            .setContentText("Open Pakkit to continue on your watch.")
                            .setContentIntent(pending)
                            .setAutoCancel(true)
                            .build(),
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                PendingApproval.clear()
                store.update(apk.id, InstallState.INSTALLED, "Installed on your watch.")
                store.file(apk.id).delete()
                manager.cancel(NOTIFICATION)
            }
            else -> {
                PendingApproval.clear()
                val detail =
                    when (status) {
                        PackageInstaller.STATUS_FAILURE_ABORTED ->
                            "Installation was cancelled. You can try again."
                        PackageInstaller.STATUS_FAILURE_BLOCKED ->
                            "The watch’s security policy blocked this installation. Use Google Play or contact the app developer."
                        PackageInstaller.STATUS_FAILURE_CONFLICT ->
                            "This APK conflicts with an installed app or its signature. Ask the developer for a compatible version."
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                            "This APK is not compatible with your watch."
                        PackageInstaller.STATUS_FAILURE_INVALID ->
                            "The system rejected this APK’s format or signature."
                        PackageInstaller.STATUS_FAILURE_STORAGE ->
                            "Your watch needs more free storage."
                        else ->
                            "The system could not finish installing. Try again or use Google Play."
                    }
                store.update(apk.id, InstallState.FAILED, detail)
                manager.cancel(NOTIFICATION)
            }
        }
    }

    companion object {
        const val NOTIFICATION = 42
    }
}
