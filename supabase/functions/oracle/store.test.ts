import { test } from "node:test";
import assert from "node:assert/strict";
import { enrollKey, issueNonce, type StoreDb } from "./store.ts";

/**
 * Task 7: enrollment and nonce issuance, the two writes the attestation protocol needs
 * before any report can pass checks 6-14.
 *
 * The fake below models the two tables as STATE — rows, primary key, the revoked flag —
 * rather than recording which methods were called. Asserting on a call log would let a
 * store.ts that writes the wrong columns, or writes nothing at all, still pass. Column
 * names are taken from supabase/migrations/20261007_nopeek_full_schema.sql, which is what
 * actually exists in the live project — not from the spec's earlier draft of them.
 *
 * Two constraints live in Postgres and cannot be modelled honestly here: the FK from
 * report_nonces.challenge_pubkey to challenges, and the base58/length CHECKs. Those are
 * verified against the deployed function instead (Task 7 Step 6), not claimed by these
 * tests.
 */
/** A row is a record of column names to values; the shapes are the migration's, quoted in the assertions. */
type Row = Record<string, unknown>;

function makeFakeDb(seed: { installKeys?: Row[] } = {}) {
  const tables: Record<"install_keys" | "report_nonces", Array<Record<string, unknown>>> = {
    install_keys: [...(seed.installKeys ?? [])],
    report_nonces: [],
  };
  const installKeys = tables.install_keys;
  const nonces = tables.report_nonces;

  // A chainable builder over one table, supporting exactly the operations store.ts uses.
  // Rows are real state: `select().eq().maybeSingle()` reads them, `upsert()` honours the
  // on-conflict key, so the assertions below are about what got written, not about which
  // methods were called.
  function from(name: "install_keys" | "report_nonces") {
    const rows = tables[name];
    return {
      filters: [] as Array<[string, string]>,
      select(_cols: string) { this.filters = []; return this; },
      eq(col: string, value: string) { this.filters.push([col, value]); return this; },
      async maybeSingle() {
        const hit = rows.find((r) => this.filters.every(([c, v]) => r[c] === v)) ?? null;
        return { data: hit, error: null };
      },
      async upsert(value: Record<string, unknown>, opts: { onConflict: string }) {
        const at = rows.findIndex((r) => r[opts.onConflict] === value[opts.onConflict]);
        if (at >= 0) rows[at] = { ...rows[at], ...value };
        else rows.push({ revoked_at: null, ...value });
        return { data: [value], error: null };
      },
      async insert(value: Record<string, unknown>) {
        rows.push({ consumed_at: null, ...value });
        return { data: [value], error: null };
      },
    };
  }

  const db = { from } as unknown as StoreDb;
  return { db, installKeys, nonces };
}

const WALLET_A = "6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj";
const WALLET_B = "DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM";
const CHALLENGE = "4HRLkRb8ZoVMPUuh5NQfGeMLRrPVGW2cQKfQqUQEQcDV";
const SPKI = "MFkwEwYHKoQivzICuQIDAQABAoGLZeMbOG7E7RfcmWu3dS+is7tfGoK7jE0dpBPg7Hw6K";
const CHAIN = "-----BEGIN CERTIFICATE-----\nMIIBtTCCARsCQABC\n-----END CERTIFICATE-----\n";
const DIGEST = "V03kcUk9V3oqTVu6IuiHFoFgf/eLmaehbyoc0zyDqIY=";

const futureExpiry = () => new Date(Date.now() + 120_000).toISOString();

/** The chain gate is required by enrollKey; these are the two outcomes it can have. */
const chainVerifies = async () => true;
const chainFails = async () => false;

test("issueNonce stores an unexpired, unconsumed nonce bound to (challenge, wallet)", async () => {
  const { db, nonces } = makeFakeDb();
  const expiresAt = futureExpiry();

  const result = await issueNonce(db, { challengePubkey: CHALLENGE, walletPubkey: WALLET_A, expiresAt });

  assert.equal(nonces.length, 1);
  const row = nonces[0];
  assert.equal(row.challenge_pubkey, CHALLENGE);
  assert.equal(row.wallet_pubkey, WALLET_A);
  assert.equal(row.expires_at, expiresAt, "the caller's window end + grace is what the DB enforces against");
  assert.equal(row.consumed_at, null, "issued, not used");

  // 32 random bytes, base64: the payload field the device embeds as the attestation
  // challenge. Short or non-random here and a captured report becomes replayable.
  assert.equal(result.nonceB64, row.nonce_b64);
  assert.equal(Buffer.from(row.nonce_b64 as string, "base64").length, 32);
});

test("issueNonce for wallet B does not invalidate wallet A's nonce", async () => {
  // Review Focus 1: a duel is two wallets on ONE challenge. If issuing B's nonce
  // overwrote or consumed A's, the second honest report in every duel would fail
  // NONCE_MISMATCH and the duelist would lose their stake for reporting truthfully.
  const { db, nonces } = makeFakeDb();
  const first = await issueNonce(db, { challengePubkey: CHALLENGE, walletPubkey: WALLET_A, expiresAt: futureExpiry() });
  const second = await issueNonce(db, { challengePubkey: CHALLENGE, walletPubkey: WALLET_B, expiresAt: futureExpiry() });

  assert.equal(nonces.length, 2);
  const a = nonces.find((r) => r.wallet_pubkey === WALLET_A)!;
  assert.equal(a.nonce_b64, first.nonceB64, "wallet A's nonce must survive wallet B's enrolment unchanged");
  assert.equal(a.consumed_at, null);
  assert.notEqual(first.nonceB64, second.nonceB64, "two issuances must not produce the same nonce");
});

test("issueNonce refuses an expiry that is not in the future", async () => {
  // report_nonces has `check (expires_at > issued_at)`, so a stale expiry is a rejected
  // insert that would surface as an opaque DB error. Refusing it by name tells the app
  // its clock or its window maths is wrong, which is the actionable answer.
  const { db, nonces } = makeFakeDb();
  await assert.rejects(
    () => issueNonce(db, {
      challengePubkey: CHALLENGE, walletPubkey: WALLET_A,
      expiresAt: new Date(Date.now() - 1_000).toISOString(),
    }),
    /expires/,
  );
  assert.equal(nonces.length, 0, "a refused nonce must not be written");
});

test("enrollKey stores the chain and leaf key against the wallet", async () => {
  const { db, installKeys } = makeFakeDb();
  await enrollKey(db, {
    walletPubkey: WALLET_A, spkiB64: SPKI, chainPem: CHAIN, certDigestB64: DIGEST,
  }, chainVerifies);

  assert.equal(installKeys.length, 1);
  assert.equal(installKeys[0].wallet_pubkey, WALLET_A);
  assert.equal(installKeys[0].keystore_spki_b64, SPKI);
  assert.equal(installKeys[0].cert_chain_pem, CHAIN);
  assert.equal(installKeys[0].cert_digest_b64, DIGEST);
  assert.equal(installKeys[0].revoked_at, null);
});

test("enrollKey upserts on the wallet pubkey rather than stacking rows", async () => {
  // The lookup that matters is one row per wallet: checks.ts reads the enrolled SPKI for
  // a wallet and compares it to the presented leaf key. Two rows means the oracle picks
  // arbitrarily, and a device that rotated its key could be judged against the old one.
  const { db, installKeys } = makeFakeDb();
  const input = { walletPubkey: WALLET_A, spkiB64: SPKI, chainPem: CHAIN, certDigestB64: DIGEST };
  await enrollKey(db, input, chainVerifies);
  await enrollKey(db, { ...input, spkiB64: `${SPKI}ROTATED` }, chainVerifies);

  assert.equal(installKeys.length, 1);
  assert.equal(installKeys[0].keystore_spki_b64, `${SPKI}ROTATED`);
});

test("enrollKey refuses to re-enroll a revoked key", async () => {
  // install_keys.revoked_at is "set, never deleted" in the migration precisely so a
  // revoked device cannot silently become re-enrollable under the same wallet. Overwrite
  // here would let a compromised key be re-blessed by submitting a fresh chain.
  const { db, installKeys } = makeFakeDb({
    installKeys: [{
      wallet_pubkey: WALLET_A,
      keystore_spki_b64: SPKI,
      cert_chain_pem: CHAIN,
      cert_digest_b64: DIGEST,
      revoked_at: "2026-10-01T00:00:00Z",
    }],
  });
  const before = { ...installKeys[0] };

  await assert.rejects(
    () => enrollKey(db, {
      walletPubkey: WALLET_A, spkiB64: `${SPKI}NEW`, chainPem: CHAIN, certDigestB64: DIGEST,
    }, chainVerifies),
    /revok/i,
  );
  assert.deepEqual(installKeys[0], before, "a refused enroll must leave the revoked row exactly as it was");
});

test("enrollKey refuses a chain that does not verify to the pinned root", async () => {
  // /enroll is public and unauthenticated, and what it writes is what check 7 later
  // trusts. Without this gate anyone could post garbage against a victim's wallet pubkey
  // and make their genuine report fail KEY_NOT_ENROLLED - a drive-by way to take an
  // honest user's stake. Asserted on state, not on the verifier being called.
  const { db, installKeys } = makeFakeDb();
  await assert.rejects(
    () => enrollKey(db, {
      walletPubkey: WALLET_A, spkiB64: SPKI, chainPem: CHAIN, certDigestB64: DIGEST,
    }, chainFails),
    /does not verify/,
  );
  assert.equal(installKeys.length, 0, "an unverified chain must not be recorded");
});
