# Volo — 3-Minute Demo Recording Script

Plan 11.2 Steps 1–2 (recording setup + beats). Record in one take at 1080p;
enable "Screen recording" in Developer Options or use
Android Studio → Tools → Screen Record.

## Setup (before rolling)

- Phantom (or Solflare) on **devnet**, Wallet A funded with SOL + ≥ 2 USDC.
- Wallet B created/funded in the same wallet app (second account) — no second
  person needed.
- Usage Access granted to Volo (Settings → Apps → Special access → Usage access).
- Demo challenge seeded (first dev-build launch) OR create one fresh on camera.

## The run (3:00)

| # | Time | Beat |
|---|------|------|
| 1 | 0:00–0:25 | **Connect wallet** — Profile tab → Connect → approve in Phantom → show wallet address + SOL/USDC/SKR balances + SKR gate badge. |
| 2 | 0:25–0:55 | **Create challenge** — Create tab → target app (TikTok) → limit 1 min → stake 1 USDC → opponent = Wallet B's address → Create → approve tx in Phantom. Show the new row on the Challenges tab. |
| 3 | 0:55–1:40 | **Usage engine** — open the target app; show the Usage engine counting seconds (Activity tab). Creator stops at ~55 s (PASS); opponent runs to ~70 s (FAIL). |
| 4 | 1:40–2:25 | **Oracle settles** — after the window: usage report auto-submits; the settle flow runs (tap **Settle** on the ended row → approve in Phantom). Show `settlement_results` in Supabase (or the SETTLED chip). Creator wins 2 USDC. |
| 5 | 2:25–3:00 | **Proof + wrap** — tap the signature → open Solscan deep link on devnet; end on the Challenges tab showing the SETTLED chip and the Profile balances. |

## Both-fail alt beat (optional b-roll)

Run both wallets past the limit → row flips to red **LOCKED** → tap →
**Recovery Lock** screen (plan 8.2 Step 3).

## 11.1 Step 3 scenario reference

- User A: 55 s usage → PASS
- User B: 70 s usage → FAIL (limit 60 s)
- Settlement: A wins 2 USDC (1 USDC from each side).
