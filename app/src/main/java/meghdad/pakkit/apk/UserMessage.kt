package meghdad.pakkit.apk

/** Only deliberately written product copy may cross an exception boundary into the UI. */
class UserMessage(message: String) : IllegalArgumentException(message)

inline fun requireUser(value: Boolean, message: () -> String) {
    if (!value) throw UserMessage(message())
}

fun Throwable.userMessage(fallback: String): String =
    when {
        this is UserMessage -> message ?: fallback
        generateSequence(this) { it.cause }
            .any {
                it is android.system.ErrnoException && it.errno == android.system.OsConstants.ENOSPC
            } -> "Free some space on your watch, then try again."
        else -> fallback
    }
