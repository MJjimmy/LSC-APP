package com.example.lsc_app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** System document pickers only; no storage permission, JS bridge, or background service. */
internal class WebFileActions(private val activity: Activity, private val webView: WebView,
                              private val downloadSession: DownloadSession = DownloadSession(activity.cacheDir)) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    @Volatile private var connection: HttpsURLConnection? = null
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var uploadPickerOpen = false
    private var uploadTypes = listOf("*/*")
    private var uploadMultiple = false
    private var uploadPage: String? = null
    @Volatile private var uploadGeneration = 0
    private var downloadBusy = false
    private var downloadFile: File? = null
    private var savedDownloadName: String? = null
    private var downloadPickerOpen = false

    fun retainedDownloadSession(): DownloadSession = downloadSession

    /** Conservative boundary: every real document navigation, welcome, or auth entry revokes exports. */
    fun cancelForAccountBoundary() {
        cancelUpload()
        val hadDownload = downloadBusy
        downloadSession.cancel()
        downloadBusy = false
        downloadFile = null
        savedDownloadName = null
        val active = connection
        Thread { active?.disconnect() }.start()
        // Leave pickerOpen set until its stale result arrives; do not reuse its request code.
        if (hadDownload) message("Download canceled because the page or account changed. If saving had begun, the selected document may be incomplete.")
    }

    fun checkSessionCookie() {
        if (downloadBusy && !downloadSession.matchesCookie(currentSessionCookie())) cancelForAccountBoundary()
    }

    private fun currentSessionCookie(): String? = CookieManager.getInstance().getCookie(AppRoutes.BASE_URL + "/")

    fun saveState(state: Bundle) {
        // No cookies, URLs, grants or provider URIs are saved by this feature.
        savedDownloadName = downloadFile?.name
        state.putBoolean(STATE_DOWNLOAD_PENDING, downloadBusy)
        state.putBoolean(STATE_DOWNLOAD_PICKER_OPEN, downloadPickerOpen)
        state.putString(STATE_DOWNLOAD_NAME, savedDownloadName)
    }

    fun restoreState(state: Bundle?) {
        downloadPickerOpen = state?.getBoolean(STATE_DOWNLOAD_PICKER_OPEN) == true
        if (state?.getBoolean(STATE_DOWNLOAD_PENDING) != true) return
        val restored = FileActionPolicy.restoreDownloadStage(activity.cacheDir,
            state.getString(STATE_DOWNLOAD_NAME))
        // Only the retained, in-memory lease can authorize a stage after rotation.
        // Process death does not restore account/cookie authority from Bundle data.
        downloadFile = restored?.takeIf { downloadSession.owns(it) && downloadSession.matchesCookie(currentSessionCookie()) }
        if (downloadFile == null) restored?.let { downloadSession.discard(it) }
        downloadBusy = downloadFile != null
        if (!downloadBusy) message("Download canceled: the temporary file is unavailable. Please download again.")
    }

    val chromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>,
                                       params: FileChooserParams): Boolean {
            if (closed || uploadPickerOpen || uploadCallback != null ||
                !FileActionPolicy.isTrusted(view.url.orEmpty()) ||
                params.mode !in setOf(FileChooserParams.MODE_OPEN, FileChooserParams.MODE_OPEN_MULTIPLE)) {
                callback.onReceiveValue(null)
                message("File selection unavailable. Open the file from an LSC page.")
                return true
            }
            uploadCallback = callback
            uploadPage = view.url
            val requestedTypes = params.acceptTypes.flatMap { it.split(',') }.map { token ->
                val type = token.trim().lowercase(java.util.Locale.ROOT)
                if (type.startsWith('.')) android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(type.drop(1)) ?: type else type
            }.toTypedArray()
            uploadTypes = FileActionPolicy.acceptTypes(requestedTypes)
            if (uploadTypes.isEmpty()) {
                cancelUpload()
                message("This file type is not supported by the document picker.")
                return true
            }
            uploadMultiple = params.mode == FileChooserParams.MODE_OPEN_MULTIPLE
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (uploadTypes.size == 1) uploadTypes.first() else "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, uploadTypes.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, uploadMultiple)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                uploadPickerOpen = true
                activity.startActivityForResult(picker, REQUEST_UPLOAD)
            } catch (_: RuntimeException) {
                uploadPickerOpen = false
                cancelUpload()
                message("No document picker available.")
            }
            return true
        }
    }

    fun cancelUpload() {
        uploadGeneration++
        val callback = uploadCallback
        uploadCallback = null
        uploadPage = null
        callback?.onReceiveValue(null)
    }

    fun onActivityResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request == REQUEST_UPLOAD) {
            uploadPickerOpen = false
            if (uploadCallback == null) return true // A navigation or recreation canceled it.
            if (result != Activity.RESULT_OK || data == null || !uploadStillTrusted()) {
                cancelUpload()
                return true
            }
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip ->
                // Bound before iterating an untrusted picker response.
                if (clip.itemCount <= 10) repeat(clip.itemCount) {
                    clip.getItemAt(it).uri?.let { uri -> uris += uri }
                }
            } ?: data.data?.let { uris += it }
            val selected = uris.distinct()
            if (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0 ||
                !FileActionPolicy.selectionAllowed(uris.size, uploadMultiple) ||
                (data.clipData?.itemCount ?: uris.size) != uris.size ||
                selected.any { !hasGrant(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }) {
                cancelUpload()
                message("Choose readable documents (maximum 10 files, 100 MB total).")
                return true
            }
            val generation = uploadGeneration
            val types = uploadTypes.toList()
            worker.execute {
                val valid = runCatching {
                    var remaining = FileActionPolicy.MAX_BYTES
                    for (uri in selected) {
                        if (!FileActionPolicy.mimeAllowed(activity.contentResolver.getType(uri), types))
                            throw IOException("Unsupported file type")
                        activity.contentResolver.openInputStream(uri)?.use {
                            remaining -= FileActionPolicy.copyBounded(it, DISCARD, remaining) {
                                closed || generation != uploadGeneration
                            }
                        } ?: throw IOException("Unreadable document")
                    }
                }.isSuccess
                main.post {
                    if (!closed && generation == uploadGeneration) {
                        if (valid && uploadStillTrusted()) {
                            val callback = uploadCallback
                            uploadCallback = null
                            uploadPage = null
                            callback?.onReceiveValue(selected.toTypedArray())
                        } else {
                            cancelUpload()
                            message("Cannot attach these files. Check type, access and the 100 MB total limit.")
                        }
                    }
                }
            }
            return true
        }
        if (request == REQUEST_DOWNLOAD) {
            downloadPickerOpen = false
            checkSessionCookie()
            val generation = downloadSession.currentGeneration()
            // Recheck after a long picker wait: Android may evict cached files.
            val staged = downloadFile
            val file = FileActionPolicy.restoreDownloadStage(activity.cacheDir, staged?.name)
                ?.takeIf { downloadSession.owns(it) && downloadSession.isCurrent(generation) }
            downloadFile = null
            savedDownloadName = null
            if (file == null) {
                downloadBusy = false
                staged?.let { worker.execute { downloadSession.discard(it) } }
                message("Download canceled: the temporary file is unavailable. Please download again. Nothing was saved.")
                return true
            }
            val uri = data?.data
            if (result != Activity.RESULT_OK || uri == null) {
                downloadBusy = false
                worker.execute { downloadSession.discard(file) }
                return true
            }
            if (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0 ||
                !hasGrant(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
                downloadBusy = false
                worker.execute { downloadSession.discard(file) }
                message("Save location did not grant write access.")
                return true
            }
            worker.execute {
                val saved = runCatching {
                    if (closed || !downloadSession.isCurrent(generation)) throw IOException("Canceled")
                    activity.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        file.inputStream().use { FileActionPolicy.copyBounded(it, output) {
                            closed || !downloadSession.isCurrent(generation)
                        } }
                    } ?: throw IOException("Cannot write destination")
                }.isSuccess
                downloadSession.discard(file) // Never delete the user-chosen destination.
                main.post {
                    if (downloadSession.isCurrent(generation)) {
                        downloadBusy = false
                        message(if (saved && !closed) "Download saved." else
                            "Could not save download. The selected destination may contain an incomplete file.")
                    }
                }
            }
            return true
        }
        return false
    }

    fun download(url: String, disposition: String?, mime: String?, length: Long) {
        if (closed || downloadBusy || downloadPickerOpen) {
            message("Finish or cancel the current download first.")
            return
        }
        if (!FileActionPolicy.isTrusted(webView.url.orEmpty()) || !FileActionPolicy.isTrusted(url) ||
            length > FileActionPolicy.MAX_BYTES) {
            message("Only LSC HTTPS downloads up to 100 MB are supported.")
            return
        }
        // CookieManager scopes the cookie to this exact trusted URL (including path).
        // Never forward it to any redirect, even a same-origin redirect.
        val cookie = CookieManager.getInstance().getCookie(url)
        val filename = FileActionPolicy.safeFilename(URLUtil.guessFileName(url, disposition, mime))
        val generation = downloadSession.begin(currentSessionCookie())
        downloadBusy = true
        message("Downloading… Choose a save location when ready.")
        worker.execute {
            var temporary: File? = null
            try {
                val conn = URL(url).openConnection() as HttpsURLConnection
                connection = conn
                if (closed || !downloadSession.isCurrent(generation)) throw IOException("Canceled")
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.requestMethod = "GET"
                conn.useCaches = false
                conn.setRequestProperty("Accept-Encoding", "identity")
                if (!cookie.isNullOrEmpty()) conn.setRequestProperty("Cookie", cookie)
                if (!FileActionPolicy.downloadAllowed(conn.responseCode, conn.contentType, conn.contentLengthLong))
                    throw IOException("Response rejected")
                temporary = File.createTempFile("lsc-download-", ".part", activity.cacheDir)
                val file = temporary
                if (!downloadSession.attach(generation, file)) throw IOException("Canceled")
                conn.inputStream.buffered().use { input ->
                    input.mark(1024)
                    val prefix = ByteArray(512)
                    var count = 0
                    while (count < prefix.size) {
                        if (closed || !downloadSession.isCurrent(generation)) throw IOException("Canceled")
                        val n = input.read(prefix, count, prefix.size - count)
                        if (n == -1) break
                        count += n
                    }
                    input.reset()
                    if (FileActionPolicy.looksLikeHtml(prefix.copyOf(count))) throw IOException("HTML response")
                    val bytes = file.outputStream().use { output ->
                        FileActionPolicy.copyBounded(input, output) { closed || !downloadSession.isCurrent(generation) }
                    }
                    if (conn.contentLengthLong >= 0 && bytes != conn.contentLengthLong)
                        throw IOException("Incomplete response")
                }
                val saveType = conn.contentType?.substringBefore(';')?.trim()
                    ?.takeIf { FileActionPolicy.acceptTypes(arrayOf(it)) == listOf(it) && it != "*/*" }
                    ?: "application/octet-stream"
                main.post {
                    checkSessionCookie()
                    if (closed || !downloadSession.isCurrent(generation)) {
                        Thread { downloadSession.discard(file) }.start()
                    } else {
                        downloadFile = file
                        try {
                            downloadPickerOpen = true
                            activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = saveType
                                putExtra(Intent.EXTRA_TITLE, filename)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }, REQUEST_DOWNLOAD)
                        } catch (_: RuntimeException) {
                            downloadPickerOpen = false
                            downloadFile = null
                            downloadBusy = false
                            worker.execute { downloadSession.discard(file) }
                            message("No save picker available.")
                        }
                    }
                }
                temporary = null // Ownership transferred to the UI/save-picker flow.
            } catch (_: Exception) {
                temporary?.let { downloadSession.discard(it) }
                main.post {
                    if (downloadSession.isCurrent(generation)) {
                        downloadBusy = false
                        message("Download failed. Check your connection and sign in again if needed. Redirects, HTML and files over 100 MB are refused.")
                    }
                }
            } finally {
                connection?.disconnect()
                connection = null
            }
        }
    }

    private fun uploadStillTrusted(): Boolean = webView.url == uploadPage &&
        FileActionPolicy.isTrusted(webView.url.orEmpty())

    private fun hasGrant(uri: Uri, flag: Int): Boolean = uri.scheme == "content" &&
        !uri.authority.isNullOrEmpty() && activity.checkUriPermission(uri, Process.myPid(), Process.myUid(), flag) ==
        PackageManager.PERMISSION_GRANTED &&
        activity.packageManager.resolveContentProvider(uri.authority!!, 0)?.packageName != activity.packageName

    private fun message(text: String) {
        if (!closed) Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }

    fun close(changingConfigurations: Boolean) {
        closed = true
        cancelUpload()
        val activeConnection = connection
        Thread { activeConnection?.disconnect() }.start()
        // Preserve only a completed stage already handed to saved state, and only
        // with the in-memory lease retained by Activity across configuration change.
        if (!changingConfigurations || downloadFile?.name != savedDownloadName || downloadFile == null)
            downloadSession.cancel()
        downloadFile = null
        // Queued cleanup runs; in-flight reads observe closed and network timeouts.
        worker.shutdown()
    }

    companion object {
        private const val STATE_DOWNLOAD_PICKER_OPEN = "lsc.download_picker_open"
        private const val STATE_DOWNLOAD_PENDING = "lsc.download_pending"
        private const val STATE_DOWNLOAD_NAME = "lsc.download_stage_name"
        const val REQUEST_UPLOAD = 6101
        const val REQUEST_DOWNLOAD = 6102
        private val DISCARD = object : java.io.OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        }
    }
}
