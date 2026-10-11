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

export interface StoreRow { [column: string]: unknown }

export interface StoreQuery {
  select(columns: string): StoreQuery;
  eq(column: string, value: string): StoreQuery;
  maybeSingle(): Promise<{ data: StoreRow | null; error: { message: string } | null }>;
  upsert(
    row: StoreRow, options: { onConflict: string },
  ): Promise<{ data: StoreRow[] | null; error: { message: string } | null }>;
  insert(row: StoreRow): Promise<{ data: StoreRow[] | null; error: { message: string } | null }>;
}

export interface StoreDb {
  from(table: "install_keys" | "report_nonces"): StoreQuery;
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
