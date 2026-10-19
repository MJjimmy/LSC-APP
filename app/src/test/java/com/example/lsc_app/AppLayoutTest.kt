package com.example.lsc_app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AppLayoutTest {
    private fun layout() = File("src/main/res/layout/activity_main.xml").readText()
    @Test fun noExtraNativeToolbarAboveAccountForms() {
        val xml = layout()
        assertFalse(xml.contains("@+id/appPageTitle"))
        assertFalse(xml.contains("@+id/appWelcomeButton"))
        assertFalse(xml.contains("@+id/appBackButton"))
    }
    @Test fun noFullScreenLoadingLayerCanCoverAccountFields() {
        assertFalse(layout().contains("@+id/pageLoading"))
    }
}
