package com.example.lsc_app

internal object FileActionPolicy {
    const val MAX_BYTES = 100L * 1024 * 1024
    const val DOWNLOAD_STAGE_TTL_MS = 60L * 60 * 1000

    /** Cleanup may touch only an owned direct child, never a link/dir or user document. */
    fun ownedDownloadStage(cacheDir: java.io.File, basename: String?): java.io.File? {
        if (basename == null || !basename.matches(Regex("lsc-download-[A-Za-z0-9_-]+\\.part"))) return null
        return runCatching {
            val cache = cacheDir.canonicalFile
            val file = java.io.File(cache, basename).canonicalFile
            file.takeIf { it.parentFile == cache && it.name == basename && it.isFile }
        }.getOrNull()
    }

    /** A basename does not authorize an expired/process-death stage. */
    fun restoreDownloadStage(cacheDir: java.io.File, basename: String?): java.io.File? =
        ownedDownloadStage(cacheDir, basename)?.takeIf {
            it.length() <= MAX_BYTES && System.currentTimeMillis() - it.lastModified() in 0..DOWNLOAD_STAGE_TTL_MS
        }

    /** Opportunistic retention bound, not a background timer. Account changes remove all owned stages. */
    fun reconcileDownloadStages(cacheDir: java.io.File, activeName: String? = null,
                                all: Boolean = false, nowMs: Long = System.currentTimeMillis()): Int =
        (cacheDir.listFiles() ?: emptyArray()).count { candidate ->
            val owned = ownedDownloadStage(cacheDir, candidate.name)
            owned != null && owned.name != activeName &&
                (all || nowMs - owned.lastModified() !in 0..DOWNLOAD_STAGE_TTL_MS) && owned.delete()
        }

    fun safeFilename(name: String): String = name.replace('\\', '/').substringAfterLast('/')
        .map { if (it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() || it in ":*?\"<>|") '_' else it }
        .joinToString("").trim().trim('.').take(120).ifBlank { "download" }

    fun acceptTypes(types: Array<String>): List<String> {
        val requested = types.flatMap { it.split(',') }.map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter { it.isNotEmpty() }
        if (requested.isEmpty()) return listOf("*/*")
        return requested.filter {
            it.matches(Regex("[a-z0-9!#$&^_.+-]+/(?:[a-z0-9!#$&^_.+-]+|\\*)")) || it == "*/*"
        }.distinct()
    }

    fun mimeAllowed(mime: String?, types: List<String>): Boolean = types.any {
        it == "*/*" || (mime != null && (it.equals(mime, true) ||
            (it.endsWith("/*") && mime.startsWith(it.substringBefore('/') + "/", true))))
    }

    fun selectionAllowed(count: Int, multiple: Boolean): Boolean = count in 1..if (multiple) 10 else 1

    fun downloadAllowed(status: Int, mime: String?, size: Long): Boolean =
        status == 200 && size <= MAX_BYTES && mime?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT) !in
            setOf("text/html", "application/xhtml+xml")

    fun looksLikeHtml(bytes: ByteArray): Boolean {
        val prefix = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase(java.util.Locale.ROOT)
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html") ||
            prefix.startsWith("<head") || prefix.startsWith("<body") || prefix.startsWith("<script")
    }

    fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, limit: Long = MAX_BYTES,
                    canceled: () -> Boolean = { false }): Long {
        var total = 0L
        val buffer = ByteArray(8192)
        while (true) {
            if (canceled() || Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val count = input.read(buffer)
            if (canceled() || Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            if (count == -1) return total
            total += count
            if (total > limit) throw java.io.IOException("File exceeds limit")
            output.write(buffer, 0, count)
        }
    }
    fun isTrusted(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return WebRequestPolicy.isMainFrameAllowed(url) && uri.rawFragment == null
    }
}
