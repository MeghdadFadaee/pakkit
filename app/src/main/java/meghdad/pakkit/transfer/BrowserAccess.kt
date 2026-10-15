package meghdad.pakkit.transfer

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Session-only browser access. Credentials stay in the exact browser origin, never in the product
 * UI.
 */
class BrowserAccess(private val changed: (Request?) -> Unit = {}) {
    data class Request(val id: String, val label: String, val address: String)

    enum class State {
        APPROVED,
        PENDING,
        DENIED,
        BUSY,
        UNKNOWN,
    }

    data class Connection(val state: State, val credential: String? = null)

    // Unapproved peers share one pending slot. Further records require a watch decision,
    // so network traffic alone cannot grow this map. Approvals do not have a time/device quota.
    private val decisions = mutableMapOf<String, State>()
    private var pending: Pair<String, Request>? = null

    @Synchronized
    fun state(credential: String?): State =
        if (credential == null) State.UNKNOWN else decisions[credential] ?: State.UNKNOWN

    @Synchronized
    fun connect(credential: String?, label: String, address: String): Connection {
        val current = state(credential)
        if (current != State.UNKNOWN) return Connection(current)
        if (pending != null) return Connection(State.BUSY)
        val secret =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val request = Request(UUID.randomUUID().toString(), label, address)
        decisions[secret] = State.PENDING
        pending = secret to request
        changed(request)
        return Connection(State.PENDING, secret)
    }

    @Synchronized
    fun answer(id: String, allow: Boolean): Boolean {
        val (credential, request) = pending?.takeIf { it.second.id == id } ?: return false
        decisions[credential] = if (allow) State.APPROVED else State.DENIED
        pending = null
        changed(null)
        return true
    }

    @Synchronized
    fun retry(credential: String?) {
        if (credential != null && state(credential) == State.DENIED) decisions.remove(credential)
    }

    @Synchronized
    fun close() {
        decisions.clear()
        pending = null
        changed(null)
    }

    companion object {
        fun credential(header: String?): String? =
            header
                ?.takeIf { it.startsWith("Bearer ") }
                ?.removePrefix("Bearer ")
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }

        fun label(agent: String): String {
            val browser =
                when {
                    "Edg" in agent -> "Edge"
                    "Firefox" in agent -> "Firefox"
                    "Chrome" in agent || "CriOS" in agent -> "Chrome"
                    "Safari" in agent -> "Safari"
                    else -> "Browser"
                }
            val device =
                when {
                    "Android" in agent -> "Android"
                    "iPhone" in agent || "iPad" in agent -> "iOS"
                    "Windows" in agent -> "Windows"
                    "Macintosh" in agent -> "Mac"
                    "Linux" in agent -> "Linux"
                    else -> null
                }
            return if (device == null) browser else "$browser on $device"
        }
    }
}
