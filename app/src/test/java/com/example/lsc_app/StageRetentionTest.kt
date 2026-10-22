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
