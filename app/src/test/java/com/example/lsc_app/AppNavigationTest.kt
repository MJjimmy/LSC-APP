package com.example.lsc_app

import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    private val base = AppRoutes.BASE_URL
    private val invite = "$base/invite.php?token=${"a".repeat(64)}"
    @Test fun destinationsUseExistingRoutesAndHideChromeOnAccountForms() {
        assertEquals(AppNavigation.Destination.HOME, AppNavigation.destination("$base/dashboard.php"))
        assertEquals(AppNavigation.Destination.GROUPS, AppNavigation.destination("$base/group.php?id=2"))
        assertEquals(AppNavigation.Destination.PROFILE, AppNavigation.destination("$base/profile.php"))
        assertFalse(AppNavigation.showWorkspaceChrome(AppRoutes.authUrl(false)))
        assertFalse(AppNavigation.showWorkspaceChrome("$base/reset-password.php?token=private"))
        assertTrue(AppNavigation.showWorkspaceChrome("$base/tasks.php"))
        assertFalse(AppNavigation.showWorkspaceChrome("https://evil.example/tasks.php"))
    }
    @Test fun retryNeverReplaysNativeExchangeOrLogout() {
        for (page in listOf("app-auth.php?action=exchange", "oauth.php?code=private", "logout.php", "offline.php"))
            assertEquals(AppRoutes.authUrl(false), AppNavigation.retryUrl("$base/$page"))
        val intended = "$base/group.php?id=7&tab=files"
        assertEquals(intended, AppNavigation.retryUrl(intended))
        assertEquals(AppRoutes.authUrl(false), AppNavigation.retryUrl("http://evil.example"))
    }
    @Test fun refreshRetainsOnlyTheExactTrustedCommittedDocument() {
        assertTrue(AppNavigation.canKeepVisible("$base/tasks.php", "$base/tasks.php"))
        assertFalse(AppNavigation.canKeepVisible("$base/tasks.php", "$base/profile.php"))
        assertFalse(AppNavigation.canKeepVisible(null, "$base/tasks.php"))
        assertFalse(AppNavigation.canKeepVisible("https://evil.example", "https://evil.example"))
    }
    @Test fun minorResourcesAndStaleRequestsNeverReplaceTheCurrentPage() {
        assertFalse(AppNavigation.shouldShowError("$base/missing.png", "$base/tasks.php", false))
        assertFalse(AppNavigation.shouldShowError("$base/groups.php", "$base/tasks.php", true))
        assertTrue(AppNavigation.shouldShowError("$base/tasks.php", "$base/tasks.php", true))
    }
    @Test fun backSkipsAuthenticationAndForeignHistoryAfterLogin() {
        val before = listOf(AppRoutes.authUrl(false), "$base/app-auth.php?action=exchange", "https://evil.example")
        assertNull(AppNavigation.backOffset("$base/dashboard.php", before))
        assertEquals(-2, AppNavigation.backOffset("$base/tasks.php", listOf("$base/groups.php", "$base/oauth.php")))
        assertNull(AppNavigation.backOffset(AppRoutes.authUrl(false), listOf("$base/profile.php")))
    }
    @Test fun invitationIntentsRejectExtraParametersAndAuthorityTricks() {
        assertEquals(invite, AppNavigation.invitationLink(invite))
        for (bad in listOf(invite+"&code=private", invite+"#fragment", invite.replace("https:", "http:"),
            invite.replace("/invite.php", "/%69nvite.php"), invite.replace("token=", "token=short"),
            invite.replace("lsc.up.railway.app", "lsc.up.railway.app.evil.example"),
            invite.replace("https://", "https://user@"), invite.replace(".app/", ".app:8443/")))
            assertNull(bad, AppNavigation.invitationLink(bad))
    }
    @Test fun sharingRequiresAnExistingInvitationAnchorOnAWorkspacePage() {
        assertTrue(AppNavigation.canShareInvitation("$base/group.php?id=2", invite))
        assertFalse(AppNavigation.canShareInvitation(AppRoutes.authUrl(false), invite))
        assertFalse(AppNavigation.canShareInvitation(AppRoutes.helpUrl(), invite))
        assertFalse(AppNavigation.canShareInvitation("$base/group.php?id=2", "$base/reset-password.php?token=private"))
    }
}
