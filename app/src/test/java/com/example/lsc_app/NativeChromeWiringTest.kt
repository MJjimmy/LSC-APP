package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** XML/Activity wiring contract, not device rendering or lifecycle instrumentation. */
class NativeChromeWiringTest {
    @Test fun noExtraNativeNavigationBarAboveWebsitePages() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        for (id in listOf("workspaceToolbar", "workspaceBack", "workspaceTitle", "workspaceRefresh", "workspaceHome")) {
            assertFalse("Remove the unwanted native toolbar: $id", layout.contains("@+id/$id"))
            assertFalse("No listener for removed toolbar: $id", source.contains("R.id.$id"))
        }
    }
    @Test fun staleFinishedPagesCannotRevealOrClearAnotherAccountsHistory() {
        val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        val finish = source.substringAfter("override fun onPageFinished").substringBefore("override fun onReceivedError")
        assertTrue(finish.contains("!loadFailed && view.url == url && url == lastPage"))
    }
    @Test fun processRestoreDoesNotReplayAnOAuthExchangeDocument() {
        val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        assertTrue(source.contains("AppNavigation.retryUrl(restoredUrl) != restoredUrl"))
    }
    @Test fun websiteHasNoNativeTopLoadingStripAndKeepsSafeErrorExit() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        assertFalse("No native loading line or reserved top slot", layout.contains("@+id/pageProgress"))
        assertTrue("Offer safe native exit from the error screen", layout.contains("@+id/errorHomeButton"))
        assertTrue("Expose file validation status without fake percentages", layout.contains("@+id/fileOperationStatus"))
        assertFalse("No full-screen loading cover", layout.contains("@+id/pageLoading"))
        val source = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        assertFalse(source.contains("onPageProgress"))
        assertTrue(source.contains("AppNavigation.backOffset"))
    }
}
