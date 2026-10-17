/**
 * The two writes the attestation protocol needs before any report can pass checks 6-14:
 * an enrolled hardware key per wallet, and a single-use nonce per (challenge, wallet).
 *
 * The Supabase client is injected rather than created here, for the same reason
 * `determine_result.ts` stays pure: these functions decide what lands in the tables the
 * verifier reads, so that has to be testable without a project ref, a key, or a network.
 * `StoreDb` is the slice of PostgREST actually used — nothing more — so the fake in
 * store.test.ts models real rows instead of choreographing calls.
 *
 * Shape validation is deliberately NOT duplicated here. `install_keys` and `report_nonces`
 * carry CHECK constraints (base58 wallet shape, SPKI 40-256 chars, digest 40-64, chain
 * containing "BEGIN CERTIFICATE", `expires_at > issued_at`), and those live in the one
 * place both this module and any future writer must obey. Duplicating them here would give
 * two definitions to drift, and the first one to be relaxed wins.
 */

import type { RejectReason } from "./checks.ts";

export interface StoreRow { [column: string]: unknown }

export interface StoreQuery
  extends PromiseLike<{ data: StoreRow[] | null; error: { message: string } | null }> {
  select(columns: string): StoreQuery;
  eq(column: string, value: string | number | boolean): StoreQuery;
  /** PostgREST ordering; only the shape the oracle uses is modelled. */
  order(column: string, options: { ascending?: boolean }): StoreQuery;
  limit(count: number): StoreQuery;
  maybeSingle(): Promise<{ data: StoreRow | null; error: { message: string } | null }>;
  /** PostgREST chains .select() on an insert result, then .single(). */
  single(): Promise<{ data: StoreRow | null; error: { message: string } | null }>;
  upsert(
    row: StoreRow, options: { onConflict: string },
  ): Promise<{ data: StoreRow[] | null; error: { message: string } | null }>;
  /**
   * PostgREST: `insert()` returns a builder that is BOTH chainable
   * (`.select().maybeSingle()/.single()`) AND thenable (`await insert(r)` resolves
   * to {data, error}). The fake below models both shapes.
   */
  insert(row: StoreRow): StoreQuery & Promise<{ data: StoreRow[] | null; error: { message: string } | null }>;
}

export interface StoreDb {
  from(table: "install_keys" | "report_nonces" | "usage_reports"): StoreQuery;
}

/** One accepted (verified) submission per side of a challenge. */
export interface VerifiedReport {
  challengePubkey: string;
  walletPubkey: string;
  evidenceHash: string;
  usedSeconds: number;
  tamperSignals: string[];
  windowStartMs: number;
  windowEndMs: number;
  signatureB64: string;
  chainPem: string;
  /** The exact bytes the device signed. Stored as bytea verbatim; never re-encoded. */
  canonicalBytes: Uint8Array;
  packageName: string;
  deviceActiveSeconds: number;
  eventCount: number;
  eventSetDigestHex: string;
}

/**
 * A failed verification to be written (verified = false) for the honesty slide.
 * Only attributable rejections are stored: the schema's wallet_pubkey is NOT NULL,
 * and an unparseable payload has no honest wallet to name — those stay log-only.
 */
export interface RejectedReport {
  challengePubkey: string;
  walletPubkey: string;
  usedSeconds: number;
  tamperSignals: string[];
  rejectReason: RejectReason;
  detail: string | null;
}

export interface EnrollInput {
  walletPubkey: string;
  /** DER SubjectPublicKeyInfo of the attested EC P-256 key, base64. */
  spkiB64: string;
  /** Full PEM chain as the device presented it. */
  chainPem: string;
  /** SHA-256 of the leaf certificate DER, base64. */
  certDigestB64: string;
}

export interface NonceInput {
  challengePubkey: string;
  walletPubkey: string;
  /** Window end + grace, as ISO. Supplied by the caller; the DB enforces it is future. */
  expiresAt: string;
}

export interface IssuedNonce { nonceB64: string; expiresAt: string }

/** Bytes per nonce. 32 is what spec 4.1 puts in the attestation challenge and the payload. */
const NONCE_BYTES = 32;

/** Node and Deno both provide btoa; Buffer does not exist in the edge runtime's browser globals. */
function base64Of(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

/**
 * Records (or refreshes) the hardware key enrolled for a wallet.
 *
 * `/enroll` is public and unauthenticated — the device cannot hold a secret — which makes
 * this an unauthenticated write that the verifier later trusts. `verifyChain` is therefore
 * required, not optional: without it anyone could bind junk to a victim's wallet pubkey and
 * their genuine report would then fail the enrolled-key check, i.e. a drive-by way to make
 * an honest user lose their stake. Verification here rejects that class of row.
 *
 * The gap this does NOT close: nothing proves the caller OWNS `walletPubkey`, so an attacker
 * with a real attested chain of their own could still enrol it under someone else's wallet.
 * Closing that needs a wallet signature on the enrolment body, which is a protocol change
 * (spec 4.1) rather than a line of code — recorded in the ledger, not silently assumed away.
 *
 * Upsert on the wallet primary key: one row per wallet, because checks.ts reads THE
 * enrolled SPKI for a wallet and compares it with the presented leaf key. Two rows would
 * mean the oracle judged a rotated key against an old one.
 *
 * A revoked key is refused rather than overwritten. install_keys.revoked_at is "set, never
 * deleted" in the migration precisely so a compromised device cannot re-bless itself by
 * submitting a fresh chain for the same wallet.
 *
 * Limitation worth naming: the read-then-upsert is not atomic, so a revoke landing between
 * the two would be overwritten by this call. Revocation is performed by an operator today,
 * not concurrently with enrollment, so this is accepted rather than ignored; making it
 * atomic needs an RPC or a guarded update, and that is a decision for whoever builds
 * revocation (nothing in this plan does).
 */
export async function enrollKey(
  db: StoreDb,
  input: EnrollInput,
  verifyChain: (chainPem: string) => Promise<boolean>,
): Promise<{ ok: true }> {
  if (!(await verifyChain(input.chainPem))) {
    throw new Error("enroll: chain does not verify to the pinned attestation root");
  }

  const { data, error } = await db
    .from("install_keys")
    .select("revoked_at")
    .eq("wallet_pubkey", input.walletPubkey)
    .maybeSingle();
  if (error) throw new Error(`enroll: install_keys lookup failed: ${error.message}`);

  if (data && data.revoked_at) {
    throw new Error(
      `enroll: wallet ${input.walletPubkey} is revoked and is not re-enrollable`,
    );
  }

  // revoked_at is deliberately absent: an upsert must never clear a revocation.
  const { error: upsertError } = await db.from("install_keys").upsert(
    {
      wallet_pubkey: input.walletPubkey,
      keystore_spki_b64: input.spkiB64,
      cert_chain_pem: input.chainPem,
      cert_digest_b64: input.certDigestB64,
    },
    { onConflict: "wallet_pubkey" },
  );
  if (upsertError) throw new Error(`enroll: install_keys upsert failed: ${upsertError.message}`);

  return { ok: true };
}

/**
 * Issues the single-use nonce a device embeds as its attestation challenge.
 *
 * One row per (challenge, wallet): a duel is two wallets on ONE challenge, so issuing
 * wallet B's nonce must leave wallet A's untouched and unconsumed — the second honest
 * report in every duel depends on it.
 *
 * `random` is injectable so a test can pin the byte length without a CSPRNG, but the
 * default is the platform one and the length is asserted after the call: a short or
 * predictable nonce makes a captured report replayable onto another challenge.
 */
export async function issueNonce(
  db: StoreDb,
  input: NonceInput,
  random: (length: number) => Uint8Array = (length) => {
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return bytes;
  },
): Promise<IssuedNonce> {
  const expiresAtMs = Date.parse(input.expiresAt);
  if (!Number.isFinite(expiresAtMs)) {
    throw new Error(`nonce: expiresAt is not an ISO timestamp: "${input.expiresAt}"`);
  }
  // report_nonces has `check (expires_at > issued_at)`. Refusing here names the caller's
  // own mistake (a stale window, or a clock that disagrees with the DB's) instead of
  // surfacing it as an opaque constraint violation.
  if (expiresAtMs <= Date.now()) {
    throw new Error(`nonce: expiresAt must be in the future, got ${input.expiresAt}`);
  }

  const bytes = random(NONCE_BYTES);
  if (bytes.length !== NONCE_BYTES) {
    throw new Error(`nonce: generator returned ${bytes.length} bytes, expected ${NONCE_BYTES}`);
  }
  const nonceB64 = base64Of(bytes);

  const { error } = await db.from("report_nonces").insert({
    challenge_pubkey: input.challengePubkey,
    wallet_pubkey: input.walletPubkey,
    nonce_b64: nonceB64,
    expires_at: input.expiresAt,
    // consumed_at is left to its default: issued, not used.
  });
  if (error) throw new Error(`nonce: report_nonces insert failed: ${error.message}`);

  return { nonceB64, expiresAt: input.expiresAt };
}

/**
 * Accepts a verified evidence bundle into the store.
 *
 * The canonical bytes come from the in-memory `Uint8Array` that the verifier
 * hashed — they are re-encoded as a `\x`-prefixed hex literal so PostgREST writes
 * the exact same bytes it verified against, byte for byte (see the ledger note
 * about a verification that then fails LATER burning the nonce). Returns the row
 * id on success, or throws a named error on a duplicate (the 23505 unique
 * violation on `usage_reports_one_verified_per_side`).
 */
export async function insertVerifiedReport(db: StoreDb, report: VerifiedReport): Promise<string> {
  const canonicalHex =
    "\\x" + [...report.canonicalBytes].map((b) => b.toString(16).padStart(2, "0")).join("");
  const { data, error } = await db
    .from("usage_reports")
    .insert({
      challenge_pubkey: report.challengePubkey,
      wallet_pubkey: report.walletPubkey,
      used_seconds: report.usedSeconds,
      tamper_signals: report.tamperSignals,
      window_start: new Date(report.windowStartMs).toISOString(),
      window_end: new Date(report.windowEndMs).toISOString(),
      verified: true,
      evidence_hash: report.evidenceHash,
      signature_b64: report.signatureB64,
      cert_chain_pem: report.chainPem,
      canonical_bytes: canonicalHex,
      package_name: report.packageName,
      device_active_seconds: report.deviceActiveSeconds,
      event_count: report.eventCount,
      event_set_digest: report.eventSetDigestHex,
    })
    .select("id")
    .single();
  if (error) {
    // Distinguish the ONE error class that maps to a 409 verdict (a duplicate
    // accepted report — Task 13's two-chances design double-firing across
    // processes) from everything else, which stays a retryable 500. The real
    // PostgrestError carries code "23505"; the fake (and older clients) only
    // carry the message, so both signals are checked.
    const dup =
      (error as { code?: string }).code === "23505" ||
      /duplicate key value/i.test(error.message) ||
      /usage_reports_one_verified_per_side|usage_reports_evidence_hash_unique/.test(error.message);
    if (dup) throw new InsertConflictError(error.message);
    throw new Error(`report: verified insert failed: ${error.message}`);
  }
  if (!data) throw new Error("report: verified insert returned no row");
  return data.id as string;
}

/**
 * Records a rejected submission as verified = false + reject_reason.
 *
 * The row is written so the deck's honesty slide has denominator data and so the
 * evidence trail can show "this report was refused, not just absent". The reason
 * carries the verifier's detail inline (the schema has one text column for both).
 * Callers must only invoke this when the payload decoded far enough to attribute
 * the report (fields exist); earlier failures have no honest wallet to name.
 * A storage failure here must never mask the rejection verdict itself.
 */
export async function insertRejectedReport(db: StoreDb, report: RejectedReport): Promise<void> {
  const { error } = await db
    .from("usage_reports")
    .insert({
      challenge_pubkey: report.challengePubkey,
      wallet_pubkey: report.walletPubkey,
      used_seconds: report.usedSeconds,
      tamper_signals: report.tamperSignals,
      verified: false,
      reject_reason: report.detail
        ? `${report.rejectReason}: ${report.detail}`
        : report.rejectReason,
    })
    .select("id")
    .maybeSingle();
  if (error) throw new Error(`report: usage_reports reject insert failed: ${error.message}`);
}

/**
 * Distinguishes the one DB error class that maps to a retryable 409 from all
 * others (which stay 500). The class name is what callers branch on.
 */
export class InsertConflictError extends Error {
  constructor(message: string) { super(message); this.name = "InsertConflictError"; }
  static is(e: unknown, message?: string): boolean {
    return e instanceof Error && e.name === "InsertConflictError" &&
      (message === undefined || e.message.includes(message));
  }
}

/** The columns /settle needs from each side's latest VERIFIED report. */
export interface SettleReportRow {
  wallet_pubkey: string;
  used_seconds: number;
  tamper_signals: string[] | null;
  reported_at: string;
  evidence_hash: string | null;
}

/**
 * The latest VERIFIED report per wallet for a challenge, newest first.
 *
 * The `verified = true` filter is the fund-loss guard: a REJECTED row carries
 * client-decoded usage numbers and must never be read as a verdict. (The comment
 * in index.ts used to claim "latest VERIFIED report" while the query selected all
 * rows — the filter now lives here where a test can pin it.)
 */
export async function latestVerifiedReports(
  db: StoreDb,
  challengePubkey: string,
): Promise<SettleReportRow[]> {
  const { data, error } = await db
    .from("usage_reports")
    .select("wallet_pubkey, used_seconds, tamper_signals, reported_at, evidence_hash")
    .eq("challenge_pubkey", challengePubkey)
    .eq("verified", true)
    .order("reported_at", { ascending: false });
  if (error) throw new Error(`settle: usage_reports lookup failed: ${error.message}`);
  return (data ?? []) as unknown as SettleReportRow[];
}

/**
 * The verified evidence bundle for a hash, or null when no accepted report
 * carries it. The `verified = true` filter keeps the public route from serving
 * a rejection's bytes as if they were accepted evidence.
 */
export async function verifiedEvidenceByHash(
  db: StoreDb,
  evidenceHash: string,
): Promise<StoreRow | null> {
  const { data, error } = await db
    .from("usage_reports")
    .select(
      "challenge_pubkey, wallet_pubkey, used_seconds, tamper_signals, window_start, window_end, " +
        "reported_at, verified, evidence_hash, signature_b64, cert_chain_pem, canonical_bytes, " +
        "package_name, device_active_seconds, event_count, event_set_digest",
    )
    .eq("evidence_hash", evidenceHash)
    .eq("verified", true)
    .maybeSingle();
  if (error) throw new Error(`evidence: usage_reports lookup failed: ${error.message}`);
  return data;
}
