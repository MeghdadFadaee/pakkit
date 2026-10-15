package meghdad.pakkit.ui

import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchColorsTest {
    @Test
    fun bodyAndSecondaryTextAreReadableOnWatchSurfaces() {
        for (surface in listOf(WatchColors.canvas, WatchColors.raised)) {
            assertTrue(contrast(WatchColors.text, surface) >= 4.5)
            assertTrue(contrast(WatchColors.secondary, surface) >= 4.5)
        }
    }

    @Test
    fun semanticColorsAreReadableOnTheirSurfaces() {
        for ((text, surface) in
            listOf(
                WatchColors.accent to WatchColors.accentSoft,
                WatchColors.mint to WatchColors.mintSoft,
                WatchColors.error to WatchColors.errorSoft,
            )) assertTrue(contrast(text, surface) >= 4.5)
    }

    @Test
    fun whiteTextContrastsWithBothActionGradientStops() {
        for (stop in listOf(0xFF7854EE.toInt(), 0xFF6038D6.toInt())) assertTrue(
            contrast(-1, stop) >= 4.5,
        )
    }

    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 255) / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
