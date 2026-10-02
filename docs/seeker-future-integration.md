# Seeker Identity — Future Integration

Plan Phase 10 ships `MockSeekerProvider` only (no Seeker hardware in scope).
This document defines the replacement path.

## 1. Replace MockSeekerProvider with SeedVaultIdentityProvider

- Implement `SeekerIdentityProvider` (`app/src/main/java/com/volo/app/data/identity/IdentityProvider.kt`)
  backed by the Solana Mobile Seed Vault identity APIs.
- Swap the default in `WalletManager.identityProvider` (currently
  `MockSeekerProvider`) — ideally via a small DI module (Hilt/Koin) once the
  project adopts one; the field already exists for runtime replacement.
- `verifyGenesisToken(wallet)` should return `IdentityVerificationResult.VerifiedSeeker`
  only when hardware-backed attestation succeeds; keep `Error` for failures and
  `Unverified` as the safe default.

## 2. Genesis Token verification via solana-mobile SDK

- Use the Seeker Genesis Token (non-transferable NFT proving device ownership)
  queried from the connected wallet's RPC:
  - resolve the wallet's token accounts, find the Genesis Token mint,
  - verify the mint + metadata against Solana Mobile's published collection.
- Cache the result per wallet address; re-verify on wallet switch.
- Until then `MockSeekerProvider.verifyGenesisToken` always returns `Unverified`
  and the Profile badge reads "Not Verified (requires Seeker hardware)".

## 3. Secure Element signing for high-security features

- Route high-value operations (settlement oracle signing, duels with large
  stakes) through Secure Element–backed keys when available, instead of the
  in-memory Mobile Wallet Adapter path.
- Keep `WalletManager.signTransaction` as the single signing entry point so the
  Secure Element implementation can be swapped in behind `SignOutcome`.

## 4. .skr domain resolution

- `MockSeekerProvider.resolveDomain` serves the demo handles `alice.skr` and
  `bob.skr` (fixed 32-byte addresses; see the class KDoc for the plan-text
  correction). Unknown handles fall back to the deterministic SHA-256 mock in
  `parseOpponent`.
- Real resolution: Seeker ID / `.skr` domains via Solana Mobile's resolver —
  replace `resolveDomain` with an RPC-backed lookup and delete the SHA fallback.
