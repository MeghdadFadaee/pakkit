package meghdad.pakkit.apk

import org.json.JSONObject

/** Durable review/installation state. Network sessions have a separate, in-memory lifetime. */
data class PendingApk(
    val id: String,
    val created: Long,
    val apk: Apk,
    val state: InstallState = InstallState.READY,
    val detail: String = "Review this app and approve installation on your watch.",
    val sessionId: Int? = null,
) {
    init {
        require(ApkRules.validId(id))
    }

    fun json(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("created", created)
            .put("apk", apk.json())
            .put("state", state.wireValue)
            .put("detail", detail)
            .apply { sessionId?.let { put("session", it) } }

    companion object {
        fun from(json: JSONObject) =
            PendingApk(
                id = json.getString("id"),
                created = json.getLong("created"),
                apk = Apk.from(json.getJSONObject("apk")),
                state = InstallState.entries.first { it.wireValue == json.getString("state") },
                detail = json.optString("detail"),
                sessionId = json.optInt("session", -1).takeIf { it >= 0 },
            )
    }
}

enum class InstallState(val wireValue: String) {
    READY("ready"),
    INSTALLING("installing"),
    CONFIRM("confirm"),
    INSTALLED("installed"),
    FAILED("failed"),
}
