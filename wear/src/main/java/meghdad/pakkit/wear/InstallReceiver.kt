package meghdad.pakkit.wear

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import meghdad.pakkit.core.*
import java.lang.ref.WeakReference

/** Only the installer can fill in this explicit, mutable PendingIntent. No exported receiver. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store=Store(context)
        val job=store.read("incoming") ?: return
        if(intent.getStringExtra("job")!=job.optString("id") || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)!=job.optInt("session",-2)) return
        val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
        when(status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirmation=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if(confirmation==null) { Link.status(context,job,"failed","The system did not provide an approval screen."); return }
                PendingApproval.id=job.getString("id"); PendingApproval.intent=confirmation
                Link.status(context,job,"confirm","Continue the system approval on your watch.")
                val activity=PendingApproval.activity?.get()
                if(activity!=null) activity.refresh()
                val manager=context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("installation","Installation approval",NotificationManager.IMPORTANCE_DEFAULT))
                if(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) {
                    val open=Intent(context,WatchActivity::class.java).putExtra("approval",confirmation).putExtra("job",job.getString("id"))
                    val pending=PendingIntent.getActivity(context,job.getInt("session"),open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.notify(42,Notification.Builder(context,"installation").setSmallIcon(meghdad.pakkit.core.R.drawable.pakkit_mark).setColor(Design.PURPLE)
                        .setContentTitle("Approve installation").setContentText("Open Pakkit to continue on your watch.")
                        .setContentIntent(pending).setAutoCancel(true).build())
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                PendingApproval.intent=null
                Link.status(context,job,"installed","Installed on your watch.")
                store.file(job.getString("id")).delete()
                context.getSystemService(NotificationManager::class.java).cancel(42)
            }
            else -> {
                PendingApproval.intent=null
                val detail=when(status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation was cancelled. You can try again."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "The watch’s security policy blocked this installation. Use Google Play or contact the app developer."
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "This APK conflicts with an installed app or its signature. Ask the developer for a compatible version."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This APK is not compatible with your watch."
                    PackageInstaller.STATUS_FAILURE_INVALID -> "The system rejected this APK’s format or signature."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Your watch needs more free storage."
                    else -> "The system could not finish installing. Try again or use Google Play."
                }
                Link.status(context,job,"failed",detail)
                context.getSystemService(NotificationManager::class.java).cancel(42)
            }
        }
    }
}
object PendingApproval {
    var id: String?=null
    var intent: Intent?=null
    var activity: WeakReference<WatchActivity>?=null
}
