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
                        "Couldn’t open approval. Try again.",
                    )
                    return
                }
                PendingApproval.id = apk.id
                PendingApproval.intent = confirmation
                store.update(
                    apk.id,
                    InstallState.CONFIRM,
                    "Ready for your approval.",
                )
                notifyApproval(context)
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
                            "Your watch blocked this app. Look for it on Google Play."
                        PackageInstaller.STATUS_FAILURE_CONFLICT ->
                            "A different version is already installed. Get a matching update from the app’s source."
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                            "This app doesn’t work on your watch. Try a version made for it."
                        PackageInstaller.STATUS_FAILURE_INVALID ->
                            "Your watch couldn’t verify this app. Get a fresh copy from its source."
                        PackageInstaller.STATUS_FAILURE_STORAGE ->
                            "Free some space on your watch, then try again."
                        else ->
                            "Couldn’t finish installing. Try again or find the app on Google Play."
                    }
                store.update(apk.id, InstallState.FAILED, detail)
                manager.cancel(NOTIFICATION)
            }
        }
    }

    companion object {
        const val NOTIFICATION = 42

        fun notifyApproval(context: Context) {
            val apk = PackageStore(context).pending() ?: return
            if (apk.state != InstallState.CONFIRM || PendingApproval.id != apk.id) return
            val sessionId = apk.sessionId ?: return
            if (PendingApproval.intent == null) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    "installation",
                    "App installation",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
            if (
                Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            ) {
                val open = Intent(context, WatchActivity::class.java)
                val pending =
                    PendingIntent.getActivity(
                        context,
                        sessionId,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                manager.notify(
                    NOTIFICATION,
                    Notification.Builder(context, "installation")
                        .setSmallIcon(R.drawable.pakkit_mark)
                        .setColor(WatchDesign.PURPLE)
                        .setContentTitle("Your app is ready to install")
                        .setContentText("Open Pakkit to give it the go-ahead.")
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }
    }
}
