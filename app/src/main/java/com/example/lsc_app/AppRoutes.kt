package com.example.lsc_app

import java.net.URI

internal object AppRoutes {
    const val BASE_URL = "https://lsc.up.railway.app"

    fun authUrl(register: Boolean): String =
        "$BASE_URL/${if (register) "register.php" else "login.php"}?app=android"

    fun helpUrl(): String = "$BASE_URL/help.php?app=android"

    fun isLandingPage(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host.equals("lsc.up.railway.app", true) &&
            uri.path.orEmpty() in setOf("", "/", "/index.php")
    }
}
