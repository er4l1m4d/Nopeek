package com.nopeek.app.data.wallet

import java.math.BigInteger

/**
 * Base58 (Bitcoin alphabet) encoder for Solana public keys.
 */
internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val ZERO = BigInteger.ZERO
    private val BASE = BigInteger.valueOf(58)

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val leadingZeros = data.takeWhile { it == 0.toByte() }.count()
        var num = BigInteger(1, data)
        val digits = StringBuilder()
        while (num >= BASE) {
            val rem = num.mod(BASE)
            num = num.divide(BASE)
            digits.append(ALPHABET[rem.toInt()])
        }
        if (num > ZERO) digits.append(ALPHABET[num.toInt()])
        return "1".repeat(leadingZeros) + digits.reverse().toString()
    }

    fun decode(text: String): ByteArray? {
        if (text.isEmpty()) return null
        val leadingOnes = text.takeWhile { it == '1' }.count()
        var num = BigInteger.ZERO
        for (ch in text) {
            val idx = ALPHABET.indexOf(ch)
            if (idx < 0) return null
            num = num.multiply(BASE).add(BigInteger.valueOf(idx.toLong()))
        }
        val magnitude = if (num == ZERO) {
            ByteArray(0)
        } else {
            val raw = num.toByteArray()
            if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        }
        return ByteArray(leadingOnes) + magnitude
    }
}
