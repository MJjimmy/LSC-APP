package com.example.lsc_app

import org.junit.Assert.*
import org.junit.Test

class CustomHostPolicyTest {
    @Test fun trustedHostsDoNotShareNativeDownloadOrCookieAuthority() {
        val railway = "https://lsc.up.railway.app/download.php"
        val custom = "https://lsc.jkskinetics.co.za/download.php"
        assertFalse(WebRequestPolicy.sameOrigin(railway, custom))
        assertTrue(WebRequestPolicy.sameOrigin(custom, "https://LSC.JKSKINETICS.CO.ZA:443/file.pdf"))
        assertFalse(WebRequestPolicy.sameOrigin(custom, "https://jkskinetics.co.za/file.pdf"))
        assertFalse(WebRequestPolicy.sameOrigin("not a URL", "not a URL"))
        val controller = java.io.File("src/main/java/com/example/lsc_app/WebFileActions.kt").readText()
        assertTrue(controller.contains("WebRequestPolicy.sameOrigin(webView.url.orEmpty(), url)"))
        assertTrue(controller.contains("WebRequestPolicy.origin(webView.url.orEmpty())"))
    }

    @Test fun onlyExplicitCustomHttpsHostJoinsTrustWithoutChangingStartingOrigin() {
        val custom = "https://lsc.jkskinetics.co.za"
        assertTrue(WebRequestPolicy.isMainFrameAllowed("$custom/login.php"))
        assertTrue(FileActionPolicy.isTrusted("$custom/report.pdf"))
        assertEquals("https://lsc.up.railway.app", AppRoutes.BASE_URL)
        for (bad in listOf("http://lsc.jkskinetics.co.za/", "https://jkskinetics.co.za/",
            "https://www.jkskinetics.co.za/", "https://other.jkskinetics.co.za/",
            "https://lsc.jkskinetics.co.za.evil.example/", "https://lsc.jkskinetics.co.za:444/",
            "https://user@lsc.jkskinetics.co.za/", "https://lsc.jkskinetics.co.za@evil.example/")) {
            assertFalse(bad, WebRequestPolicy.isMainFrameAllowed(bad))
            assertFalse(bad, FileActionPolicy.isTrusted(bad))
        }
    }
}
