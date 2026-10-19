package com.example.lsc_app

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingDownloadStageTest {
    @get:Rule val temporary = TemporaryFolder(File("build").apply { mkdirs() })

    @Test fun restoredPickerStageKeepsExactBytesWithoutAnyActivityReference() {
        val cache = temporary.newFolder("cache")
        val stage = File.createTempFile("lsc-download-", ".part", cache)
        stage.writeText("saved download contents")
        val restored = FileActionPolicy.restoreDownloadStage(cache, stage.name)
        assertEquals(stage.canonicalFile, restored)
        assertEquals("saved download contents", restored!!.readText())
    }

    @Test fun missingStageCannotBeRestoredAfterProcessDeathOrCacheEviction() {
        val cache = temporary.newFolder("cache")
        assertNull(FileActionPolicy.restoreDownloadStage(cache, null))
        assertNull(FileActionPolicy.restoreDownloadStage(cache, "lsc-download-missing.part"))
        val stage = File.createTempFile("lsc-download-", ".part", cache)
        stage.delete()
        assertNull(FileActionPolicy.restoreDownloadStage(cache, stage.name))
    }

    @Test fun restoreRejectsTraversalAbsolutePathsAndUnownedFilesWithoutDeletingThem() {
        val cache = temporary.newFolder("cache")
        val outside = temporary.newFile("lsc-download-outside.part").apply { writeText("private") }
        File(cache, "private.txt").writeText("private")
        listOf("../${outside.name}", "..\\${outside.name}", outside.absolutePath,
            "private.txt", "lsc-download-.part", "lsc-download-nested/file.part",
            "lsc-download-x.part:stream").forEach {
            assertNull(it, FileActionPolicy.restoreDownloadStage(cache, it))
        }
        assertEquals("private", outside.readText())
        assertEquals("private", File(cache, "private.txt").readText())
    }

    @Test fun restoreAcceptsLimitButRejectsOversizeAndDirectories() {
        val cache = temporary.newFolder("cache")
        val stage = File.createTempFile("lsc-download-", ".part", cache)
        RandomAccessFile(stage, "rw").use { it.setLength(FileActionPolicy.MAX_BYTES) }
        assertNotNull(FileActionPolicy.restoreDownloadStage(cache, stage.name))
        RandomAccessFile(stage, "rw").use { it.setLength(FileActionPolicy.MAX_BYTES + 1) }
        assertNull(FileActionPolicy.restoreDownloadStage(cache, stage.name))
        File(cache, "lsc-download-directory.part").mkdir()
        assertNull(FileActionPolicy.restoreDownloadStage(cache, "lsc-download-directory.part"))
    }
}
