package meghdad.pakkit.apk

import org.junit.Assert.*
import org.junit.Test

class UserMessageTest {
    @Test
    fun hidesUnexpectedPlatformAndFileErrors() {
        for (error in
            listOf(
                java.io.IOException("/data/private/file.apk"),
                IllegalArgumentException("Invalid request headers"),
                SecurityException("Permission denial"),
            )) {
            assertEquals("Try again.", error.userMessage("Try again."))
        }
    }

    @Test
    fun retainsDeliberateRecoveryCopy() {
        val error = runCatching { requireUser(false) { "Choose another APK." } }.exceptionOrNull()!!
        assertEquals("Choose another APK.", error.userMessage("Try again."))
    }
}
