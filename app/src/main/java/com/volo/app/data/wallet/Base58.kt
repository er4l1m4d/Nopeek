package com.volo.app.data.wallet

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
}
