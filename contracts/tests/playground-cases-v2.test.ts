// Task 10 Step 4 — v2 manual cases 17–24, executable in the Solana Playground
// test sandbox (tests/anchor.test.ts + `test` command).
//
// The Playground UI this session runs has no Call tab, so the "Call tab cases"
// instruction from manual-test-cases.md is executed through the sandbox instead.
// Written against the MEASURED sandbox constraints (ATTESTATION_DEPLOY_NOTES.md
// "Playground test sandbox"): the runner exposes pg/web3/anchor/BN/assert/Buffer
// only, no token library (SPL instructions hand-laid-out below), no dynamic
// import, pg.connection + pg.wallet only, describe() required.
//
// One wallet plays three roles, because contracts/program/src/lib.rs pins
// ORACLE_AUTHORITY and TREASURY to it: creator, oracle, treasury. Opponents are
// keypairs — and each case that needs the opponent to SIGN (join) uses a fresh
// generated keypair that the script airdrops, while pair-stable cases (21–23)
// use a deterministic keypair that never signs (create stores it; join never
// runs on those challenges).
//
// Time-gated design for cases 21–23 (LOCK_SECONDS = 3600 is compiled in):
// the lock challenges are seeded with nonce = unix-hour bucket, so the current
// bucket's challenge exercises the TOO-EARLY branches today, and a RE-RUN more
// than one hour later finds the previous bucket's challenge matured and
// exercises case 23's happy path. First run reports case 23 as SKIP-postpone,
// never as a false PASS.

// Identifiers below are injected by the Playground test sandbox (measured list, see
// ATTESTATION_DEPLOY_NOTES.md "Playground test sandbox"); declared here so the
// local IDE silences flags on them, per the trust-the-Gradle-analog convention.
declare const pg: any;
declare const web3: any;
declare const anchor: any;
declare const BN: any;
declare const assert: any;
declare const describe: any;
declare const it: any;
declare const before: any;
declare const after: any;

const TP = new web3.PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
const ATA = new web3.PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
const SYS = web3.SystemProgram.programId;
const RENT = new web3.PublicKey("SysvarRent111111111111111111111111111111111");
const PROGRAM = pg.program.programId;
const WALLET = pg.wallet.publicKey;
const TREASURY = new web3.PublicKey("6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj");
const MINT_SPACE = 82;
const TOK_ACC_SPACE = 165;

const STAKE = new BN(5_000_000);          // 5 units at 6 decimals
const FUND = new BN(50_000_000);
const TARGET = "com.instagram.android.PAD_TO_32B"; // 32 bytes: the v2 raw-offset layout assumes a fixed-width string
const HASH = Buffer.from("0c88710472984325013f15b1ba5a1f926b6a8d299a97888d6e2f1fff8e4d2d9a", "hex");
const ZERO32 = Buffer.alloc(32);

const PASS = 1, FAIL_R = 2, UNVERIFIED = 3;
const S_CREATED = 0, S_ACTIVE = 1, S_SETTLED = 2, S_LOCKED = 4, S_LRESOLVED = 5;
const R_CREATOR_WON = 1, R_BOTH_FAIL = 4;

let passCount = 0, failCount = 0, skipCount = 0;
function ok(name: string, detail: string) {
  passCount++; console.log("PASS " + name + " :: " + detail);
}
function bad(name: string, detail: string) {
  failCount++; console.log("FAIL " + name + " :: " + detail);
}
function skip(name: string, why: string) {
  skipCount++; console.log("SKIP " + name + " :: " + why);
}

// --- address helpers ---------------------------------------------------------
async function challengePda(creator: any, opponent: any, nonce: number) {
  const found = await web3.PublicKey.findProgramAddress(
    [Buffer.from("challenge"), creator.toBuffer(), opponent.toBuffer(),
      new BN(nonce).toArrayLike(Buffer, "le", 4)], PROGRAM);
  return { address: found[0], bump: found[1] };
}
async function reputationPda(wallet: any) {
  const found = await web3.PublicKey.findProgramAddress(
    [Buffer.from("reputation"), wallet.toBuffer()], PROGRAM);
  return found[0];
}
async function ataOf(owner: any, mintKey: any) {
  const found = await web3.PublicKey.findProgramAddress(
    [owner.toBuffer(), TP.toBuffer(), mintKey.toBuffer()], ATA);
  return found[0];
}

// --- hand-rolled SPL instructions (no token library in this sandbox) ---------
function u64Into(buf: any, at: number, value: any) {
  // Explicit Buffer class: the three-arg form is the canonical BN call. The two-
  // arg form resolves the class off the ambient scope, which the sandbox guard
  // context has broken before ("is not a constructor"), and silently mis-
  // serializing a token amount is worse than throwing.
  const le = value.toArrayLike(Buffer, "le", 8);
  for (let i = 0; i < 8; i++) buf[at + i] = le[i];
}
function ixInitializeMint(mintKey: any, authority: any) {
  const d = Buffer.alloc(67);
  d[0] = 0; d[1] = 6;
  authority.toBuffer().copy(d, 2);
  d[34] = 1;
  authority.toBuffer().copy(d, 35);
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: mintKey, isSigner: false, isWritable: true },
      { pubkey: RENT, isSigner: false, isWritable: false },
    ],
    programId: TP, data: d,
  });
}
function ixCreateAta(funder: any, owner: any, mintKey: any, vault: any) {
  // Account order is fixed by the spl-associated-token-account interface
  // (instruction.rs build_associated_token_account_instruction): the program
  // reads the TOKEN program at index 5 and uses it for the seed derivation, so
  // putting it at index 4 (as an earlier draft did) makes the derived address
  // mismatch -> "Associated address does not match seed derivation".
  // 0 funder(s,w) 1 ata(w) 2 wallet 3 mint 4 SYSTEM 5 TOKEN. No rent account.
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: funder, isSigner: true, isWritable: true },
      { pubkey: vault, isSigner: false, isWritable: true },
      { pubkey: owner, isSigner: false, isWritable: false },
      { pubkey: mintKey, isSigner: false, isWritable: false },
      { pubkey: SYS, isSigner: false, isWritable: false },
      { pubkey: TP, isSigner: false, isWritable: false },
    ],
    programId: ATA, data: Buffer.from([0]),
  });
}
function ixMintTo(to: any, amount: any, mintKey: any, authority: any) {
  const d = Buffer.alloc(9);
  d[0] = 7; u64Into(d, 1, amount);
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: mintKey, isSigner: false, isWritable: true },
      { pubkey: to, isSigner: false, isWritable: true },
      { pubkey: authority, isSigner: true, isWritable: false },
    ],
    programId: TP, data: d,
  });
}

// --- tx plumbing -------------------------------------------------------------
let markSeq = 0;
function mark(step: string) { console.log("MARK " + (++markSeq) + " " + step); }
async function sendTx(tx: any, extraSigners: any[]) {
  const { blockhash } = await pg.connection.getLatestBlockhash("confirmed");
  tx.recentBlockhash = blockhash;
  tx.feePayer = WALLET;
  const signers = [pg.wallet.keypair].concat(extraSigners).filter((s: any) => !!s);
  const sig = await pg.connection.sendTransaction(tx, signers, {
    skipPreflight: false,
  });
  const st = await pg.connection.confirmTransaction(sig, "confirmed");
  if (st.value && st.value.err) throw new Error("TX_ERR " + JSON.stringify(st.value.err));
  return sig;
}
async function createTestMint() {
  mark("mint: keypair generate");
  const mintKp = web3.Keypair.generate();
  mark("mint: rent exemption");
  const rentMint = await pg.connection.getMinimumBalanceForRentExemption(MINT_SPACE);
  mark("mint: new Transaction");
  const t1 = new web3.Transaction();
  mark("mint: createAccount ix");
  t1.add(web3.SystemProgram.createAccount({
    fromPubkey: WALLET, newAccountPubkey: mintKp.publicKey,
    space: MINT_SPACE, lamports: rentMint, programId: TP,
  }));
  mark("mint: initializeMint ix");
  t1.add(ixInitializeMint(mintKp.publicKey, WALLET));
  mark("mint: sendTx");
  await sendTx(t1, [mintKp]);
  mark("mint: done");
  return mintKp.publicKey;
}
async function bal(address: any): Promise<number> {
  mark("bal: getTokenAccountBalance");
  const r = await pg.connection.getTokenAccountBalance(address);
  return Number(r.value.uiAmountString);
}
async function lamports(address: any): Promise<number> {
  return await pg.connection.getBalance(address);
}

// Anchor program-derived errors come back as `failed with simulated
// transaction error: Custom { code: 6012, error: "EvidenceZero" }` (or with the
// error string only inside the log tail). Read whichever is present.
function errName(e: any): string {
  const s = String(e && e.message ? e.message : e);
  const m = s.match(/Custom \{ code: (\d+)[^}]*\}/) || s.match(/AnchorError.*code:\s*(\d+)/);
  if (m) {
    try {
      const idlErr = pg.program.idl.errors.find((x: any) => x.code === Number(m[1]));
      if (idlErr) return idlErr.name;
    } catch (_e) { /* fall through */ }
  }
  const n = s.match(/error: "(\w+)"/) || s.match(/"\w*::(\w+)"/);
  return n ? n[1] : s.slice(0, 120);
}
async function expectError(caseName: string, expected: string, fn: any): Promise<void> {
  try {
    await fn();
    bad(caseName, "expected " + expected + ", but transaction SUCCEEDED");
  } catch (e) {
    const got = errName(e);
    if (got === expected) ok(caseName, "rejected with " + expected);
    else bad(caseName, "expected " + expected + ", got " + got);
  }
}

// Create a challenge and join it, so settle cases have an Active duel.
// The opponent is a freshly generated, airdropped keypair so it can sign join.
async function fundedOpponent(small: boolean) {
  const opp = web3.Keypair.generate();
  if (small) {
    try {
      await pg.connection.confirmTransaction(
        await pg.connection.requestAirdrop(opp.publicKey, 20_000_000), "confirmed");
    } catch (e) {
      console.log("NOTE airdrop to opponent failed: " + errName(e));
    }
  }
  return opp;
}
async function activeChallenge(mintKey: any, opp: any, nonce: number, opts: any) {
  const o = opts || {};
  const pda = await challengePda(WALLET, opp.publicKey, nonce);
  const aAta = await ataOf(WALLET, mintKey);
  const oAta = await ataOf(opp.publicKey, mintKey);
  const escrow = await ataOf(pda.address, mintKey);
  const cB0 = await bal(aAta);
  await pg.program.methods.createChallenge(
    o.stake || STAKE, o.target || TARGET, o.limit || 1800, o.winSeconds || 3600, nonce)
    .accounts({ challenge: pda.address, opponent: opp.publicKey, mint: mintKey,
      creatorToken: aAta, escrow: escrow })
    .rpc();
  if (o.join !== false) {
    const tx = new web3.Transaction();
    tx.add(ixCreateAta(WALLET, opp.publicKey, mintKey, oAta));
    tx.add(ixMintTo(oAta, FUND, mintKey, WALLET));
    tx.add(pg.program.methods.joinChallenge()
      .accounts({ challenge: pda.address, opponent: opp.publicKey,
        opponentToken: oAta, escrow: escrow })
      .instruction());
    await sendTx(tx, [opp]);
  }
  return { pda, aAta, oAta, escrow, cB0 };
}
async function settle(c: any, args: any, opp: any, mintKey: any) {
  const crRep = await reputationPda(WALLET);
  const opRep = await reputationPda(opp.publicKey);
  return pg.program.methods.settleChallenge(
    args[0], args[1], args[2], args[3])
    .accounts({ challenge: c.pda.address, oracle: WALLET, escrow: c.escrow,
      creatorToken: c.aAta, opponentToken: c.oAta,
      creatorReputation: crRep, opponentReputation: opRep })
    .rpc();
}
// This Anchor version returns u32/u64 fields as BN, but a plain number is
// possible on some decode paths; normalise either to a JS number.
function num(x: any): number {
  return x && typeof x.toNumber === "function" ? x.toNumber() : Number(x);
}
async function fetchChallenge(address: any) {
  return pg.program.account.challenge.fetch(address);
}
const HOUR_BUCKET = Math.floor(Date.now() / 1000 / 3600);

describe("volo_challenge v2 - cases 17-24", function (this: any) {
  this.timeout(300_000);
  let mint: any;
  let detAlly: any;      // deterministic pair partner for cases 21-23 (never signs)
  let detAllyAta: any;   // its ATA, pre-created so settle account constraints pass

  before(async () => {
    console.log("SETUP program " + PROGRAM.toString().slice(0, 8) + " wallet " +
      WALLET.toString().slice(0, 8) + " bucket " + HOUR_BUCKET);
    try {
      mark("before: create mint");
      mint = await createTestMint();
      mark("before: ataOf wallet");
      const aAta = await ataOf(WALLET, mint);
      mark("before: t2 new Transaction");
      const t2 = new web3.Transaction();
      t2.add(ixCreateAta(WALLET, WALLET, mint, aAta));
      mark("before: t2 mintTo");
      t2.add(ixMintTo(aAta, FUND, mint, WALLET));
      mark("before: t2 sendTx");
      await sendTx(t2, []);
      mark("before: balance check");
      const b = await bal(aAta);
      console.log("SETUP mint ok, creator holds " + b);
      assert(b === 50, "token plumbing wrong, creator holds " + b);
      mark("before: deterministic ally keypair");
      // deterministic pair partner from a fixed secret so hour-bucket challenges
      // land on the same PDA across separate runs.
      const secret = Buffer.from("7a11ef1c9f4e2b6d8a0c3e5f7a1c2d3e5f6a7b8c9d0e1f2a3b4c5d6e7f8091a2", "hex");
      detAlly = web3.Keypair.fromSeed(secret); // 32-byte seed -> fromSeed (fromSecretKey needs 64 bytes)
      detAllyAta = await ataOf(detAlly.publicKey, mint);
      mark("before: t3 new Transaction");
      const t3 = new web3.Transaction();
      t3.add(ixCreateAta(WALLET, detAlly.publicKey, mint, detAllyAta));
      mark("before: t3 sendTx");
      await sendTx(t3, []);
      mark("before: complete");
      console.log("SETUP det ally " + detAlly.publicKey.toString().slice(0, 8));
    } catch (e) {
      const err: any = e;
      console.log("HOOKFAIL name=" + (err && err.name) + " msg=" + (err && err.message));
      const st = String((err && err.stack) || "no stack").split("\n").slice(0, 9).join(" || ");
      console.log("HOOKSTACK " + st.slice(0, 900));
      throw err;
    }
  });

  // ---- Case 17 (headline): re-duel same pair with nonce 0 then 1 -------------
  it("case17 - second duel same pair nonce=1", async () => {
    const opp = await fundedOpponent(false);
    const n0 = await challengePda(WALLET, opp.publicKey, 0);
    const n1 = await challengePda(WALLET, opp.publicKey, 1);
    assert(!n0.address.equals(n1.address), "nonce did not change the PDA");
    const aAta = await ataOf(WALLET, mint);
    const esc0 = await ataOf(n0.address, mint);
    await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, 0)
      .accounts({ challenge: n0.address, opponent: opp.publicKey, mint,
        creatorToken: aAta, escrow: esc0 }).rpc();
    // same pair, SAME nonce -> must be rejected (v1's dead end, now explicit)
    try {
      await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, 0)
        .accounts({ challenge: n0.address, opponent: opp.publicKey, mint,
          creatorToken: aAta, escrow: esc0 }).rpc();
      bad("case17b", "nonce=0 re-create succeeded, expected collision");
    } catch (e) { ok("case17b", "nonce=0 re-create rejected: " + errName(e)); }
    // same pair, nonce=1 -> fresh account
    const esc1 = await ataOf(n1.address, mint);
    await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, 1)
      .accounts({ challenge: n1.address, opponent: opp.publicKey, mint,
        creatorToken: aAta, escrow: esc1 }).rpc();
    const a0 = await fetchChallenge(n0.address);
    const a1 = await fetchChallenge(n1.address);
    assert(a0.status === S_CREATED && a1.status === S_CREATED, "both must be Created");
    assert(num(a0.nonce) === 0 && num(a1.nonce) === 1, "nonces wrong");
    ok("case17", "two distinct Created PDAs, nonce 0 and 1 coexist (headline)");
  });

  // ---- Case 18: settle stores evidence hashes; decoded + RAW offsets ---------
  it("case18 - settle records hashes and raw bytes", async () => {
    const opp = await fundedOpponent(true);
    const c = await activeChallenge(mint, opp, 20, {});
    await settle(c, [PASS, FAIL_R, HASH, ZERO32], opp, mint);
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_SETTLED && a.result === R_CREATOR_WON,
      "expected Settled/CreatorWon, got " + a.status + "/" + a.result);
    const cb = Buffer.from(a.creatorEvidenceHash.data ?? a.creatorEvidenceHash);
    const ob = Buffer.from(a.opponentEvidenceHash.data ?? a.opponentEvidenceHash);
    assert(cb.equals(HASH), "creator hash readback mismatch");
    assert(ob.equals(ZERO32), "opponent hash should be zero");
    const eB = await bal(c.escrow);
    assert(eB === 0, "escrow should be drained, has " + eB);
    // raw byte contract for Task 11 decodeV2Layout: nonce@175, hashes@179/211
    const info = await pg.connection.getAccountInfo(c.pda.address);
    const raw = info.data;
    const nonceLe = raw.readUInt32LE(175);
    assert(nonceLe === 20, "raw nonce@175 = " + nonceLe + ", expected 20");
    assert(raw.subarray(179, 211).equals(HASH), "raw creator hash@179 mismatch");
    assert(raw.subarray(211, 243).equals(ZERO32), "raw opponent hash@211 not zero");
    assert(raw.readBigInt64LE(243) > 0n, "raw settledAt@243 not set");
    ok("case18", "decoded + RAW offsets nonce@175 hashes@179/211 settledAt@243 all match");
  });

  // ---- Case 19: PASS with nonzero hash is accepted (fresh duel) --------------
  it("case19 - settle PASS with nonzero hash ok", async () => {
    const opp = await fundedOpponent(true);
    const c = await activeChallenge(mint, opp, 30, {});
    await settle(c, [PASS, FAIL_R, HASH, ZERO32], opp, mint);
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_SETTLED && a.result === R_CREATOR_WON, "not settled creator-won");
    ok("case19", "PASS + nonzero evidence hash accepted");
  });

  // ---- Case 20: PASS with ZERO hash rejected, state untouched ----------------
  it("case20 - settle PASS with zero hash rejected", async () => {
    const opp = await fundedOpponent(true);
    const c = await activeChallenge(mint, opp, 40, {});
    const eBefore = await bal(c.escrow);
    await expectError("case20", "EvidenceZero",
      () => settle(c, [PASS, FAIL_R, ZERO32, ZERO32], opp, mint));
    const a = await fetchChallenge(c.pda.address);
    const eAfter = await bal(c.escrow);
    assert(a.status === S_ACTIVE, "status should stay 1, saw " + a.status);
    assert(eAfter === eBefore, "escrow moved: " + eBefore + " -> " + eAfter);
    ok("case20b", "still Active and escrow untouched after rejection");
  });

  // ---- Case 21: UNVERIFIED/UNVERIFIED with zero hashes locks -----------------
  it("case21 - both unverified locks escrow", async () => {
    const pda = await challengePda(WALLET, detAlly.publicKey, HOUR_BUCKET);
    const aAta = await ataOf(WALLET, mint);
    const escrow = await ataOf(pda.address, mint);
    const e0 = await bal(aAta);
    await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, HOUR_BUCKET)
      .accounts({ challenge: pda.address, opponent: detAlly.publicKey, mint,
        creatorToken: aAta, escrow: escrow }).rpc();
    const tx = new web3.Transaction();
    tx.add(ixMintTo(detAllyAta, FUND, mint, WALLET));
    tx.add(pg.program.methods.joinChallenge()
      .accounts({ challenge: pda.address, opponent: detAlly.publicKey,
        opponentToken: detAllyAta, escrow: escrow }).instruction());
    await sendTx(tx, [detAlly]);
    const crRep = await reputationPda(WALLET);
    const opRep = await reputationPda(detAlly.publicKey);
    await pg.program.methods.settleChallenge(UNVERIFIED, UNVERIFIED, ZERO32, ZERO32)
      .accounts({ challenge: pda.address, oracle: WALLET, escrow: escrow,
        creatorToken: aAta, opponentToken: detAllyAta,
        creatorReputation: crRep, opponentReputation: opRep }).rpc();
    const a = await fetchChallenge(pda.address);
    const e1 = await bal(escrow);
    assert(a.status === S_LOCKED && a.result === R_BOTH_FAIL,
      "expected Locked/BothFail, got " + a.status + "/" + a.result);
    assert(e1 === 10, "escrow should hold both stakes (10), has " + e1);
    ok("case21", "3/3 + zero hashes accepted -> BothFailLocked, escrow parked at " + e1 +
      " (bucket " + HOUR_BUCKET + " = today's pair for case23 rerun)");
  });

  // ---- Case 22: resolve_lock before the lock hour -----------------------------
  it("case22 - resolve_lock too early rejected", async () => {
    const pda = await challengePda(WALLET, detAlly.publicKey, HOUR_BUCKET);
    const info = await pg.connection.getAccountInfo(pda.address);
    const settledAt = Buffer.from(info.data).readBigInt64LE(243);
    const elapsed = Number(BigInt(Math.floor(Date.now() / 1000)) - settledAt);
    if (elapsed >= 3600) {
      skip("case22", "ran " + elapsed + "s after settle, hour already passed");
      return;
    }
    const escrow = await ataOf(pda.address, mint);
    const eBefore = await bal(escrow);
    const treasuryAta = await ataOf(TREASURY, mint);
    await expectError("case22", "TooEarlyToResolve", () =>
      pg.program.methods.resolveLock()
        .accounts({ challenge: pda.address, oracle: WALLET, mint: mint,
          escrow: escrow, treasury: TREASURY, treasuryToken: treasuryAta,
        }).rpc());
    const eAfter = await bal(escrow);
    assert(eAfter === eBefore, "escrow moved on a rejected resolve: " + eBefore + " -> " + eAfter);
    ok("case22b", "escrow intact after refusal (" + elapsed + "s < 3600s)");
  });

  // ---- Case 24: reputation bumps for both parties, streaks across two duels --
  it("case24 - reputation kept/broken/streak", async () => {
    const opp = await fundedOpponent(true);
    const c1 = await activeChallenge(mint, opp, 61, {});
    await settle(c1, [PASS, FAIL_R, HASH, ZERO32], opp, mint);
    const wRep1 = await pg.program.account.reputation.fetch(await reputationPda(WALLET));
    const oRep1 = await pg.program.account.reputation.fetch(await reputationPda(opp.publicKey));
    assert(num(wRep1.kept) >= 1 && num(wRep1.currentStreak) >= 1 &&
      num(wRep1.longestStreak) >= num(wRep1.currentStreak),
      "creator rep wrong after duel 1: " + JSON.stringify(wRep1));
    assert(num(oRep1.broken) === 1 && num(oRep1.currentStreak) === 0,
      "opponent rep wrong after duel 1: " + JSON.stringify(oRep1));
    const c2 = await activeChallenge(mint, opp, 62, {});   // same pair, second duel
    await settle(c2, [PASS, FAIL_R, HASH, ZERO32], opp, mint);
    const wRep2 = await pg.program.account.reputation.fetch(await reputationPda(WALLET));
    const oRep2 = await pg.program.account.reputation.fetch(await reputationPda(opp.publicKey));
    const wKept = num(wRep2.kept), wStreak = num(wRep2.currentStreak);
    assert(wKept === num(wRep1.kept) + 1 && wStreak === num(wRep1.currentStreak) + 1,
      "creator streak did not advance: " + wKept + "/" + wStreak);
    assert(num(oRep2.broken) === 2 && num(oRep2.currentStreak) === 0,
      "opponent broken should be 2, saw " + JSON.stringify(oRep2));
    ok("case24", "creator streak " + num(wRep1.currentStreak) + "->" + wStreak +
      ", opponent broken 1->2 streak 0");
  });

  // ---- Case 23: resolve_lock AFTER the lock hour (time-gated) ----------------
  // Runs LAST so a matured challenge from a previous hour-bucket is swept if one
  // exists. Fresh first run -> SKIP with instructions, never a false PASS.
  it("case23 - resolve_lock sweeps matured lock", async () => {
    const prev = HOUR_BUCKET - 1;
    const pda = await challengePda(WALLET, detAlly.publicKey, prev);
    let a: any = null;
    try { a = await fetchChallenge(pda.address); } catch (e) { a = null; }
    if (!a) { skip("case23", "no hour-" + prev + " challenge yet; re-run this suite >1h " +
      "after case21 created its lock and this branch sweeps it"); return; }
    if (a.status !== S_LOCKED) { skip("case23", "prev-bucket challenge status " +
      a.status + ", nothing to sweep"); return; }
    // sweep against the MINT THE CHALLENGE ITSELF RECORDS — a rerun creates a new
    // session mint, and every token account here must belong to the old one.
    const lockMint = new web3.PublicKey(a.tokenMint);
    const escrow = await ataOf(pda.address, lockMint);
    const treasuryAta = await ataOf(TREASURY, lockMint);
    const info = await pg.connection.getAccountInfo(pda.address);
    const settledAt = Buffer.from(info.data).readBigInt64LE(243);
    const elapsed = Number(BigInt(Math.floor(Date.now() / 1000)) - settledAt);
    if (elapsed < 3600) { skip("case23", elapsed + "s < 3600s lock hour; re-run later"); return; }
    const eB = await bal(escrow);
    await pg.program.methods.resolveLock()
      .accounts({ challenge: pda.address, oracle: WALLET, mint: lockMint,
        escrow: escrow, treasury: TREASURY, treasuryToken: treasuryAta }).rpc();
    const a2 = await fetchChallenge(pda.address);
    const eA = await bal(escrow);
    const tB = await bal(treasuryAta);
    assert(a2.status === S_LRESOLVED, "status should be 5, saw " + a2.status);
    assert(eA === 0, "escrow not drained: " + eA);
    assert(tB >= eB, "treasury short: " + tB + " < " + eB);
    ok("case23", "matured lock swept: escrow " + eB + " -> 0, treasury +" + eB);
  });

  after(async () => {
    const sol = await lamports(WALLET);
    console.log("SUMMARY pass=" + passCount + " fail=" + failCount + " skip=" + skipCount +
      " walletSOL=" + (sol / 1e9).toFixed(5));
    if (failCount > 0) throw new Error(failCount + " case(s) FAILED");
  });
});
