package com.nopeek.app.data.solana

import java.io.ByteArrayOutputStream
import java.math.BigInteger

data class PdaAddress(val address: ByteArray, val bump: Int)

/**
 * Solana program-derived-address derivation: sha256(len-prefixed seeds + bump +
 * programId + "ProgramDerivedAddress"), skipping bumps whose hash lands on the
 * ed25519 curve (on-curve points cannot be PDAs).
 *
 * The on-curve check follows RFC 8032 point decompression (recovery of x from y).
 */
object Pda {

    private const val MAX_SEED_LEN = 32
    private val P =
        BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val D =
        BigInteger.valueOf(-121665).mod(P).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val SQRT_M1 = BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P)

    fun findProgramAddress(seeds: List<ByteArray>, programId: ByteArray): PdaAddress {
        for (seed in seeds) {
            require(seed.size <= MAX_SEED_LEN) { "seed longer than $MAX_SEED_LEN bytes" }
        }
        for (bump in 255 downTo 0) {
            val candidate = tryDerive(seeds, programId, bump)
            if (!isOnCurve(candidate)) return PdaAddress(candidate, bump)
        }
        throw IllegalStateException("could not find program address for seeds")
    }

    fun tryDerive(seeds: List<ByteArray>, programId: ByteArray, bump: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        for (seed in seeds) {
            buffer.write(seed.size)
            buffer.write(seed)
        }
        buffer.write(bump)
        buffer.write(programId)
        buffer.write("ProgramDerivedAddress".toByteArray(Charsets.UTF_8))
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(buffer.toByteArray())
    }

    /** RFC 8032 decompression: does there exist an x with x^2 = (y^2-1)/(d*y^2+1)? */
    fun isOnCurve(address: ByteArray): Boolean {
        if (address.size != 32) return false
        // bytes are little-endian y with the sign of x in the top bit of byte 31
        val le = address.reversedArray()
        var y = BigInteger(1, le).clearBit(255)
        if (y >= P) return false
        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(BigInteger.ONE).mod(P)
        val x2 = try {
            u.multiply(v.modInverse(P)).mod(P)
        } catch (e: ArithmeticException) {
            return false
        }
        var x = x2.modPow(P.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), P)
        if (x.multiply(x).mod(P) != x2) {
            x = x.multiply(SQRT_M1).mod(P)
        }
        return x.multiply(x).mod(P) == x2
    }
}
