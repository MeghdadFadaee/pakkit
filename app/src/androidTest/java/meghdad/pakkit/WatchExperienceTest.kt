package meghdad.pakkit

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import meghdad.pakkit.apk.InstallState
import meghdad.pakkit.transfer.Receiving
import meghdad.pakkit.ui.RoundScrollView
import meghdad.pakkit.ui.WatchActivity
import meghdad.pakkit.ui.WatchDesign
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchExperienceTest : WatchTest() {
    private fun views(view: View): List<View> =
        listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
            else emptyList()

    private fun screen() = views(requireNotNull(activity).window.decorView)

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(220) // Finite screen entrance; screenshots capture the settled UI.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(context.getExternalFilesDir(null), "product-review").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun assertActionsReachable() {
        instrumentation.runOnMainSync {
            val all = screen()
            val scroll = all.filterIsInstance<RoundScrollView>().single()
            for (button in all.filterIsInstance<Button>()) {
                assertTrue(button.height >= WatchDesign.dp(context, 48))
                scroll.scrollTo(
                    0,
                    (button.top + button.height / 2 - scroll.height / 2).coerceAtLeast(0),
                )
                val rect = Rect()
                assertTrue(button.getGlobalVisibleRect(rect))
                assertEquals(button.height, rect.height())
                assertEquals(button.width, rect.width())
            }
            scroll.scrollTo(0, 0)
        }
    }

    @Test
    fun homeLeadsWithBrandAndReceiving() {
        openWatch()
        instrumentation.runOnMainSync {
            val labels = screen().filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.contains("Pack it. Send it. Wear it."))
            assertTrue(labels.contains("Start receiving"))
            assertFalse(labels.contains("Allow installs"))
            val start = screen().filterIsInstance<Button>().single { it.text == "Start receiving" }
            val visible = Rect()
            assertTrue(start.getGlobalVisibleRect(visible))
            assertEquals("Receiving is visible without scrolling", start.height, visible.height())
        }
        assertActionsReachable()
        capture("home")
    }

    @Test
    fun reviewApprovalFailureAndSuccessHaveReachableActions() {
        val apk = fixture()
        store.stage(apk)
        openWatch()
        val sessions = context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        for ((state, detail) in
            listOf(
                InstallState.READY to "Ready when you are.",
                InstallState.INSTALLING to "Getting ready to install…",
                InstallState.CONFIRM to "Ready for your approval.",
                InstallState.FAILED to "Free some space on your watch, then try again.",
                InstallState.INSTALLED to "Installed on your watch.",
            )) {
            store.update(apk.id, state, detail)
            instrumentation.runOnMainSync { (activity as WatchActivity).refresh() }
            instrumentation.waitForIdleSync()
            assertActionsReachable()
            capture(state.wireValue)
        }
        assertEquals(
            sessions,
            context.packageManager.packageInstaller.mySessions.map { it.sessionId },
        )
    }

    @Test
    fun linkAndProgressRemainAccessibleWithoutExtraDialogs() {
        val owner = Any()
        try {
            Receiving.begin(owner)
            Receiving.update(owner) { it.copy(url = "http://10.0.2.16:8080/#test-display-link") }
            openWatch()
            capture("scan")
            instrumentation.runOnMainSync {
                screen().filterIsInstance<Button>().single { it.text == "View link" }.performClick()
            }
            instrumentation.waitForIdleSync()
            assertActionsReachable()
            capture("link")
            instrumentation.runOnMainSync {
                screen()
                    .filterIsInstance<Button>()
                    .single { it.text == "Back to code" }
                    .performClick()
            }
            Receiving.update(owner) { it.copy(total = 1000, received = 420) }
            instrumentation.waitForIdleSync()
            capture("sending")
            Receiving.update(owner) { it.copy(received = 1000) }
            instrumentation.waitForIdleSync()
            capture("checking")
        } finally {
            Receiving.end(owner, "Session ended")
        }
    }
}
