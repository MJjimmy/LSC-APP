package com.example.lsc_app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class StageRetentionTest {
    @Test fun expiredStageCannotBeRestoredAfterProcessDeath() {
        val cache = Files.createTempDirectory("lsc-stage-expiry-test").toFile()
        try {
            val stage = java.io.File(cache, "lsc-download-expired.part").apply { writeText("synthetic") }
            assertTrue(stage.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L))
            assertNull("A two-hour-old stage must not reopen a prior session export",
                FileActionPolicy.restoreDownloadStage(cache, stage.name))
        } finally { cache.deleteRecursively() }
    }

    @Test fun cleanupExpiresOnlyOwnedDirectChildrenAndExemptsAnActiveStage() {
        val cache = Files.createTempDirectory("lsc-cleanup-test").toFile()
        val outside = Files.createTempFile("lsc-download-outside", ".part").toFile()
        try {
            val old = java.io.File(cache, "lsc-download-old.part").apply { writeText("synthetic") }
            val active = java.io.File(cache, "lsc-download-active.part").apply { writeText("synthetic") }
            val fresh = java.io.File(cache, "lsc-download-fresh.part").apply { writeText("synthetic") }
            val other = java.io.File(cache, "not-a-stage.part").apply { writeText("keep") }
            val directory = java.io.File(cache, "lsc-download-dir.part").apply { mkdir() }
            val now = System.currentTimeMillis()
            assertTrue(old.setLastModified(now - 2 * 60 * 60 * 1000L))
            assertTrue(active.setLastModified(now - 2 * 60 * 60 * 1000L))
            assertEquals(1, FileActionPolicy.reconcileDownloadStages(cache, active.name, nowMs = now))
            assertFalse(old.exists())
            assertTrue(active.exists())
            assertTrue(fresh.exists())
            assertEquals(2, FileActionPolicy.reconcileDownloadStages(cache, all = true))
            assertTrue(other.exists())
            assertTrue(directory.exists())
            assertTrue(outside.exists())
        } finally { cache.deleteRecursively(); outside.delete() }
    }

    @Test fun cancelDuringReadDoesNotWriteTheReturnedBytes() {
        var canceled = false
        val input = object : ByteArrayInputStream(ByteArray(8)) {
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                val count = super.read(buffer, off, len)
                canceled = true // Account boundary while the provider/network read was blocked.
                return count
            }
        }
        val output = ByteArrayOutputStream()
        try {
            FileActionPolicy.copyBounded(input, output) { canceled }
            fail("Canceled copy must stop")
        } catch (_: InterruptedIOException) { /* expected */ }
        assertEquals("Do not emit bytes after observing revocation", 0, output.size())
    }
}
