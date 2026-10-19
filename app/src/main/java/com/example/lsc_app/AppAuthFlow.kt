package com.example.lsc_app

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom

/** The browser gets a challenge, never the verifier that redeems the app session. */
internal object AppAuthFlow {
    data class Pending(val state: String, val verifier: String, val createdAtMs: Long)
    private val hex = Regex("[a-f0-9]{64}")
    private const val MAX_AGE_MS = 10 * 60 * 1000L

    fun create(now: Long): Pending {
        val random = SecureRandom()
        fun token(): String = ByteArray(32).also(random::nextBytes).toHex()
        return Pending(token(), token(), now)
    }

    fun browserUrl(pending: Pending): String {
        require(hex.matches(pending.state) && hex.matches(pending.verifier))
        val challenge = MessageDigest.getInstance("SHA-256")
            .digest(pending.verifier.toByteArray(Charsets.UTF_8)).toHex()
        return "${AppRoutes.BASE_URL}/app-auth.php?action=start&challenge=$challenge&state=${pending.state}"
    }

    fun exchangeBody(callback: String, pending: Pending?, now: Long): String? {
        if (pending == null || now < pending.createdAtMs || now - pending.createdAtMs > MAX_AGE_MS) return null
        if (!hex.matches(pending.state) || !hex.matches(pending.verifier)) return null
        val uri = runCatching { URI(callback) }.getOrNull() ?: return null
        if (uri.scheme != "lscapp" || uri.host != "auth" || uri.userInfo != null || uri.port != -1 ||
            uri.path.orEmpty() !in setOf("", "/") || uri.fragment != null) return null
        val values = uri.rawQuery?.split('&')?.map { it.split('=', limit = 2) } ?: return null
        if (values.size != 2 || values.any { it.size != 2 } || values.map { it[0] }.toSet() != setOf("code", "state")) return null
        val fields = values.associate { it[0] to it[1] }
        val code = fields["code"] ?: return null
        val state = fields["state"] ?: return null
        if (!hex.matches(code) || !hex.matches(state) ||
            !MessageDigest.isEqual(state.toByteArray(Charsets.UTF_8), pending.state.toByteArray(Charsets.UTF_8))) return null
        return "code=$code&verifier=${pending.verifier}"
    }

    fun isGoogleStart(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "lscapp" && uri.host == "google" && uri.path.orEmpty() in setOf("", "/") &&
            uri.userInfo == null && uri.port == -1 && uri.rawQuery == null && uri.fragment == null
    }

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
