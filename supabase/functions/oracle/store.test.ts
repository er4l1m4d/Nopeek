import { test } from "node:test";
import assert from "node:assert/strict";
import {
  enrollKey,
  insertRejectedReport,
  insertVerifiedReport,
  InsertConflictError,
  issueNonce,
  latestVerifiedReports,
  verifiedEvidenceByHash,
  type StoreDb,
} from "./store.ts";

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

function makeFakeDb(seed: {
  installKeys?: Row[];
  usageReports?: Row[];
  /** Injected PostgREST failures, per operation. */
  failures?: { select?: string; upsert?: string; insert?: string };
} = {}) {
  const tables: Record<"install_keys" | "report_nonces" | "usage_reports", Array<Record<string, unknown>>> = {
    install_keys: [...(seed.installKeys ?? [])],
    report_nonces: [],
    usage_reports: [...(seed.usageReports ?? [])],
  };
  const installKeys = tables.install_keys;
  const nonces = tables.report_nonces;
  const reports = tables.usage_reports;
  const failures = seed.failures ?? {};

  // A chainable query builder over one table, supporting exactly the operations
  // store.ts uses. Rows are real state: `select().eq().maybeSingle()` reads them,
  // `upsert()` honours the on-conflict key, so the assertions below are about what got
  // written, not which methods were called.
  function from(name: "install_keys" | "report_nonces" | "usage_reports") {
    const rows = tables[name];
    // Closure insert state: `.insert(r).select("id").single()` is a RETURNING of the
    // inserted row (or the insert error), NOT a table-wide re-query — this is the
    // distinction the duplicate-submission test depends on.
    let insertData: Record<string, unknown>[] | null = null;
    let insertError: { message: string } | null = null;
    let filters: Array<[string, string | number | boolean]> = [];
    let orderSpec: { col: string; ascending: boolean } | null = null;
    let limitN: number | undefined = undefined;

    const applyFilters = () =>
      rows.filter((r) => filters.every(([c, v]) => r[c] === v));

    const readResult = () => {
      // RETURNING semantics first: an insert in this chain answers from its own result.
      if (insertData !== null || insertError !== null) {
        return { data: insertError ? null : (insertData ? insertData[0] : null), error: insertError };
      }
      if (failures.select) return { data: null, error: { message: failures.select } };
      let hit = applyFilters();
      if (orderSpec) {
        const { col, ascending } = orderSpec;
        hit = [...hit].sort((a, b) => {
          const av = String(a[col] ?? ""); const bv = String(b[col] ?? "");
          const cmp = av < bv ? -1 : av > bv ? 1 : 0;
          return ascending ? cmp : -cmp;
        });
      }
      if (typeof limitN === "number") hit = hit.slice(0, limitN);
      // single() and maybeSingle() both return one row (or null); the fake does not
      // model the multiple-rows error single() would raise on the real client.
      return { data: hit[0] ?? null, error: null };
    };

    const builder: any = {
      select() { filters = []; orderSpec = null; limitN = undefined; return builder; },
      eq(col: string, value: string | number | boolean) { filters.push([col, value]); return builder; },
      order(col: string, opts: { ascending?: boolean }) {
        orderSpec = { col, ascending: opts?.ascending !== false };
        return builder;
      },
      limit(n: number) { limitN = n; return builder; },
      async maybeSingle() { return readResult(); },
      async single() { return readResult(); },
      async upsert(value: Record<string, unknown>, opts: { onConflict: string }) {
        if (failures.upsert) return { data: null, error: { message: failures.upsert } };
        const at = rows.findIndex((r) => r[opts.onConflict] === value[opts.onConflict]);
        if (at >= 0) rows[at] = { ...rows[at], ...value };
        else rows.push(name === "install_keys" ? { revoked_at: null, ...value } : { consumed_at: null, ...value });
        return { data: [value], error: null };
      },
      insert(value: Record<string, unknown>) {
        // Chainable AND thenable: `.insert(r)` returns the builder; awaiting it (with or
        // without a chained .select().single()) resolves to the PostgREST result shape.
        insertData = null; insertError = null; filters = []; orderSpec = null; limitN = undefined;
        if (failures.insert) {
          insertError = { message: failures.insert };
          return builder;
        }
        if (name === "usage_reports" && (value as Record<string, unknown>).verified === true) {
          // usage_reports_one_verified_per_side (challenge, wallet). The fake enforces it
          // so the duplicate-accepted-submission path is testable without a real PG.
          const dup = rows.find((r) =>
            r.challenge_pubkey === value.challenge_pubkey &&
            r.wallet_pubkey === value.wallet_pubkey &&
            r.verified === true,
          );
          if (dup) {
            insertError = { message:
              `duplicate key value violates unique constraint "usage_reports_one_verified_per_side"` };
            return builder;
          }
          const dupHash = rows.find((r) =>
            r.evidence_hash !== undefined && r.evidence_hash === value.evidence_hash,
          );
          if (dupHash) {
            insertError = { message:
              `duplicate key value violates unique constraint "usage_reports_evidence_hash_unique"` };
            return builder;
          }
        }
        const stored = name === "install_keys" ? { revoked_at: null, ...value }
          : name === "report_nonces" ? { consumed_at: null, ...value }
          : { id: `row-${rows.length + 1}`, ...value };
        rows.push(stored);
        insertData = [stored];
        return builder;
      },
      // thenable: `await builder` resolves to {data, error}. An insert in the chain
      // answers from its own result; otherwise this is the filtered SELECT.
      then(resolve: (v: { data: Record<string, unknown>[] | null; error: { message: string } | null }) => void) {
        if (insertData !== null || insertError !== null) {
          resolve({ data: insertData, error: insertError });
          return builder;
        }
        let hit = applyFilters();
        if (orderSpec) {
          const { col, ascending } = orderSpec;
          hit = [...hit].sort((a, b) => {
            const av = String(a[col] ?? ""); const bv = String(b[col] ?? "");
            const cmp = av < bv ? -1 : av > bv ? 1 : 0;
            return ascending ? cmp : -cmp;
          });
        }
        if (typeof limitN === "number") hit = hit.slice(0, limitN);
        resolve({ data: hit, error: null });
        return builder;
      },
    };
    return builder;
  }

  const db = { from } as unknown as StoreDb;
  return { db, installKeys, nonces, reports };
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

test("enrollKey maps a PostgREST read failure to a named error, not a crash", async () => {
  // The Task 7 review's untested branch: a DB failure on the lookup previously had no
  // test, so a refactor could turn it into an unhandled rejection on a public route.
  const { db, installKeys } = makeFakeDb({ failures: { select: "permission denied for table install_keys" } });
  await assert.rejects(
    () => enrollKey(db, {
      walletPubkey: WALLET_A, spkiB64: SPKI, chainPem: CHAIN, certDigestB64: DIGEST,
    }, chainVerifies),
    /install_keys lookup failed/,
  );
  assert.equal(installKeys.length, 0, "a failed lookup must not fall through to a write");
});

test("enrollKey maps an upsert failure to a named error and stores nothing", async () => {
  const { db, installKeys } = makeFakeDb({ failures: { upsert: "new row violates row-level security policy" } });
  await assert.rejects(
    () => enrollKey(db, {
      walletPubkey: WALLET_A, spkiB64: SPKI, chainPem: CHAIN, certDigestB64: DIGEST,
    }, chainVerifies),
    /install_keys upsert failed/,
  );
  assert.equal(installKeys.length, 0);
});

test("issueNonce maps an insert failure to a named error and issues nothing", async () => {
  // The mirror branch on the nonce path: if report_nonces refuses the insert, the caller
  // must learn issuance failed (and retry) rather than receive an unusable nonce.
  const { db, nonces } = makeFakeDb({ failures: { insert: "violates foreign key constraint" } });
  await assert.rejects(
    () => issueNonce(db, { challengePubkey: CHALLENGE, walletPubkey: WALLET_A, expiresAt: futureExpiry() }),
    /report_nonces insert failed/,
  );
  assert.equal(nonces.length, 0);
});

// ---------------------------------------------------------------------------
// Task 14: the report-storage half (the plan's Step 1 tests, against the same
// state fake — a store.ts that writes the wrong column or skips the write must
// fail here, not only against the deployed function).
// ---------------------------------------------------------------------------

const BYTES = new Uint8Array([0, 1, 2, 255, 65, 66, 67, 128]);

function verifiedInput(overrides: Partial<Parameters<typeof insertVerifiedReport>[1]> = {}): Parameters<typeof insertVerifiedReport>[1] {
  return {
    challengePubkey: CHALLENGE,
    walletPubkey: WALLET_A,
    evidenceHash: "a".repeat(64),
    usedSeconds: 12,
    tamperSignals: [],
    windowStartMs: Date.parse("2026-10-17T10:00:00Z"),
    windowEndMs: Date.parse("2026-10-17T10:02:00Z"),
    signatureB64: "c2ln",
    chainPem: CHAIN,
    canonicalBytes: BYTES,
    packageName: "com.nopeek.app",
    deviceActiveSeconds: 100,
    eventCount: 4,
    eventSetDigestHex: "b".repeat(64),
    ...overrides,
  };
}

test("a verified submission stores the row with verified=true and its computed evidence_hash", async () => {
  const { db, reports } = makeFakeDb();

  const id = await insertVerifiedReport(db, verifiedInput());

  assert.equal(reports.length, 1, "exactly one row for one accepted submission");
  const row = reports[0];
  assert.equal(row.id, id, "the returned id is the stored row's");
  assert.equal(row.verified, true);
  assert.equal(row.evidence_hash, "a".repeat(64));
  assert.equal(row.challenge_pubkey, CHALLENGE);
  assert.equal(row.wallet_pubkey, WALLET_A);
  assert.equal(row.used_seconds, 12);
  // The bytes are stored byte-exact as a \x bytea literal — the whole
  // "publish what anyone can re-verify" claim rests on this being the same
  // bytes the verifier hashed, not a re-encoding.
  assert.equal(row.canonical_bytes, "\\x" + [...BYTES].map((b) => b.toString(16).padStart(2, "0")).join(""));
  assert.equal(row.signature_b64, "c2ln");
  assert.equal(row.cert_chain_pem, CHAIN);
  assert.equal(row.reject_reason, undefined, "an accepted row carries no rejection");
});

test("submitting the same canonical bytes twice does not insert twice", async () => {
  // Review Focus 2 (server half). The DB backs this twice over:
  // usage_reports_one_verified_per_side (challenge, wallet) and
  // usage_reports_evidence_hash_unique (evidence_hash). The fake enforces both so
  // the store maps the violation to InsertConflictError — which the handler
  // turns into a 409 verdict the client does NOT retry — instead of a raw 500.
  const { db, reports } = makeFakeDb();
  await insertVerifiedReport(db, verifiedInput());

  await assert.rejects(
    () => insertVerifiedReport(db, verifiedInput()),
    InsertConflictError,
    "the same side submitting twice must hit the one-verified-per-side constraint",
  );
  assert.equal(reports.length, 1, "no second row");

  // The OTHER guard: different wallet, same bytes (a replayed bundle) is still
  // refused by the evidence-hash index.
  await assert.rejects(
    () => insertVerifiedReport(db, verifiedInput({ walletPubkey: WALLET_B })),
    InsertConflictError,
  );
  assert.equal(reports.length, 1);
});

test("a rejected submission stores reject_reason and never sets verified=true", async () => {
  // The honesty slide's denominator: a refusal is data. The write must carry the
  // decoded identity (never the request body) and verified must stay false —
  // a rejected row that could be read as verified is the fund-loss bug class.
  const { db, reports } = makeFakeDb();
  await insertRejectedReport(db, {
    challengePubkey: CHALLENGE,
    walletPubkey: WALLET_A,
    usedSeconds: 999,
    tamperSignals: ["CLOCK_CHANGED"],
    rejectReason: "USAGE_EXCEEDS_ACTIVE",
    detail: "used 999s of a 120s window",
  });

  assert.equal(reports.length, 1);
  const row = reports[0];
  assert.equal(row.verified, false, "a rejected row is never verified");
  assert.equal(row.reject_reason, "USAGE_EXCEEDS_ACTIVE: used 999s of a 120s window");
  assert.equal(row.used_seconds, 999);
  assert.equal(row.evidence_hash, undefined, "a rejected row carries no evidence hash");
});

test("settle's report selection reads only VERIFIED rows, newest first", async () => {
  // The reviewer's fund-loss finding: the settle query selected ALL rows while its
  // comment claimed "latest VERIFIED report". Once rejections are stored, a late
  // rejected row (client-decoded numbers) must not shadow an earlier verified one.
  const { db } = makeFakeDb({
    usageReports: [
      // Older VERIFIED row for the creator (the honest report).
      { challenge_pubkey: CHALLENGE, wallet_pubkey: WALLET_A, used_seconds: 5,
        tamper_signals: [], reported_at: "2026-10-17T10:01:00Z",
        evidence_hash: "c".repeat(64), verified: true },
      // Newer REJECTED row for the same wallet (a forged retry that failed checks).
      { challenge_pubkey: CHALLENGE, wallet_pubkey: WALLET_A, used_seconds: 0,
        tamper_signals: [], reported_at: "2026-10-17T10:05:00Z",
        evidence_hash: null, verified: false, reject_reason: "BAD_SIGNATURE" },
      // A verified row for the opponent (newest overall).
      { challenge_pubkey: CHALLENGE, wallet_pubkey: WALLET_B, used_seconds: 7,
        tamper_signals: [], reported_at: "2026-10-17T10:06:00Z",
        evidence_hash: "d".repeat(64), verified: true },
      // A verified row for a DIFFERENT challenge must not leak in.
      { challenge_pubkey: "OTHER", wallet_pubkey: WALLET_A, used_seconds: 1,
        tamper_signals: [], reported_at: "2026-10-17T10:07:00Z",
        evidence_hash: "e".repeat(64), verified: true },
    ],
  });

  const rows = await latestVerifiedReports(db, CHALLENGE);

  assert.equal(rows.length, 2, "rejected rows and other challenges are excluded");
  assert.deepEqual(rows.map((r) => r.wallet_pubkey), [WALLET_B, WALLET_A],
    "newest verified first");
  assert.equal(rows[1].evidence_hash, "c".repeat(64),
    "the creator's verified evidence hash survives the later rejected retry");
});

test("evidence lookup returns the verified row for a stored hash and null otherwise", async () => {
  // GET /evidence/<hash>'s contract: the exact bytes whose hash was requested, and
  // a 404 for anything else. The verified filter here also keeps a rejection's
  // bytes from being served as accepted evidence.
  const stored = {
    challenge_pubkey: CHALLENGE, wallet_pubkey: WALLET_A, used_seconds: 12,
    tamper_signals: [], window_start: "2026-10-17T10:00:00Z", window_end: "2026-10-17T10:02:00Z",
    reported_at: "2026-10-17T10:02:30Z", verified: true, evidence_hash: "a".repeat(64),
    signature_b64: "c2ln", cert_chain_pem: CHAIN,
    canonical_bytes: "\\x" + [...BYTES].map((b) => b.toString(16).padStart(2, "0")).join(""),
    package_name: "com.nopeek.app", device_active_seconds: 100, event_count: 4,
    event_set_digest: "b".repeat(64),
  };
  const { db } = makeFakeDb({
    usageReports: [stored, { ...stored, evidence_hash: "f".repeat(64), verified: false }],
  });

  const hit = await verifiedEvidenceByHash(db, "a".repeat(64));
  assert.ok(hit, "the stored verified hash is found");
  assert.equal((hit as Row).verified, true);

  assert.equal(await verifiedEvidenceByHash(db, "0".repeat(64)), null, "unknown hash -> null (the handler's 404)");
  assert.equal(
    await verifiedEvidenceByHash(db, "f".repeat(64)),
    null,
    "a REJECTED row's hash must not be served as accepted evidence",
  );
});
