package com.example.lsc_app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadSessionTest {
    @Test fun boundaryRevokesWorkerAndDeletesPrivateStageButNotExport() {
        val cache = Files.createTempDirectory("lsc-session-test").toFile()
        val exported = Files.createTempFile("lsc-chosen-export-test", ".pdf").toFile()
        try {
            exported.writeText("user-owned synthetic document")
            val session = DownloadSession(cache)
            val token = session.begin("synthetic-account-a")
            val stage = File(cache, "lsc-download-current.part").apply { writeText("synthetic") }
            assertTrue(session.attach(token, stage))
            assertTrue(session.owns(stage))
            assertFalse(session.matchesCookie("synthetic-account-b"))
            session.cancel()
            assertFalse(session.isCurrent(token))
            assertFalse(stage.exists())
            assertTrue(exported.exists())
            val late = File(cache, "lsc-download-late.part").apply { writeText("synthetic") }
            assertFalse(session.attach(token, late))
            assertFalse(late.exists())
            assertTrue(exported.exists())
        } finally { cache.deleteRecursively(); exported.delete() }
    }

    @Test fun onlyRetainedLeaseAuthorizesPickerAcrossRotationNotProcessDeath() {
        val cache = Files.createTempDirectory("lsc-session-rotation-test").toFile()
        try {
            val retained = DownloadSession(cache)
            val token = retained.begin("synthetic-account-a")
            val stage = File(cache, "lsc-download-picker.part").apply { writeText("synthetic") }
            assertTrue(retained.attach(token, stage))
            assertTrue(retained.owns(File(cache, stage.name)))
            assertTrue(retained.matchesCookie("synthetic-account-a"))
            val afterProcessDeath = DownloadSession(cache)
            assertFalse(afterProcessDeath.owns(stage))
            retained.discard(stage)
            assertFalse(stage.exists())
        } finally { cache.deleteRecursively() }
    }

    @Test fun discardCannotDeleteUnrelatedPrivateOrExternalFiles() {
        val cache = Files.createTempDirectory("lsc-session-scope-test").toFile()
        val external = Files.createTempFile("lsc-download-external", ".part").toFile()
        try {
            val unrelated = File(cache, "other-file.part").apply { writeText("keep") }
            val session = DownloadSession(cache)
            session.discard(unrelated)
            session.discard(external)
            session.cancel()
            assertTrue(unrelated.exists())
            assertTrue(external.exists())
        } finally { cache.deleteRecursively(); external.delete() }
    }
}
