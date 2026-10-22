package com.example.lsc_app

import java.net.URI

/** Main documents and static subresources have deliberately different trust boundaries. */
internal object WebRequestPolicy {
    private fun httpsUri(url: String): URI? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme.equals("https", true) && !it.host.isNullOrEmpty() &&
            it.rawUserInfo == null && (it.port == -1 || it.port == 443) }
    }

    fun isMainFrameAllowed(url: String): Boolean =
        httpsUri(url)?.host.equals("lsc.up.railway.app", true)

    fun isRequestAllowed(url: String, mainFrame: Boolean, method: String): Boolean {
        if (isMainFrameAllowed(url)) return true // Includes trusted form POSTs and fragments.
        if (mainFrame || method != "GET") return false
        val uri = httpsUri(url) ?: return false
        // Explicitly trusted static dependencies observed on the LSC login/register pages.
        // They are not document/navigation authorities and cannot receive native file actions.
        return when (uri.host.lowercase(java.util.Locale.ROOT)) {
            "fonts.googleapis.com" -> uri.rawPath == "/css2"
            "fonts.gstatic.com" -> uri.rawPath.startsWith("/s/inter/")
            "cdn.jsdelivr.net" -> uri.rawPath == "/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" ||
                uri.rawPath.startsWith("/npm/bootstrap-icons@1.11.3/font/fonts/") &&
                (uri.rawPath.endsWith(".woff") || uri.rawPath.endsWith(".woff2"))
            else -> false
        }
    }
}
