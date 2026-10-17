// Task 10 Step 4 — CASE 17 ONLY (the headline: one pair can duel twice).
// Cheap variant of playground-cases-v2.test.ts: 1 mint + 1 creator ATA + 2
// creates, so it costs a handful of devnet transactions, not the full 17-24 sweep.
//
// It also settles the open ConstraintSeeds question. Evidence so far: the deployed
// ProgramData CONTAINS every v2-only string (reputation seed, "nonzero evidence
// hash", ...), so the on-chain binary is v2 - a redeploy cannot be the fix. Our JS
// derivation equals the source's seed rule exactly, yet the chain rejected it, so
// the suspect is the sandbox Anchor client auto-deriving the seeded `challenge`
// account instead of honouring `.accounts({ challenge })`. This file therefore
// creates with BOTH conventions and reports which address actually landed on chain.
//
// Carries every fix already proven on-chain in this task:
//   - ATA create accounts: funder, ata, owner, mint, SYSTEM(4), TOKEN(5); no rent; data [0]
//   - uiAmountString (getTokenAccountBalance has no uiTokenAmount)
//   - 3-arg toArrayLike(Buffer,"le",8) for BN bytes
//   - no guard-banned substrings anywhere, comments included (runner scans those too)

declare const pg: any;
declare const web3: any;
declare const BN: any;
declare const describe: any;
declare const it: any;
declare const before: any;
declare const after: any;

const TOKEN_PROG = new web3.PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
const ATA_PROG = new web3.PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
const SYS_PROG = web3.SystemProgram.programId;
const RENT_SYS = new web3.PublicKey("SysvarRent111111111111111111111111111111111");
const PROGRAM = pg.program.programId;
const WALLET = pg.wallet.publicKey;

const STAKE = new BN(5_000_000);
const FUND = new BN(50_000_000);
const LABEL = "com.instagram.android.PAD_TO_32B"; // exactly 32 bytes
const STATUS_CREATED = 0;

function le4(n: number) { const b = Buffer.alloc(4); b.writeUInt32LE(n >>> 0, 0); return b; }
function msg(e: any) { const x: any = e; return String((x && x.message) || x); }
// An earlier run lost its answer to a 260-char slice, so print the caused-by
// account name and every base58 token in full, one per line.
function acctOf(e: any) {
  const m = msg(e).match(/caused by account: (\w+)/);
  return m ? m[1] : msg(e).slice(0, 60);
}
function toksOf(e: any) {
  return msg(e).split(/[^1-9A-HJ-NP-Za-km-z]+/)
    .filter((t) => t.length >= 32 && t.length <= 44)
    .map((t, i) => "T" + i + "=" + t);
}
async function ataOf(owner: any, mintKey: any) {
  const found = await web3.PublicKey.findProgramAddress(
    [owner.toBuffer(), TOKEN_PROG.toBuffer(), mintKey.toBuffer()], ATA_PROG);
  return found[0];
}
async function challengePda(creator: any, opponent: any, nonce: number) {
  const found = await web3.PublicKey.findProgramAddress(
    [Buffer.from("challenge"), creator.toBuffer(), opponent.toBuffer(), le4(nonce)], PROGRAM);
  return found[0];
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
      { pubkey: RENT_SYS, isSigner: false, isWritable: false },
    ],
    programId: TOKEN_PROG, data: d,
  });
}
function ixCreateAta(funder: any, owner: any, mintKey: any, vault: any) {
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: funder, isSigner: true, isWritable: true },
      { pubkey: vault, isSigner: false, isWritable: true },
      { pubkey: owner, isSigner: false, isWritable: false },
      { pubkey: mintKey, isSigner: false, isWritable: false },
      { pubkey: SYS_PROG, isSigner: false, isWritable: false },
      { pubkey: TOKEN_PROG, isSigner: false, isWritable: false },
    ],
    programId: ATA_PROG, data: Buffer.from([0]),
  });
}
function ixMintTo(to: any, amount: any, mintKey: any, authority: any) {
  const d = Buffer.alloc(9);
  d[0] = 7;
  const le = amount.toArrayLike(Buffer, "le", 8);
  for (let i = 0; i < 8; i++) d[i + 1] = le[i];
  return new web3.TransactionInstruction({
    keys: [
      { pubkey: mintKey, isSigner: false, isWritable: true },
      { pubkey: to, isSigner: false, isWritable: true },
      { pubkey: authority, isSigner: true, isWritable: false },
    ],
    programId: TOKEN_PROG, data: d,
  });
}
async function sendTx(tx: any, extraSigners: any[]) {
  const { blockhash } = await pg.connection.getLatestBlockhash("confirmed");
  tx.recentBlockhash = blockhash;
  tx.feePayer = WALLET;
  const sig = await pg.connection.sendTransaction(
    tx, [pg.wallet.keypair].concat(extraSigners).filter((s: any) => !!s),
    { skipPreflight: false });
  const st = await pg.connection.confirmTransaction(sig, "confirmed");
  if (st.value && st.value.err) throw new Error("TX_ERR " + JSON.stringify(st.value.err));
  return sig;
}

// Which account in a landed create transaction is the Challenge? The one the
// PROGRAM owns (escrow/mint belong to the Token program, PDAs we passed are not).
async function findLandedChallenge(sig: any) {
  const tx = await pg.connection.getTransaction(sig, { encoding: "jsonParsed", maxSupportedTransactionVersion: 0 });
  const keys: string[] = tx.transaction.message.accountKeys.map((k: any) => k.pubkey);
  const infos = await pg.connection.getMultipleAccountsInfo(keys.map((k: string) => new web3.PublicKey(k)));
  for (let i = 0; i < keys.length; i++) {
    if (infos[i] && infos[i].owner === PROGRAM.toString() && keys[i] !== PROGRAM.toString()) {
      return new web3.PublicKey(keys[i]);
    }
  }
  return null;
}

async function createDuel(nonce: number, opponent: any, preferred: any, mintKey: any, creatorAta: any) {
  // Attempt 1: our own JS derivation, passed explicitly.
  const escOwn = await ataOf(preferred, mintKey);
  try {
    const sig = await pg.program.methods.createChallenge(STAKE, LABEL, 1800, 3600, nonce)
      .accounts({ challenge: preferred, opponent: opponent, mint: mintKey,
        creatorToken: creatorAta, escrow: escOwn }).rpc();
    const landed = await findLandedChallenge(sig);
    console.log("OVERRIDE accepted nonce=" + nonce + " jsPda=" + preferred.toString() +
      " landed=" + (landed ? landed.toString() : "?"));
    return { address: landed || preferred, via: "override" };
  } catch (e) {
    console.log("OVERRIDE rejected nonce=" + nonce + " acct=" + acctOf(e) + " | " + toksOf(e).join(" | "));
  }
  // Attempt 2: let the client derive `challenge` itself.
  const sig2 = await pg.program.methods.createChallenge(STAKE, LABEL, 1800, 3600, nonce)
    .accounts({ opponent: opponent, mint: mintKey, creatorToken: creatorAta,
      escrow: await ataOf(preferred, mintKey) }).rpc();
  const landed2 = await findLandedChallenge(sig2);
  console.log("CLIENT-DERIVED accepted nonce=" + nonce + " landed=" +
    (landed2 ? landed2.toString() : "?") + " (jsPda was " + preferred.toString() + ")");
  return { address: landed2 || preferred, via: "client" };
}

describe("volo_challenge v2 - case 17 only", function (this: any) {
  this.timeout(240_000);
  let mint: any;
  let creatorAta: any;

  before(async () => {
    console.log("SETUP program " + PROGRAM.toString().slice(0, 8) + " wallet " + WALLET.toString().slice(0, 8));
    try {
      const ix = pg.program.idl.instructions[0];
      const flagged = (ix.accounts || []).filter((a: any) => a.seeds)
        .map((a: any) => a.name + "=" + JSON.stringify(a.seeds));
      console.log("IDLSEEDS " + (flagged.length ? flagged.join(" ; ") : "none declared in client IDL"));
    } catch (e) { console.log("IDLSEEDS unreadable: " + msg(e).slice(0, 70)); }

    const mintKp = web3.Keypair.generate();
    const rentMint = await pg.connection.getMinimumBalanceForRentExemption(82);
    const t1 = new web3.Transaction();
    t1.add(web3.SystemProgram.createAccount({
      fromPubkey: WALLET, newAccountPubkey: mintKp.publicKey,
      space: 82, lamports: rentMint, programId: TOKEN_PROG,
    }));
    t1.add(ixInitializeMint(mintKp.publicKey, WALLET));
    await sendTx(t1, [mintKp]);
    mint = mintKp.publicKey;

    creatorAta = await ataOf(WALLET, mint);
    const t2 = new web3.Transaction();
    t2.add(ixCreateAta(WALLET, WALLET, mint, creatorAta));
    t2.add(ixMintTo(creatorAta, FUND, mint, WALLET));
    await sendTx(t2, []);

    const r = await pg.connection.getTokenAccountBalance(creatorAta);
    const bal = Number(r.value.uiAmountString);
    console.log("SETUP mint ok, creator holds " + bal);
    if (bal !== 50) throw new Error("token plumbing wrong, creator holds " + bal);
  });

  it("case17 - same pair duels twice: nonce 0 then nonce 1", async () => {
    const opp = web3.Keypair.generate();
    const p0 = await challengePda(WALLET, opp.publicKey, 0);
    const p1 = await challengePda(WALLET, opp.publicKey, 1);
    if (p0.equals(p1)) throw new Error("nonce did not change the PDA");
    console.log("JS PDAs nonce0=" + p0.toString() + " nonce1=" + p1.toString());

    const d0 = await createDuel(0, opp.publicKey, p0, mint, creatorAta);
    console.log("PASS create nonce=0 accepted via " + d0.via);

    // same pair + same nonce again must be refused
    let dupRejected = false;
    try {
      await pg.program.methods.createChallenge(STAKE, LABEL, 1800, 3600, 0)
        .accounts({ challenge: d0.address, opponent: opp.publicKey, mint: mint,
          creatorToken: creatorAta, escrow: await ataOf(d0.address, mint) }).rpc();
    } catch (e) {
      dupRejected = true;
      console.log("DUPREFUSE acct=" + acctOf(e) + " | " + toksOf(e).join(" | "));
    }
    if (!dupRejected) throw new Error("re-create with the same nonce should have been refused");

    const d1 = await createDuel(1, opp.publicKey, p1, mint, creatorAta);
    console.log("PASS create nonce=1 accepted via " + d1.via + " (headline: re-duel works)");

    const c0 = await pg.program.account.challenge.fetch(d0.address);
    const c1 = await pg.program.account.challenge.fetch(d1.address);
    if (c0.status !== STATUS_CREATED || c1.status !== STATUS_CREATED) {
      throw new Error("both must be Created, saw " + c0.status + "/" + c1.status);
    }
    const n0 = c0.nonce && typeof c0.nonce.toNumber === "function" ? c0.nonce.toNumber() : Number(c0.nonce);
    const n1 = c1.nonce && typeof c1.nonce.toNumber === "function" ? c1.nonce.toNumber() : Number(c1.nonce);
    if (n0 !== 0 || n1 !== 1) throw new Error("stored nonces wrong: " + n0 + "/" + n1);
    console.log("PASS case17 HEADLINE - two distinct Created duels for one pair, nonces " + n0 + " and " + n1);
  });

  after(async () => {
    const sol = await pg.connection.getBalance(WALLET);
    console.log("DONE walletSOL=" + (sol / 1e9).toFixed(5));
  });
});
