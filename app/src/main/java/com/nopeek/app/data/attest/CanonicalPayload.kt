package com.nopeek.app.data.attest

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * The exact bytes a device signs and an oracle hashes (spec 4.2).
 *
 * Layout: one raw `version` byte, then each remaining field as a big-endian
 * `u32` byte-count followed by its UTF-8 bytes, in the spec's order. Numeric
 * fields are written as their canonical decimal string, so the two language
 * implementations agree on a single textual rule instead of negotiating integer
 * widths and endianness — the device signs these exact bytes and the oracle
 * hashes exactly the bytes it received, so any divergence here would produce a
 * settlement whose own evidence cannot be reproduced.
 *
 * `tamperSignals` is joined with `,`. Signal names never contain a comma, and an
 * empty list encodes as a zero-length field, which decodes back to an empty list
 * rather than a list holding one empty string.
 */
data class EvidenceFields(
    val version: Byte,
    val challengePubkey: String,
    val walletPubkey: String,
    val packageName: String,
    val targetPackage: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val usedSeconds: Int,
    val deviceActiveSeconds: Long,
    val eventCount: Int,
    val eventSetDigestHex: String,
    val tamperSignals: List<String>,
    val nonceB64: String,
)

object CanonicalPayload {

    const val VERSION: Byte = 1

    /**
     * Kotlin `Long` can carry values TypeScript's `Number` cannot represent exactly.
     * Bounding the domain here rather than at the oracle means the device never
     * signs, transmits and burns its single-use nonce on a report the oracle could
     * not read. Must stay identical to the TypeScript guard in `evidence.ts`.
     */
    const val MAX_EVIDENCE_VALUE: Long = 9_007_199_254_740_991L // 2^53 - 1

    private val CANONICAL_INT = Regex("0|[1-9][0-9]*")

    fun encode(f: EvidenceFields): ByteArray {
        assertAscii(f.challengePubkey, "challengePubkey")
        assertAscii(f.walletPubkey, "walletPubkey")
        assertAscii(f.packageName, "packageName")
        assertAscii(f.targetPackage, "targetPackage")
        assertAscii(f.eventSetDigestHex, "eventSetDigestHex")
        assertAscii(f.nonceB64, "nonceB64")
        assertRange(f.windowStartMs, "windowStartMs")
        assertRange(f.windowEndMs, "windowEndMs")
        assertRange(f.usedSeconds.toLong(), "usedSeconds")
        assertRange(f.deviceActiveSeconds, "deviceActiveSeconds")
        assertRange(f.eventCount.toLong(), "eventCount")
        for (signal in f.tamperSignals) {
            // Joined with ",", ["A,B"] and ["A","B"] are the same bytes, and [""] is
            // the same bytes as []. Two distinct reports must not share one hash.
            require(signal.isNotEmpty() && !signal.contains(',')) {
                "evidence tamper signal must be non-empty and contain no comma"
            }
            assertAscii(signal, "tamperSignals")
        }

        val out = ByteArrayOutputStream()
        out.write(f.version.toInt() and 0xFF)
        writeField(out, f.challengePubkey)
        writeField(out, f.walletPubkey)
        writeField(out, f.packageName)
        writeField(out, f.targetPackage)
        writeField(out, f.windowStartMs.toString())
        writeField(out, f.windowEndMs.toString())
        writeField(out, f.usedSeconds.toString())
        writeField(out, f.deviceActiveSeconds.toString())
        writeField(out, f.eventCount.toString())
        writeField(out, f.eventSetDigestHex)
        writeField(out, f.tamperSignals.joinToString(","))
        writeField(out, f.nonceB64)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): EvidenceFields {
        var cursor = 0
        fun readByte(): Int {
            require(cursor < bytes.size) { "truncated canonical payload" }
            return bytes[cursor++].toInt() and 0xFF
        }
        val version = readByte().toByte()
        require(version == VERSION) { "evidence version $version is not supported" }
        val fields = mutableListOf<String>()
        repeat(12) {
            val len = (readByte() shl 24) or (readByte() shl 16) or (readByte() shl 8) or readByte()
            require(len >= 0) { "negative field length" }
            require(cursor + len <= bytes.size) { "truncated field in canonical payload" }
            fields.add(String(bytes, cursor, len, StandardCharsets.UTF_8))
            cursor += len
        }
        require(cursor == bytes.size) { "trailing bytes after canonical payload" }
        return EvidenceFields(
            version = version,
            challengePubkey = fields[0],
            walletPubkey = fields[1],
            packageName = fields[2],
            targetPackage = fields[3],
            windowStartMs = canonical(fields[4]) { it.toLong() },
            windowEndMs = canonical(fields[5]) { it.toLong() },
            usedSeconds = intAt(fields[6], "usedSeconds"),
            deviceActiveSeconds = canonical(fields[7]) { it.toLong() },
            eventCount = intAt(fields[8], "eventCount"),
            eventSetDigestHex = fields[9],
            tamperSignals = if (fields[10].isEmpty()) emptyList() else fields[10].split(","),
            nonceB64 = fields[11],
        )
    }

    /** Lowercase hex sha-256 of the bytes given — the value written on-chain at settle. */
    fun digestHex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeField(out: ByteArrayOutputStream, value: String) {
        val raw = value.toByteArray(StandardCharsets.UTF_8)
        out.write((raw.size ushr 24) and 0xFF)
        out.write((raw.size ushr 16) and 0xFF)
        out.write((raw.size ushr 8) and 0xFF)
        out.write(raw.size and 0xFF)
        out.write(raw, 0, raw.size)
    }

    private fun assertRange(value: Long, label: String) {
        require(value >= 0) { "evidence field $label must not be negative" }
        require(value <= MAX_EVIDENCE_VALUE) { "evidence field $label exceeds the safe integer range" }
    }

    private fun assertAscii(value: String, label: String) {
        for (element in value) {
            // Java maps a non-ASCII or unpaired character to "?" where a UTF-8
            // encoder emits U+FFFD, so one logical field would get two byte forms
            // and two hashes. Real values are base58, hex, base64 or a package name.
            require(element.code in 0x20..0x7e) { "evidence field $label must be printable ASCII" }
        }
    }

    private fun canonical(s: String, converter: (String) -> Long): Long {
        require(CANONICAL_INT.matches(s)) { "non-canonical numeric evidence field: \"$s\"" }
        return converter(s)
    }

    /**
     * A 16-digit value is inside the safe-integer domain yet outside Int, and
     * `.toInt()` would wrap it silently into a different number. Reject instead:
     * `usedSeconds` and `eventCount` are counts of seconds and events.
     */
    private fun intAt(s: String, label: String): Int {
        val value = canonical(s) { it.toLong() }
        require(value <= Int.MAX_VALUE.toLong()) { "evidence field $label exceeds its declared range" }
        return value.toInt()
    }
}
