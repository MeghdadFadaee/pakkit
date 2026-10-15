package meghdad.pakkit.apk

import org.junit.Assert.*
import org.junit.Test

class CompatibilityTest {
    private val apk = Apk("Test", "test.app", "1", 31, 1024, "digest", setOf("arm64-v8a"), true)

    @Test
    fun acceptsCompatibleSdkAndProcessor() {
        PackageInspector.checkForWatch(apk, 36, setOf("arm64-v8a"))
    }

    @Test
    fun rejectsActualPlatformIncompatibility() {
        assertTrue(
            runCatching { PackageInspector.checkForWatch(apk, 30, setOf("arm64-v8a")) }.isFailure
        )
        assertTrue(
            runCatching { PackageInspector.checkForWatch(apk, 36, setOf("x86_64")) }.isFailure
        )
    }

    @Test
    fun storageUsesAvailableBytesWithoutAMultiplierOrOverflow() {
        PackageInspector.requireSpace(100, 100)
        PackageInspector.requireSpace(Long.MAX_VALUE, Long.MAX_VALUE)
        assertTrue(runCatching { PackageInspector.requireSpace(101, 100) }.isFailure)
        assertTrue(runCatching { PackageInspector.requireSpace(Long.MAX_VALUE, 1024) }.isFailure)
    }
}
