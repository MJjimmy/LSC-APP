package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Static callback wiring regression; does not simulate an Android WebView. */
class WebViewBoundaryWiringTest {
    @Test fun mainFramePostAndRedirectHaveIndependentFailClosedGuards() {
        val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        assertTrue("POST must be checked outside shouldOverrideUrlLoading",
            source.contains("override fun shouldInterceptRequest"))
        assertTrue("Redirect destinations must be checked at actual page start",
            source.contains("WebRequestPolicy.isMainFrameAllowed(url)"))
        assertTrue("Check request role, not just its host", source.contains("request.isForMainFrame"))
    }
}
