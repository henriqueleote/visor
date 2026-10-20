package com.macedonga.visor.core.identity.token

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Where the provisioning token comes from. */
/** [unreachable]: Meta was not reached at all, so the login itself may well be fine. */
class TokenError(message: String, val unreachable: Boolean = false) : Exception(message)

class Token(
    val value: String,
    /** "ar" or "fb". */
    val universe: String,
) {
    /** Never the value. This ends up in logs. */
    override fun toString(): String = "Token(universe=$universe)"
}

fun interface TokenSource {
    fun get(): Token
}

/** The user agent both Meta auth calls send. */
internal const val META_USER_AGENT =
    "Dalvik/2.1.0 (Linux; U; Android 15) " +
        "[FBAN/Stella;FBAV/289.0.0.25.162;FBSN/Android;FBSV/15;FBLC/en_US]"

/** A JSON string or number as Python's `str()` would show it, or null when absent. */
internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** `sorted(payload)` as Python prints it in an error message: `['a', 'b']`. */
internal fun JsonObject.sortedKeys(): String = keys.sorted().joinToString(", ", "[", "]") { "'$it'" }
