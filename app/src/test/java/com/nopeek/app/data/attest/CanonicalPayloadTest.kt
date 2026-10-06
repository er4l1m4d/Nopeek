package com.nopeek.app.data.attest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the Kotlin encoder to the same shared byte vector the TypeScript encoder
 * is pinned to (spec 4.2, plan Task 6). The device signs these bytes and the
 * oracle hashes the bytes it receives, so an agreement failure between the two
 * languages would mean a settlement referencing evidence its own publisher
 * cannot reproduce.
 *
 * Neither side may special-case its own output: both read expectedHex and
 * expectedHash from the vector file, which was computed once and pinned.
 */
class CanonicalPayloadTest {

    private val vector: JSONObject = loadVector()

    @Test
    fun encodesSharedVectorByteForByte() {
        val expected = vector.getString("expectedHex")
        assertTrue("expectedHex must be pinned in the vector file", expected.isNotEmpty())
        assertEquals(expected, CanonicalPayload.encode(fields()).toHex())
    }

    @Test
    fun matchesSharedVectorHash() {
        val bytes = CanonicalPayload.encode(fields())
        assertEquals(vector.getString("expectedHash"), CanonicalPayload.digestHex(bytes))
    }

    @Test
    fun lengthPrefixedFieldsRoundTrip() {
        assertEquals(fields(), CanonicalPayload.decode(CanonicalPayload.encode(fields())))
    }

    @Test
    fun tamperSignalsOfZeroOneAndThreeEntriesRoundTrip() {
        for (signals in listOf(
            emptyList(),
            listOf("PERMISSION_REVOKED"),
            listOf("CLOCK_CHANGED", "REPORT_LATE", "APP_UNINSTALLED"),
        )) {
            val f = fields().copy(tamperSignals = signals)
            assertEquals(signals, CanonicalPayload.decode(CanonicalPayload.encode(f)).tamperSignals)
        }
    }

    @Test
    fun aSingleFieldChangeChangesTheDigest() {
        val base = CanonicalPayload.encode(fields())
        val mutated = CanonicalPayload.encode(fields().copy(usedSeconds = fields().usedSeconds + 1))
        assertNotEquals(CanonicalPayload.digestHex(base), CanonicalPayload.digestHex(mutated))
    }

    @Test
    fun nonAsciiTargetPackageSurvivesRoundTrip() {
        val f = fields().copy(targetPackage = "com.exämple.app")
        assertEquals("com.exämple.app", CanonicalPayload.decode(CanonicalPayload.encode(f)).targetPackage)
    }

    private fun fields(): EvidenceFields {
        val o = vector.getJSONObject("fields")
        val signals = o.getJSONArray("tamperSignals")
        return EvidenceFields(
            version = o.getInt("version").toByte(),
            challengePubkey = o.getString("challengePubkey"),
            walletPubkey = o.getString("walletPubkey"),
            packageName = o.getString("packageName"),
            targetPackage = o.getString("targetPackage"),
            windowStartMs = o.getLong("windowStartMs"),
            windowEndMs = o.getLong("windowEndMs"),
            usedSeconds = o.getInt("usedSeconds"),
            deviceActiveSeconds = o.getLong("deviceActiveSeconds"),
            eventCount = o.getInt("eventCount"),
            eventSetDigestHex = o.getString("eventSetDigestHex"),
            tamperSignals = (0 until signals.length()).map { signals.getString(it) },
            nonceB64 = o.getString("nonceB64"),
        )
    }

    /**
     * Gradle runs unit tests with the module directory as the working directory,
     * so the vector lives one level up in the oracle tree. Resolved by walking up
     * rather than hardcoding a machine path — and it fails loudly if not found,
     * because a silently skipped cross-language check is the exact thing this
     * task exists to prevent.
     */
    private fun loadVector(): JSONObject {
        val relative = "supabase/functions/oracle/fixtures/evidence-vector.json"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return JSONObject(candidate.readText())
            dir = dir.parentFile
        }
        throw IllegalStateException("shared evidence vector not found at $relative from any ancestor of the test working directory")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
