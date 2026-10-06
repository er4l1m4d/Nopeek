package com.nopeek.app.data.solana

import org.json.JSONObject
import java.security.MessageDigest

data class IdlAccountMeta(
    val name: String,
    val isWritable: Boolean,
    val isSigner: Boolean,
)

data class IdlArg(val name: String, val type: String)

data class IdlInstruction(
    val name: String,
    val discriminator: ByteArray,
    val accounts: List<IdlAccountMeta>,
    val args: List<IdlArg>,
)

data class IdlAccountDef(val name: String, val discriminator: ByteArray)

data class IdlField(val name: String, val type: String)

class NopeekIdl(
    val address: String,
    val instructions: List<IdlInstruction>,
    accounts: List<IdlAccountDef>,
    private val types: JSONObject,
) {
    private val byName = instructions.associateBy { it.name }
    val accounts = accounts.associateBy { it.name }

    fun instruction(name: String): IdlInstruction? = byName[name]

    fun structFields(structName: String): List<IdlField> {
        val struct = types.optJSONArray("types")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.optString("name") == structName }
        } ?: return emptyList()
        val fields = struct.optJSONObject("type")?.optJSONArray("fields") ?: return emptyList()
        return (0 until fields.length()).mapNotNull { i ->
            val f = fields.optJSONObject(i) ?: return@mapNotNull null
            val type = f.opt("type") ?: return@mapNotNull null
            IdlField(f.optString("name"), type as? String ?: type.toString())
        }
    }
}

/**
 * Parses the Anchor 0.31 IDL shipped at app/src/main/assets/idl.json.
 *
 * Discriminators are recomputed from names (sha256("global:"+name) /
 * sha256("account:"+name), first 8 bytes) rather than trusted from the file,
 * so a mismatch with the stored value is caught by IdlParserTest.
 */
object IdlParser {

    fun sha256(text: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))

    fun discriminator(instructionName: String): ByteArray =
        sha256("global:$instructionName").copyOf(8)

    fun accountDiscriminator(accountName: String): ByteArray =
        sha256("account:$accountName").copyOf(8)

    fun parse(json: String): NopeekIdl {
        val root = JSONObject(json)

        val instructions = root.optJSONArray("instructions")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val ins = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = ins.optString("name")
                if (name.isEmpty()) return@mapNotNull null
                val accounts = ins.optJSONArray("accounts")?.let { a ->
                    (0 until a.length()).mapNotNull { j ->
                        val acc = a.optJSONObject(j) ?: return@mapNotNull null
                        IdlAccountMeta(
                            name = acc.optString("name"),
                            isWritable = acc.optBoolean("writable"),
                            isSigner = acc.optBoolean("signer"),
                        )
                    }
                } ?: emptyList()
                val args = ins.optJSONArray("args")?.let { a ->
                    (0 until a.length()).mapNotNull { j ->
                        val arg = a.optJSONObject(j) ?: return@mapNotNull null
                        val type = arg.opt("type") ?: return@mapNotNull null
                        IdlArg(arg.optString("name"), type as? String ?: type.toString())
                    }
                } ?: emptyList()
                IdlInstruction(name, discriminator(name), accounts, args)
            }
        } ?: emptyList()

        val accounts = root.optJSONArray("accounts")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val acc = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = acc.optString("name")
                if (name.isEmpty()) return@mapNotNull null
                IdlAccountDef(name, accountDiscriminator(name))
            }
        } ?: emptyList()

        return NopeekIdl(root.optString("address"), instructions, accounts, root)
    }
}
