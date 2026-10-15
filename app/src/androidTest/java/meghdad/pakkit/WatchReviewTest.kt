package meghdad.pakkit

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import meghdad.pakkit.ui.RoundScrollView
import meghdad.pakkit.ui.WatchDesign
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchReviewTest : WatchTest() {
    @Test
    fun reviewActionsAreReachableAndReceiptDoesNotInstall() {
        val apk = fixture()
        store.stage(apk)
        val before = context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        openWatch()
        instrumentation.runOnMainSync {
            fun views(view: View): List<View> =
                listOf(view) +
                    if (view is ViewGroup)
                        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
                    else emptyList()
            val all = views(requireNotNull(activity).window.decorView)
            assertTrue(all.filterIsInstance<TextView>().any { it.text == apk.apk.label })
            val scroll = all.filterIsInstance<RoundScrollView>().single()
            val buttons = all.filterIsInstance<Button>()
            assertTrue(buttons.any { it.text == "Install" || it.text == "Allow installs" })
            for (button in buttons) {
                assertTrue(button.height >= WatchDesign.dp(context, 48))
                scroll.scrollTo(
                    0,
                    (button.top + button.height / 2 - scroll.height / 2).coerceAtLeast(0),
                )
                val visible = Rect()
                assertTrue(button.getGlobalVisibleRect(visible))
                assertEquals(button.height, visible.height())
            }
        }
        assertEquals(
            before,
            context.packageManager.packageInstaller.mySessions.map { it.sessionId },
        )
    }
}
