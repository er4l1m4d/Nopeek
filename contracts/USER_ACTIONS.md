# Volo — User Action Checklist (Phase 5: Solana Playground)

These steps require your browser/wallet — they cannot be automated. Everything
else for Phase 5 is already written locally.

## Before you start (5 min)

- [ ] **Free disk space on C:** (only ~0.7 GB free; local `cargo check` was blocked).
      Anchor's dependency tree needs several GB if you ever build locally.

## 5.1 Steps 1–2: Create the program (browser)

- [ ] Go to https://solana-playground.com (plan's `solanapayground.com` is a typo), select **devnet**.
- [ ] New Project → From Scratch → name `volo_challenge`.
- [ ] Paste `contracts/program/src/lib.rs` over the boilerplate.
- [ ] Keep Playground's generated `declare_id!` (replace the placeholder line).
- [ ] Replace `ORACLE_AUTHORITY` with your oracle wallet pubkey
      (from `devnet-wallet-info.txt`, or any keypair you'll sign settle with).

## 5.1 Steps 7–8: Deploy + export IDL

- [ ] Build and Deploy (needs devnet SOL — if airdrop is still rate-limited, try
      https://faucet.solana.com or the Playground wallet's airdrop button).
- [ ] Save program id to `contracts/DEPLOYED_PROGRAM_ID.txt`.
- [ ] Export IDL from Playground → save as `contracts/program/idl-from-playground.json`.
- [ ] **Diff it against `contracts/program/idl.json`** (hand-authored, Row 4) —
      if they differ, the Playground one wins; tell the agent to update ours.

## 5.2 Step 2: Execute manual tests (browser)

- [ ] Run the 16 cases in `contracts/tests/manual-test-cases.md` via the Call tab.
- [ ] Report any FAIL that isn't listed as an expected rejection.

## Phase 6: Supabase oracle (6.1 Steps 4–5 + 6.2 Step 3)

- [ ] Create the Supabase project (0.4 — https://supabase.com, free tier).
- [ ] SQL Editor → paste `supabase/migrations/20261001_volo_init.sql` → Run.
- [ ] Settings → Functions → Environment: set
      `SUPABASE_URL = https://<ref>.supabase.co`,
      `SUPABASE_SERVICE_ROLE_KEY = <service-role key>`,
      `ORACLE_API_SECRET = <random string>` (function rejects requests missing
      header `x-oracle-secret` once this is set — remove it if you want it open).
- [ ] Functions → `oracle` → deploy `supabase/functions/oracle/` (Dashboard
      "Deploy from source" or `supabase functions deploy oracle`). Also disable
      "Verify JWT" for this function (the app sends its own secret header, not a
      Supabase JWT).
- [ ] Test: `curl https://<ref>.supabase.co/functions/v1/oracle/health`
      Expected: `{"status":"alive", ...}` (add `-H "x-oracle-secret: ..."` if set).
- [ ] 6.2 Step 3 test: call `/register-challenge` with a fake base58 challenge
      pubkey, then `/submit-report` with known `usedSeconds` above/below limit,
      then `/settle` — check `settlement_results` row matches the matrix in
      `contracts/tests/manual-test-cases.md` (cases 7–10).

## Phase 8: settlement flow (8.1 Step 4 + on-device verification)

- [ ] Configure the app backend: `app/src/main/java/com/volo/app/data/usage/OracleReportApi.kt`
      -> `VoloBackend.SUPABASE_URL` (your project ref) and `VoloBackend.SUPABASE_ANON_KEY`
      (Settings -> API keys -> anon public). Rebuild after editing.
- [ ] 8.1 Step 4: run the app through a challenge window end (or trigger the
      UsageWorker), then check the `usage_reports` table has the new row.
- [ ] On-device settle: after a challenge window ends, open the Challenges tab —
      an ended ACTIVE row shows a **Settle** button; tap it and approve the
      transaction in the wallet. Expected: row flips to SETTLED and
      `settlement_results` has a row.
- [ ] Both-fail path: submit reports above limit from BOTH sides (or skip both),
      then settle — expected: red **LOCKED** chip; tapping the row opens the
      Recovery Lock screen.
- [ ] Tamper path: revoke Usage Access mid-window — expected: challenge row gets
      a red VERIFY chip (STATUS_PENDING_VERIFICATION).

## Phase 11: demo day (11.1 Steps 3–4 + 11.2)

- [ ] Fund Wallet A (creator): devnet SOL + ≥ 2 USDC in Phantom (Wallet A = your
      connected wallet). Fund Wallet B (opponent, second Phantom account): devnet
      SOL + ≥ 1 USDC. No second person needed.
- [ ] 11.1 Step 3 scenario: run 55 s (PASS) vs 70 s (FAIL) per
      `assets/demo-script.md`; expected: automatic settlement, User A +2 USDC.
- [ ] 11.1 Step 4: tap **Settle** on the ended row → approve in wallet →
      expected: balance up, row shows SETTLED.
- [ ] 11.2 Steps 1–2: record the 3-minute demo following `assets/demo-script.md`.
- [ ] 11.2 Step 4: upload video (Unlisted) + deck PDF; save links to
      `submission-links.txt`.

## Later phases also gated on you (heads-up)

- Phase 0.2–0.3: devnet SOL airdrop + USDC faucet (plan's Goerli steps are obsolete).
- Phase 0.4: create the Supabase project + enable auth.
- Phase 6.1 Steps 4–5: set function env vars + deploy oracle function.
- Device tests: 2.2 Step 4 (Phantom connect), 3.1 Step 3 (permission toggle),
  3.2 Step 5 (usage seconds), 4.2 Step 3 (persistence).
