package com.example.lsc_app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.app.AlertDialog

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ContextMenu
import android.view.WindowInsetsController
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast

import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Native entry experience; the existing PHP backend still owns accounts and data. */
class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var fileActions: WebFileActions
    private lateinit var welcomeScreen: View
    private lateinit var appScreen: View
    private lateinit var pageError: View
    private lateinit var errorMessage: TextView
    private var showingWeb = false
    private var loadFailed = false
    private var restoringInitialPage = false
    private var resetHistoryOnFinish = false
    private var googleSignInInProgress = false
    private var committedPage: String? = null
    private var fileOperationActive = false

    private var lastPage = AppRoutes.authUrl(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LSC_Native)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.lscWebView)
        welcomeScreen = findViewById(R.id.welcomeScreen)
        appScreen = findViewById(R.id.appScreen)
        pageError = findViewById(R.id.pageError)
        errorMessage = findViewById(R.id.pageErrorMessage)

        applySystemInsets()
        configureWebView()
        fileActions.restoreState(savedInstanceState)

        findViewById<View>(R.id.createAccountButton).setOnClickListener { openAccountPage(true) }
        findViewById<View>(R.id.signInButton).setOnClickListener { openAccountPage(false) }
        findViewById<View>(R.id.helpButton).setOnClickListener {
            cancelWelcomeAnimation()
            showApp()
            resetHistoryOnFinish = true
            lastPage = AppRoutes.helpUrl()
            webView.loadUrl(lastPage)
        }
        findViewById<View>(R.id.retryButton).setOnClickListener {
            pageError.visibility = View.GONE
            lastPage = AppNavigation.retryUrl(lastPage)
            webView.loadUrl(lastPage)
        }
        findViewById<View>(R.id.errorHomeButton).setOnClickListener { confirmLeaving { showWelcome(false) } }
        registerForContextMenu(webView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { navigateBack() }
            )
        }
        if (handleBrowserReturn(intent.data)) return
        if (handleInvitation(intent.data)) return
        if (savedInstanceState?.getBoolean(STATE_WEB) == true) {
            showApp()
            lastPage = savedInstanceState.getString(STATE_PAGE) ?: AppRoutes.authUrl(false)
            restoringInitialPage = true
            val restored = webView.restoreState(savedInstanceState)
            val restoredUrl = restored?.currentItem?.url.orEmpty()
            if (restored == null || !WebRequestPolicy.isMainFrameAllowed(restoredUrl) ||
                AppNavigation.retryUrl(restoredUrl) != restoredUrl) {
                restoringInitialPage = false
                lastPage = AppNavigation.retryUrl(lastPage)
                webView.loadUrl(lastPage)
            } else {
                lastPage = restored.currentItem!!.url
                committedPage = lastPage
                webView.visibility = View.VISIBLE
            }
        } else {
            // No website/index request is needed to display this screen, even offline.
            showWelcome(savedInstanceState == null)
        }
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        intent = newIntent
        if (!handleBrowserReturn(newIntent.data)) handleInvitation(newIntent.data)
    }

    private fun beginGoogleSignIn() {
        if (googleSignInInProgress) return
        googleSignInInProgress = true
        fileActions.cancelForAccountBoundary()
        val pending = AppAuthFlow.create(System.currentTimeMillis())
        val prefs = getSharedPreferences("lsc_pending_google", MODE_PRIVATE)
        prefs.edit().putString("state", pending.state).putString("verifier", pending.verifier)
            .putLong("created", pending.createdAtMs).apply()
        // Stop the interrupted WebView before opening the browser. Back/cancel must
        // reveal a native entry screen, not an error from the abandoned navigation.
        showWelcome(false)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppAuthFlow.browserUrl(pending))))
        } catch (_: ActivityNotFoundException) {
            googleSignInInProgress = false
            prefs.edit().clear().apply()
            Toast.makeText(this, R.string.lsc_browser_missing, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleBrowserReturn(uri: Uri?): Boolean {
        if (uri?.scheme != "lscapp" || uri.host != "auth") return false
        val prefs = getSharedPreferences("lsc_pending_google", MODE_PRIVATE)
        val state = prefs.getString("state", null)
        val verifier = prefs.getString("verifier", null)
        val pending = if (state != null && verifier != null)
            AppAuthFlow.Pending(state, verifier, prefs.getLong("created", 0L)) else null
        val body = AppAuthFlow.exchangeBody(uri.toString(), pending, System.currentTimeMillis())
        if (body == null) {
            Toast.makeText(this, "Sign-in expired or was not started in LSC. Please try again.", Toast.LENGTH_LONG).show()
            return false
        }
        fileActions.cancelForAccountBoundary()
        // Consume locally before posting; duplicate return intents must not replay a login.
        googleSignInInProgress = false
        prefs.edit().clear().apply()
        intent.data = null
        cancelWelcomeAnimation()
        showApp()
        resetHistoryOnFinish = true
        lastPage = AppRoutes.authUrl(false)
        webView.postUrl(AppRoutes.BASE_URL + "/app-auth.php?action=exchange", body.toByteArray(Charsets.UTF_8))
        return true
    }

    private fun finishCancelledGoogleSignIn() {
        val prefs = getSharedPreferences("lsc_pending_google", MODE_PRIVATE)
        // A verified callback consumes this state before onResume. Remaining state
        // means the user came back without completing Google (also after recreation).
        if (!googleSignInInProgress && !prefs.contains("state")) return
        googleSignInInProgress = false
        prefs.edit().clear().apply()
        intent.data = null
        loadFailed = false
        pageError.visibility = View.GONE
        showWelcome(false)
    }

    private fun applySystemInsets() {
        val root = findViewById<View>(R.id.rootLayout)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val keyboard = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) isAlgorithmicDarkeningAllowed = false
        }
        webView.setBackgroundColor(getColor(R.color.lsc_surface))
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        fileActions = WebFileActions(this, webView,
            lastNonConfigurationInstance as? DownloadSession ?: DownloadSession(cacheDir))
        fileActions.onOperationStatus = { status ->
            fileOperationActive = status != null
            findViewById<TextView>(R.id.fileOperationMessage).text = status.orEmpty()
            findViewById<View>(R.id.fileOperationStatus).visibility = if (status == null) View.GONE else View.VISIBLE
        }
        webView.webChromeClient = fileActions.chromeClient
        webView.setDownloadListener { url, _, disposition, mime, length ->
            fileActions.download(url, disposition, mime, length)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (request.isForMainFrame && AppAuthFlow.isGoogleStart(uri.toString())) {
                    beginGoogleSignIn()
                    return true
                }
                // Older cached forms can still redirect to Google. Start a NEW browser-bound
                // flow instead of copying the WebView OAuth state into a different cookie jar.
                if (request.isForMainFrame && uri.scheme == "https" && uri.host == "accounts.google.com"
                    && view.url?.let { isTrusted(Uri.parse(it)) } == true) {
                    beginGoogleSignIn()
                    return true
                }
                if (isTrusted(uri)) {
                    if (request.isForMainFrame && AppRoutes.isLandingPage(uri.toString())) {
                        showWelcome(true)
                        return true
                    }
                    return false
                }
                if (request.isForMainFrame && request.hasGesture() && uri.scheme.equals("https", true)) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, R.string.lsc_browser_missing, Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }

            // Unlike shouldOverrideUrlLoading, this also checks form POST requests.
            // Android intercepts the initial resource URL, not every redirect destination;
            // actual document starts/commits below provide an independent fail-closed guard.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (WebRequestPolicy.isRequestAllowed(request.url.toString(), request.isForMainFrame, request.method)) null
                else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(),
                    java.io.ByteArrayInputStream(ByteArray(0)))

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (!WebRequestPolicy.isMainFrameAllowed(url)) {
                    view.visibility = View.INVISIBLE
                    view.stopLoading()
                    showPageError(R.string.lsc_page_blocked)
                } else if (!loadFailed && url == lastPage) {
                    committedPage = url
                    view.visibility = View.VISIBLE
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // Refresh retains only the exact trusted committed document; other pages
                // stay hidden until their own trusted commit. Independent redirect guards remain.
                view.visibility = if (!loadFailed && AppNavigation.canKeepVisible(committedPage, url))
                    View.VISIBLE else View.INVISIBLE
                if (!restoringInitialPage || url != lastPage) fileActions.cancelForAccountBoundary()
                restoringInitialPage = false
                if (!WebRequestPolicy.isMainFrameAllowed(url)) {
                    view.visibility = View.INVISIBLE
                    view.stopLoading()
                    showPageError(R.string.lsc_page_blocked)
                    return
                }
                if (!showingWeb) return
                if (AppRoutes.isLandingPage(url)) {
                    view.stopLoading()
                    showWelcome(true)
                    return
                }
                if (isTrusted(Uri.parse(url))) lastPage = url
                loadFailed = false
                pageError.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!WebRequestPolicy.isMainFrameAllowed(url)) {
                    view.visibility = View.INVISIBLE
                    view.stopLoading()
                    showPageError(R.string.lsc_connection_message)
                    return
                }
                fileActions.checkSessionCookie()
                if (!showingWeb || AppRoutes.isLandingPage(url)) return

                if (!loadFailed && view.url == url && url == lastPage) view.visibility = View.VISIBLE
                if (resetHistoryOnFinish && !loadFailed && view.url == url && url == lastPage) {
                    // Do not navigate back into a previous account screen or the website index.
                    view.clearHistory()
                    resetHistoryOnFinish = false
                }
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (AppNavigation.shouldShowError(request.url.toString(), lastPage, request.isForMainFrame))
                    showPageError(R.string.lsc_connection_message)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (response.statusCode >= 500 &&
                    AppNavigation.shouldShowError(request.url.toString(), lastPage, request.isForMainFrame))
                    showPageError(R.string.lsc_connection_message)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (AppNavigation.shouldShowError(error.url, lastPage, true)) showPageError(R.string.lsc_tls_message)
            }
        }
    }

    private fun isTrusted(uri: Uri): Boolean = WebRequestPolicy.isMainFrameAllowed(uri.toString())

    private fun handleInvitation(uri: Uri?): Boolean {
        val url = uri?.toString()?.let(AppNavigation::invitationLink) ?: return false
        // A new user-selected invitation replaces only an unfinished browser attempt, never the session.
        googleSignInInProgress = false
        getSharedPreferences("lsc_pending_google", MODE_PRIVATE).edit().clear().apply()
        fileActions.cancelForAccountBoundary()
        cancelWelcomeAnimation()
        showApp()
        lastPage = url
        resetHistoryOnFinish = true
        intent.data = null
        webView.loadUrl(url)
        return true
    }

    @Suppress("DEPRECATION")
    private fun updateSystemBars() {
        val background = getColor(R.color.lsc_surface)
        findViewById<View>(R.id.rootLayout).setBackgroundColor(background)
        window.statusBarColor = background
        // API 24/25 cannot draw dark navigation icons; keep a fixed contrast fallback.
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            background else getColor(R.color.lsc_navy)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(mask, mask)
        } else {
            var flags = window.decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun confirmLeaving(action: () -> Unit) {
        if (!fileOperationActive) { action(); return }
        AlertDialog.Builder(this).setTitle(R.string.lsc_leave_title).setMessage(R.string.lsc_leave_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.lsc_leave_action) { _, _ -> action() }.show()
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, view, info)
        if (view != webView || !showingWeb || loadFailed || committedPage != webView.url) return
        val hit = webView.hitTestResult ?: return
        if (hit.type !in setOf(WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) return
        val link = hit.extra ?: return
        if (!AppNavigation.canShareInvitation(webView.url.orEmpty(), link)) return
        menu.add(R.string.lsc_share_invitation).setOnMenuItemClickListener {
            if (AppNavigation.canShareInvitation(webView.url.orEmpty(), link)) {
                try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                    }, getString(R.string.lsc_share_invitation)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.lsc_no_share_app, Toast.LENGTH_LONG).show()
                }
            }
            true
        }
    }

    private fun openAccountPage(register: Boolean) {
        fileActions.cancelForAccountBoundary()
        cancelWelcomeAnimation()
        showApp()
        resetHistoryOnFinish = true
        lastPage = AppRoutes.authUrl(register)
        webView.loadUrl(lastPage)
    }

    private fun showApp() {
        showingWeb = true
        loadFailed = false
        committedPage = null
        webView.visibility = View.INVISIBLE
        welcomeScreen.visibility = View.GONE
        appScreen.visibility = View.VISIBLE
        pageError.visibility = View.GONE
        updateSystemBars()
    }

    private fun showWelcome(animate: Boolean) {
        fileActions.cancelForAccountBoundary()
        showingWeb = false
        committedPage = null
        updateSystemBars()
        webView.stopLoading()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(webView.windowToken, 0)
        webView.clearFocus()
        appScreen.visibility = View.GONE
        welcomeScreen.visibility = View.VISIBLE
        welcomeScreen.scrollTo(0, 0)
        val elements = welcomeElements()
        cancelWelcomeAnimation()
        val motionAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        elements.forEachIndexed { index, view ->
            view.alpha = if (animate && motionAllowed) 0f else 1f
            view.translationY = if (animate && motionAllowed) 22f * resources.displayMetrics.density else 0f
            if (animate && motionAllowed) {
                view.animate().alpha(1f).translationY(0f).setStartDelay(index * 85L)
                    .setDuration(520L).setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
        }
    }

    private fun welcomeElements(): List<View> = listOf(
        findViewById(R.id.welcomeLogo), findViewById(R.id.welcomeCopy),
        findViewById(R.id.welcomeFeatures), findViewById(R.id.welcomeActions),
        findViewById(R.id.welcomeFooter)
    )

    private fun cancelWelcomeAnimation() = welcomeElements().forEach { it.animate().cancel() }

    private fun showPageError(message: Int) {
        if (!showingWeb) return
        loadFailed = true
        errorMessage.setText(message)
        pageError.visibility = View.VISIBLE
    }

    private fun navigateBack() {
        confirmLeaving {
            if (!showingWeb) {
                finish()
            } else {
                val list = webView.copyBackForwardList()
                val before = (0 until list.currentIndex).map { list.getItemAtIndex(it).url }
                val offset = AppNavigation.backOffset(lastPage, before)
                when {
                    offset != null -> webView.goBackOrForward(offset)
                    AppNavigation.destination(lastPage) == AppNavigation.Destination.AUTH || loadFailed ||
                        AppNavigation.destination(lastPage) == AppNavigation.Destination.HELP -> showWelcome(false)
                    else -> finish()
                }
            }
        }
    }

    @Deprecated("Used for Android 12 and earlier.")
    override fun onBackPressed() = navigateBack()

    override fun onSaveInstanceState(outState: Bundle) {
        fileActions.saveState(outState)
        outState.putBoolean(STATE_WEB, showingWeb)
        outState.putString(STATE_PAGE, lastPage)
        if (showingWeb) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        finishCancelledGoogleSignIn()
        webView.onResume()
        fileActions.checkSessionCookie()
    }

    @Deprecated("Platform document picker result for this Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!fileActions.onActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRetainNonConfigurationInstance(): Any = fileActions.retainedDownloadSession()

    override fun onDestroy() {
        fileActions.close(isChangingConfigurations)
        cancelWelcomeAnimation()
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val STATE_WEB = "lsc.showing_web"
        private const val STATE_PAGE = "lsc.last_page"
    }
}
