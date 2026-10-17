// Task 4 Step 3 (revived 2026-10-17) — the 16 core manual cases (1-16 in
// contracts/tests/manual-test-cases.md), as ONE executable script for the Solana
// Playground test sandbox (tests/anchor.test.ts + `test` command).
//
// This file REPLACES the earlier "INCOMPLETE DRAFT" (whose own header said only
// case 1 was written, and wrong: it passed c.oAtaEscrow, which freshChallenge
// never returned, and treated the opponent token account as the escrow when the
// escrow is the ATA of the challenge PDA). It is written against the MEASURED
// sandbox constraints (ATTESTATION_DEPLOY_NOTES.md "Playground test sandbox"):
// the runner exposes pg/web3/anchor/BN/assert/Buffer only, no token library (SPL
// instructions hand-laid below), no dynamic import, pg.connection + pg.wallet
// only, describe() required.
//
// v2 SEED NOTE: the deployed program is v2 — challenge seeds are
// [b"challenge", creator, opponent, nonce.to_le_bytes()] (u32 LE). The v1 cases'
// seed spelling is the nonce=0 case of v2; because this file must be re-runnable,
// every fresh-pair case uses its own nonce (the case number). One wallet plays
// three roles — creator, oracle and treasury — because the program pins
// ORACLE_AUTHORITY and TREASURY to it (contracts/program/src/lib.rs lines 21-29).
// Opponents are freshly generated keypairs that sign their joins; the fee payer
// is always pg.wallet, so opponents need no SOL.
//
// TIME-GATED CASES 14/15 (refund_unjoined needs start_time + 3600): ONE seeded
// refund challenge (deterministic ally, nonce 14, window 3600) is created by
// case14's first run and refunded by a later run. Until then case14 reports SKIP
// with the clock remaining, and once exercised it reports SKIP forever; case15
// proves the TOO-EARLY branch against the fresh seed. A time-gated run can never
// report a false PASS.
//
// ATA NOTE: ixCreateAta uses CreateIdempotent (data [1]) so ensure-ATA on an
// existing account no-ops instead of failing; plain Create [0] refuses an
// initialized account ("Provided owner is not allowed"), which broke re-joins.

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

const STAKE = new BN(5_000_000);          // 5 units at 6 decimals
const FUND = new BN(50_000_000);
const TARGET = "com.instagram.android.PAD_TO_32B";    // 32 bytes: the settled
const TARGET_33 = "com.instagram.android.PAD_TO_32BX"; // 33 bytes -> TargetAppTooLong
const HASH = Buffer.from("0c88710472984325013f15b1ba5a1f926b6a8d299a97888d6e2f1fff8e4d2d9a", "hex");
const ZERO32 = Buffer.alloc(32);

const PASS = 1, FAIL_R = 2, UNVERIFIED = 3;
const S_CREATED = 0, S_ACTIVE = 1, S_SETTLED = 2, S_REFUNDED = 3, S_LOCKED = 4;
const R_CREATOR_WON = 1, R_OPPONENT_WON = 2, R_BOTH_PASS = 3, R_BOTH_FAIL = 4;

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
    // data [1] = CreateIdempotent: reruns and same-opponent re-joins must no-op
    // on an existing ATA instead of failing (plain Create [0] refuses one).
    programId: ATA, data: Buffer.from([1]),
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

// Create a challenge and join it, so settle cases have an Active duel. The
// opponent is freshly generated per call; it signs the join but never pays.
async function activeChallenge(mintKey: any, nonce: number) {
  const opp = web3.Keypair.generate();
  const pda = await challengePda(WALLET, opp.publicKey, nonce);
  const aAta = await ataOf(WALLET, mintKey);
  const oAta = await ataOf(opp.publicKey, mintKey);
  const escrow = await ataOf(pda.address, mintKey);
  const cB0 = await bal(aAta);
  await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, nonce)
    .accounts({ challenge: pda.address, opponent: opp.publicKey, mint: mintKey,
      creatorToken: aAta, escrow: escrow })
    .rpc();
  const tx = new web3.Transaction();
  tx.add(ixCreateAta(WALLET, opp.publicKey, mintKey, oAta));
  tx.add(ixMintTo(oAta, FUND, mintKey, WALLET));
  tx.add(pg.program.methods.joinChallenge()
    .accounts({ challenge: pda.address, opponent: opp.publicKey,
      opponentToken: oAta, escrow: escrow })
    .instruction());
  await sendTx(tx, [opp]);
  return { pda, opp, aAta, oAta, escrow, cB0 };
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

describe("volo_challenge - cases 1-16 (v2 seeds)", function (this: any) {
  this.timeout(300_000);
  let mint: any;
  let detAlly: any;     // deterministic ally: wrong-signer probe (case5) and the
  let detAllyAta: any;  // time-gated refund seed's opponent (cases 14/15)
  let c1: any = null;   // case1's Created challenge (case5 joins it wrong)
  let c4: any = null;   // case4's Active duel (cases 6 and 16 reuse it)

  before(async () => {
    console.log("SETUP program " + PROGRAM.toString().slice(0, 8) + " wallet " +
      WALLET.toString().slice(0, 8) + " suite cases1-16");
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
      const secret = Buffer.from("7a11ef1c9f4e2b6d8a0c3e5f7a1c2d3e5f6a7b8c9d0e1f2a3b4c5d6e7f8091a2", "hex");
      detAlly = web3.Keypair.fromSeed(secret); // 32-byte seed -> fromSeed
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
      throw err;
    }
  });

  // ---- Case 1: create_challenge happy path -----------------------------------
  it("case1 - create_challenge happy path", async () => {
    const opp = web3.Keypair.generate();
    const pda = await challengePda(WALLET, opp.publicKey, 1);
    const aAta = await ataOf(WALLET, mint);
    const escrow = await ataOf(pda.address, mint);
    await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 86400, 1)
      .accounts({ challenge: pda.address, opponent: opp.publicKey, mint,
        creatorToken: aAta, escrow: escrow }).rpc();
    const a = await fetchChallenge(pda.address);
    const e = await bal(escrow);
    assert(a.status === S_CREATED, "status " + a.status + " != Created");
    assert(a.result === 0, "result " + a.result + " != 0");
    assert(num(a.nonce) === 1, "nonce " + num(a.nonce) + " != 1");
    assert(e === 5, "escrow " + e + " != 5");
    c1 = { pda, opp, aAta, escrow };
    ok("case1", "Created, escrow 5, status 0, result 0, window 86400");
  });

  // ---- Case 2: create_challenge rejects zero stake ---------------------------
  it("case2 - create_challenge rejects zero stake", async () => {
    const opp = web3.Keypair.generate();
    const pda = await challengePda(WALLET, opp.publicKey, 2);
    const aAta = await ataOf(WALLET, mint);
    const escrow = await ataOf(pda.address, mint);
    await expectError("case2", "InvalidStake", () =>
      pg.program.methods.createChallenge(new BN(0), TARGET, 1800, 86400, 2)
        .accounts({ challenge: pda.address, opponent: opp.publicKey, mint,
          creatorToken: aAta, escrow: escrow }).rpc());
    let exists = true;
    try { await fetchChallenge(pda.address); } catch (e) { exists = false; }
    assert(!exists, "challenge account persisted despite InvalidStake rollback");
  });

  // ---- Case 3: create_challenge rejects >32 byte target_app ------------------
  it("case3 - create_challenge rejects >32 byte target_app", async () => {
    const opp = web3.Keypair.generate();
    const pda = await challengePda(WALLET, opp.publicKey, 3);
    const aAta = await ataOf(WALLET, mint);
    const escrow = await ataOf(pda.address, mint);
    await expectError("case3", "TargetAppTooLong", () =>
      pg.program.methods.createChallenge(STAKE, TARGET_33, 1800, 86400, 3)
        .accounts({ challenge: pda.address, opponent: opp.publicKey, mint,
          creatorToken: aAta, escrow: escrow }).rpc());
  });

  // ---- Case 4: join_challenge activates the duel -----------------------------
  it("case4 - join_challenge activates", async () => {
    const opp = web3.Keypair.generate();
    const pda = await challengePda(WALLET, opp.publicKey, 4);
    const aAta = await ataOf(WALLET, mint);
    const oAta = await ataOf(opp.publicKey, mint);
    const escrow = await ataOf(pda.address, mint);
    await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, 4)
      .accounts({ challenge: pda.address, opponent: opp.publicKey, mint,
        creatorToken: aAta, escrow: escrow }).rpc();
    const tx = new web3.Transaction();
    tx.add(ixCreateAta(WALLET, opp.publicKey, mint, oAta));
    tx.add(ixMintTo(oAta, FUND, mint, WALLET));
    tx.add(pg.program.methods.joinChallenge()
      .accounts({ challenge: pda.address, opponent: opp.publicKey,
        opponentToken: oAta, escrow: escrow }).instruction());
    await sendTx(tx, [opp]);
    const a = await fetchChallenge(pda.address);
    const e = await bal(escrow);
    assert(a.status === S_ACTIVE, "status " + a.status + " != Active");
    assert(e === 10, "escrow " + e + " != 10 (both stakes)");
    assert(a.opponent === opp.publicKey.toBase58(), "opponent not recorded");
    c4 = { pda, opp, aAta, oAta, escrow };
    ok("case4", "Active, escrow 10, opponent recorded");
  });

  // ---- Case 5: join from the wrong signer is rejected ------------------------
  it("case5 - join_challenge from wrong signer", async () => {
    assert(c1, "case1 did not complete");
    await expectError("case5", "NotOpponent", () => sendTx(new web3.Transaction()
      .add(pg.program.methods.joinChallenge()
        .accounts({ challenge: c1.pda.address, opponent: detAlly.publicKey,
          opponentToken: detAllyAta, escrow: c1.escrow }).instruction()),
      [detAlly]));
    const a = await fetchChallenge(c1.pda.address);
    assert(a.status === S_CREATED, "status changed to " + a.status);
    assert(await bal(c1.escrow) === 5, "escrow changed");
    ok("case5", "requested signer rejected, challenge unchanged (Created, escrow 5)");
  });

  // ---- Case 6: join when already Active is rejected --------------------------
  it("case6 - join_challenge when already Active", async () => {
    assert(c4, "case4 did not complete");
    await expectError("case6", "NotCreated", () => sendTx(new web3.Transaction()
      .add(pg.program.methods.joinChallenge()
        .accounts({ challenge: c4.pda.address, opponent: c4.opp.publicKey,
          opponentToken: c4.oAta, escrow: c4.escrow }).instruction()),
      [c4.opp]));
    const a = await fetchChallenge(c4.pda.address);
    assert(a.status === S_ACTIVE, "status changed to " + a.status);
    assert(await bal(c4.escrow) === 10, "escrow changed");
    ok("case6", "second join rejected, duel still Active, escrow 10");
  });

  // ---- Case 7: creator PASS, opponent FAIL -> creator wins -------------------
  it("case7 - settle creator PASS opponent FAIL -> creator wins", async () => {
    const c = await activeChallenge(mint, 7);
    await settle(c, [PASS, FAIL_R, HASH, ZERO32], c.opp, mint);
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_SETTLED, "status " + a.status);
    assert(a.result === R_CREATOR_WON, "result " + a.result);
    assert(await bal(c.escrow) === 0, "escrow not drained");
    // cB0 is read BEFORE the creator's stake leaves, so a creator win (+2x
    // stake back) lands at cB0 - stake + 2*stake = cB0 + 5.
    assert(await bal(c.aAta) === c.cB0 + 5, "creator not paid winnings (expected +5 net)");
    ok("case7", "escrow 10 -> creator (net +5), drained, result CreatorWon");
  });

  // ---- Case 8: both PASS -> both refunded ------------------------------------
  it("case8 - settle both PASS -> both refunded", async () => {
    const c = await activeChallenge(mint, 8);
    const oB = await bal(c.oAta);
    await settle(c, [PASS, PASS, HASH, HASH], c.opp, mint);
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_SETTLED, "status " + a.status);
    assert(a.result === R_BOTH_PASS, "result " + a.result);
    assert(await bal(c.escrow) === 0, "escrow not drained");
    assert(await bal(c.aAta) === c.cB0, "creator was not refunded exactly their stake");
    assert(await bal(c.oAta) === oB + 5, "opponent was not refunded exactly their stake");
    ok("case8", "both stakes returned, escrow drained, result BothPass");
  });

  // ---- Case 9a: both FAIL -> locked escrow -----------------------------------
  it("case9a - settle both FAIL (2,2) -> locked", async () => {
    const c = await activeChallenge(mint, 9);
    await settle(c, [FAIL_R, FAIL_R, ZERO32, ZERO32], c.opp, mint);
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_LOCKED, "status " + a.status);
    assert(a.result === R_BOTH_FAIL, "result " + a.result);
    assert(await bal(c.escrow) === 10, "escrow not parked at 10");
    ok("case9a", "both FAIL (2,2) -> BothFailLocked, escrow parked at 10");
  });

  // ---- Case 9b: both UNVERIFIED -> same locked gate --------------------------
  it("case9b - settle both UNVERIFIED (3,3) -> locked", async () => {
    const c = await activeChallenge(mint, 99);
    await settle(c, [UNVERIFIED, UNVERIFIED, ZERO32, ZERO32], c.opp, mint);
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_LOCKED, "status " + a.status);
    assert(a.result === R_BOTH_FAIL, "result " + a.result);
    assert(await bal(c.escrow) === 10, "escrow not parked at 10");
    ok("case9b", "both UNVERIFIED (3,3) -> also BothFailLocked (same gate)");
  });

  // ---- Case 10: creator FAIL, opponent PASS -> opponent wins -----------------
  it("case10 - settle creator FAIL opponent PASS -> opponent wins", async () => {
    const c = await activeChallenge(mint, 10);
    const oB = await bal(c.oAta);
    await settle(c, [FAIL_R, PASS, ZERO32, HASH], c.opp, mint);
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_SETTLED, "status " + a.status);
    assert(a.result === R_OPPONENT_WON, "result " + a.result);
    assert(await bal(c.escrow) === 0, "escrow not drained");
    assert(await bal(c.oAta) === oB + 10, "opponent did not receive both stakes");
    ok("case10", "opponent paid 10, escrow drained, result OpponentWon");
  });

  // ---- Case 11: settle from a non-oracle signer is rejected ------------------
  it("case11 - settle rejected from non-oracle signer", async () => {
    const c = await activeChallenge(mint, 11);
    const rando = web3.Keypair.generate();
    // The settle struct's reputation accounts are init_if_needed with
    // payer = oracle, and Anchor runs those system CPIs BEFORE constraint
    // checks: a zero-lamport oracle dies with a SystemProgram "insufficient
    // lamports" (custom 0x1) before NotOracle can speak. Fund the impostor so
    // the oracle constraint is the thing that answers (probe-case11 evidence:
    // funded -> Error Code: NotOracle. Error Number: 6006).
    await sendTx(new web3.Transaction().add(web3.SystemProgram.transfer({
      fromPubkey: WALLET, toPubkey: rando.publicKey, lamports: 20_000_000 })), []);
    const crRep = await reputationPda(WALLET);
    const opRep = await reputationPda(c.opp.publicKey);
    await expectError("case11", "NotOracle", () => sendTx(new web3.Transaction()
      .add(pg.program.methods.settleChallenge(PASS, FAIL_R, HASH, ZERO32).accounts({
        challenge: c.pda.address, oracle: rando.publicKey, escrow: c.escrow,
        creatorToken: c.aAta, opponentToken: c.oAta,
        creatorReputation: crRep, opponentReputation: opRep,
      }).instruction()), [rando]));
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_ACTIVE, "status changed to " + a.status);
    assert(await bal(c.escrow) === 10, "escrow changed");
    ok("case11", "non-oracle settlement rejected, duel still Active, escrow 10");
  });

  // ---- Case 12: settle when not Active is rejected ---------------------------
  it("case12 - settle rejected when not Active", async () => {
    const opp = web3.Keypair.generate();
    const pda = await challengePda(WALLET, opp.publicKey, 12);
    const aAta = await ataOf(WALLET, mint);
    const oAta = await ataOf(opp.publicKey, mint);
    const escrow = await ataOf(pda.address, mint);
    await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, 12)
      .accounts({ challenge: pda.address, opponent: opp.publicKey, mint,
        creatorToken: aAta, escrow: escrow }).rpc();
    // the opponent token account must exist for the settle account constraints
    await sendTx(new web3.Transaction()
      .add(ixCreateAta(WALLET, opp.publicKey, mint, oAta)), []);
    const crRep = await reputationPda(WALLET);
    const opRep = await reputationPda(opp.publicKey);
    await expectError("case12", "NotActive", () =>
      pg.program.methods.settleChallenge(PASS, FAIL_R, HASH, ZERO32).accounts({
        challenge: pda.address, oracle: WALLET, escrow: escrow,
        creatorToken: aAta, opponentToken: oAta,
        creatorReputation: crRep, opponentReputation: opRep,
      }).rpc());
    const a = await fetchChallenge(pda.address);
    assert(a.status === S_CREATED, "status " + a.status);
    assert(await bal(escrow) === 5, "escrow changed");
    ok("case12", "unjoined duel not settleable, still Created, escrow 5");
  });

  // ---- Case 13: settle rejects out-of-range result codes ---------------------
  it("case13 - settle rejects bad result codes", async () => {
    const c = await activeChallenge(mint, 13);
    await expectError("case13", "ResultOutOfRange", () =>
      settle(c, [7, FAIL_R, HASH, ZERO32], c.opp, mint));
    const a = await fetchChallenge(c.pda.address);
    assert(a.status === S_ACTIVE, "status changed to " + a.status);
    assert(await bal(c.escrow) === 10, "escrow changed");
    ok("case13", "code 7 rejected, duel still Active, escrow 10");
  });

  // ---- Case 14: refund_unjoined after 1 hour (time-gated) --------------------
  it("case14 - refund_unjoined after 1 hour", async () => {
    const seedPda = await challengePda(WALLET, detAlly.publicKey, 14);
    let seed: any = null;
    try { seed = await fetchChallenge(seedPda.address); } catch (e) { seed = null; }
    if (!seed) {
      const aAta = await ataOf(WALLET, mint);
      const escrow = await ataOf(seedPda.address, mint);
      await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 3600, 14)
        .accounts({ challenge: seedPda.address, opponent: detAlly.publicKey, mint,
          creatorToken: aAta, escrow: escrow }).rpc();
      const fresh = await fetchChallenge(seedPda.address);
      const now = Math.floor(Date.now() / 1000);
      const until = num(fresh.startTime) + 3600 - now;
      skip("case14", "refund challenge seeded (nonce 14); refund_unjoined needs " +
        "start+3600, so re-run this suite in ~" + Math.ceil(until / 60) + " min");
      return;
    }
    if (seed.status === S_REFUNDED) {
      const seedMint = new web3.PublicKey(seed.tokenMint);
      const escrow = await ataOf(seedPda.address, seedMint);
      assert(await bal(escrow) === 0, "refunded but escrow still holds tokens");
      ok("case14", "refund previously exercised (status 3, escrow 0)");
      return;
    }
    const seedMint = new web3.PublicKey(seed.tokenMint);
    const escrow = await ataOf(seedPda.address, seedMint);
    const creatorAta = await ataOf(WALLET, seedMint);
    const now = Math.floor(Date.now() / 1000);
    const need = num(seed.startTime) + 3600;
    if (now < need) {
      skip("case14", (need - now) + "s until the refund hour; re-run later");
      return;
    }
    const cB = await bal(creatorAta);
    const eB = await bal(escrow);
    await pg.program.methods.refundUnjoined()
      .accounts({ challenge: seedPda.address, creator: WALLET,
        escrow: escrow, creatorToken: creatorAta }).rpc();
    const a = await fetchChallenge(seedPda.address);
    assert(a.status === S_REFUNDED, "status " + a.status);
    assert(await bal(escrow) === 0, "escrow not drained: " + (await bal(escrow)));
    assert(await bal(creatorAta) === cB + eB, "creator did not receive the refund");
    ok("case14", "refunded after the hour: status 3, escrow " + eB + " -> 0");
  });

  // ---- Case 15: refund_unjoined too early is rejected ------------------------
  it("case15 - refund_unjoined too early", async () => {
    const seedPda = await challengePda(WALLET, detAlly.publicKey, 14);
    let seed: any = null;
    try { seed = await fetchChallenge(seedPda.address); } catch (e) { seed = null; }
    if (!seed) { skip("case15", "seed not created yet (case14 runs first)"); return; }
    if (seed.status === S_REFUNDED) {
      skip("case15", "seed already refunded; too-early branch was proven on the fresh seed");
      return;
    }
    if (seed.status !== S_CREATED) { skip("case15", "seed status " + seed.status); return; }
    const now = Math.floor(Date.now() / 1000);
    if (now >= num(seed.startTime) + 3600) {
      skip("case15", "refund hour passed; case14 exercises the success path");
      return;
    }
    const seedMint = new web3.PublicKey(seed.tokenMint);
    const escrow = await ataOf(seedPda.address, seedMint);
    const creatorAta = await ataOf(WALLET, seedMint);
    await expectError("case15", "TooEarlyToRefund", () =>
      pg.program.methods.refundUnjoined()
        .accounts({ challenge: seedPda.address, creator: WALLET,
          escrow: escrow, creatorToken: creatorAta }).rpc());
    const a = await fetchChallenge(seedPda.address);
    assert(a.status === S_CREATED, "status changed to " + a.status);
    assert(await bal(escrow) === 5, "escrow changed");
    ok("case15", "refund before the hour rejected, still Created, escrow 5");
  });

  // ---- Case 16: refund_unjoined after a join is rejected ---------------------
  it("case16 - refund_unjoined after join", async () => {
    assert(c4, "case4 did not complete");
    await expectError("case16", "NotCreated", () =>
      pg.program.methods.refundUnjoined()
        .accounts({ challenge: c4.pda.address, creator: WALLET,
          escrow: c4.escrow, creatorToken: c4.aAta }).rpc());
    const a = await fetchChallenge(c4.pda.address);
    assert(a.status === S_ACTIVE, "status changed to " + a.status);
    assert(await bal(c4.escrow) === 10, "escrow changed");
    ok("case16", "Active duel not refundable (NotCreated), escrow intact at 10");
  });

  after(async () => {
    console.log("SUMMARY pass=" + passCount + " fail=" + failCount + " skip=" + skipCount);
    if (failCount > 0) throw new Error(failCount + " case(s) failed");
    console.log("RESULT PASS" + (skipCount > 0 ? " (" + skipCount + " time-gated/deferred)" : ""));
  });
});
