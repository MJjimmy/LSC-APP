package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source-wiring checks only; picker grants and external viewers need device testing. */
class FileFeedbackWiringTest {
    private val source = File("src/main/java/com/example/lsc_app/WebFileActions.kt").readText()
    @Test fun validationAndDownloadReportIndeterminateStatus() {
        assertTrue(source.contains("operationStatus(\"Checking selected files"))
        assertTrue(source.contains("operationStatus(\"Downloading"))
        assertTrue(source.contains("operationStatus(\"Saving document"))
        assertTrue(source.contains("operationStatus(null)"))
    }
    @Test fun savedDocumentsUseReadGrantedContentUrisForOpenAndShare() {
        assertTrue(source.contains("private fun offerSavedDocument"))
        assertTrue(source.contains("Intent.ACTION_SEND"))
        assertTrue(source.contains("Intent.ACTION_VIEW"))
        assertTrue(source.contains("ClipData.newRawUri"))
        assertTrue(source.contains("hasGrant(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)"))
        assertFalse(source.contains("Uri.fromFile"))
    }
    @Test fun accountChangesDismissCompletedDownloadActions() {
        val cancel = source.substringAfter("fun cancelForAccountBoundary()").substringBefore("fun checkSessionCookie")
        assertTrue(cancel.contains("completedDownloadDialog?.dismiss()"))
        val saved = source.substringAfter("private fun offerSavedDocument").substringBefore("private fun uploadStillTrusted")
        assertTrue(saved.contains("downloadSession.isCurrent(generation)"))
        assertTrue(saved.contains("downloadSession.matchesCookie"))
    }
}
