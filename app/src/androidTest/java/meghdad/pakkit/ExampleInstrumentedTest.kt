package meghdad.pakkit

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import meghdad.pakkit.core.Design
import meghdad.pakkit.core.BrandPalette
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import meghdad.pakkit.core.Packages
import meghdad.pakkit.core.Store
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ProductInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun texts(view: View): List<TextView> = when(view) {
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        is TextView -> listOf(view)
        else -> emptyList()
    }
    @Test fun navigationAndRecreationRetainSelectedPage() {
        val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity=monitor.waitForActivityWithTimeout(5000) as Activity
        instrumentation.runOnMainSync {
            assertEquals("Pocket to Wrist.", activity.findViewById<TextView>(R.id.page_heading).text.toString())
            activity.findViewById<View>(R.id.nav_guide).performClick()
            assertEquals("Good to know.", activity.findViewById<TextView>(R.id.page_heading).text.toString())
            activity.recreate()
        }
        val recreated=monitor.waitForActivityWithTimeout(5000)
        assertNotNull(recreated)
        instrumentation.runOnMainSync {
            assertEquals("Good to know.", recreated.findViewById<TextView>(R.id.page_heading).text.toString())
            recreated.finish()
        }
        instrumentation.removeMonitor(monitor)
    }
    @Test fun themeChooserAppliesAndPersistsMidnightTheme() {
        val original = Design.themeMode(context)
        Design.setThemeMode(context, "light")
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val light = monitor.waitForActivityWithTimeout(5000)
            assertNotNull(light)
            instrumentation.runOnMainSync {
                assertEquals(BrandPalette.Light.text, light.findViewById<TextView>(R.id.page_heading).currentTextColor)
                light.findViewById<View>(R.id.appearance).performClick()
            }
            onView(withText("Midnight dark")).perform(click())
            val dark = monitor.waitForActivityWithTimeout(5000)
            assertNotNull(dark)
            instrumentation.runOnMainSync {
                assertEquals("dark", Design.themeMode(context))
                assertEquals(BrandPalette.Dark.text, dark.findViewById<TextView>(R.id.page_heading).currentTextColor)
                dark.recreate()
            }
            val restored = monitor.waitForActivityWithTimeout(5000)
            assertNotNull(restored)
            instrumentation.runOnMainSync {
                assertEquals(BrandPalette.Dark.text, restored.findViewById<TextView>(R.id.page_heading).currentTextColor)
                restored.finish()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            Design.setThemeMode(context, original)
        }
    }
    @Test fun readsSignedCompleteWearApk() {
        val file=File(context.cacheDir,"fixture.apk")
        try {
            val digest=instrumentation.context.assets.open("fixture.apk").use { input -> file.outputStream().use { meghdad.pakkit.core.TransferRules.copyBounded(input,it).second } }
            val apk=Packages.inspect(context,file,digest)
            assertEquals("meghdad.pakkit.fixture",apk.packageName)
            assertTrue(apk.wear)
            assertEquals(31,apk.minSdk)
            assertTrue(apk.bytes>0)
        } finally { file.delete() }
    }
    @Test fun rejectsOwnPackageForSelfUpdates() {
        val file=File(context.applicationInfo.sourceDir)
        val failure=runCatching { Packages.inspect(context,file,"test") }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("Google Play"))
    }
    @Test fun rejectsNonApkArchivesAndPathTraversal() {
        val file=File(context.cacheDir,"invalid.apk")
        ZipOutputStream(file.outputStream()).use { it.putNextEntry(ZipEntry("readme.txt")); it.write("hello".toByteArray()); it.closeEntry() }
        try {
            assertNotNull(runCatching { Packages.inspect(context,file,"test") }.exceptionOrNull())
            assertNotNull(runCatching { Store(context).file("../outside") }.exceptionOrNull())
        } finally { file.delete() }
    }
}
