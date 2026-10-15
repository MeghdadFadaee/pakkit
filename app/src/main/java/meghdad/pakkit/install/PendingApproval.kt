package meghdad.pakkit.install

import android.content.Intent

/** Ephemeral system intent; interrupted approval is recovered through the notification or retry. */
object PendingApproval {
    var id: String? = null
    var intent: Intent? = null

    fun clear() {
        id = null
        intent = null
    }
}
