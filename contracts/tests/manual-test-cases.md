# Volo Escrow — Manual Test Cases (Playground "Call" tab)

Program: `volo_challenge` (devnet). Run after Task 5.1 deploy.
All cases assume two funded devnet wallets (A = creator, B = opponent) with
USDC token accounts on the same mint, and `ORACLE_AUTHORITY` set to a third
wallet O you control in Playground's wallet selector.

Legend: PASS = instruction succeeds with expected state; FAIL = instruction must
be rejected with the listed error.

## Setup

- C1: Deploy program, note program id.
- C2: Set `declare_id!` and `ORACLE_AUTHORITY` (O) — redeploy if changed.

## Case 1: create_challenge happy path

- Accounts: challenge=PDA([b"challenge", A, B]), creator=A (signer, funded),
  opponent=B, mint=USDC, creator_token=A's USDC ATA, escrow=PDA ATA.
- Args: stake_amount=5000000, target_app="com.instagram.android",
  limit_seconds=1800, window_seconds=86400.
- Expected: PASS. Escrow balance = 5000000. Status=0 (Created), result=0.

## Case 2: create_challenge rejects zero stake

- Args: stake_amount=0. Expected: FAIL with `InvalidStake`.

## Case 3: create_challenge rejects >32 byte target_app

- Args: target_app = 33+ chars. Expected: FAIL with `TargetAppTooLong`.

## Case 4: join_challenge activates

- Signer B, opponent_token = B's USDC ATA, escrow as above.
- Args: none. Expected: PASS. Escrow = 10000000, status=1 (Active).

## Case 5: join_challenge from wrong signer

- Signer A (or any key != B). Expected: FAIL with `NotOpponent` / signature
  or constraint failure.

## Case 6: join_challenge when already Active

- Run join a second time. Expected: FAIL with `NotCreated`.

## Case 7: settle — creator PASS, opponent FAIL -> creator wins

- Signer O (oracle). Args: creator_result=1, opponent_result=2.
- Expected: PASS. Escrow drained to creator_token (+10000000),
  status=2 (Settled), result=1 (CreatorWon).

## Case 8: settle — both PASS -> both refunded

- Fresh challenge, joined (status=1). Args: 1, 1.
- Expected: status=2, result=3 (BothPass), creator gets +5000000,
  opponent gets +5000000.

## Case 9: settle — both FAIL (or UNVERIFIED) -> locked

- Fresh challenge, joined. Args: 2, 2 and separately 3, 3.
- Expected: status=4 (BothFailLocked), result=4 (BothFail), escrow untouched.

## Case 10: settle — creator FAIL, opponent PASS -> opponent wins

- Args: 2, 1. Expected: status=2, result=2 (OpponentWon),
  opponent_token +10000000.

## Case 11: settle rejected from non-oracle signer

- Signer A instead of O. Expected: FAIL with `NotOracle`.

## Case 12: settle rejected when not Active

- Challenge in Created (never joined). Args: 1, 2.
- Expected: FAIL with `NotActive`.

## Case 13: settle rejects bad result codes

- Args: creator_result=7. Expected: FAIL with `ResultOutOfRange`.

## Case 14: refund_unjoined after 1 hour

- Challenge in Created, clock > start_time + 3600s.
- Signer A. Expected: PASS. Escrow back to creator (+5000000),
  status=3 (Refunded).

## Case 15: refund_unjoined too early

- Challenge in Created, <1h old. Expected: FAIL with `TooEarlyToRefund`.

## Case 16: refund_unjoined after join (status Active)

- Signer A on an Active challenge. Expected: FAIL with `NotCreated`.

---

# v2 cases (17–24) — duel nonce, evidence hashes, treasury, reputation

v2 changes the PDA: challenge seeds are now
`[b"challenge", creator, opponent, nonce.to_le_bytes()]` with `nonce: u32`
little-endian, so EVERY v1-style PDA is a different address. Cases 17–24 assume
the v2 program is deployed and replace the v1 seed spelling everywhere.

## Case 17: second duel, same pair, nonce 1 succeeds where nonce 0 collided

- v2 Case 1's create with nonce=0, then create again with the same A, B and
  nonce=1. Under v1 the second create died with `AccountAlreadyInitialized`
  (same PDA); under v2 the nonce byte makes a fresh address.
- Args: stake_amount=5000000, target_app="com.instagram.android",
  limit_seconds=1800, window_seconds=3600, nonce=1.
- Expected: PASS. Two distinct challenge PDAs exist, both status=0.

## Case 18: settle stores both evidence hashes and they read back

- Challenge (nonce=0) created AND joined. Signer O.
- Args: creator_result=1, opponent_result=2,
  creator_evidence_hash = 32 bytes (e.g. the hex of a real evidence hash),
  opponent_evidence_hash = 32 zero bytes.
- Expected: PASS. Reading the challenge account: bytes at offset 179..211 =
  creator_evidence_hash, 211..243 = opponent_evidence_hash (zeros),
  offset 175..179 = nonce (LE), status=2.

## Case 19: settle with a nonzero evidence hash for a PASS succeeds

- Fresh joined challenge. Args: creator_result=1, opponent_result=2,
  creator_evidence_hash = nonzero 32 bytes.
- Expected: PASS (Case 18 is the same shape; this case pins that a PASS is
  allowed exactly when its hash is nonzero).

## Case 20: settle with a zero evidence hash for a PASS fails

- Fresh joined challenge. Args: creator_result=1, opponent_result=2,
  creator_evidence_hash = [0u8; 32].
- Expected: FAIL with `EvidenceZero`. Escrow untouched, status still 1.

## Case 21: settle with zero hashes for FAIL/UNVERIFIED succeeds

- Fresh joined challenge. Args: creator_result=3, opponent_result=3,
  both hashes = [0u8; 32].
- Expected: PASS. Status=4 (BothFailLocked), result=4, escrow untouched —
  a window with no verified evidence may not carry a hash claim.

## Case 22: resolve_lock before end_time + 3600 fails

- Challenge from Case 21 (status=4, BothFailLocked), less than one hour after
  end_time. Signer O. Args: none.
- Expected: FAIL with `TooEarlyToResolve`.

## Case 23: resolve_lock after the lock hour moves escrow to the treasury

- Same challenge, once clock > end_time + 3600. Signer O.
- The treasury ATA (associated token account of TREASURY for the challenge's
  mint) is passed and created if missing (payer = oracle).
- Expected: PASS. Escrow balance 0, treasury ATA balance = the full former
  escrow balance, status=5 (LockedResolved).

## Case 24: settle bumps Reputation for both parties

- Fresh joined challenge. Signer O. Args: creator_result=1, opponent_result=2,
  creator hash nonzero, opponent hash zero. The two Reputation PDAs
  ([b"reputation", creator] and [b"reputation", opponent]) passed as mut,
  init_if_missing, payer = oracle.
- Expected: PASS, and on the accounts: creator kept=1, current_streak=1,
  longest_streak=1, last_settled_at set; opponent broken=1, current_streak=0.
  A second settle (new challenge, same pair, creator PASS again) must give the
  creator kept=2, current_streak=2, longest_streak=2; an opponent FAIL on the
  same second duel keeps opponent current_streak=0, broken=2.
