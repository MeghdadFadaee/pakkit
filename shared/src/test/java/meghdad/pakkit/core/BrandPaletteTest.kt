package meghdad.pakkit.core

import org.junit.Assert.assertTrue
import org.junit.Test

class BrandPaletteTest {
    @Test fun bodyAndSecondaryCopyMeetNormalTextContrast() {
        for (palette in listOf(BrandPalette.Light, BrandPalette.Dark)) {
            for (surface in listOf(palette.canvas, palette.surface, palette.raised)) {
                assertTrue("Body contrast", Design.contrast(palette.text, surface) >= 4.5)
                assertTrue("Secondary contrast", Design.contrast(palette.secondary, surface) >= 4.5)
            }
        }
    }
    @Test fun semanticBadgesRemainReadableInBothThemes() {
        for (p in listOf(BrandPalette.Light, BrandPalette.Dark)) {
            assertTrue("Purple badge", Design.contrast(p.accent, p.accentSoft) >= 4.5)
            assertTrue("Mint badge", Design.contrast(p.mint, p.mintSoft) >= 4.5)
            assertTrue("Error badge", Design.contrast(p.error, p.errorSoft) >= 4.5)
        }
    }
    @Test fun primaryButtonTextContrastsWithBothGradientStops() {
        for (stop in listOf(0xFF7854EE.toInt(), 0xFF6038D6.toInt())) {
            assertTrue(Design.contrast(0xFFFFFFFF.toInt(), stop) >= 4.5)
        }
    }
}
