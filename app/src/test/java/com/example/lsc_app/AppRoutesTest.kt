package com.example.lsc_app

import org.junit.Assert.*
import org.junit.Test

class AppRoutesTest {
    @Test fun createAccountOpensRegistrationDirectly() {
        assertEquals("https://lsc.up.railway.app/register.php?app=android", AppRoutes.authUrl(true))
    }
    @Test fun existingAccountOpensSignInDirectly() {
        assertEquals("https://lsc.up.railway.app/login.php?app=android", AppRoutes.authUrl(false))
    }
    @Test fun websiteLandingRoutesReturnToNativeWelcome() {
        assertTrue(AppRoutes.isLandingPage("https://lsc.up.railway.app/"))
        assertTrue(AppRoutes.isLandingPage("https://lsc.up.railway.app/index.php?logged_out=1"))
    }
    @Test fun applicationPagesStayInsideTheApp() {
        assertFalse(AppRoutes.isLandingPage("https://lsc.up.railway.app/dashboard.php"))
        assertFalse(AppRoutes.isLandingPage("https://lsc.up.railway.app/login.php?app=android"))
        assertFalse(AppRoutes.isLandingPage("https://lsc.up.railway.app/register.php?app=android"))
    }
}
