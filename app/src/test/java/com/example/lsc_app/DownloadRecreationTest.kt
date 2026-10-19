package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring guard, not an Android lifecycle/instrumentation test. */
class DownloadRecreationTest {
    @Test fun savePickerStageIsHandedAcrossActivityRecreation() {
        val activity = File("src/main/java/com/example/lsc_app/MainActivity.kt").readText()
        val controller = File("src/main/java/com/example/lsc_app/WebFileActions.kt").readText()
        assertTrue("Restore the pending save before receiving picker results",
            activity.contains("fileActions.restoreState(savedInstanceState)"))
        assertTrue(activity.contains("fileActions.saveState(outState)"))
        assertTrue(activity.contains("fileActions.close(isChangingConfigurations)"))
        assertFalse("A missing stage must not silently accept the picker result",
            controller.contains("if (file == null) return true"))
    }
}
