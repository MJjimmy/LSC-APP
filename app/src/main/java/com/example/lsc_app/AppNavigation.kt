package com.example.lsc_app

import java.net.URI

/** Presentation and history policy only. The server still owns all authentication/permissions. */
internal object AppNavigation {
    enum class Destination { HOME, GROUPS, TASKS, PROFILE, FILES, NOTIFICATIONS, CALENDAR, HELP, INVITATION, AUTH, OTHER }
    private val routes = mapOf(
        "/dashboard.php" to Destination.HOME, "/groups.php" to Destination.GROUPS,
        "/group.php" to Destination.GROUPS, "/tasks.php" to Destination.TASKS,
        "/profile.php" to Destination.PROFILE, "/files.php" to Destination.FILES,
        "/notifications.php" to Destination.NOTIFICATIONS, "/calendar.php" to Destination.CALENDAR,
        "/help.php" to Destination.HELP, "/invite.php" to Destination.INVITATION,
        "/login.php" to Destination.AUTH, "/register.php" to Destination.AUTH,
        "/forgot-password.php" to Destination.AUTH, "/reset-password.php" to Destination.AUTH,
        "/app-auth.php" to Destination.AUTH, "/oauth.php" to Destination.AUTH, "/logout.php" to Destination.AUTH
    )
    private val oneShot = setOf("/app-auth.php", "/oauth.php", "/logout.php", "/download.php", "/offline.php")
    private fun path(url: String): String? = if (WebRequestPolicy.isMainFrameAllowed(url))
        runCatching { URI(url).rawPath }.getOrNull() else null

    fun destination(url: String): Destination = routes[path(url)] ?: Destination.OTHER
    fun showWorkspaceChrome(url: String): Boolean = path(url) != null && !AppRoutes.isLandingPage(url) &&
        destination(url) != Destination.AUTH && path(url) !in oneShot

    /** Never retry a native one-time-code exchange or logout as an ordinary GET. */
    fun retryUrl(url: String): String = if (path(url) != null && path(url) !in oneShot &&
        !AppRoutes.isLandingPage(url)) url else AppRoutes.authUrl(false)

    fun canKeepVisible(committed: String?, starting: String): Boolean = committed != null &&
        WebRequestPolicy.isMainFrameAllowed(starting) && committed == starting && path(starting) !in oneShot

    fun shouldShowError(failed: String, active: String, mainFrame: Boolean): Boolean = mainFrame &&
        failed.substringBefore('#') == active.substringBefore('#')

    /** history contains only entries before the current index; result is goBackOrForward offset. */
    fun backOffset(current: String, history: List<String>): Int? {
        val currentAuth = destination(current) == Destination.AUTH
        for (i in history.indices.reversed()) {
            val candidate = history[i]
            if (path(candidate) == null || path(candidate) in oneShot || AppRoutes.isLandingPage(candidate)) continue
            val candidateAuth = destination(candidate) == Destination.AUTH
            // Expired/logout/account boundaries must not reveal the preceding account's cached pages.
            if (currentAuth != candidateAuth) continue
            if (path(candidate) == "/reset-password.php") continue
            return i - history.size
        }
        return null
    }

    /** Only the server's existing 32-byte hex invitation format; no arbitrary query forwarding. */
    fun invitationLink(url: String): String? {
        if (path(url) != "/invite.php") return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.rawFragment != null || uri.rawQuery?.matches(Regex("token=[a-f0-9]{64}")) != true) return null
        return url
    }

    /** Share an actual anchor rendered on a workspace page, not an inferred login or a scraped page. */
    fun canShareInvitation(page: String, link: String): Boolean =
        showWorkspaceChrome(page) && destination(page) !in setOf(Destination.HELP, Destination.INVITATION, Destination.OTHER) &&
            invitationLink(link) != null
}
