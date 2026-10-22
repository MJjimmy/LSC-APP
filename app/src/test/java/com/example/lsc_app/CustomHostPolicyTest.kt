package com.example.lsc_app

import org.junit.Assert.*
import org.junit.Test

class CustomHostPolicyTest {
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
