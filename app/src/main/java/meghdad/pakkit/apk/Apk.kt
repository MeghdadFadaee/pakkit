package meghdad.pakkit.apk

import org.json.JSONObject

data class Apk(
    val label: String,
    val packageName: String,
    val version: String,
    val minSdk: Int,
    val bytes: Long,
    val sha256: String,
    val abis: Set<String>,
    val wear: Boolean,
) {
    fun json() =
        JSONObject()
            .put("label", label)
            .put("package", packageName)
            .put("version", version)
            .put("minSdk", minSdk)
            .put("bytes", bytes)
            .put("sha256", sha256)
            .put("abis", abis.joinToString(","))
            .put("wear", wear)

    companion object {
        fun from(j: JSONObject) =
            Apk(
                j.getString("label"),
                j.getString("package"),
                j.getString("version"),
                j.getInt("minSdk"),
                j.getLong("bytes"),
                j.getString("sha256"),
                j.getString("abis").split(',').filter { it.isNotBlank() }.toSet(),
                j.getBoolean("wear"),
            )
    }
}
