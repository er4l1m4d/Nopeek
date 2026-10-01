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
