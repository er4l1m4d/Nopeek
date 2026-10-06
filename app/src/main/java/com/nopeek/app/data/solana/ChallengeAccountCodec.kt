package com.nopeek.app.data.solana

data class ChallengeAccount(
    val creator: ByteArray,
    val opponent: ByteArray,
    val tokenMint: ByteArray,
    val stakeAmount: Long,
    val targetApp: String,
    val limitSeconds: Int,
    val windowSeconds: Int,
    val startTime: Long,
    val endTime: Long,
    val status: Int,
    val result: Int,
    val bump: Int,
)

/**
 * Decodes the on-chain Challenge account defined in contracts/program/src/lib.rs:
 * disc(8) + creator(32) + opponent(32) + mint(32) + stake u64 + string(u32+utf8)
 * + u32 + u32 + i64 + i64 + u8 + u8 + u8  == 175 bytes (LEN).
 */
object ChallengeAccountCodec {

    private const val HEADER = 8 + 32 + 32 + 32 + 8 + 4
    private const val TAIL = 4 + 4 + 8 + 8 + 3

    fun decode(bytes: ByteArray): ChallengeAccount? {
        if (bytes.size < HEADER + TAIL) return null
        val discriminator = IdlParser.accountDiscriminator("Challenge")
        for (i in discriminator.indices) {
            if (bytes[i] != discriminator[i]) return null
        }
        val creator = bytes.copyOfRange(8, 40)
        val opponent = bytes.copyOfRange(40, 72)
        val mint = bytes.copyOfRange(72, 104)
        val stake = le64(bytes, 104)
        val appLen = le32(bytes, 112)
        if (appLen < 0 || bytes.size != HEADER + appLen + TAIL) return null
        val appBytes = bytes.copyOfRange(HEADER, HEADER + appLen)
        val app = try {
            String(appBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        var offset = HEADER + appLen
        val limit = le32(bytes, offset); offset += 4
        val window = le32(bytes, offset); offset += 4
        val start = le64(bytes, offset); offset += 8
        val end = le64(bytes, offset); offset += 8
        val status = bytes[offset].toInt() and 0xFF; offset += 1
        val result = bytes[offset].toInt() and 0xFF; offset += 1
        val bump = bytes[offset].toInt() and 0xFF
        return ChallengeAccount(
            creator = creator,
            opponent = opponent,
            tokenMint = mint,
            stakeAmount = stake,
            targetApp = app,
            limitSeconds = limit,
            windowSeconds = window,
            startTime = start,
            endTime = end,
            status = status,
            result = result,
            bump = bump,
        )
    }

    private fun le32(bytes: ByteArray, offset: Int): Int {
        var v = 0
        for (i in 0 until 4) v = v or ((bytes[offset + i].toInt() and 0xFF) shl (8 * i))
        return v
    }

    private fun le64(bytes: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
