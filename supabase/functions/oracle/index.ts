import { createClient } from "npm:@supabase/supabase-js@2";
import {
  determineResult,
  settleOutcome,
  type Verdict,
} from "./determine_result.ts";
import { productionDeps, verifyReport, type NonceStatus } from "./checks.ts";
import { authorizeRequest } from "./auth.ts";
import { enrollKey, issueNonce } from "./store.ts";
import {
  makeNobleEcdsaVerifier,
  parseRevokedFeed,
  pemToDerBlocks,
  readAttestationExtension,
  verifyChainToPinnedRoot,
} from "./x509.ts";
// Pinned to the version @noble/curves is installed at in package.json (2.4.0), which is
// the version x509.test.ts verified against the real Google chain under Node. A floating
// major here would let production run maths the tested code never checked.
import { p256, p384, p521 } from "npm:@noble/curves@2.4.0/nist.js";

/**
 * The attested identity the report must carry. Both values were measured, not recalled:
 * the package name is the applicationId in app/build.gradle.kts, and the digest is
 * sha256 of the debug keystore's signing certificate — computed by keytool and confirmed
 * equal to the value x509.ts parses out of attestationApplicationId in the captured Redmi
 * chain. Task 19 replaces the digest with the release keystore's.
 */
const EXPECTED_PACKAGE_NAME = "com.nopeek.app";
const EXPECTED_APK_SIGNER_DIGEST_B64 = "V03kcUk9V3oqTVu6IuiHFoFgf/eLmaehbyoc0zyDqIY=";
const REVOKED_SERIALS_URL = "https://android.googleapis.com/attestation/status";

/**
 * ECDSA for the chain links, from pure JS rather than the host.
 *
 * Deno 2.1.4 — the runtime Supabase Edge Functions run — throws `DOMException: Not
 * implemented` from crypto.subtle.verify for P-384, and Google signs the attestation
 * intermediate with a P-384 key. Measured per-link on the deployed function: the P-256
 * leaf link verified, the P-384 link did not, so the live oracle rejected every genuine
 * device chain at CHAIN_NOT_ROOTED. All ECDSA links go through here — not just P-384 — so
 * there is one implementation and no host-dependent behaviour left to be surprised by.
 * The third link (the intermediate's signature, verified under the pinned root's RSA key)
 * still uses the host's RSASSA-PKCS1-v1_5, which this runtime does implement: the live
 * endpoint reached NONCE_MISMATCH on a real chain, which requires that link to have
 * verified. The root's own self-signature is never checked — it is trusted by identity.
 */
const ECDSA_VERIFY = makeNobleEcdsaVerifier({ "P-256": p256, "P-384": p384, "P-521": p521 });

/**
 * Verifier dependencies, built here and nowhere else, so that "the chain anchor cannot be
 * supplied by the caller" stays auditable in one place.
 *
 * revokedEntries distinguishes null (feed unreachable -> verifyReport fails closed) from
 * [] (feed fetched, nothing revoked). Collapsing those turns a Google outage into a
 * silent pass, which is the exact bug this hook was written to avoid.
 */
function verifierDeps() {
  return productionDeps(EXPECTED_PACKAGE_NAME, EXPECTED_APK_SIGNER_DIGEST_B64, {
    ecdsaVerify: ECDSA_VERIFY,
    lookupEnrolledKey: async (walletPubkey: string) => {
      const { data, error } = await db
        .from("install_keys")
        .select("keystore_spki_b64")
        .eq("wallet_pubkey", walletPubkey)
        .is("revoked_at", null)
        .maybeSingle();
      if (error) throw new Error(`install_keys lookup failed: ${error.message}`);
      return data ? { spkiB64: data.keystore_spki_b64 as string } : null;
    },

    consumeNonce: async (
      nonceB64: string,
      challengePubkey: string,
      walletPubkey: string,
    ): Promise<NonceStatus> => {
      const { data, error } = await db
        .from("report_nonces")
        .select("id, expires_at, consumed_at")
        .eq("challenge_pubkey", challengePubkey)
        .eq("wallet_pubkey", walletPubkey)
        .eq("nonce_b64", nonceB64)
        .maybeSingle();
      if (error) throw new Error(`report_nonces lookup failed: ${error.message}`);
      if (!data) return "mismatch";
      if (data.consumed_at) return "consumed";
      if (new Date(data.expires_at as string).getTime() <= Date.now()) return "expired";
      // Conditional update, success judged by RETURNING the row rather than by a count
      // option. Measured live 2026-10-13: with { count: "EXACT" } the edge runtime's
      // supabase-js returned no usable count on a SUCCESSFUL consume, so the ok branch
      // read as a lost race and every genuine report was refused NONCE_CONSUMED with its
      // nonce burned by its own verification. `.select()` after `.update()` returns the
      // updated row (empty on a lost race), which is shape-stable across versions.
      const { data: updated, error: cErr } = await db
        .from("report_nonces")
        .update({ consumed_at: new Date().toISOString() })
        .eq("id", data.id)
        .is("consumed_at", null)
        .select("id")
        .maybeSingle();
      if (cErr) throw new Error(`report_nonces consume failed: ${cErr.message}`);
      return updated ? "ok" : "consumed";
    },

    // Google's real feed keys its list `entries`, writes serials in a mix of decimal and
    // hex, and gives each entry { status, reason } with no issuer. Reading `serials` — the
    // shape this code guessed — returned null on every call, and the fail-closed contract
    // correctly turned that into CHAIN_REVOKED for every genuine report. Parsing lives in
    // x509.ts so it can be tested against a captured slice of the real file.
    revokedEntries: async () => {
      try {
        const res = await fetch(REVOKED_SERIALS_URL, { signal: AbortSignal.timeout(8000) });
        if (!res.ok) return null;
        return parseRevokedFeed(await res.json());
      } catch (e) {
        // null, not []: an unreachable or unparseable feed must not read as "nothing is
        // revoked". Logged, because an outage and a format change otherwise look identical
        // from the outside — both are just a rejected report.
        console.log(`REVOCATION_FEED_UNAVAILABLE ${e instanceof Error ? e.message : String(e)}`);
        return null;
      }
    },
  });
}

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ORACLE_API_SECRET = Deno.env.get("ORACLE_API_SECRET");

const db = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

const BASE58 = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/;
const MAX_USED_SECONDS = 2_592_000; // 30 days
const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, content-type, x-oracle-secret",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

/**
 * POST /enroll — public by design (spec 7 trust inversion): the device cannot hold a
 * shared secret without shipping it in the APK, so the attestation signature is the
 * authentication. Authorisation for this route is decided in auth.ts against the same
 * `path` the router dispatches on.
 *
 * The chain gate is what makes an unauthenticated write safe: without it anyone could bind
 * garbage to a victim's wallet pubkey and make their genuine report fail KEY_NOT_ENROLLED.
 * store.ts documents the gap this leaves (no proof the caller owns the wallet).
 */
async function handleEnroll(req: Request): Promise<Response> {
  const raw = await req.json().catch(() => null) as Record<string, unknown> | null;
  if (!raw) return json({ error: "body must be JSON" }, 400);
  const required = ["walletPubkey", "spkiB64", "chainPem", "certDigestB64"] as const;
  const missing = required.filter((name) => typeof raw[name] !== "string" || raw[name] === "");
  if (missing.length) return json({ error: "enroll: missing fields", missing }, 400);
  // A four-certificate Google chain is ~5KB of PEM; 64KB is generous headroom. The bound
  // goes BEFORE any parsing: without it a public unauthenticated route will hand attacker-
  // chosen megabytes to the DER walker (Task 7 review, minor 3).
  if ((raw.chainPem as string).length > 64_000) {
    return json({ error: "enroll: chainPem too large" }, 400);
  }

  try {
    // Order is load-bearing: chain gate -> identity tie -> write. The identity check MUST
    // precede enrollKey, or a valid-format wrong digest/spki is upserted first and only
    // refused afterwards - the polluted row would then sit under the victim's wallet.
    if (!(await verifyChainToPinnedRoot(raw.chainPem as string, ECDSA_VERIFY))) {
      return json({ error: "enroll: chain does not verify to the pinned attestation root" }, 400);
    }
    const leaf = await leafIdentityOf(raw.chainPem as string);
    if (leaf.spkiB64 !== raw.spkiB64) {
      return json({ error: "enroll: spkiB64 does not match the chain's leaf public key" }, 400);
    }
    if (leaf.digestB64 !== raw.certDigestB64) {
      return json({ error: "enroll: certDigestB64 is not sha256 of the leaf certificate" }, 400);
    }
    // The chain is already proven above; enrollKey re-checks it (idempotent) because its
    // contract is "never write without the gate", and keeping that promise in one place
    // beats saving three signature verifications.
    await enrollKey(db, {
      walletPubkey: raw.walletPubkey as string,
      spkiB64: raw.spkiB64 as string,
      chainPem: raw.chainPem as string,
      certDigestB64: raw.certDigestB64 as string,
    }, (chainPem) => verifyChainToPinnedRoot(chainPem, ECDSA_VERIFY));
    return json({ ok: true });
  } catch (e) {
    // 400, never a 500: this route is public, and a thrown error would be retryable noise
    // that tells the device nothing about what it got wrong.
    return json({ error: e instanceof Error ? e.message : String(e) }, 400);
  }
}

/**
 * The leaf-identity tie the Task 7 review asked for (minor 1): /enroll proves the CHAIN is
 * genuine, but the row stores spkiB64/certDigestB64 as the caller supplied them. If those
 * two described a different key than the chain's leaf, check 7 would compare the report's
 * leaf against a value the submitter picked — the enrollment row would vouch for a key the
 * chain never attested. Both values are recomputed from the verified chain here, and a
 * mismatch is refused before any write.
 */
async function leafIdentityOf(chainPem: string): Promise<{ spkiB64: string; digestB64: string }> {
  const [leafDer] = pemToDerBlocks(chainPem);
  if (!leafDer) throw new Error("enroll: chain has no leaf certificate");
  const info = await readAttestationExtension(chainPem);
  const digest = await crypto.subtle.digest("SHA-256", leafDer);
  const digestB64 = btoa(String.fromCharCode(...new Uint8Array(digest)));
  return { spkiB64: info.leafSpkiB64, digestB64 };
}

/** POST /nonce — public for the same reason. Issues the single-use window nonce. */
async function handleNonce(req: Request): Promise<Response> {
  const raw = await req.json().catch(() => null) as Record<string, unknown> | null;
  if (!raw) return json({ error: "body must be JSON" }, 400);
  const required = ["challengePubkey", "walletPubkey", "expiresAt"] as const;
  const missing = required.filter((name) => typeof raw[name] !== "string" || raw[name] === "");
  if (missing.length) return json({ error: "nonce: missing fields", missing }, 400);

  try {
    return json(await issueNonce(db, {
      challengePubkey: raw.challengePubkey as string,
      walletPubkey: raw.walletPubkey as string,
      expiresAt: raw.expiresAt as string,
    }));
  } catch (e) {
    return json({ error: e instanceof Error ? e.message : String(e) }, 400);
  }
}

interface SubmitReportBody {
  challengePubkey: string;
  walletPubkey: string;
  usedSeconds: number;
  tamperSignals?: string[];
  windowStart?: string;
  windowEnd?: string;
}

function validateReport(
  raw: unknown,
): { ok: true; body: SubmitReportBody } | { ok: false; error: string } {
  if (typeof raw !== "object" || raw === null) return { ok: false, error: "body must be a JSON object" };
  const b = raw as Record<string, unknown>;
  if (typeof b.challengePubkey !== "string" || !BASE58.test(b.challengePubkey)) {
    return { ok: false, error: "challengePubkey must be base58 (32-44 chars)" };
  }
  if (typeof b.walletPubkey !== "string" || !BASE58.test(b.walletPubkey)) {
    return { ok: false, error: "walletPubkey must be base58 (32-44 chars)" };
  }
  if (
    typeof b.usedSeconds !== "number" ||
    !Number.isInteger(b.usedSeconds) ||
    b.usedSeconds < 0 ||
    b.usedSeconds > MAX_USED_SECONDS
  ) {
    return { ok: false, error: "usedSeconds must be an integer 0..2592000" };
  }
  const tamperSignals = b.tamperSignals ?? [];
  if (
    !Array.isArray(tamperSignals) ||
    tamperSignals.some((s) => typeof s !== "string") ||
    tamperSignals.length > 16
  ) {
    return { ok: false, error: "tamperSignals must be a string array (max 16)" };
  }
  for (const w of [b.windowStart, b.windowEnd]) {
    if (w !== undefined && (typeof w !== "string" || Number.isNaN(Date.parse(w)))) {
      return { ok: false, error: "windowStart/windowEnd must be ISO timestamps" };
    }
  }
  return {
    ok: true,
    body: {
      challengePubkey: b.challengePubkey,
      walletPubkey: b.walletPubkey,
      usedSeconds: b.usedSeconds,
      tamperSignals: tamperSignals as string[],
      windowStart: b.windowStart as string | undefined,
      windowEnd: b.windowEnd as string | undefined,
    },
  };
}

async function handleSubmitReport(req: Request): Promise<Response> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "invalid JSON" }, 400);
  }
  const parsed = validateReport(raw);
  if (!parsed.ok) return json({ error: parsed.error }, 400);
  // validateReport returns a NEW object holding only the five fields it knows about, so
  // reading the evidence off `parsed.body` always yields undefined - which made every
  // submission, genuine or forged, come back EVIDENCE_MISSING. The evidence has to be
  // taken from the raw body; it is validated by the verifier, not by this shape check.
  const rawObj = raw as Record<string, unknown>;
  const b = {
    ...parsed.body,
    canonicalBytesB64: rawObj.canonicalBytesB64,
    signatureB64: rawObj.signatureB64,
    chainPem: rawObj.chainPem,
  };

  // A self-reported number is not evidence. Before this line existed, any caller could
  // POST a usedSeconds value and have it believed; the report is now settled only if a
  // hardware attestation backs the exact bytes it was computed from.
  const missing = (["canonicalBytesB64", "signatureB64", "chainPem"] as const)
    .filter((k) => typeof b[k] !== "string" || (b[k] as string).length === 0);
  if (missing.length > 0) {
    return json({
      error: "report rejected: attestation evidence required",
      missingFields: missing,
      reason: "EVIDENCE_MISSING",
    }, 400);
  }

  // The claims being checked have to come from the challenge we are settling, not from
  // whoever is submitting. A caller that could supply its own target package or window
  // could simply describe a duel it actually won.
  const { data: ch, error: chErr } = await db
    .from("challenges")
    .select("target_app, window_seconds, end_time")
    .eq("challenge_pubkey", b.challengePubkey)
    .maybeSingle();
  if (chErr) return json({ error: `challenge lookup failed: ${chErr.message}` }, 500);
  if (!ch || !ch.end_time) {
    return json({
      error: "unknown challenge - no window to verify against",
      reason: "CHALLENGE_UNKNOWN",
    }, 400);
  }

  const windowEndMs = Date.parse(ch.end_time as string);
  const windowStartMs = windowEndMs - Number(ch.window_seconds) * 1000;

  let canonicalBytes: Uint8Array;
  try {
    const bin = atob(b.canonicalBytesB64 as string);
    canonicalBytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) canonicalBytes[i] = bin.charCodeAt(i);
  } catch {
    return json({ error: "canonicalBytesB64 is not valid base64", reason: "PAYLOAD_UNPARSEABLE" }, 400);
  }

  const result = await verifyReport(
    {
      canonicalBytes,
      signatureB64: b.signatureB64 as string,
      chainPem: b.chainPem as string,
      submittedAtMs: Date.now(),
      windowEndMs,
      graceSeconds: 300,
      expectedTargetPackage: ch.target_app as string,
      expectedWindowStartMs: windowStartMs,
    },
    verifierDeps(),
  );

  if (!result.ok) {
    // Nothing is stored for a rejected report, so a forgery attempt cannot occupy the
    // one-report-per-challenge slot or influence settlement. The reason is returned to
    // the caller and logged; persisting it for the Evidence screen is Task 14's follow-up.
    console.log(`REJECT ${b.challengePubkey} ${result.reason} ${result.detail ?? ""}`.trim());
    return json({ error: "report rejected", reason: result.reason, detail: result.detail }, 400);
  }

  const { data, error } = await db
    .from("usage_reports")
    .insert({
      challenge_pubkey: result.fields.challengePubkey,
      // Everything below comes from `result.fields`, i.e. the values decoded from the
      // exact bytes the device signed and the hardware key attested. Storing the request
      // body here would let a report be verified against one number and settled on
      // another: the attestation would prove bytes the ledger never recorded.
      wallet_pubkey: result.fields.walletPubkey,
      used_seconds: result.fields.usedSeconds,
      tamper_signals: result.fields.tamperSignals,
      window_start: new Date(result.fields.windowStartMs).toISOString(),
      window_end: new Date(result.fields.windowEndMs).toISOString(),
      // The evidence itself, so GET /evidence/<hash> can publish what anyone needs to
      // re-run the fourteen checks offline — the whole "judge cannot dismiss it" claim
      // (spec 4.4). The migration's unique index on evidence_hash backs this column;
      // leaving it null, as the insert did before, made the route unimplementable.
      verified: true,
      evidence_hash: result.evidenceHash,
      signature_b64: b.signatureB64 as string,
      cert_chain_pem: b.chainPem as string,
      canonical_bytes: "\\x" + [...canonicalBytes].map((b) => b.toString(16).padStart(2, "0")).join(""),
      package_name: result.fields.packageName,
      device_active_seconds: result.fields.deviceActiveSeconds,
      event_count: result.fields.eventCount,
      event_set_digest: result.fields.eventSetDigestHex,
    })
    .select("id")
    .single();
  if (error) return json({ error: error.message }, 500);

  return json({
    ok: true,
    id: data.id,
    evidenceHash: result.evidenceHash,
    checksPassed: result.checks,
  });
}

interface RegisterChallengeBody {
  challengePubkey: string;
  creatorPubkey: string;
  opponentPubkey: string;
  targetApp: string;
  limitSeconds: number;
  windowSeconds: number;
  stakeAmount: number;
  endTime?: string;
}

async function handleRegisterChallenge(req: Request): Promise<Response> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "invalid JSON" }, 400);
  }
  if (typeof raw !== "object" || raw === null) return json({ error: "body must be a JSON object" }, 400);
  const b = raw as Record<string, unknown>;
  const str = (v: unknown, name: string) =>
    typeof v === "string" && v.length > 0 && v.length <= 200 ? null : `${name} must be a 1..200 char string`;
  const num = (v: unknown, name: string, max: number) =>
    typeof v === "number" && Number.isInteger(v) && v > 0 && v <= max ? null : `${name} must be an integer 1..${max}`;

  const errors = [
    typeof b.challengePubkey === "string" && BASE58.test(b.challengePubkey) ? null : "challengePubkey must be base58",
    typeof b.creatorPubkey === "string" && BASE58.test(b.creatorPubkey) ? null : "creatorPubkey must be base58",
    typeof b.opponentPubkey === "string" && BASE58.test(b.opponentPubkey) ? null : "opponentPubkey must be base58",
    str(b.targetApp, "targetApp"),
    num(b.limitSeconds, "limitSeconds", 604800),
    num(b.windowSeconds, "windowSeconds", 604800),
    num(b.stakeAmount, "stakeAmount", Number.MAX_SAFE_INTEGER),
  ].filter(Boolean);
  if (errors.length > 0) return json({ error: errors.join("; ") }, 400);

  const { error } = await db.from("challenges").upsert(
    {
      challenge_pubkey: b.challengePubkey as string,
      creator_pubkey: b.creatorPubkey as string,
      opponent_pubkey: b.opponentPubkey as string,
      target_app: b.targetApp as string,
      limit_seconds: b.limitSeconds as number,
      window_seconds: b.windowSeconds as number,
      stake_amount: b.stakeAmount as number,
      end_time: typeof b.endTime === "string" ? b.endTime : null,
      status: "ACTIVE",
    },
    { onConflict: "challenge_pubkey" },
  );
  if (error) return json({ error: error.message }, 500);
  return json({ ok: true });
}

function verdictOf(
  report: { used_seconds: number; tamper_signals: string[] | null } | undefined,
  limitSeconds: number,
): Verdict {
  if (!report) return "UNVERIFIED";
  return determineResult(
    { usedSeconds: report.used_seconds, tamperSignals: report.tamper_signals ?? [] },
    { limitSeconds },
  );
}

async function handleSettle(req: Request): Promise<Response> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "invalid JSON" }, 400);
  }
  const pk = (raw as Record<string, unknown> | null)?.challengePubkey;
  if (typeof pk !== "string" || !BASE58.test(pk)) {
    return json({ error: "challengePubkey must be base58 (32-44 chars)" }, 400);
  }

  const { data: challenge, error: chErr } = await db
    .from("challenges")
    .select("creator_pubkey, opponent_pubkey, limit_seconds, status")
    .eq("challenge_pubkey", pk)
    .maybeSingle();
  if (chErr) return json({ error: chErr.message }, 500);
  if (!challenge) return json({ error: "challenge not found - call register-challenge first" }, 404);
  if (challenge.status !== "ACTIVE") {
    return json({ error: `challenge is ${challenge.status}, expected ACTIVE` }, 409);
  }

  const { data: reports, error: rErr } = await db
    .from("usage_reports")
    .select("wallet_pubkey, used_seconds, tamper_signals, reported_at, evidence_hash")
    .eq("challenge_pubkey", pk)
    .order("reported_at", { ascending: false });
  if (rErr) return json({ error: rErr.message }, 500);

  const latest = (pubkey: string) =>
    (reports ?? []).find((r) => r.wallet_pubkey === pubkey);

  const creatorVerdict = verdictOf(latest(challenge.creator_pubkey), challenge.limit_seconds);
  const opponentVerdict = verdictOf(latest(challenge.opponent_pubkey), challenge.limit_seconds);
  const outcome = settleOutcome(creatorVerdict, opponentVerdict);

  const { error: upErr } = await db.from("settlement_results").upsert(
    {
      challenge_pubkey: pk,
      status: outcome.status,
      both_fail: outcome.bothFail,
      settled: outcome.settled,
      creator_pubkey: challenge.creator_pubkey,
      opponent_pubkey: challenge.opponent_pubkey,
      creator_result: creatorVerdict,
      opponent_result: opponentVerdict,
      creator_code: outcome.creatorCode,
      opponent_code: outcome.opponentCode,
      status_code: outcome.statusCode,
      // The evidence each side settled from, carried to the on-chain settle transaction
      // (program v2 refuses a PASS with a zero hash). The hex strings are the
      // usage_reports.evidence_hash of each side's latest VERIFIED report - the exact
      // bytes the hardware attested. A missing report yields null, which the client
      // refuses to sign over rather than zero-filling into a fake claim.
      creator_evidence_hash: latest(challenge.creator_pubkey)?.evidence_hash ?? null,
      opponent_evidence_hash: latest(challenge.opponent_pubkey)?.evidence_hash ?? null,
      settled_at: new Date().toISOString(),
    },
    { onConflict: "challenge_pubkey" },
  );
  if (upErr) return json({ error: upErr.message }, 500);

  const nextStatus = outcome.bothFail ? "BOTH_FAIL_LOCKED" : "SETTLED";
  const { error: stErr } = await db
    .from("challenges")
    .update({ status: nextStatus })
    .eq("challenge_pubkey", pk);
  if (stErr) return json({ error: stErr.message }, 500);

  return json({
    ok: true,
    challengePubkey: pk,
    status: outcome.status,
    bothFail: outcome.bothFail,
    settled: outcome.settled,
    creatorVerdict,
    opponentVerdict,
    creatorResult: outcome.creatorCode,
    opponentResult: outcome.opponentCode,
    settlementResult: outcome.statusCode,
    creatorEvidenceHash: latest(challenge.creator_pubkey)?.evidence_hash ?? null,
    opponentEvidenceHash: latest(challenge.opponent_pubkey)?.evidence_hash ?? null,
  });
}

function route(pathname: string): string {
  const p = pathname.replace(/^\/oracle/, "").replace(/\/+$/, "");
  return p === "" ? "/health" : p;
}

/**
 * GET /evidence/<hash> — the public publisher (plan Task 14, spec 4.4).
 *
 * The report is already public in the sense that matters: its bytes were verified by
 * hardware attestation and its hash is what settlement records. Publishing the evidence
 * bundle lets anyone — a judge, the losing duelist, a auditor — re-run the fourteen checks
 * offline and reach the same verdict, which is the difference between "the oracle says so"
 * and "anyone can check". Read-only, keyed by the hash of the exact signed bytes, and
 * returns nothing for a hash that was never accepted (404, not a guessable surface).
 */
async function handleEvidence(hash: string): Promise<Response> {
  if (!/^[0-9a-f]{64}$/.test(hash)) {
    return json({ error: "evidence hash must be 64 lowercase hex chars" }, 400);
  }
  const { data, error } = await db
    .from("usage_reports")
    .select(
      "challenge_pubkey, wallet_pubkey, used_seconds, tamper_signals, window_start, window_end, " +
        "reported_at, verified, evidence_hash, signature_b64, cert_chain_pem, canonical_bytes, " +
        "package_name, device_active_seconds, event_count, event_set_digest",
    )
    .eq("evidence_hash", hash)
    .eq("verified", true)
    .maybeSingle();
  if (error) return json({ error: error.message }, 500);
  if (!data) return json({ error: "no accepted report carries this evidence hash" }, 404);

  // PostgREST returns bytea as a "\x..." hex string; the bundle publishes base64, which is
  // what every other surface (the app, the Task 6 vector) uses for the same bytes.
  let canonicalBytesB64: string | null = null;
  const hex = typeof data.canonical_bytes === "string" ? data.canonical_bytes : "";
  if (hex.startsWith("\\x") || hex.startsWith("0x")) {
    const body = hex.slice(2);
    const bytes = new Uint8Array(body.length / 2);
    for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(body.slice(i * 2, i * 2 + 2), 16);
    canonicalBytesB64 = btoa(String.fromCharCode(...bytes));
  }

  return json({
    evidenceHash: data.evidence_hash,
    verified: data.verified,
    reportedAt: data.reported_at,
    challengePubkey: data.challenge_pubkey,
    walletPubkey: data.wallet_pubkey,
    packageName: data.package_name,
    usedSeconds: data.used_seconds,
    deviceActiveSeconds: data.device_active_seconds,
    eventCount: data.event_count,
    eventSetDigestHex: data.event_set_digest,
    tamperSignals: data.tamper_signals ?? [],
    windowStart: data.window_start,
    windowEnd: data.window_end,
    // The re-verification material: bytes + signature + chain. With these three anyone
    // can re-run every check from chain anchoring down to signature, without trusting us.
    canonicalBytesB64,
    signatureB64: data.signature_b64,
    chainPem: data.cert_chain_pem,
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS_HEADERS });
  const path = route(new URL(req.url).pathname);
  // Decided by auth.ts on this same `path`, so an exempt prefix can never dispatch a
  // protected handler: both the check and the router below read one identical string.
  const denied = authorizeRequest(path, req.headers.get("x-oracle-secret"), ORACLE_API_SECRET);
  if (denied) return json({ error: denied.error }, denied.status);

  try {
    if (req.method === "POST" && path === "/enroll") {
      return await handleEnroll(req);
    }
    if (req.method === "POST" && path === "/nonce") {
      return await handleNonce(req);
    }
    if (req.method === "GET" && path === "/health") {
      return json({ status: "alive", time: new Date().toISOString() });
    }
    if (req.method === "POST" && path === "/submit-report") {
      return await handleSubmitReport(req);
    }
    if (req.method === "POST" && path === "/register-challenge") {
      return await handleRegisterChallenge(req);
    }
    if (req.method === "POST" && path === "/settle") {
      return await handleSettle(req);
    }
    if (req.method === "GET" && path.startsWith("/evidence/")) {
      return await handleEvidence(path.slice("/evidence/".length));
    }
    return json({ error: `no route for ${req.method} ${path}` }, 404);
  } catch (e) {
    return json({ error: e instanceof Error ? e.message : "internal error" }, 500);
  }
});
