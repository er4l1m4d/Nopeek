package com.volo.app.data.solana

import com.volo.app.data.wallet.Base58
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionCompilerTest {

    /** A key whose base58 decoding is exactly 32 bytes (31 leading zero bytes + value). */
    private fun key(c: Char) = "1".repeat(31) + c

    private fun le64(v: Long): ByteArray =
        ByteArray(8) { i -> (v ushr (8 * i)).toByte() }

    @Test
    fun shortvec_encodesKnownValues() {
        assertArrayEquals(byteArrayOf(0), TransactionCompiler.shortvec(0))
        assertArrayEquals(byteArrayOf(0x7F), TransactionCompiler.shortvec(127))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), TransactionCompiler.shortvec(128))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x7F), TransactionCompiler.shortvec(16383))
        assertArrayEquals(
            byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x01),
            TransactionCompiler.shortvec(16384),
        )
    }

    @Test
    fun message_headerCountsAndFeePayerFirst() {
        val feePayer = key('F')
        val signer = key('S')
        val writable = key('W')
        val readonly = key('R')
        val programId = key('P')
        val ix = BuiltInstruction(
            programId = programId,
            accounts = listOf(
                AccountKey(feePayer, isSigner = true, isWritable = true),
                AccountKey(signer, isSigner = true, isWritable = false),
                AccountKey(writable, isSigner = false, isWritable = true),
                AccountKey(readonly, isSigner = false, isWritable = false),
            ),
            data = byteArrayOf(1, 2, 3),
        )
        val msg = TransactionCompiler.compileMessage(
            blockhash = key('B'),
            feePayer = feePayer,
            instructions = listOf(ix),
        )
        // header: (numSignatures=2, readonlySigned=1, readonlyUnsigned=2 (readonly + programId))
        assertEquals(2, msg[0].toInt())
        assertEquals(1, msg[1].toInt())
        assertEquals(2, msg[2].toInt())
        // key count = feePayer + signer + writable + readonly + programId = 5
        assertEquals(5, msg[3].toInt())
        // fee payer key bytes immediately after count
        assertArrayEquals(Base58.decode(feePayer), msg.copyOfRange(4, 36))
    }

    @Test
    fun message_programIdResolvesAsReadonlyNonSigner() {
        val feePayer = key('F')
        val programId = key('P')
        val dest = key('D')
        val ix = BuiltInstruction(
            programId = programId,
            accounts = listOf(
                AccountKey(feePayer, isSigner = true, isWritable = true),
                AccountKey(dest, isSigner = false, isWritable = true),
            ),
            data = byteArrayOf(9),
        )
        val compiled = TransactionCompiler.compileInstructions(
            instructions = listOf(ix),
            keyIndex = TransactionCompiler.keyIndexMap(listOf(ix), feePayer),
        )
        assertEquals(2, compiled.first().programIdIndex)
        assertArrayEquals(byteArrayOf(9), compiled.first().data)
        assertEquals(listOf(0, 1), compiled.first().accountIndexes)
    }

    @Test
    fun wireFormat_unsignedTransactionHasPlaceholderSignatures() {
        val feePayer = key('F')
        val ix = BuiltInstruction(
            programId = key('P'),
            accounts = listOf(AccountKey(feePayer, isSigner = true, isWritable = true)),
            data = byteArrayOf(),
        )
        val tx = TransactionCompiler.compileTransaction(
            blockhash = key('B'),
            feePayer = feePayer,
            instructions = listOf(ix),
        )
        // 1 required signature -> shortvec(1) + 64 zero bytes + message
        assertEquals(1, tx[0].toInt())
        for (i in 1..64) assertEquals(0, tx[i].toInt())
        val msg = TransactionCompiler.compileMessage(key('B'), feePayer, listOf(ix))
        assertArrayEquals(msg, tx.copyOfRange(65, tx.size))
    }

    @Test
    fun duplicateAccountKeys_mergePrivilegesAcrossInstructions() {
        val feePayer = key('F')
        val shared = key('S')
        val ix1 = BuiltInstruction(
            programId = key('P'),
            accounts = listOf(AccountKey(shared, isSigner = false, isWritable = false)),
            data = byteArrayOf(1),
        )
        val ix2 = BuiltInstruction(
            programId = key('P'),
            accounts = listOf(AccountKey(shared, isSigner = false, isWritable = true)),
            data = byteArrayOf(2),
        )
        val keys = TransactionCompiler.messageKeys(listOf(ix1, ix2), feePayer)
        assertEquals(3, keys.size)
        val sharedKey = keys.first { it.pubkey == shared }
        assertEquals(true, sharedKey.isWritable)
        val header = TransactionCompiler.headerCounts(keys)
        // 1 signer (feePayer, writable), 0 readonly signed, 1 readonly unsigned (programId)
        assertEquals(1, header.first)
        assertEquals(0, header.second)
        assertEquals(1, header.third)
    }
}
