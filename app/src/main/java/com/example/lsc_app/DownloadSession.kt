package com.example.lsc_app

import java.io.File

/** Process-memory ownership only. Never serialize cookies or recover another process's lease. */
internal class DownloadSession(private val cacheDir: File) {
    @Volatile private var generation = 0L
    private var cookie: String? = null
    private val stages = mutableSetOf<File>()

    @Synchronized fun begin(sessionCookie: String?): Long {
        cancel()
        cookie = sessionCookie
        return generation
    }

    fun currentGeneration(): Long = generation
    fun isCurrent(candidate: Long): Boolean = generation == candidate
    @Synchronized fun matchesCookie(candidate: String?): Boolean = cookie == candidate

    @Synchronized fun attach(candidate: Long, file: File): Boolean {
        val owned = FileActionPolicy.restoreDownloadStage(cacheDir, file.name) ?: return false
        if (!isCurrent(candidate) || owned != file.canonicalFile) {
            discard(file)
            return false
        }
        stages += owned
        return true
    }

    @Synchronized fun owns(file: File): Boolean = file.canonicalFile in stages

    @Synchronized fun discard(file: File) {
        // Accept only direct-child private stage files; destinations never enter this set.
        val owned = FileActionPolicy.restoreDownloadStage(cacheDir, file.name)
        if (owned == file.canonicalFile) {
            stages.remove(owned)
            owned.delete()
        }
    }

    @Synchronized fun cancel() {
        generation++ // Workers and queued picker completions immediately lose authority.
        cookie = null
        stages.forEach { it.delete() }
        stages.clear()
    }
}
