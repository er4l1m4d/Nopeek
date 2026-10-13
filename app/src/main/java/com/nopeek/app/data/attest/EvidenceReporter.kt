package com.nopeek.app.data.attest

import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.usage.NopeekBackend
import com.nopeek.app.data.usage.isoUtc
import com.nopeek.app.data.usage.reportPayload
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * What the reporter concluded about one window. Four states, and the distinction between the
 * last two is the one that matters to a user: a refusal is a verdict, a transport failure is
 * not, and treating it as one would forfeit a stake over a bad network.
 */
sealed class ReportOutcome {
    /** The oracle accepted the report. [evidenceHash] is lowercase hex sha-256 of the bytes sent. */
    data class Accepted(val evidenceHash: String) : ReportOutcome()

    /** The oracle or a local precondition refused it. [reason] is the rejection, not a verdict. */
    data class Rejected(val reason: String) : ReportOutcome()

    /** The window has not closed, or its grace has run out — decided before any network call. */
    object NotYetDue : ReportOutcome()

    /** Could not reach the oracle. Retryable: nothing was consumed and no verdict exists. */
    data class TransportFailed(val cause: String) : ReportOutcome()
}

/** The oracle's answer to POST /submit-report, collapsed to what the reporter can act on. */
sealed class SubmitResult {
    object Accepted : SubmitResult()
    data class Refused(val reason: String) : SubmitResult()
    data class Failed(val cause: String) : SubmitResult()
}

/**
 * The three calls the protocol needs, as one injectable seam.
 *
 * Keeping them behind an interface is not decoration: it is what lets the ordering and byte
 * rules below be tested on the JVM. The keystore and the network are each proven separately —
 * the keystore on the device in EnrollRoundTripTest, these routes against the deployed
 * function — because a JVM test of an HTTP call is a test of a stub.
 */
interface EvidenceTransport {
    /**
     * Single-use nonce for this (challenge, wallet). Implementations THROW when issuance
     * fails: a report without a server-issued nonce cannot pass checks 5 and 9, so there is
     * nothing sensible to fall back to and no point minting a key or signing bytes first.
     */
    fun fetchNonce(challengePubkey: String, walletPubkey: String, windowEndMs: Long): String

    /** Registers the freshly minted key. False means the oracle will not trust this chain. */
    fun enroll(walletPubkey: String, key: EnrolledKey): Boolean

    fun submit(body: Map<String, Any>): SubmitResult
}

/**
 * Produces one attested, signed compliance report (plan Task 13, spec 4.1).
 *
 * The order is the protocol and cannot be rearranged:
 * 1. `/nonce` — the server decides the window's single-use value.
 * 2. mint — a fresh attested key whose `attestationChallenge` IS that nonce, so the hardware
 *    attests this window rather than the install. (A key minted once could never satisfy
 *    check 6 for a second window; that contradiction is what this ordering fixes.)
 * 3. `/enroll` — publish the chain before anything is signed with it, because check 7 compares
 *    the enrolled leaf against the leaf in the chain sent with the report.
 * 4. encode → sign → DER→raw → `/submit-report`.
 *
 * Steps 2 and 4 are injected as plain functions, so this class can be tested without a
 * keystore. [sign] must return whatever the platform produced — DER from AndroidKeyStore —
 * and this class converts it, because the oracle pins exactly one signature encoding and the
 * translation belongs on the side that has the extra information, not on the verifier.
 */
class EvidenceReporter(
    private val transport: EvidenceTransport,
    private val packageName: String,
    private val mintKey: (challengeB64: String) -> EnrolledKey,
    private val sign: (alias: String, bytes: ByteArray) -> ByteArray,
    private val graceSeconds: Int = 60,
) {

    /**
     * Windows already accepted in this process, keyed by window identity -> evidence hash.
     *
     * Keyed by the window and NOT by the evidence hash, which is what plan Task 13 assumed.
     * That assumption cannot hold: `nonce` is one of the canonical fields (§4.2), so a repeat
     * attempt produces fresh bytes and a fresh hash, and a hash-keyed cache would miss every
     * time — while making a second `/nonce` call to find out, burning a row of the window's
     * single-use supply. Keying on the window identity short-circuits before any transport
     * call, which is the behaviour Review Focus 2 actually needs: one submission per window.
     *
     * In-memory only. The durable guarantee is the oracle's nonce row plus the `verified`
     * column; this cache spares a retrying worker within one process a keygen, a mint and a
     * POST for a window the oracle has already accepted.
     */
    private val acceptedWindows = mutableMapOf<String, String>()

    fun submit(report: UsageReport, targetPackage: String): ReportOutcome {
        val windowKey = windowKeyOf(report, targetPackage)
        acceptedWindows[windowKey]?.let { return ReportOutcome.Accepted(it) }

        val nonceB64 = try {
            transport.fetchNonce(report.challengePubkey, report.walletPubkey, report.windowEnd)
        } catch (e: IOException) {
            return ReportOutcome.TransportFailed("nonce: ${e.message}")
        } catch (e: RuntimeException) {
            return ReportOutcome.TransportFailed("nonce: ${e.message}")
        }

        val enrolled = try {
            mintKey(nonceB64)
        } catch (e: IllegalStateException) {
            // No attested chain means there is no compliance claim to make on this device.
            return ReportOutcome.Rejected("ATTESTATION_UNAVAILABLE: ${e.message}")
        }

        val canonicalBytes = CanonicalPayload.encode(
            EvidenceFields(
                version = CanonicalPayload.VERSION,
                challengePubkey = report.challengePubkey,
                walletPubkey = report.walletPubkey,
                packageName = packageName,
                targetPackage = targetPackage,
                windowStartMs = report.windowStart,
                windowEndMs = report.windowEnd,
                usedSeconds = report.usedSeconds,
                deviceActiveSeconds = report.deviceActiveSeconds,
                eventCount = report.eventCount,
                eventSetDigestHex = report.eventSetDigestHex,
                tamperSignals = com.nopeek.app.data.usage.tamperSignalNames(report.tamperSignals),
                nonceB64 = nonceB64,
            ),
        )

        val evidenceHash = CanonicalPayload.digestHex(canonicalBytes)

        if (!transport.enroll(report.walletPubkey, enrolled)) {
            // Nothing has been submitted, so the nonce is still unconsumed and the window is
            // still winnable. Submitting anyway would burn the one nonce and still be refused.
            return ReportOutcome.Rejected("ENROLL_REFUSED")
        }

        val der = sign(enrolled.alias, canonicalBytes)
        val raw = try {
            derEcdsaToRawP1363(der)
        } catch (e: IllegalArgumentException) {
            // The keystore returned something that is not the signature format we expect.
            // Sending it would be refused BAD_SIGNATURE, and guessing here would produce a
            // signature over different bytes than the ones the device meant to attest.
            return ReportOutcome.Rejected("SIGNATURE_SHAPE: ${e.message}")
        }

        val body = reportPayload(report) + mapOf(
            "canonicalBytesB64" to Base64.getEncoder().encodeToString(canonicalBytes),
            "signatureB64" to Base64.getEncoder().encodeToString(raw),
            "chainPem" to enrolled.chainPem,
        )

        return when (val result = transport.submit(body)) {
            is SubmitResult.Accepted -> {
                acceptedWindows[windowKey] = evidenceHash
                ReportOutcome.Accepted(evidenceHash)
            }
            is SubmitResult.Refused -> ReportOutcome.Rejected(result.reason)
            is SubmitResult.Failed -> ReportOutcome.TransportFailed(result.cause)
        }
    }

    /**
     * Everything that makes one window's report the same report: the pair, the window, and
     * the measured content. Not the nonce, which arrives from the server per attempt, and not
     * `submittedAt`, which is a wall-clock stamp that differs between retries of one window.
     */
    private fun windowKeyOf(report: UsageReport, targetPackage: String) = listOf(
        report.challengePubkey, report.walletPubkey, targetPackage,
        report.windowStart, report.windowEnd, report.usedSeconds,
        report.deviceActiveSeconds, report.eventCount, report.eventSetDigestHex,
        com.nopeek.app.data.usage.tamperSignalNames(report.tamperSignals).joinToString(","),
    ).joinToString("|")

    /**
     * `targetPackage` is a parameter rather than a field of [UsageReport] because it belongs
     * to the challenge, not to the measurement: check 11 compares the attested target against
     * the challenge row's `target_app`, so the value must arrive from Room with the challenge
     * and never be guessed here. A wrong guess would be attested by hardware, transmitted, and
     * then refused — a forfeit caused by our own plumbing rather than by the user's behaviour.
     */
}

/**
 * The deployed oracle's three routes, over HttpURLConnection.
 *
 * Field names and response shapes are copied from supabase/functions/oracle/index.ts as
 * deployed — `expiresAt` is required by /nonce, whose answer is `nonceB64`; /enroll answers
 * `{ok}`; /submit-report answers `{ok, id, evidenceHash, checksPassed}`. Written from the
 * route source rather than from memory, because two of today's bugs were exactly this class
 * of drift.
 */
class OracleEvidenceTransport : EvidenceTransport {

    override fun fetchNonce(challengePubkey: String, walletPubkey: String, windowEndMs: Long): String {
        val response = post(
            NopeekBackend.oracleUrl("nonce"),
            JSONObject(
                mapOf(
                    "challengePubkey" to challengePubkey,
                    "walletPubkey" to walletPubkey,
                    // windowEnd + grace, in the device clock: the oracle stores this as the
                    // row's expiry and check 14 refuses a report after it, so a value in the
                    // wrong clock or format reads as late on a report that was on time.
                    "expiresAt" to isoUtc(windowEndMs + GRACE_SECONDS * 1000L),
                ),
            ),
        )
        return response.getString("nonceB64")
    }

    override fun enroll(walletPubkey: String, key: EnrolledKey): Boolean {
        val response = post(
            NopeekBackend.oracleUrl("enroll"),
            JSONObject(
                mapOf(
                    "walletPubkey" to walletPubkey,
                    "spkiB64" to key.spkiB64,
                    "chainPem" to key.chainPem,
                    "certDigestB64" to key.certDigestB64,
                ),
            ),
        )
        return response.optBoolean("ok", false)
    }

    override fun submit(body: Map<String, Any>): SubmitResult = try {
        val response = post(NopeekBackend.oracleUrl("submit-report"), JSONObject(body))
        if (response.optBoolean("ok", false)) {
            SubmitResult.Accepted
        } else {
            // A 400 with a reason is a refusal; the reason is already the oracle's own
            // domain code (EVIDENCE_MISSING, CHAIN_NOT_ROOTED, NONCE_MISMATCH, ...).
            SubmitResult.Refused(response.optString("error", response.optString("reason", "REFUSED")))
        }
    } catch (e: HttpErrorResponse) {
        // 4xx from the oracle is a VERDICT, not a transport problem: the body names the
        // reason and nothing about the connection failed. Surfacing it as Failed (retryable)
        // made the worker re-mint, re-enroll and resubmit a report the oracle had already
        // judged — burning the window's nonce for nothing.
        SubmitResult.Refused(e.reason())
    } catch (e: IOException) {
        SubmitResult.Failed(e.message ?: "network")
    }

    private fun post(url: String, body: JSONObject): org.json.JSONObject {
        if (!NopeekBackend.isConfigured()) throw IOException("backend not configured")
        // Redirects are followed MANUALLY, once, with the hop logged. HttpURLConnection with
        // instanceFollowRedirects=true silently re-sends the POST body on a 307/308 — for
        // /submit-report that means the oracle executes twice: the first run consumes the
        // window's single nonce, the second returns NONCE_CONSUMED, and the app never saw
        // the real verdict. The hop must be visible and the resend deliberate.
        var currentUrl = url
        var currentBody = body.toString()
        repeat(2) { attempt ->
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(currentBody.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                android.util.Log.i("NOPEEK_HTTP", "attempt=$attempt code=$code url=$currentUrl body=${text.take(300)}")
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw HttpErrorResponse(code, "redirect without Location: $text")
                    // 307/308 keep method and body; 301/302 would not, and a POST silently
                    // becoming a GET is worse than a loud failure.
                    if (code != 307 && code != 308) throw HttpErrorResponse(code, "unhandled redirect $code -> $location")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    throw HttpErrorResponse(code, text)
                }
                return org.json.JSONObject(text)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("unreachable: repeat() must return or throw")
    }

    private companion object {
        /** Must match the oracle's grace window, or the expiry it stores is not the one used. */
        const val GRACE_SECONDS = 60
    }
}

/** A non-2xx oracle response. `reason()` extracts the oracle's own reason code when present. */
class HttpErrorResponse(val code: Int, private val body: String) : IOException("$code: ${body.take(200)}") {
    fun reason(): String = try {
        org.json.JSONObject(body).optString("reason", "HTTP_$code")
    } catch (_: org.json.JSONException) {
        "HTTP_$code"
    }
}
