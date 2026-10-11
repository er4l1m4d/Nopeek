import { test } from "node:test";
import assert from "node:assert/strict";
import { isPublicRoute, authorizeRequest } from "./auth.ts";

/**
 * Task 7 Step 3: /enroll and /nonce must be reachable WITHOUT the shared secret, because
 * the device calls them and a secret in the APK is not a secret. Spec 7's trust inversion:
 * for the device-facing routes the attestation signature IS the authentication.
 *
 * Extracted from index.ts into a runtime-neutral module so this can be asserted at all:
 * index.ts is Deno-native and excluded from the typecheck project, so a rule that lives
 * only there is a rule with no test. The brief named this index.test.ts; the code under
 * test moved here so the test could exist.
 */

const SECRET = "test-oracle-secret";

test("enroll is reachable without the shared secret", () => {
  assert.equal(authorizeRequest("/enroll", null, SECRET), null, "no header, still allowed");
  assert.equal(authorizeRequest("/nonce", null, SECRET), null);
  assert.equal(authorizeRequest("/submit-report", null, SECRET), null, "the signature is the auth");
  assert.equal(authorizeRequest("/health", null, SECRET), null);
});

test("a wrong secret does not lock a device out of the public routes", () => {
  // The header is simply not consulted on these paths. Rejecting on a bad-but-irrelevant
  // header would make the app's enrollment fail in a way that looks like an attestation
  // problem, and the device has no way to report the difference.
  assert.equal(authorizeRequest("/enroll", "not-the-secret", SECRET), null);
  assert.equal(authorizeRequest("/nonce", "not-the-secret", SECRET), null);
});

test("settle and register-challenge still require the secret when one is configured", () => {
  const denied = authorizeRequest("/settle", null, SECRET);
  assert.notEqual(denied, null, "settle must not be public");
  assert.equal(denied?.status, 401);
  assert.equal(authorizeRequest("/register-challenge", null, SECRET)?.status, 401);

  assert.equal(authorizeRequest("/settle", SECRET, SECRET), null);
  assert.equal(authorizeRequest("/settle", "wrong", SECRET)?.status, 401);
});

test("the public set covers the evidence and reputation publishers by prefix", () => {
  // Spec 7 lists /evidence/<hash> and /reputation/<wallet> as unauthenticated reads. They
  // do not exist yet (Tasks 14 and 17); pinning the prefix here means adding those routes
  // cannot accidentally inherit the protected default.
  assert.ok(isPublicRoute("/evidence/7efb9ba42f7ec4c2"));
  assert.ok(isPublicRoute("/reputation/6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj"));
  assert.equal(isPublicRoute("/settle"), false);
  assert.equal(isPublicRoute("/"), false);
});

test("KNOWN GAP: with no secret configured, the protected routes are open to anyone", () => {
  // This is today's deployed state, not an endorsement. ORACLE_API_SECRET is unset on the
  // project, and the behaviour when it is unset is to allow - so /settle is currently
  // callable by whoever finds the URL. Pinned so the gap cannot be mistaken for enforced
  // auth and so fixing it is a visible decision rather than a silent one:
  //   - setting the secret is one `supabase secrets set`, but
  //   - the Kotlin app calls register-challenge and settle with NO secret today, so
  //     enforcing it would break the duel flow (Task 15) until those callers move behind
  //     the oracle itself.
  // Deciding that is a human call; it is recorded in the ledger, not assumed here.
  assert.equal(authorizeRequest("/settle", null, ""), null);
  assert.equal(authorizeRequest("/register-challenge", null, undefined), null);
});
