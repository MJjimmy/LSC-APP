package com.example.lsc_app

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class AppAuthFlowTest {
    private val now = 1_000_000L
    private val pending = AppAuthFlow.Pending("a".repeat(64), "b".repeat(64), now)
    private val code = "c".repeat(64)
    private val callback = "lscapp://auth?code=$code&state=${pending.state}"
    @Test fun newLoginUsesFreshStateAndVerifier() {
        val first = AppAuthFlow.create(now)
        val second = AppAuthFlow.create(now)
        assertTrue(first.state.matches(Regex("[a-f0-9]{64}")))
        assertTrue(first.verifier.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(first.state, second.state)
        assertNotEquals(first.verifier, second.verifier)
    }
    @Test fun browserReceivesChallengeButNeverVerifier() {
        val hash = MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray()).joinToString("") { "%02x".format(it) }
        val url = AppAuthFlow.browserUrl(pending)
        assertTrue(url.startsWith(AppRoutes.BASE_URL + "/app-auth.php?action=start&"))
        assertTrue(url.contains("challenge=$hash"))
        assertTrue(url.contains("state=${pending.state}"))
        assertFalse(url.contains(pending.verifier))
    }
    @Test fun validReturnExchangesOpaqueCodeAndLocalVerifier() {
        assertEquals("code=$code&verifier=${pending.verifier}", AppAuthFlow.exchangeBody(callback,pending,now+100))
    }
    @Test fun wrongStateOrExpiredReturnIsRejected() {
        assertNull(AppAuthFlow.exchangeBody(callback.replace(pending.state,"d".repeat(64)),pending,now+100))
        assertNull(AppAuthFlow.exchangeBody(callback,pending,now+11*60*1000))
        assertNull(AppAuthFlow.exchangeBody(callback,pending,now-1))
    }
    @Test fun absentPendingLoginCannotBeReplayed() {
        assertNull(AppAuthFlow.exchangeBody(callback,null,now+100))
    }
    @Test fun unexpectedReturnAndDuplicateParametersAreRejected() {
        assertNull(AppAuthFlow.exchangeBody(callback.replace("lscapp://auth","https://lsc.up.railway.app"),pending,now))
        assertNull(AppAuthFlow.exchangeBody(callback + "&code=" + code,pending,now))
        assertNull(AppAuthFlow.exchangeBody(callback.replace(code,"short"),pending,now))
    }
    @Test fun onlyTheFixedGoogleStartLinkTriggersBrowserLogin() {
        assertTrue(AppAuthFlow.isGoogleStart("lscapp://google"))
        assertFalse(AppAuthFlow.isGoogleStart("lscapp://other"))
        assertFalse(AppAuthFlow.isGoogleStart("https://example.com/google"))
    }
}
