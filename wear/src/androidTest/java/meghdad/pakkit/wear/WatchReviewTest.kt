package meghdad.pakkit.wear

import android.content.Intent
import android.widget.TextView
import android.widget.Button
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import meghdad.pakkit.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WatchReviewTest {
    @Test fun receivingAFileDoesNotCreateAnInstallSession() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=instrumentation.targetContext
        val store=Store(c)
        val id=UUID.randomUUID().toString()
        val file=store.file(id)
        val digest=instrumentation.context.assets.open("fixture.apk").use { input -> file.outputStream().use { TransferRules.copyBounded(input,it).second } }
        val apk=Packages.inspect(c,file,digest)
        assertEquals("meghdad.pakkit.fixture",apk.packageName)
        val before=c.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet()
        store.write("incoming",JSONObject().put("id",id).put("source","qa-fixture")
            .put("created",System.currentTimeMillis()).put("apk",apk.json()).put("state","ready")
            .put("detail","Ready. Review and install on your watch."))
        val monitor=instrumentation.addMonitor(WatchActivity::class.java.name,null,false)
        c.startActivity(Intent(c,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity=monitor.waitForActivityWithTimeout(5000)
        assertNotNull(activity)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            fun texts(v:View):List<String> = when(v) {
                is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
                is TextView -> listOf(v.text.toString())
                else -> emptyList()
            }
            assertTrue(texts(activity.window.decorView).contains("Pakkit QA fixture"))
            fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
            val all = views(activity.window.decorView)
            val scroll = all.filterIsInstance<RoundScrollView>().single()
            val buttons = all.filterIsInstance<Button>()
            assertTrue(buttons.any { it.text == "Install" || it.text == "Allow installs" })
            for (button in buttons) {
                assertTrue("48 dp target height", button.height >= Design.dp(c, 48))
                scroll.scrollTo(0, (button.top + button.height / 2 - scroll.height / 2).coerceAtLeast(0))
                val visible = Rect()
                assertTrue("Action is reachable", button.getGlobalVisibleRect(visible))
                assertEquals("Action is fully visible", button.height, visible.height())
                assertTrue("Action is horizontally readable", visible.width() >= Design.dp(c, 48))
            }
            scroll.scrollTo(0, 0)
        }
        assertEquals(before,c.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet())
        instrumentation.removeMonitor(monitor)
        // Deliberately leave the actual signed fixture on the review screen for manual consent QA.
    }
}
