package meghdad.pakkit.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

data class Apk(val label: String, val packageName: String, val version: String, val minSdk: Int,
    val bytes: Long, val sha256: String, val abis: Set<String>, val wear: Boolean) {
    fun json() = JSONObject().put("label", label).put("package", packageName).put("version", version)
        .put("minSdk", minSdk).put("bytes", bytes).put("sha256", sha256)
        .put("abis", abis.joinToString(",")).put("wear", wear)
    companion object {
        fun from(j: JSONObject) = Apk(j.getString("label"), j.getString("package"), j.getString("version"),
            j.getInt("minSdk"), j.getLong("bytes"), j.getString("sha256"),
            j.getString("abis").split(',').filter { it.isNotBlank() }.toSet(), j.getBoolean("wear"))
    }
}
object Packages {
    @Suppress("DEPRECATION")
    fun inspect(context: Context, file: File, hash: String): Apk {
        require(file.length() in 1..TransferRules.MAX_BYTES) { "Choose an APK smaller than 50 MB." }
        val abis = mutableSetOf<String>()
        ZipFile(file).use { zip ->
            require(zip.getEntry("AndroidManifest.xml") != null) { "This file is not an Android APK." }
            require(zip.size() < 100_000) { "This archive contains too many entries." }
            var expanded = 0L
            zip.entries().asSequence().forEach { entry ->
                require(entry.size >= 0 && entry.size <= 128L * 1024 * 1024) { "This APK contains an unusually large archive entry." }
                expanded += entry.size
                require(expanded <= 200L * 1024 * 1024) { "This APK expands beyond Pakkit’s package limit." }
                if (entry.name == "AndroidManifest.xml") require(entry.size <= 2 * 1024 * 1024) { "This APK manifest is too large." }
                val parts = entry.name.split('/')
                if (parts.size >= 3 && parts[0] == "lib" && entry.name.endsWith(".so")) abis += parts[1]
            }
        }
        val info = context.packageManager.getPackageArchiveInfo(file.path,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_CONFIGURATIONS)
            ?: error("Android could not read this APK. Split packages and bundles are not supported.")
        require(info.splitNames.isNullOrEmpty()) { "Choose a complete, single APK rather than a split package." }
        require(info.signingInfo?.apkContentsSigners?.isNotEmpty() == true) { "This APK has no readable signing certificate." }
        require(info.packageName != context.packageName) { "Update Pakkit through Google Play." }
        val app = requireNotNull(info.applicationInfo)
        app.sourceDir = file.path
        app.publicSourceDir = file.path
        context.packageManager.getResourcesForApplication(app).assets.openXmlResourceParser("AndroidManifest.xml").use { xml ->
            while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                require(xml.name != "uses-split") { "Choose a complete APK; this file requires other splits." }
                if (xml.name == "manifest") require(xml.getAttributeValue(null, "split").isNullOrEmpty()) { "This is a split APK. Choose a complete APK." }
                if (xml.name == "application") require(!xml.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "isSplitRequired", false)) { "This APK requires additional splits." }
            }
        }
        return Apk(app.loadLabel(context.packageManager).toString().take(100), info.packageName,
            info.versionName.orEmpty().take(64), app.minSdkVersion, file.length(), hash, abis,
            info.reqFeatures?.any { it.name == PackageManager.FEATURE_WATCH } == true)
    }
    fun checkForWatch(apk: Apk, sdk: Int, abis: Set<String>, free: Long) {
        require(apk.minSdk <= sdk) { "This APK needs Android API ${apk.minSdk}; your watch runs API $sdk." }
        require(TransferRules.compatibleAbis(apk.abis, abis)) { "This APK was built for a different processor." }
        require(free > apk.bytes * 3 + 20 * 1024 * 1024) { "Free more storage on your watch, then try again." }
    }
    fun free(context: Context) = StatFs(context.filesDir.path).availableBytes
}
