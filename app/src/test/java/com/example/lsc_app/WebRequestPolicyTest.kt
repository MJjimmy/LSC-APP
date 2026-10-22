package com.example.lsc_app

import org.junit.Assert.*
import org.junit.Test

class WebRequestPolicyTest {
    private val home = "https://lsc.up.railway.app"
    @Test fun postRequiresSameMainFrameAuthority() {
        assertTrue(WebRequestPolicy.isRequestAllowed("$home/login.php", true, "POST"))
        assertFalse(WebRequestPolicy.isRequestAllowed("https://evil.example/", true, "POST"))
    }
    @Test fun redirectsHaveNoMainFrameAuthorityExemption() {
        listOf("https://accounts.google.com/", "https://fonts.googleapis.com/css2", "http://lsc.up.railway.app/",
            "https://lsc.up.railway.app.evil.example/", "https://lsc.up.railway.app@evil.example/",
            "https://evil.example@lsc.up.railway.app/", "https://lsc.up.railway.app:444/", "file:///tmp/a",
            "data:text/html,hello", "javascript:alert(1)", "https://lsc.up.railway.app\\@evil.example/")
            .forEach { assertFalse(it, WebRequestPolicy.isMainFrameAllowed(it)) }
        assertTrue(WebRequestPolicy.isMainFrameAllowed("$home:443/help.php#section"))
    }
    @Test fun staticResourcePermissionIsNotNavigationPermission() {
        val css = "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css"
        assertTrue(WebRequestPolicy.isRequestAllowed(css, false, "GET"))
        assertFalse(WebRequestPolicy.isRequestAllowed(css, true, "GET"))
        assertFalse(WebRequestPolicy.isRequestAllowed(css, false, "POST"))
        assertFalse(WebRequestPolicy.isRequestAllowed("https://cdn.jsdelivr.net/npm/unreviewed/index.js", false, "GET"))
        assertFalse(WebRequestPolicy.isRequestAllowed("https://cdn.jsdelivr.net:444/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css", false, "GET"))
        assertFalse(WebRequestPolicy.isRequestAllowed("https://unknown.example/script.js", false, "GET"))
        assertTrue(WebRequestPolicy.isRequestAllowed("https://fonts.googleapis.com/css2?family=Inter", false, "GET"))
        assertTrue(WebRequestPolicy.isRequestAllowed("https://fonts.gstatic.com/s/inter/v1/font.woff2", false, "GET"))
        assertTrue(WebRequestPolicy.isRequestAllowed("$home/api.php", false, "POST"))
    }
}
