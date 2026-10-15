package meghdad.pakkit.transfer

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Live session state is never persisted: process death revokes browser approvals. */
object Receiving {
    data class State(
        val active: Boolean = false,
        val url: String? = null,
        val detail: String = "Connect your watch and browser to the same Wi-Fi.",
        val browserRequest: BrowserAccess.Request? = null,
        val received: Long = 0,
        val total: Long = 0,
    )

    @Volatile
    var state = State()
        private set

    private var owner: Any? = null
    private var access: BrowserAccess? = null
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val notify = Runnable { listeners.forEach { it() } }

    fun observe(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeObserver(listener: () -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized
    fun begin(owner: Any, access: BrowserAccess? = null) {
        this.access = access
        this.owner = owner
        publish(State(active = true, detail = "Connecting to Wi-Fi…"))
    }

    @Synchronized
    fun update(owner: Any, change: (State) -> State) {
        if (this.owner === owner) publish(change(state))
    }

    @Synchronized
    fun end(owner: Any, detail: String) {
        if (this.owner !== owner) return
        this.owner = null
        this.access = null
        publish(State(detail = detail))
    }

    fun answerBrowser(id: String, allow: Boolean): Boolean {
        val current = synchronized(this) { access }
        return current?.answer(id, allow) ?: false
    }

    private fun publish(state: State) {
        this.state = state
        main.removeCallbacks(notify)
        main.post(notify)
    }
}
