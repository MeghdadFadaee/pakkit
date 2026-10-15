package meghdad.pakkit.apk

import android.content.Context
import android.content.pm.PackageManager
import android.os.StatFs
import java.io.File
import java.util.zip.ZipFile

object PackageInspector {
    @Suppress("DEPRECATION")
    fun inspect(context: Context, file: File, hash: String): Apk {
        requireUser(file.length() in 1..ApkRules.MAX_BYTES) { "Choose an APK up to 50 MB." }
        val abis = mutableSetOf<String>()
        ZipFile(file).use { zip ->
            requireUser(zip.getEntry("AndroidManifest.xml") != null) {
                "This file is not an Android APK."
            }
            requireUser(zip.size() < 100_000) {
                "This file is too complex to open. Try another APK."
            }
            var expanded = 0L
            zip.entries().asSequence().forEach { entry ->
                requireUser(entry.size >= 0 && entry.size <= 128L * 1024 * 1024) {
                    "This app is too large to unpack. Try a smaller APK."
                }
                expanded += entry.size
                requireUser(expanded <= 200L * 1024 * 1024) {
                    "This app is too large to unpack. Try a smaller APK."
                }
                if (entry.name == "AndroidManifest.xml")
                    requireUser(entry.size <= 2 * 1024 * 1024) {
                        "This file can’t be opened. Try another APK."
                    }
                val parts = entry.name.split('/')
                if (parts.size >= 3 && parts[0] == "lib" && entry.name.endsWith(".so"))
                    abis += parts[1]
            }
        }
        val info =
            context.packageManager.getPackageArchiveInfo(
                file.path,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_CONFIGURATIONS,
            ) ?: throw UserMessage("This file can’t be opened. Choose a complete APK.")
        requireUser(info.splitNames.isNullOrEmpty()) {
            "This is only part of an app. Choose a complete APK."
        }
        requireUser(info.signingInfo?.apkContentsSigners?.isNotEmpty() == true) {
            "This app’s signature can’t be verified. Get a fresh copy from its source."
        }
        requireUser(info.packageName != context.packageName) {
            "Update Pakkit through Google Play."
        }
        val app = requireNotNull(info.applicationInfo)
        app.sourceDir = file.path
        app.publicSourceDir = file.path
        context.packageManager
            .getResourcesForApplication(app)
            .assets
            .openXmlResourceParser("AndroidManifest.xml")
            .use { xml ->
                while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                    requireUser(xml.name != "uses-split") {
                        "This is only part of an app. Choose a complete APK."
                    }
                    if (xml.name == "manifest")
                        requireUser(xml.getAttributeValue(null, "split").isNullOrEmpty()) {
                            "This is only part of an app. Choose a complete APK."
                        }
                    if (xml.name == "application")
                        requireUser(
                            !xml.getAttributeBooleanValue(
                                "http://schemas.android.com/apk/res/android",
                                "isSplitRequired",
                                false,
                            )
                        ) {
                            "This is only part of an app. Choose a complete APK."
                        }
                }
            }
        return Apk(
            app.loadLabel(context.packageManager).toString().take(100),
            info.packageName,
            info.versionName.orEmpty().take(64),
            app.minSdkVersion,
            file.length(),
            hash,
            abis,
            info.reqFeatures?.any { it.name == PackageManager.FEATURE_WATCH } == true,
        )
    }

    fun checkForWatch(apk: Apk, sdk: Int, abis: Set<String>, free: Long) {
        requireUser(apk.minSdk <= sdk) {
            "This app needs a newer version of Android. Check your watch for updates."
        }
        requireUser(ApkRules.compatibleAbis(apk.abis, abis)) {
            "This version doesn’t work on your watch. Try a version made for it."
        }
        requireSpace(apk.bytes, free)
    }

    fun requireSpace(bytes: Long, free: Long) {
        requireUser(free > bytes * 3 + 20 * 1024 * 1024) {
            "Free more storage on your watch, then try again."
        }
    }

    fun free(context: Context) = StatFs(context.filesDir.path).availableBytes
}
