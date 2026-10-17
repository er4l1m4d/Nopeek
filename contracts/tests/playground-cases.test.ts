// *** INCOMPLETE DRAFT - DO NOT PASTE INTO PLAYGROUND YET ***
// Only case 1 of 16 is written, and it is wrong: it passes `c.oAtaEscrow`, which
// freshChallenge never returns, and it treats the opponent token account as the escrow
// when the escrow is the ATA of the challenge PDA. Cases 2-13 and 15-16 are absent.
// See the task ledger before reusing any of this.
//
// Task 4 Step 3 — the 16 manual cases, as one executable script.
//
// PASTE THIS OVER tests/anchor.test.ts IN SOLANA PLAYGROUND AND RUN `test`.
// It replaces 16 hand-run Call-tab submissions with one command, and it re-runs in
// seconds after Task 10 changes the program, which clicking cannot.
//
// Written against what the Playground test sandbox actually exposes (measured, not
// assumed): globals are pg, web3, anchor, BN, assert, Buffer, borsh, BufferLayout,
// describe/it. There is NO token library here, and a dynamic import aborts the whole
// file, so every SPL instruction below is laid out by hand. pg.provider does not
// exist; only pg.connection and pg.wallet.
//
// The oracle is this same wallet, because contracts/program/src/lib.rs sets
// ORACLE_AUTHORITY to it — so settle runs signed, and the non-oracle case uses a
// throwaway key.

const TOKEN_PROGRAM = new web3.PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
const ATA_PROGRAM = new web3.PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
const SYS = web3.SystemProgram.programId;
const MINT_SPACE = 82;
const TOKEN_ACCOUNT_SPACE = 165;

const STAKE = new BN(5_000_000);      // 5 USDC-equivalent at 6 decimals
const TWO_STAKES = new BN(10_000_000);
const FUND = new BN(50_000_000);
const TARGET = "com.instagram.android";

const PASS = 1, FAIL = 2, UNVERIFIED = 3;
const S_CREATED = 0, S_ACTIVE = 1, S_SETTLED = 2, S_REFUNDED = 3, S_LOCKED = 4;
const R_CREATOR_WON = 1, R_OPPONENT_WON = 2, R_BOTH_PASS = 3, R_BOTH_FAIL = 4;

let mint: any;          // PublicKey of the mint we create
let ataA: any;          // creator token account
let wallet: any;        // pg.wallet.publicKey

let passCount = 0, failCount = 0, skipCount = 0;
function ok(name: string, detail: string) {
  passCount++; console.log("PASS " + name + " :: " + detail);
}
function bad(name: string, detail: string) {
  failCount++; console.log("FAIL " + name + " :: " + detail);
  throw new Error(name + ": " + detail);
}
function skip(name: string, why: string) {
  skipCount++; console.log("SKIP " + name + " :: " + why);
}

async function ataOf(owner: any): Promise<any> {
  const found = await web3.PublicKey.findProgramAddress(
    [owner.toBuffer(), TOKEN_PROGRAM.toBuffer(), mint.toBuffer()], ATA_PROGRAM);
  return found[0];
}
async function challengePda(creator: any, opponent: any): Promise<any> {
  const found = await web3.PublicKey.findProgramAddress(
    [Buffer.from("challenge"), creator.toBuffer(), opponent.toBuffer()],
    pg.program.programId);
  return { address: found[0], bump: found[1] };
}

// --- hand-rolled SPL token instructions (no library in this sandbox) ---------

function u64Into(buf: any, at: number, value: any) {
  const le = value.toArrayLike("le", 8);
  for (let i = 0; i < 8; i++) buf[at + i] = le[i];
}

function ixInitializeMint(mintKey: any, authority: any) {
  // layout: 0=instr tag, 1=decimals, 2..33=mint authority, 34=freeze present, 35..66=freeze
  const d = Buffer.alloc(67);
  d[0] = 0; d[1] = 6;
  authority.toBuffer().copy(d, 2);
  d[34] = 1;
  authority.toBuffer().copy(d, 35);
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: mintKey, isSigner: false, isWritable: true },
      { pubkey: new web3.PublicKey("SysvarRent111111111111111111111111111111111"),
        isSigner: false, isWritable: false },
    ],
    programId: TOKEN_PROGRAM, data: d,
  });
}
function ixCreateAta(funder: any, owner: any) {
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: funder, isSigner: true, isWritable: true },
      { pubkey: ataFor(owner), isSigner: false, isWritable: true },
      { pubkey: owner, isSigner: false, isWritable: false },
      { pubkey: mint, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROGRAM, isSigner: false, isWritable: false },
      { pubkey: SYS, isSigner: false, isWritable: false },
    ],
    programId: ATA_PROGRAM, data: Buffer.alloc(0),
  });
}
function ixMintTo(to: any, amount: any) {
  const d = Buffer.alloc(9);
  d[0] = 7; u64Into(d, 1, amount);
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: mint, isSigner: false, isWritable: true },
      { pubkey: to, isSigner: false, isWritable: true },
      { pubkey: wallet, isSigner: true, isWritable: false },
    ],
    programId: TOKEN_PROGRAM, data: d,
  });
}
let ataCache: any = {};
function ataFor(owner: any) { return ataCache[owner.toBase58()]; }

async function balanceOf(account: any): Promise<number> {
  const r = await pg.connection.getTokenAccountBalance(account);
  return Number(r.value.uiAmount);
}
async function freshChallenge(opponentFunded: boolean) {
  const opp = web3.Keypair.generate();
  const pda = await challengePda(wallet, opp.publicKey);
  const oAta = await ataOf(opp.publicKey);
  ataCache[opp.publicKey.toBase58()] = oAta;
  const aAta = ataA;
  if (opponentFunded) {
    const tx = new web3.Transaction();
    tx.add(ixCreateAta(wallet, opp.publicKey));
    tx.add(ixMintTo(oAta, FUND));
    await pg.connection.sendTransaction(tx, [wallet === pg.wallet.publicKey ? null : null]
      .filter(Boolean) as any, { skipPreflight: false }).catch(() => {});
  }
  return { opp, pda, oAta, aAta };
}

describe("volo_challenge - 16 manual cases", function () {
  this.timeout(180_000);

  before(async () => {
    wallet = pg.wallet.publicKey;
    console.log("SETUP wallet " + wallet.toBase58().slice(0, 6) + " program " +
      pg.program.programId.toBase58().slice(0, 6));

    const mintKp = web3.Keypair.generate();
    const rentMint = await pg.connection.getMinimumBalanceForRentExemption(MINT_SPACE);
    const rentTok = await pg.connection.getMinimumBalanceForRentExemption(TOKEN_ACCOUNT_SPACE);

    const t1 = new web3.Transaction();
    t1.add(web3.SystemProgram.createAccount({
      fromPubkey: wallet, newAccountPubkey: mintKp.publicKey,
      space: MINT_SPACE, lamports: rentMint, programId: TOKEN_PROGRAM,
    }));
    t1.add(ixInitializeMint(mintKp.publicKey, wallet));
    await pg.connection.sendTransaction(t1, [mintKp]);

    mint = mintKp.publicKey;
    ataA = await ataOf(wallet);
    ataCache[wallet.toBase58()] = ataA;

    const t2 = new web3.Transaction();
    t2.add(ixCreateAta(wallet, wallet));
    t2.add(ixMintTo(ataA, FUND));
    await pg.connection.sendTransaction(t2, []);

    const b = await balanceOf(ataA);
    console.log("SETUP mint created, creator balance " + b);
    assert(b === 50, "token plumbing is wrong - creator should hold 50, saw " + b);
  });

  // ---- Case 1: create_challenge happy path -----------------------------------
  it("case 1 create_challenge escrows and opens Created", async () => {
    const c = await freshChallenge(false);
    await pg.program.methods.createChallenge(STAKE, TARGET, 1800, 86400)
      .accounts({ challenge: c.pda.address, opponent: c.opp.publicKey, mint,
        creatorToken: c.aAta, escrow: c.oAtaEscrow })
      .rpc();
    const acct = await pg.program.account.challenge.fetch(c.pda.address);
    const escrowBal = await balanceOf(await ataOf(c.pda.address));
    assert(Number(acct.stakeAmount) === 5_000_000, "stake not stored");
    assert(acct.status === S_CREATED, "status should be 0, saw " + acct.status);
    assert(acct.result === 0, "result should be 0, saw " + acct.result);
    assert(escrowBal === 5, "escrow should hold 5, saw " + escrowBal);
    ok("case1", "escrow=5 status=Created result=0");
  });
});
