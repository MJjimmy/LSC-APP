package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring checks, not a simulation of Android Activity/browser lifecycle events. */
class GoogleSignInReturnWiringTest {
    private val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
    private fun section(start: String, end: String): String =
        source.substringAfter(start, "").substringBefore(end)

    @Test fun repeatedTapCannotReplaceThePendingVerifier() {
        val start = section("private fun beginGoogleSignIn()", "private fun handleBrowserReturn")
        val guard = start.indexOf("if (googleSignInInProgress) return")
        val create = start.indexOf("AppAuthFlow.create")
        assertTrue("Ignore another tap before generating or saving a replacement flow", guard >= 0 && guard < create)
        assertTrue(start.contains("googleSignInInProgress = true"))
    }

    @Test fun handingOffToBrowserLeavesASafeNativeScreen() {
        val start = section("private fun beginGoogleSignIn()", "private fun handleBrowserReturn")
        val welcome = start.indexOf("showWelcome(false)")
        assertTrue("Stop the interrupted WebView and show the welcome screen before leaving",
            welcome >= 0 && welcome < start.indexOf("startActivity("))
    }

    @Test fun cancelledReturnClearsPendingWithoutPostingACode() {
        val cancel = section("private fun finishCancelledGoogleSignIn()", "private fun applySystemInsets")
        assertTrue(cancel.contains("prefs.contains(\"state\")"))
        assertTrue(cancel.contains("prefs.edit().clear().apply()"))
        assertTrue(cancel.contains("googleSignInInProgress = false"))
        assertTrue(cancel.contains("showWelcome(false)"))
        assertFalse("Cancellation must never submit or replay an exchange", cancel.contains("postUrl("))
    }

    @Test fun cancellationIsHandledBeforeTheWebViewResumes() {
        val resume = section("override fun onResume()", "override fun onActivityResult")
        val cancel = resume.indexOf("finishCancelledGoogleSignIn()")
        assertTrue(cancel >= 0 && cancel < resume.indexOf("webView.onResume()"))
    }

    @Test fun verifiedReturnDisarmsCancellationBeforePosting() {
        val callback = section("private fun handleBrowserReturn", "private fun finishCancelledGoogleSignIn")
        val disarm = callback.indexOf("googleSignInInProgress = false")
        assertTrue(disarm > callback.indexOf("if (body == null)"))
        assertTrue(disarm < callback.indexOf("prefs.edit().clear().apply()"))
        assertTrue(callback.indexOf("prefs.edit().clear().apply()") < callback.indexOf("webView.postUrl("))
        assertTrue(callback.contains("AppAuthFlow.exchangeBody"))
    }

    @Test fun missingBrowserReleasesTheTapGuard() {
        val failure = section("catch (_: ActivityNotFoundException)", "private fun handleBrowserReturn")
        assertTrue(failure.contains("googleSignInInProgress = false"))
        assertTrue(failure.contains("prefs.edit().clear().apply()"))
    }
}
