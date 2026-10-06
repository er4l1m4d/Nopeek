package com.nopeek.app.data.solana

import com.nopeek.app.data.wallet.Base58

data class CompiledInstruction(
    val programIdIndex: Int,
    val accountIndexes: List<Int>,
    val data: ByteArray,
)

/**
 * Legacy (pre-versioned) Solana transaction serialization:
 * shortvec(signatureCount) + signature placeholders + message,
 * message = header(3) + shortvec(keyCount) + keys(32B each) + blockhash(32B)
 *           + shortvec(ixCount) + [programIdIndex + shortvec + idxs + shortvec + data].
 */
object TransactionCompiler {

    fun shortvec(value: Int): ByteArray {
        require(value >= 0) { "shortvec value must be >= 0" }
        val out = ArrayList<Byte>()
        var v = value
        while (true) {
            val byte = (v and 0x7F).toByte()
            v = v ushr 7
            if (v == 0) {
                out.add(byte)
                break
            }
            out.add((byte.toInt() or 0x80).toByte())
        }
        return out.toByteArray()
    }

    private fun decodeKey(pubkey: String): ByteArray {
        val bytes = Base58.decode(pubkey) ?: throw IllegalArgumentException("invalid pubkey $pubkey")
        if (bytes.size != 32) throw IllegalArgumentException("pubkey must decode to 32 bytes: $pubkey")
        return bytes
    }

    /** Distinct keys, fee payer first, then writable signers / readonly signers / writable / readonly. */
    fun messageKeys(instructions: List<BuiltInstruction>, feePayer: String): List<AccountKey> {
        val merged = LinkedHashMap<String, AccountKey>()
        fun merge(key: AccountKey) {
            val existing = merged[key.pubkey]
            merged[key.pubkey] = if (existing == null) {
                key
            } else {
                existing.copy(
                    isSigner = existing.isSigner || key.isSigner,
                    isWritable = existing.isWritable || key.isWritable,
                )
            }
        }
        merge(AccountKey(feePayer, isSigner = true, isWritable = true))
        for (ix in instructions) {
            for (account in ix.accounts) merge(account)
            merge(AccountKey(ix.programId, isSigner = false, isWritable = false))
        }
        val feePayerKey = merged.remove(feePayer)!!.copy(isSigner = true, isWritable = true)
        val rest = merged.values
        val writableSigners = rest.filter { it.isSigner && it.isWritable }
        val readonlySigners = rest.filter { it.isSigner && !it.isWritable }
        val writableNonSigners = rest.filter { !it.isSigner && it.isWritable }
        val readonlyNonSigners = rest.filter { !it.isSigner && !it.isWritable }
        return listOf(feePayerKey) + writableSigners + readonlySigners + writableNonSigners + readonlyNonSigners
    }

    /** (numRequiredSignatures, numReadonlySignedAccounts, numReadonlyUnsignedAccounts) */
    fun headerCounts(keys: List<AccountKey>): Triple<Int, Int, Int> {
        val signed = keys.count { it.isSigner }
        val readonlySigned = keys.count { it.isSigner && !it.isWritable }
        val readonlyUnsigned = keys.count { !it.isSigner && !it.isWritable }
        return Triple(signed, readonlySigned, readonlyUnsigned)
    }

    fun keyIndexMap(instructions: List<BuiltInstruction>, feePayer: String): Map<String, Int> =
        messageKeys(instructions, feePayer).mapIndexed { i, k -> k.pubkey to i }.toMap()

    fun compileInstructions(
        instructions: List<BuiltInstruction>,
        keyIndex: Map<String, Int>,
    ): List<CompiledInstruction> = instructions.map { ix ->
        CompiledInstruction(
            programIdIndex = keyIndex.getValue(ix.programId),
            accountIndexes = ix.accounts.map { keyIndex.getValue(it.pubkey) },
            data = ix.data,
        )
    }

    fun compileMessage(
        blockhash: String,
        feePayer: String,
        instructions: List<BuiltInstruction>,
    ): ByteArray {
        val keys = messageKeys(instructions, feePayer)
        val (signed, readonlySigned, readonlyUnsigned) = headerCounts(keys)
        val blockhashBytes = decodeKey(blockhash)
        val compiled = compileInstructions(instructions, keyIndex = keys.mapIndexed { i, k -> k.pubkey to i }.toMap())

        val out = ArrayList<Byte>()
        out.add(signed.toByte())
        out.add(readonlySigned.toByte())
        out.add(readonlyUnsigned.toByte())
        out.addAll(shortvec(keys.size).toList())
        for (key in keys) out.addAll(decodeKey(key.pubkey).toList())
        out.addAll(blockhashBytes.toList())
        out.addAll(shortvec(compiled.size).toList())
        for (ix in compiled) {
            out.add(ix.programIdIndex.toByte())
            out.addAll(shortvec(ix.accountIndexes.size).toList())
            for (idx in ix.accountIndexes) out.add(idx.toByte())
            out.addAll(shortvec(ix.data.size).toList())
            out.addAll(ix.data.toList())
        }
        return out.toByteArray()
    }

    /** Unsigned wire format: shortvec(n) + n zeroed signatures + message. */
    fun compileTransaction(
        blockhash: String,
        feePayer: String,
        instructions: List<BuiltInstruction>,
    ): ByteArray {
        val keys = messageKeys(instructions, feePayer)
        val signatureCount = headerCounts(keys).first
        val message = compileMessage(blockhash, feePayer, instructions)
        return shortvec(signatureCount) + ByteArray(signatureCount * 64) + message
    }
}
