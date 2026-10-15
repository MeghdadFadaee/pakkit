package meghdad.pakkit

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.math.hypot
import kotlin.math.min
import meghdad.pakkit.transfer.ReceiveService
import meghdad.pakkit.transfer.Receiving
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchSessionTest : WatchTest() {
    @Test
    fun receivingQrDecodesAndFitsWithinCircularDisplay() {
        openWatch()
        context.startForegroundService(Intent(context, ReceiveService::class.java))
        await(30_000) { Receiving.state.url != null }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            fun views(view: View): List<View> =
                listOf(view) +
                    if (view is ViewGroup)
                        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
                    else emptyList()
            val screen = requireNotNull(activity).window.decorView
            val image =
                views(screen).filterIsInstance<ImageView>().single {
                    it.contentDescription?.startsWith("QR code") == true
                }
            val visible = Rect()
            assertTrue(image.getGlobalVisibleRect(visible))
            val size = min(image.width, image.height)
            val radius = min(screen.width, screen.height) / 2.0
            for (x in listOf(visible.centerX() - size / 2.0, visible.centerX() + size / 2.0)) {
                for (y in listOf(visible.centerY() - size / 2.0, visible.centerY() + size / 2.0)) {
                    assertTrue(
                        "Full QR square stays within the bezel",
                        hypot(x - screen.width / 2.0, y - screen.height / 2.0) < radius,
                    )
                }
            }
            val bitmap = (image.drawable as BitmapDrawable).bitmap
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            assertEquals(
                Receiving.state.url,
                MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text,
            )
        }
    }
}
