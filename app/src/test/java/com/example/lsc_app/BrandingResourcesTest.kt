package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Resource-contract tests; real launch and contrast still need device checks. */
class BrandingResourcesTest {
    @Test fun adaptiveBrandingSupportsThemedIconsAndLightSurfaces() {
        val res = File("src/main/res")
        val themed = File(res, "mipmap-anydpi-v33/ic_launcher.xml")
        assertTrue("Provide an Android themed launcher icon", themed.isFile)
        assertTrue(themed.readText().contains("monochrome"))
        assertTrue(File(res, "values/lsc_native.xml").readText().contains("lsc_surface"))
        assertTrue(File(res, "values/lsc_native.xml").readText().contains("Theme.LSC.Launch"))
        val launch = File(res, "values-v31/lsc_native.xml").readText()
        assertTrue(launch.contains("windowSplashScreenAnimatedIcon"))
        assertFalse("No timed splash hold", launch.contains("windowSplashScreenAnimationDuration"))
    }
}
