package meghdad.pakkit

import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.ext.junit.runners.AndroidJUnit4
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.install.ApkInstaller
import meghdad.pakkit.install.InstallReceiver
import meghdad.pakkit.install.PendingApproval
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallSafetyTest : WatchTest() {
    @Test
    fun watchActionAndSystemConfirmationCompleteInstallation() {
        assumeTrue(context.packageManager.canRequestPackageInstalls())
        val apk = fixture()
        store.stage(apk)
        openWatch()
        instrumentation.runOnMainSync {
            fun views(view: android.view.View): List<android.view.View> =
                listOf(view) +
                    if (view is android.view.ViewGroup)
                        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
                    else emptyList()
            views(requireNotNull(activity).window.decorView)
                .filterIsInstance<android.widget.Button>()
                .single { it.text == "Install" }
                .performClick()
        }
        await(15_000) { store.pending()?.state == InstallState.CONFIRM }
        // Exercise the real system button. No shell install or source-permission grant.
        var consentClicked = false
        try {
            await(15_000) {
                if (consentClicked) return@await true
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val button =
                    root
                        ?.let { node ->
                            listOf("Install", "Update").flatMap {
                                node.findAccessibilityNodeInfosByText(it)
                            }
                        }
                        ?.firstOrNull {
                            it.text?.toString() in setOf("Install", "Update") &&
                                it.isClickable &&
                                it.isEnabled &&
                                it.packageName?.toString() != context.packageName
                        }
                consentClicked =
                    button?.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
                    ) == true
                consentClicked
            }
        } catch (failure: Exception) {
            fun labels(node: android.view.accessibility.AccessibilityNodeInfo): List<String> =
                listOf(
                    "${node.packageName}: ${node.text} / ${node.contentDescription} clickable=${node.isClickable}"
                ) +
                    (0 until node.childCount).flatMap {
                        node.getChild(it)?.let(::labels) ?: emptyList()
                    }
            println(instrumentation.uiAutomation.rootInActiveWindow?.let(::labels))
            throw failure
        }
        await(30_000) {
            store.pending()?.state in setOf(InstallState.INSTALLED, InstallState.FAILED)
        }
        assertEquals(store.pending()?.detail, InstallState.INSTALLED, store.pending()?.state)
        assertFalse(store.file(apk.id).exists())
        assertNull(PendingApproval.intent)
    }

    @Test
    fun receiptAndForgedCallbacksCannotMarkPackageInstalled() {
        val apk = fixture()
        store.stage(apk)
        val sessions = context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        for ((job, session) in listOf(apk.id to -1, "wrong-job" to 42, apk.id to 42)) {
            val intent =
                Intent()
                    .putExtra("job", job)
                    .putExtra(PackageInstaller.EXTRA_SESSION_ID, session)
                    .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
            instrumentation.runOnMainSync { InstallReceiver().onReceive(context, intent) }
            assertEquals(InstallState.READY, store.pending()?.state)
        }
        assertEquals(
            sessions,
            context.packageManager.packageInstaller.mySessions.map { it.sessionId },
        )
    }

    @Test
    fun changedPackageCannotCreateAnInstallSession() {
        assumeTrue(
            "Enable source approval through the emulator's system UI",
            context.packageManager.canRequestPackageInstalls(),
        )
        val apk = fixture()
        store.stage(apk)
        val before = context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        store.file(apk.id).appendText("changed")
        ApkInstaller(context).install(apk)
        assertEquals(InstallState.FAILED, store.pending()?.state)
        assertTrue(store.pending()!!.detail.contains("changed"))
        assertEquals(
            before,
            context.packageManager.packageInstaller.mySessions.map { it.sessionId },
        )
    }

    @Test
    fun officialInstallerRequiresSystemUserConsent() {
        assumeTrue(
            "Enable source approval through the emulator's system UI",
            context.packageManager.canRequestPackageInstalls(),
        )
        val apk = fixture()
        store.stage(apk)
        openWatch()
        ApkInstaller(context).install(apk)
        await(10_000) { store.pending()?.state in setOf(InstallState.CONFIRM, InstallState.FAILED) }
        assertEquals(store.pending()?.detail, InstallState.CONFIRM, store.pending()?.state)
        assertEquals(apk.id, PendingApproval.id)
        assertNotNull(PendingApproval.intent)
        assertNotNull(store.pending()?.sessionId)
    }
}
