package com.nopeek.app.data.attest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
    fun nonAsciiFieldIsRejectedRatherThanReEncoded() {
        // Java maps a non-ASCII character to "?" where TextEncoder emits U+FFFD, so
        // accepting it would give one logical field two byte forms and two hashes.
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalPayload.encode(fields().copy(targetPackage = "com.exämple.app"))
        }
    }

    @Test
    fun decodeReadsPinnedVectorBytesIntoNamedFields() {
        val d = CanonicalPayload.decode(hexToBytes(vector.getString("expectedHex")))
        val f = fields()
        // Field by field: a symmetric reordering of the decoder round-trips and
        // would keep an assertEquals-on-the-whole-object green.
        assertEquals(f.version, d.version)
        assertEquals(f.challengePubkey, d.challengePubkey)
        assertEquals(f.walletPubkey, d.walletPubkey)
        assertEquals(f.packageName, d.packageName)
        assertEquals(f.targetPackage, d.targetPackage)
        assertEquals(f.windowStartMs, d.windowStartMs)
        assertEquals(f.windowEndMs, d.windowEndMs)
        assertEquals(f.usedSeconds, d.usedSeconds)
        assertEquals(f.deviceActiveSeconds, d.deviceActiveSeconds)
        assertEquals(f.eventCount, d.eventCount)
        assertEquals(f.eventSetDigestHex, d.eventSetDigestHex)
        assertEquals(f.tamperSignals, d.tamperSignals)
        assertEquals(f.nonceB64, d.nonceB64)
    }

    @Test
    fun encoderRefusesTamperSignalsThatWouldCollideAfterJoining() {
        for (signals in listOf(listOf("A,B"), listOf(""))) {
            assertThrows(IllegalArgumentException::class.java) {
                CanonicalPayload.encode(fields().copy(tamperSignals = signals))
            }
        }
    }

    @Test
    fun encoderRefusesNegativeValues() {
        assertThrows(IllegalArgumentException::class.java) { CanonicalPayload.encode(fields().copy(usedSeconds = -1)) }
        assertThrows(IllegalArgumentException::class.java) { CanonicalPayload.encode(fields().copy(deviceActiveSeconds = -1L)) }
        assertThrows(IllegalArgumentException::class.java) { CanonicalPayload.encode(fields().copy(eventCount = -1)) }
        assertThrows(IllegalArgumentException::class.java) { CanonicalPayload.encode(fields().copy(windowStartMs = -1L)) }
    }

    @Test
    fun encoderRefusesNumericsBeyondTheSafeIntegerRange() {
        // Kotlin Long can carry a value the oracle cannot read; catching it here
        // stops the device signing, spending its single-use nonce, then losing.
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalPayload.encode(fields().copy(windowEndMs = 9007199254740992L))
        }
    }

    @Test
    fun decoderAcceptsOnlyVersionOne() {
        for (v in listOf<Byte>(0, 2, Byte.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                CanonicalPayload.decode(CanonicalPayload.encode(fields().copy(version = v)))
            }
        }
    }

    @Test
    fun decoderRejectsNumericFieldsTheEncoderCouldNeverProduce() {
        for (raw in listOf("", "+42", "007", "1e3", "0x10", " 5 ", "42.", "-1", "NaN", "Infinity")) {
            assertThrows("non-canonical numeric \"$raw\" must be rejected", IllegalArgumentException::class.java) {
                CanonicalPayload.decode(bytesWithUsedSeconds(raw))
            }
        }
    }

    @Test
    fun decoderRejectsACountTooLargeForTheClientField() {
        // Canonical and inside the safe-integer range, but outside the Int the
        // client stores it in: the oracle must not accept a number the device
        // could never have produced, or the two domains diverge again.
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalPayload.decode(bytesWithUsedSeconds("2147483648"))
        }
    }

    /** Builds length-prefixed bytes with a raw, possibly non-canonical numeric field. */
    private fun bytesWithUsedSeconds(raw: String): ByteArray {
        val f = fields()
        val values = listOf(
            f.challengePubkey, f.walletPubkey, f.packageName, f.targetPackage,
            f.windowStartMs.toString(), f.windowEndMs.toString(), raw,
            f.deviceActiveSeconds.toString(), f.eventCount.toString(), f.eventSetDigestHex,
            f.tamperSignals.joinToString(","), f.nonceB64,
        )
        val out = java.io.ByteArrayOutputStream()
        out.write(f.version.toInt() and 0xFF)
        for (v in values) {
            val bytes = v.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            out.write((bytes.size ushr 24) and 0xFF)
            out.write((bytes.size ushr 16) and 0xFF)
            out.write((bytes.size ushr 8) and 0xFF)
            out.write(bytes.size and 0xFF)
            out.write(bytes, 0, bytes.size)
        }
        return out.toByteArray()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
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
