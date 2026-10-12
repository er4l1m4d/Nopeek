package com.nopeek.app.data.attest

import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File-scope because [EvidenceReporterTest.Recorder] is a nested class and cannot default to
 * an instance property of the test class.
 */
private const val NONCE_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

/**
 * Task 13: the reporter that turns a measured window into a signed submission.
 *
 * Everything the outside world provides — nonce issuance, enrollment, submission, the
 * keystore's mint and sign — arrives as an injected function, so the protocol's ordering
 * and byte discipline can be proven on the JVM. The keystore and the network are exercised
 * separately, on the device and against the deployed oracle; a JVM test of those would be a
 * test of a stub.
 *
 * What is worth asserting here is precisely what has already gone wrong in this project:
 * the device must sign the exact bytes it transmits (spec 4.2), the signature must reach the
 * oracle in the one encoding the oracle pins, and the nonce that the payload names must be
 * the same value the hardware attestation was minted with. That last triangle is check 6,
 * and it is the bug the previous task's review caught: a key minted once can never carry a
 * later window's challenge, so every honest report after the first would have been refused.
 */
class EvidenceReporterTest {

    private val windowStart = 1_760_000_000_000L
    private val windowEnd = windowStart + 1_800_000L

    /** Comes from the challenge, never guessed: check 11 compares it to `target_app`. */
    private val target = "com.instagram.android"

    private fun report(signals: TamperSignals = TamperSignals()) = UsageReport(
        challengePubkey = "DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM",
        walletPubkey = "6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj",
        usedSeconds = 42,
        windowStart = windowStart,
        windowEnd = windowEnd,
        tamperSignals = signals,
        deviceActiveSeconds = 120,
        eventCount = 7,
        eventSetDigestHex = "0".repeat(64),
    )

    private val enrolled = EnrolledKey(
        alias = "NopeekAttest.v1",
        spkiB64 = "MFkwEwYHKo".padEnd(91, 'A'),
        chainPem = "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n",
        certDigestB64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    )

    /** A DER SEQUENCE{r,s} shaped like the one AndroidKeyStore emits (Task 7: len 72, 0x30). */
    private val derSignature: ByteArray = run {
        val r = ByteArray(32) { (it + 1).toByte() }.also { it[0] = 0x7f }
        val s = ByteArray(32) { (it + 33).toByte() }.also { it[0] = 0x7f }
        byteArrayOf(0x30, (2 + r.size + 2 + s.size).toByte(), 0x02, r.size.toByte()) + r +
            byteArrayOf(0x02, s.size.toByte()) + s
    }

    /** Records what the reporter asked the outside world to do, and can fail on command. */
    private class Recorder(
        val nonce: String = NONCE_B64,
        val nonceFails: Boolean = false,
        val enrollOk: Boolean = true,
        val submitResult: SubmitResult = SubmitResult.Accepted,
    ) : EvidenceTransport {
        val calls = mutableListOf<String>()
        var lastBody: Map<String, Any> = emptyMap()

        override fun fetchNonce(challengePubkey: String, walletPubkey: String, windowEndMs: Long): String {
            calls += "nonce"
            if (nonceFails) throw java.io.IOException("nonce endpoint unreachable")
            return nonce
        }

        override fun enroll(walletPubkey: String, key: EnrolledKey): Boolean {
            calls += "enroll"
            return enrollOk
        }

        override fun submit(body: Map<String, Any>): SubmitResult {
            calls += "submit"
            lastBody = body
            return submitResult
        }
    }

    private fun reporter(
        transport: EvidenceTransport,
        mintedInto: MutableList<String> = mutableListOf(),
        signedBytes: MutableList<ByteArray> = mutableListOf(),
    ) = EvidenceReporter(
        transport = transport,
        packageName = "com.nopeek.app",
        mintKey = { challengeB64 ->
            mintedInto += challengeB64
            enrolled
        },
        sign = { alias, bytes ->
            assertEquals("must sign with the key it just minted", enrolled.alias, alias)
            signedBytes += bytes
            derSignature
        },
    )

    @Test
    fun signsTheExactBytesItTransmits() {
        val recorder = Recorder()
        val signed = mutableListOf<ByteArray>()
        reporter(recorder, signedBytes = signed).submit(report(), target)

        assertEquals(1, signed.size)
        val sent = java.util.Base64.getDecoder().decode(recorder.lastBody["canonicalBytesB64"] as String)

        // Not "equivalent" — identical. The oracle hashes the bytes it received and calls
        // that digest the evidence; if the device signed anything else, the hash pinned
        // on-chain would not describe the payload that was attested.
        assertArrayEquals(signed.single(), sent)

        // And those bytes must decode back to the report that produced them, so the
        // published evidence is readable by a stranger rather than merely stored.
        val decoded = CanonicalPayload.decode(sent)
        assertEquals(report().challengePubkey, decoded.challengePubkey)
        assertEquals("com.nopeek.app", decoded.packageName)
        assertEquals(target, decoded.targetPackage)
        assertEquals(42, decoded.usedSeconds)
        assertEquals(windowStart, decoded.windowStartMs)
        assertEquals(windowEnd, decoded.windowEndMs)
    }

    @Test
    fun sendsTheRawSignatureTheOraclePinsNotTheDerTheKeystoreEmits() {
        val recorder = Recorder()
        reporter(recorder).submit(report(), target)

        val raw = java.util.Base64.getDecoder().decode(recorder.lastBody["signatureB64"] as String)
        assertEquals(
            "the oracle accepts fixed-width r||s only; a DER signature is refused BAD_SIGNATURE",
            64, raw.size,
        )
        assertArrayEquals(derEcdsaToRawP1363(derSignature), raw)
        assertNotEquals(
            "the keystore's own DER bytes were sent untranslated",
            derSignature.toList(), raw.toList(),
        )
    }

    @Test
    fun mintsTheAttestationKeyWithTheNonceThePayloadCarries() {
        val recorder = Recorder()
        val mintedWith = mutableListOf<String>()
        val signed = mutableListOf<ByteArray>()
        reporter(recorder, mintedWith, signed).submit(report(), target)

        // The check-6 triangle: /nonce's value is what the hardware attests AND what the
        // signed payload names. If these ever diverge, an honest device is refused — the
        // exact failure that made a persistent enrolled key impossible to use.
        val payloadNonce = CanonicalPayload
            .decode(java.util.Base64.getDecoder().decode(recorder.lastBody["canonicalBytesB64"] as String))
            .nonceB64
        assertEquals(NONCE_B64, mintedWith.single())
        assertEquals(NONCE_B64, payloadNonce)
    }

    @Test
    fun enrollsBeforeSubmittingAndSendsTheChainThatWasEnrolled() {
        val recorder = Recorder()
        reporter(recorder).submit(report(), target)

        assertEquals(listOf("nonce", "enroll", "submit"), recorder.calls)
        assertEquals(
            "the chain the oracle verifies must be the chain it enrolled; sending a different " +
                "one would make check 7 compare the enrolled key against a leaf it never saw",
            enrolled.chainPem, recorder.lastBody["chainPem"],
        )
    }

    @Test
    fun doesNotBurnASingleUseNonceWhenEnrollmentFails() {
        // Enrollment is the step that makes the presented chain trustworthy. Submitting
        // anyway would consume the window's one nonce and still be refused, leaving the
        // user with neither a verdict nor a nonce to retry with.
        val recorder = Recorder(enrollOk = false)
        val outcome = reporter(recorder).submit(report(), target)

        assertTrue("submit must not be reached when enroll is refused", recorder.calls == listOf("nonce", "enroll"))
        assertTrue(outcome is ReportOutcome.Rejected)
    }

    @Test
    fun reportsTransportFailureWithoutClaimingAVerdict() {
        val recorder = Recorder(nonceFails = true)
        val outcome = reporter(recorder).submit(report(), target)

        // The attempt itself is recorded — the fake notes the call, then throws. What must NOT
        // appear is a mint, an enroll or a submit: nothing may be signed or sent for a window
        // that has no server-issued nonce.
        assertEquals(listOf("nonce"), recorder.calls)
        assertTrue(
            "an unreachable oracle is not a loss; it must stay retryable",
            outcome is ReportOutcome.TransportFailed,
        )
    }

    @Test
    fun doesNotSubmitTheSameWindowTwiceInOneProcess() {
        // Review Focus 2, client half — keyed by the WINDOW, not by the evidence hash.
        // `nonce` is one of the canonical fields, so a retry hashes differently by
        // construction and a hash-keyed cache would miss every time while burning a second
        // single-use nonce to discover that. A repeat attempt must make no transport call at
        // all, and must still report the hash the first attempt produced.
        val recorder = Recorder()
        val subject = reporter(recorder)

        val first = subject.submit(report(), target)
        val second = subject.submit(report(), target)

        assertEquals(listOf("nonce", "enroll", "submit"), recorder.calls)
        assertEquals(
            "unchanged window must resolve to the same evidence hash",
            (first as ReportOutcome.Accepted).evidenceHash,
            (second as ReportOutcome.Accepted).evidenceHash,
        )
    }

    @Test
    fun aDifferentWindowStillSubmits() {
        // The other edge of the same cache: if the key is too coarse, the second duel of a
        // pair, or the same duel's next window, is silently swallowed and the user never gets
        // a verdict — which looks identical to success from the caller's side.
        val recorder = Recorder()
        val subject = reporter(recorder)

        subject.submit(report(), target)
        val later = report().copy(windowStart = windowStart + 1000)
        subject.submit(later, target)

        assertEquals(listOf("nonce", "enroll", "submit", "nonce", "enroll", "submit"), recorder.calls)
    }

    @Test
    fun acceptedOutcomeCarriesTheHashTheOracleWillCompute() {
        val recorder = Recorder()
        val outcome = reporter(recorder).submit(report(), target)

        val sent = java.util.Base64.getDecoder().decode(recorder.lastBody["canonicalBytesB64"] as String)
        assertEquals(
            "the device must predict the on-chain evidence hash from the bytes it sent, or the " +
                "hash it publishes is not the one that settled the duel",
            CanonicalPayload.digestHex(sent),
            (outcome as ReportOutcome.Accepted).evidenceHash,
        )
    }

    @Test
    fun tamperSignalsReachThePayloadNotJustTheLegacyBody() {
        val recorder = Recorder()
        reporter(recorder).submit(report(TamperSignals(clockJumpedForward = true)), target)

        val decoded = CanonicalPayload
            .decode(java.util.Base64.getDecoder().decode(recorder.lastBody["canonicalBytesB64"] as String))
        assertEquals(listOf("CLOCK_JUMPED_FORWARD"), decoded.tamperSignals)
        // The oracle blocks on ANY non-empty tamper list, so the signal has to be inside the
        // signed bytes rather than only in the JSON body — body fields are not attested.
        assertEquals(listOf("CLOCK_JUMPED_FORWARD"), recorder.lastBody["tamperSignals"])
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) =
        org.junit.Assert.assertArrayEquals(expected, actual)
}
