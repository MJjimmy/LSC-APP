package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring regression, not proof of platform lifecycle behavior. */
class DownloadBoundaryWiringTest {
    @Test fun navigationAndAuthenticationInvalidateDownloadGeneration() {
        val activity = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        val controller = File("src/main/java/com/example/lsc_app/WebFileActions.kt").readText()
        assertTrue("All top-level navigation must revoke previous download ownership",
            activity.substringAfter("override fun onPageStarted").substringBefore("override fun onPageFinished")
                .contains("fileActions.cancelForAccountBoundary()"))
        for (entry in listOf("private fun beginGoogleSignIn()", "private fun openAccountPage", "private fun showWelcome")) {
            assertTrue(entry, activity.substringAfter(entry).substringBefore("\n    }").contains("fileActions.cancelForAccountBoundary()"))
        }
        assertTrue(controller.contains("fun cancelForAccountBoundary()"))
        assertTrue("Network/copy/picker must share cancellation", controller.contains("downloadSession.isCurrent(generation)"))
        assertFalse("Never remove user-chosen exports", controller.contains("deleteDocument("))
    }
}
