package com.nopeek.app.data.attest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Task 13: the signature encoding bridge.
 *
 * Measured on the Redmi in Task 7 (`NOPEEK_SIGN_SHAPE len=72 firstByte=0x30 der=true
 * rawP1363=false`): AndroidKeyStore's SHA256withECDSA emits a DER SEQUENCE, while the
 * oracle's verifyEcdsaSignature imports the signature as fixed-width raw r||s (P-1363),
 * which is what WebCrypto wants. Those two facts came from two different files and had
 * never been put in the same room. Left unreconciled, every genuine device signature fails
 * BAD_SIGNATURE while all oracle tests stay green — the device would look enrolled and
 * healthy and still never settle.
 *
 * The direction chosen is convert-on-device, oracle-stays-strict, because the project's
 * canonical-bytes rule is one format per thing: accepting both encodings would double the
 * set of byte forms that satisfy "this payload was signed by this key" and would quietly
 * break the one-format principle that makes the Kotlin and TypeScript encoders agree
 * byte-for-byte.
 */
class EcdsaSignatureEncodingTest {

    /** Builds a DER SEQUENCE{INTEGER r, INTEGER s} the way a signer would, minus the pads. */
    private fun der(r: ByteArray, s: ByteArray): ByteArray {
        fun integer(v: ByteArray): ByteArray {
            // DER INTEGER is signed: a high bit set on the first byte needs a leading 0x00
            // so the value is not read as negative. That is the pad this conversion must
            // remove, and the reason a naive "take the last 32 bytes" is wrong.
            val needsPad = v.firstOrNull()?.let { it.toInt() and 0x80 != 0 } == true
            val body = if (needsPad) byteArrayOf(0) + v else v
            return byteArrayOf(0x02, body.size.toByte()) + body
        }
        val body = integer(r) + integer(s)
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    @Test
    fun fullWidthComponentsAreCopiedWithoutTheirPadBytes() {
        val r = ByteArray(32) { (it + 1).toByte() }
        val s = ByteArray(32) { (it + 33).toByte() }
        // Both start with a byte >= 0x80, so DER prepends 0x00 to each: the encoded value is
        // 33 bytes and the 32 real bytes sit at the END. Stripping the tag/length and taking
        // the first 32 bytes would silently return 0x00 followed by the wrong 31.
        val highR = withHighBit(r)
        val highS = withHighBit(s)

        val raw = derEcdsaToRawP1363(der(highR, highS))

        assertEquals("a P-256 raw signature is exactly 64 bytes", 64, raw.size)
        assertEquals(
            "r must be the 32 value bytes, pad removed, right-aligned",
            highR.toList(), raw.slice(0 until 32).toList(),
        )
        assertEquals(
            "s must be the 32 value bytes, pad removed, right-aligned",
            highS.toList(), raw.slice(32 until 64).toList(),
        )
    }

    @Test
    fun shortComponentsAreLeftPaddedWithZeros() {
        // ECDSA s is frequently small, and DER does not encode leading zero bytes at all.
        // Raw P-1363 always occupies 32 bytes, so the difference must be filled with zeros
        // at the front — not appended, and not left as a 3-byte hole in a 64-byte buffer.
        val r = byteArrayOf(0x01, 0x02, 0x03)
        val s = byteArrayOf(0x7F.toByte())

        val raw = derEcdsaToRawP1363(der(r, s))

        assertEquals(64, raw.size)
        val expectedR = ByteArray(29) + r
        val expectedS = ByteArray(31) + s
        assertEquals("short r zero-padded on the left", expectedR.toList(), raw.slice(0 until 32).toList())
        assertEquals("short s zero-padded on the left", expectedS.toList(), raw.slice(32 until 64).toList())
    }

    @Test
    fun theRawLayoutMatchesWhatTheOracleExpectsByteForByte() {
        // The property the oracle actually depends on: the 64 bytes must be r||s with each
        // component interpreted as an unsigned big-endian integer, because that is the layout
        // WebCrypto and @noble/curves both expect for namedCurve P-256.
        val r = ByteArray(32) { 0xA5.toByte() }
        val s = ByteArray(32) { 0x5A.toByte() }

        val raw = derEcdsaToRawP1363(der(r, s))

        assertEquals(r.toList(), raw.take(32))
        assertEquals(s.toList(), raw.drop(32))
    }

    @Test
    fun oversizedComponentsAreRefusedRatherThanTruncated() {
        // 33 value bytes whose first byte has the top bit CLEAR, so DER adds no sign pad and
        // the length really is 33: the number does not fit a P-256 field. Built as valid DER
        // (SEQUENCE length, then two INTEGERs) so the rejection can only come from the width
        // check — if this were malformed, a parser error would pass the test for free.
        val tooLong = ByteArray(33) { 0x01 }
        val encoded = byteArrayOf(0x30, 38, 0x02, tooLong.size.toByte()) + tooLong +
            byteArrayOf(0x02, 0x01, 0x01)

        assertThrows(IllegalArgumentException::class.java) { derEcdsaToRawP1363(encoded) }
    }

    @Test
    fun structurallyBrokenInputIsRefused() {
        // Each of these is a different way a byte string can look like a signature without
        // being one. A silent wrong answer here becomes BAD_SIGNATURE on a genuine report,
        // which costs an honest user their stake — so all of them must throw.
        assertThrows(IllegalArgumentException::class.java) { derEcdsaToRawP1363(ByteArray(0)) }
        // not a SEQUENCE
        assertThrows(IllegalArgumentException::class.java) { derEcdsaToRawP1363(byteArrayOf(0x02, 0x01, 0x01)) }
        // SEQUENCE claiming more bytes than were given
        assertThrows(IllegalArgumentException::class.java) {
            derEcdsaToRawP1363(byteArrayOf(0x30, 0x40, 0x02, 0x01, 0x01))
        }
        // only one INTEGER
        assertThrows(IllegalArgumentException::class.java) {
            derEcdsaToRawP1363(byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01))
        }
    }

    /** Sets the top bit of the first byte, which is what makes DER prepend its sign pad. */
    private fun withHighBit(input: ByteArray): ByteArray {
        val out = input.clone() as ByteArray
        out[0] = (out[0].toInt() or 0x80).toByte()
        return out
    }
}
