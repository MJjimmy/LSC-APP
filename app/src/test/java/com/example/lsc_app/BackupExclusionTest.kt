package com.example.lsc_app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class BackupExclusionTest {
    @Test fun manifestLinksRulesExcludingEveryPrivateDomainForBothTransferModes() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val app = manifest.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals("false", app.getAttributeNS(ns, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(ns, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(ns, "dataExtractionRules"))
        val domains = setOf("root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref")
        fun exclusions(path: String, section: String) {
            val doc = factory.newDocumentBuilder().parse(File(path))
            val entries = (doc.getElementsByTagName(section).item(0) as org.w3c.dom.Element).getElementsByTagName("exclude")
            val actual = (0 until entries.length).map { entries.item(it) as org.w3c.dom.Element }
            assertEquals(domains, actual.filter { it.getAttribute("path") == "." }.map { it.getAttribute("domain") }.toSet())
        }
        exclusions("src/main/res/xml/backup_rules.xml", "full-backup-content")
        exclusions("src/main/res/xml/data_extraction_rules.xml", "cloud-backup")
        exclusions("src/main/res/xml/data_extraction_rules.xml", "device-transfer")
    }
}
