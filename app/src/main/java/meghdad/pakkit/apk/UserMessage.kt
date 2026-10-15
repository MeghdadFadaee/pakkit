package meghdad.pakkit.apk

/** Only deliberately written product copy may cross an exception boundary into the UI. */
class UserMessage(message: String) : IllegalArgumentException(message)

inline fun requireUser(value: Boolean, message: () -> String) {
    if (!value) throw UserMessage(message())
}

fun Throwable.userMessage(fallback: String): String =
    if (this is UserMessage) message ?: fallback else fallback
