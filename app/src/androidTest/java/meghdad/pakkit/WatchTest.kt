package meghdad.pakkit

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.PackageInspector
import meghdad.pakkit.apk.PendingApk
import meghdad.pakkit.data.PackageStore
import meghdad.pakkit.install.PendingApproval
import meghdad.pakkit.transfer.ReceiveService
import meghdad.pakkit.transfer.Receiving
import meghdad.pakkit.ui.WatchActivity
import org.junit.After
import org.junit.Before

abstract class WatchTest {
    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val context = instrumentation.targetContext
    protected val store = PackageStore(context)
    protected var activity: Activity? = null

    @Before
    fun prepare() {
        context.stopService(Intent(context, ReceiveService::class.java))
        await { !Receiving.state.active }
        store.pending()?.let { store.dismiss(it.id) }
        PendingApproval.clear()
    }

    protected fun openWatch() {
        val monitor = instrumentation.addMonitor(WatchActivity::class.java.name, null, false)
        context.startActivity(
            Intent(context, WatchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity = requireNotNull(monitor.waitForActivityWithTimeout(5000))
        instrumentation.removeMonitor(monitor)
        instrumentation.waitForIdleSync()
    }

    protected fun fixture(): PendingApk {
        val id = UUID.randomUUID().toString()
        val file = store.file(id)
        val digest =
            instrumentation.context.assets.open("fixture.apk").use { input ->
                file.outputStream().use { ApkRules.copyAndHash(input, it).second }
            }
        return PendingApk(
            id,
            System.currentTimeMillis(),
            PackageInspector.inspect(context, file, digest),
        )
    }

    protected fun await(timeout: Long = 5000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(25)
        check(condition()) { "Condition did not become true within ${timeout}ms" }
    }

    @After
    fun clean() {
        context.stopService(Intent(context, ReceiveService::class.java))
        await { !Receiving.state.active }
        store.pending()?.let { pending ->
            pending.sessionId?.let {
                runCatching { context.packageManager.packageInstaller.abandonSession(it) }
            }
            store.dismiss(pending.id)
        }
        PendingApproval.clear()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        instrumentation.runOnMainSync { activity?.finish() }
    }
}
