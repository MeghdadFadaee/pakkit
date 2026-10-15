package meghdad.pakkit.apk

import org.junit.Assert.*
import org.junit.Test

class CompatibilityTest {
    private val apk = Apk("Test", "test.app", "1", 31, 1024, "digest", setOf("arm64-v8a"), true)

    @Test
    fun acceptsCompatibleSdkAbiAndStorage() {
        PackageInspector.checkForWatch(apk, 36, setOf("arm64-v8a"), 100L * 1024 * 1024)
    }

    @Test
    fun rejectsIncompatibleSdkProcessorAndInsufficientStorage() {
        assertTrue(
            runCatching {
                PackageInspector.checkForWatch(apk, 30, setOf("arm64-v8a"), Long.MAX_VALUE)
            }
                .isFailure,
        )
        assertTrue(
            runCatching { PackageInspector.checkForWatch(apk, 36, setOf("x86_64"), Long.MAX_VALUE) }
                .isFailure,
        )
        assertTrue(
            runCatching { PackageInspector.checkForWatch(apk, 36, setOf("arm64-v8a"), 1024) }
                .isFailure,
        )
    }
}
