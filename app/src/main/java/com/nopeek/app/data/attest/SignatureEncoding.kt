package com.nopeek.app.data.attest

/**
 * DER ECDSA signature -> fixed-width raw `r || s` (the IEEE P-1363 layout).
 *
 * This exists because two measured facts lived in different files and contradicted each
 * other. Task 7 measured the Redmi's keystore emitting `len=72 firstByte=0x30` — a DER
 * `SEQUENCE { INTEGER r, INTEGER s }` — while the oracle's `verifyEcdsaSignature` imports the
 * signature as 64 raw bytes, which is what WebCrypto and @noble/curves require for
 * `namedCurve P-256`. Unreconciled, every genuine device signature is rejected
 * BAD_SIGNATURE and the honest user forfeits, while every oracle test stays green because
 * the tests fed it raw bytes in the first place.
 *
 * The conversion happens HERE, on the device, and the oracle keeps accepting exactly one
 * encoding. Accepting both would double the set of byte forms that satisfy "this payload was
 * signed by this key" and break the one-format rule that makes the Kotlin and TypeScript
 * canonical encoders agree byte-for-byte (spec 4.2).
 *
 * `componentSize` is 32 for P-256. DER omits leading zero bytes of the value and prepends one
 * when the top bit is set (so a positive INTEGER is not read as negative), so the two
 * transformations this performs are dropping a 33rd pad byte and left-padding a short value —
 * both on each component independently. Neither may truncate: a silently shortened r would
 * verify against a number the device never signed.
 */
fun derEcdsaToRawP1363(der: ByteArray, componentSize: Int = 32): ByteArray {
    val body = sequenceBody(der)

    val (rBytes, afterR) = integerAt(body, 0, "r", componentSize)
    val (sBytes, afterS) = integerAt(body, afterR, "s", componentSize)
    require(afterS == body.size) {
        "ecdsa signature has trailing bytes after s (${body.size - afterS} of them); not a " +
            "two-component DER sequence"
    }

    val raw = ByteArray(componentSize * 2)
    copyRightAligned(rBytes, raw, 0, componentSize)
    copyRightAligned(sBytes, raw, componentSize, componentSize)
    return raw
}

/** The SEQUENCE's content bytes, rejecting anything that is not one. */
private fun sequenceBody(der: ByteArray): ByteArray {
    require(der.size >= 2) { "ecdsa signature is ${der.size} bytes, too short to be a DER SEQUENCE" }
    require(der[0].toInt() and 0xff == 0x30) {
        "ecdsa signature starts 0x${(der[0].toInt() and 0xff).toString(16)}, not a DER SEQUENCE " +
            "(0x30) — it is probably already raw, or not a signature at all"
    }
    val firstLengthByte = der[1].toInt() and 0xff
    val contentStart: Int
    val contentLength: Int
    if (firstLengthByte and 0x80 == 0) {
        contentStart = 2
        contentLength = firstLengthByte
    } else {
        // Long form: 0x81/0x82 says how many length bytes follow. A P-256 signature never
        // needs it, but DER allows it, and refusing here would reject a valid encoding.
        val lengthBytes = firstLengthByte and 0x7f
        require(lengthBytes in 1..2 && der.size >= 2 + lengthBytes) {
            "ecdsa SEQUENCE declares $lengthBytes length bytes, which this parser cannot read"
        }
        var accumulated = 0
        for (i in 0 until lengthBytes) {
            accumulated = (accumulated shl 8) or (der[2 + i].toInt() and 0xff)
        }
        contentStart = 2 + lengthBytes
        contentLength = accumulated
    }
    require(contentLength >= 2) { "ecdsa SEQUENCE content is $contentLength bytes" }
    require(contentStart + contentLength <= der.size) {
        "ecdsa SEQUENCE declares $contentLength content bytes but only " +
            "${der.size - contentStart} are present"
    }
    return der.copyOfRange(contentStart, contentStart + contentLength)
}

/**
 * Reads one DER INTEGER starting at [at], returning its value bytes (sign pad removed) and
 * the offset after it.
 */
private fun integerAt(body: ByteArray, at: Int, label: String, componentSize: Int): Pair<ByteArray, Int> {
    require(at + 2 <= body.size) { "ecdsa signature is missing its $label INTEGER" }
    require(body[at].toInt() and 0xff == 0x02) {
        "expected the $label INTEGER (0x02) at offset $at, found 0x${(body[at].toInt() and 0xff).toString(16)}"
    }
    val length = body[at + 1].toInt() and 0xff
    require(length in 1..componentSize + 1) {
        "$label is encoded in $length bytes, outside 1..${componentSize + 1} — a value that long " +
            "is not a P-256 component and truncating it would change what the signature means"
    }
    require(at + 2 + length <= body.size) {
        "$label declares $length bytes but only ${body.size - at - 2} remain"
    }
    var value = body.copyOfRange(at + 2, at + 2 + length)
    // Drop the sign pad DER prepends when the top bit of the value is set. Only a leading
    // zero followed by a non-zero first real byte qualifies, so an all-zero value stays put.
    if (value.size > 1 && value[0].toInt() == 0) value = value.copyOfRange(1, value.size)
    return value to (at + 2 + length)
}

private fun copyRightAligned(value: ByteArray, into: ByteArray, offset: Int, componentSize: Int) {
    require(value.size <= componentSize) {
        "component is ${value.size} bytes, wider than $componentSize"
    }
    // Left-pad with zeros: raw P-1363 places the value's most significant byte at
    // (componentSize - value.size), so a 3-byte r occupies the LAST three bytes of its half.
    System.arraycopy(value, 0, into, offset + (componentSize - value.size), value.size)
}
