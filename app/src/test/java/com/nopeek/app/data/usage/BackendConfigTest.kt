package com.nopeek.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backend config stopped being a placeholder on 2026-10-11, and that is a behaviour
 * change rather than a string edit: `SettlementClient.fetchSettlementResult`,
 * `triggerSettlement` and `registerChallenge` all return early while [NopeekBackend.isConfigured]
 * is false. Once it is true they attempt real network calls. The signed report path is the same
 * story through `OracleEvidenceTransport`, which refuses to open a connection when unconfigured
 * so the reporter surfaces TransportFailed — a retryable state, never a silent false.
 *
 * Pinned here so a future edit that reintroduces a placeholder fails loudly instead of
 * quietly turning every submission into a no-op that returns `false` — which is precisely
 * the kind of silent failure the rest of this project is built to avoid.
 */
class BackendConfigTest {

    @Test
    fun backendIsConfiguredAgainstTheRealProjectRef() {
        assertTrue(
            "SUPABASE_URL still holds a placeholder - every report submission would silently no-op",
            NopeekBackend.isConfigured(),
        )
    }

    @Test
    fun urlIsAHttpsSupabaseProjectAndNotThePlaceholder() {
        assertTrue(NopeekBackend.SUPABASE_URL.startsWith("https://"))
        assertTrue(NopeekBackend.SUPABASE_URL.endsWith(".supabase.co"))
        assertFalse(NopeekBackend.SUPABASE_URL.contains("PROJECT-REF"))
        assertFalse(NopeekBackend.SUPABASE_ANON_KEY.contains("PLACEHOLDER"))
    }

    @Test
    fun derivedEndpointsAreWellFormed() {
        assertEquals(
            "https://ybcavetnzivwsfnwzvtd.supabase.co/functions/v1/oracle/submit-report",
            NopeekBackend.oracleUrl("submit-report"),
        )
        assertEquals(
            "https://ybcavetnzivwsfnwzvtd.supabase.co/rest/v1/settlement_results",
            NopeekBackend.restUrl("settlement_results"),
        )
    }

    @Test
    fun anonKeyIsTheJwtShapeSupabaseIssues() {
        // Three dot-separated base64url segments. Not a secret check - the point is that
        // nobody pastes the service_role key here by mistake, which would have a different
        // payload and is the credential that must never ship in a client.
        val parts = NopeekBackend.SUPABASE_ANON_KEY.split(".")
        assertEquals("anon key must be a JWT", 3, parts.size)
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        assertTrue("this key must be the anon role, saw: $payload", payload.contains("\"role\":\"anon\""))
    }
}
