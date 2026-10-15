package meghdad.pakkit

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import meghdad.pakkit.apk.ApkRules
import meghdad.pakkit.apk.PackageInspector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageInspectionTest : WatchTest() {
    @Test
    fun selfUpdateIsRejectedBeforeCreatingAnInstallSession() {
        val ownApk = File(context.applicationInfo.sourceDir)
        val digest = ownApk.inputStream().use { ApkRules.copyBounded(it).second }
        val result = runCatching { PackageInspector.inspect(context, ownApk, digest) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Google Play"))
    }

    @Test
    fun zipWithoutAndroidManifestIsRejected() {
        val file = store.file(UUID.randomUUID().toString())
        try {
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("readme.txt"))
                it.write("Not an app".toByteArray())
                it.closeEntry()
            }
            val digest = file.inputStream().use { ApkRules.copyBounded(it).second }
            assertTrue(runCatching { PackageInspector.inspect(context, file, digest) }.isFailure)
        } finally {
            file.delete()
        }
    }
}
