import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  describeChain,
  isSerialRevoked,
  makeNobleEcdsaVerifier,
  parseRevokedFeed,
  PINNED_ATTESTATION_ROOT_PEM,
  readAttestationExtension,
  serialNumbers,
  verifyChainSignatures,
  verifyChainToPinnedRoot,
  verifyChainToRoot,
  verifyEcdsaSignature,
  type ChainLink,
  type RevokedEntry,
} from "./x509.ts";

const dir = new URL("./fixtures/", import.meta.url);
const deviceChainPem = readFileSync(new URL("device-chain.pem", dir), "utf8");
const pinnedRootPem = readFileSync(new URL("google-attestation-root.pem", dir), "utf8");
// A second, genuinely valid Google attestation root (self-signed, ECDSA secp384r1 -
// identified by name only so no reader assumes Ed448 from the filename). This device's
// chain does NOT terminate here, so accepting it would prove the check was "is this any
// old Google root" rather than "does this chain end at the root we pinned".
const otherGoogleRootPem = readFileSync(
  new URL("google-attestation-root-ca1-p384.pem", dir),
  "utf8",
);
const enrolledSpkiB64 = readFileSync(new URL("device-spki.b64", dir), "utf8").trim();

/**
 * Values below are the ones AOSP's KeyDescription schema assigns. Cited rather than
 * recalled, because misreading SecurityLevel as { Software(1) } once nearly caused a
 * downgrade of the project's central claim:
 *   SecurityLevel   ::= ENUMERATED { Software(0), TrustedEnvironment(1), StrongBox(2) }
 *   VerifiedBootState ::= ENUMERATED { Verified(0), SelfSigned(1), Unverified(2) }
 */
test("reads every asserted field out of the captured device chain", async () => {
  const info = await readAttestationExtension(deviceChainPem);

  assert.equal(info.securityLevel, "TrustedEnvironment");
  assert.equal(info.deviceLocked, true);
  assert.equal(info.verifiedBootState, "Verified");
  assert.equal(info.applicationPackageName, "com.nopeek.app");
  assert.equal(
    Buffer.from(info.attestationChallengeB64, "base64").toString("utf8"),
    "nopeek-s1-probe",
  );
  // The leaf's SPKI must be the one enrolled on device, or the oracle would be
  // checking a signature made by some other key.
  assert.equal(info.leafSpkiB64, enrolledSpkiB64);
  // attestationApplicationId carries the APK signing-cert digest (SHA-256 -> 44 b64 chars).
  assert.equal(info.signingCertDigestB64.length, 44);
  assert.match(info.rootIssuerDn, /f92009e853b6b045/);
});

test("the device chain's own signatures verify", async () => {
  assert.equal(await verifyChainSignatures(deviceChainPem), true);
});

test("verifyChainToRoot accepts only the pinned root this chain ends at", async () => {
  assert.equal(await verifyChainToRoot(deviceChainPem, pinnedRootPem), true);
  assert.equal(await verifyChainToRoot(deviceChainPem, otherGoogleRootPem), false);
});

test("one flipped byte inside the leaf certificate breaks chain verification", async () => {
  // Tampering has to be detected by *signature* verification. If the code only
  // compared issuer/subject names or lengths, a mutated certificate would sail
  // through and every claim downstream would be decoration.
  const tampered = tamperWithLeafPublicKey(deviceChainPem);
  assert.notEqual(tampered, deviceChainPem, "tamper fixture did not change anything");
  assert.equal(await verifyChainSignatures(tampered), false);
  assert.equal(await verifyChainToRoot(tampered, pinnedRootPem), false);
});

test("verifyEcdsaSignature accepts the signature it made and rejects mutated bytes", async () => {
  // Exercised with a locally generated key pair so the primitive is proven
  // independently of the captured device data.
  const { publicKey, privateKey } = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign", "verify"],
  );
  const spkiB64 = Buffer.from(await crypto.subtle.exportKey("spki", publicKey)).toString("base64");
  // Explicit ArrayBuffer-backed view: TextEncoder.encode() is typed as returning
  // Uint8Array<ArrayBufferLike>, and WebCrypto's BufferSource requires a plain
  // ArrayBuffer one — SharedArrayBuffer would not be accepted at runtime here.
  const bytes = new Uint8Array(new TextEncoder().encode("nopeek evidence payload"));
  const signature = new Uint8Array(await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    privateKey,
    bytes,
  ));

  assert.equal(await verifyEcdsaSignature(spkiB64, bytes, Buffer.from(signature).toString("base64")), true);

  const mutated = new Uint8Array(bytes);
  mutated[mutated.length - 1] ^= 0x01;
  assert.equal(await verifyEcdsaSignature(spkiB64, mutated, Buffer.from(signature).toString("base64")), false);

  const otherKey = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"],
  );
  const otherSpki = Buffer.from(await crypto.subtle.exportKey("spki", otherKey.publicKey)).toString("base64");
  assert.equal(await verifyEcdsaSignature(otherSpki, bytes, Buffer.from(signature).toString("base64")), false);
});

test("every certificate in the chain exposes a serial for revocation checking", () => {
  const serials = serialNumbers(deviceChainPem);
  assert.equal(serials.length, 4);
  serials.forEach((s) => assert.match(s, /^[0-9a-f]+$/));
});

test("isSerialRevoked flags a batch attestation key Google published as leaked", async () => {
  const links = await describeChain(deviceChainPem);
  assert.equal(isSerialRevoked(deviceChainPem, []), false);

  const revoked: RevokedEntry[] = [
    { issuer: links[1].issuerDn, serial: links[1].serialHex },
  ];
  assert.equal(isSerialRevoked(deviceChainPem, revoked), true);

  // Google's published serials are not always formatted the way our parser renders
  // them, so padding, case, a 0x prefix and stray spaces must all still match.
  const serial = links[1].serialHex;
  for (const variant of [serial.toUpperCase(), `0${serial}`, `0x${serial}`, `  ${serial} `]) {
    assert.equal(
      isSerialRevoked(deviceChainPem, [{ issuer: links[1].issuerDn, serial: variant }]),
      true,
      `formatting variant "${variant}" should still be recognised`,
    );
  }
});

test("isSerialRevoked requires the issuer to match, not the serial alone", async () => {
  const links = await describeChain(deviceChainPem);
  // When a caller DOES supply an issuer, it has to agree. Serial uniqueness is only
  // guaranteed per issuer, so a serial matched against the wrong CA would mark a genuine,
  // locked, verified-boot device revoked - and a revoked device is UNVERIFIED, which under
  // the fail-closed rule forfeits an honest user's stake. Google's own feed supplies no
  // issuer at all, which is why this field is optional; the device-side protection for that
  // case is the leaf exclusion, tested separately below.
  assert.equal(
    isSerialRevoked(deviceChainPem, [{ issuer: "CN=Unrelated CA", serial: links[1].serialHex }]),
    false,
    "a real serial under a different issuer must not make this chain revoked",
  );
  // The near-miss that matters: a real serial paired with a different certificate's issuer.
  assert.equal(
    isSerialRevoked(deviceChainPem, [{ issuer: links[2].issuerDn, serial: links[1].serialHex }]),
    false,
    "serial of one certificate with another's issuer must not match",
  );
  // Whitespace and case in the published issuer string must not break matching.
  assert.equal(
    isSerialRevoked(deviceChainPem, [
      { issuer: `  ${links[1].issuerDn.toUpperCase()} `, serial: links[1].serialHex },
    ]),
    true,
  );
});

test("a revoked serial is recognised in whichever base Google's feed writes it in", async () => {
  const links = await describeChain(deviceChainPem);
  const hex = links[1].serialHex;
  const decimal = BigInt(`0x${hex}`).toString(10);
  // Measured on the live feed and pinned in fixtures/revoked-feed-shape.json: 979 keys are
  // 17-20 digits with no letters, 780 are 30-32 characters containing a-f. The feed mixes
  // bases. Reading it as one base only leaves the other group permanently un-compared, and
  // that fails OPEN - a published leaked batch key would keep verifying forever.
  for (const form of [hex, decimal, `0x${hex}`, `  ${decimal} `]) {
    assert.equal(
      isSerialRevoked(deviceChainPem, [{ serial: form }]), true,
      `form "${form}" must be recognised`,
    );
  }
  // Neither reading may become a wildcard: an unrelated number must not match under either.
  assert.equal(isSerialRevoked(deviceChainPem, [{ serial: "9999999999999999999" }]), false);
});

test("an issuer-less feed entry revokes by serial, and the leaf is still excluded", async () => {
  const links = await describeChain(deviceChainPem);
  // Google's entries carry no issuer field at all (measured). Requiring one - which is what
  // the issuer-AND-serial rule did when the feed turned out not to provide it - makes every
  // comparison fail and silently disables revocation for all 1759 published keys. The
  // protection the issuer was there for comes from excluding the leaf instead.
  assert.equal(isSerialRevoked(deviceChainPem, [{ serial: links[1].serialHex }]), true);
  // The leaf's serial is "01", one of the most common serials in existence: a CA-level entry
  // must never be compared against it, or an honest device loses its stake on a coincidence.
  assert.equal(isSerialRevoked(deviceChainPem, [{ serial: links[0].serialHex }]), false);
});

test("parseRevokedFeed reads Google's envelope and fails closed on anything else", () => {
  const feed = JSON.parse(readFileSync(new URL("revoked-feed-shape.json", dir), "utf8"));
  const parsed = parseRevokedFeed(feed);
  assert.equal(parsed?.length, 4, "the captured slice held four REVOKED entries");
  assert.ok(parsed?.every((entry) => entry.issuer === undefined), "nothing invented for a field the feed lacks");
  assert.equal(
    isSerialRevoked(deviceChainPem, parsed ?? []), false,
    "the live slice must not revoke our own chain - it does not contain its serials",
  );

  // A shape we do not recognise is not the same as an empty feed: [] means Google says
  // nothing is revoked today, null means we cannot tell, and only the second fails closed.
  assert.equal(parseRevokedFeed({ serials: {} }), null, "the previously guessed key was never Google's shape");
  assert.equal(parseRevokedFeed({}), null);
  assert.equal(parseRevokedFeed(null), null);
  assert.deepEqual(parseRevokedFeed({ entries: {} }), []);
  // A digits-only key is ambiguous, so BOTH readings are honoured - see the base test below.
  assert.deepEqual((parseRevokedFeed({ entries: { "123": { status: "PENDING" } } }) ?? []).map((e) => e.serial), ["123"]);

  // Loud on garbage inside a recognised envelope, so a format change cannot pass quietly.
  assert.throws(() => parseRevokedFeed({ entries: { "123": {} } }), /status/);
  assert.throws(() => parseRevokedFeed({ entries: { "0z9": { status: "REVOKED" } } }), /serial/);
});

test("every entry in the feed disqualifies a key, whatever status Google attached to it", () => {
  // Presence in this feed is the signal: Google has stopped trusting that attestation key.
  // Its vocabulary is not one-valued - SUSPENDED means temporary removal, and a key taken
  // out of service is not a key to settle money on. Filtering to status === "REVOKED" drops
  // those silently, which is a FALSE ACCEPT: a report signed on a flagged key would go on to
  // settle. The asymmetry is what gave it away - a missing status threw loudly while an
  // unrecognised one passed quietly.
  const batchCert = "137778401798951626961013231794950430006"; // certs[1], as Google writes decimals
  const parsed = parseRevokedFeed({
    entries: { [batchCert]: { status: "SUSPENDED", reason: "KEY_COMPROMISE" } },
  });
  assert.equal(parsed?.length, 1, "a suspended key is still a key we must not trust");
  assert.equal(isSerialRevoked(deviceChainPem, parsed ?? []), true);

  // Whitespace in a published status must not become a silent pass either.
  const padded = parseRevokedFeed({ entries: { [batchCert]: { status: " REVOKED " } } });
  assert.equal(isSerialRevoked(deviceChainPem, padded ?? []), true, "' REVOKED ' is REVOKED");
});

test("the pure-JS adapter asks noble with the options that make verification correct", async () => {
  // noble 2.4.0 ignores UNKNOWN option keys without complaint, so a typo in `lowS` or a
  // renamed `format` would leave production silently using noble's defaults - and every
  // current fixture would still verify, because Google's captured signatures happen to be
  // low-S. Asserting the call itself is the only way to see that. prehash:false and
  // format:"compact" are also pinned here rather than trusted.
  const calls: Array<Record<string, unknown>> = [];
  const spy = {
    verify: (signature: Uint8Array, message: Uint8Array, publicKey: Uint8Array, opts?: Record<string, unknown>) => {
      calls.push({
        sigLen: signature.length, msgLen: message.length, pubLen: publicKey.length, ...(opts ?? {}),
      });
      return true;
    },
  };
  // Only P-256 is supplied, so the chain stops at the next link: exactly one call expected.
  const ok = await verifyChainSignatures(deviceChainPem, makeNobleEcdsaVerifier({ "P-256": spy }));
  assert.equal(calls.length, 1, "the P-256 link reached the curve instance");
  assert.equal(ok, false, "a link with no supplied implementation must fail closed, not fall back to the host");

  assert.equal(calls[0].prehash, false, "digest comes from the certificate's own hash OID, not the curve's default");
  assert.equal(calls[0].lowS, false, "noble's default rejects high-S, which CA signatures may legitimately carry");
  assert.equal(calls[0].format, "compact");
  assert.equal(calls[0].pubLen, 65, "uncompressed P-256 point: 0x04 || X || Y");
  assert.equal(calls[0].sigLen, 64, "fixed-width r||s, converted from DER by x509");
  assert.equal(calls[0].msgLen, 32, "a SHA-256 digest was handed over, not the raw TBS bytes");
});

test("isSerialRevoked rejects an unparseable serial rather than silently ignoring it", async () => {
  const links = await describeChain(deviceChainPem);
  const issuer = links[1].issuerDn;
  // Google's feed is external input. If its format ever changes, an entry this parser
  // cannot understand must fail loudly: treating it as "does not match" is how a leaked
  // attestation key keeps verifying forever. Note a purely decimal string is still
  // legal hex, so it is not a valid rejection case; only non-hex characters are.
  assert.throws(
    () => isSerialRevoked(deviceChainPem, [{ issuer, serial: "ff:aa" }]),
    /hex/,
  );
  assert.throws(
    () => isSerialRevoked(deviceChainPem, [{ issuer, serial: "1234567890g" }]),
    /hex/,
  );
});

test("a chain with no attestation extension is refused rather than read as compliant", async () => {
  // The strictness rule that matters here is direction: a chain that cannot supply
  // RootOfTrust must raise, never fall back to deviceLocked=true. The pinned root is
  // a self-signed CA certificate with no such extension, so it is a case I can build
  // without forging anything.
  await assert.rejects(
    () => readAttestationExtension(singleCertPem(pinnedRootPem)),
    /x509:/,
  );
  // And a chain with no certificates at all must raise a domain error, not a TypeError
  // about reading 'der' of undefined, which the handler would surface as a 500.
  await assert.rejects(() => readAttestationExtension("not a pem"), /x509:/);
});

test("describeChain reports a reordered chain as broken", async () => {
  // Its doc comment promises that issuer lookup by DN, rather than by position,
  // surfaces a reordered chain. If the Evidence screen shows all-green for bytes the
  // settlement path rejected, the public re-verification story contradicts itself.
  const reordered = reorderChain(deviceChainPem, [0, 2, 1, 3]);
  assert.equal(await verifyChainSignatures(reordered), false);
  const links: ChainLink[] = await describeChain(reordered);
  assert.ok(
    links.some((link) => !link.linkIsNextCertificate || !link.signatureValid),
    "describeChain must not report every link valid for a chain verifyChainSignatures rejects",
  );
});

test("the pinned root is bound in code, not passed in by a caller", async () => {
  // verifyChainToRoot(chain, rootPem) trusts whatever root the caller supplies. One
  // wiring slip in the oracle - root from the request body, or from the device's own
  // enrolled chain - collapses the whole design into the self-consistency check the
  // plan rejected as ledger Row 9. The pinned bytes must live in the module.
  assert.ok(PINNED_ATTESTATION_ROOT_PEM.includes("BEGIN CERTIFICATE"));
  // The fixture the tests read and the constant the code trusts must be one document.
  assert.equal(
    PINNED_ATTESTATION_ROOT_PEM.replace(/\s+/g, ""),
    pinnedRootPem.replace(/\s+/g, ""),
    "pinned constant drifted from fixtures/google-attestation-root.pem",
  );
  assert.equal(await verifyChainToPinnedRoot(deviceChainPem), true);
  assert.equal(await verifyChainToPinnedRoot(otherGoogleRootChainSubstitute()), false);
});

/** Builds a chain that ends at the *other* valid Google root, to prove pinning bites. */
function otherGoogleRootChainSubstitute(): string {
  return deviceChainPem + "\n" + otherGoogleRootPem;
}

function reorderChain(pem: string, order: number[]): string {
  const blocks = [...pem.matchAll(/-----BEGIN CERTIFICATE-----\s*([\s\S]*?)\s*-----END CERTIFICATE-----/g)]
    .map((m) => m[1]);
  return order.map((i) => `-----BEGIN CERTIFICATE-----\n${blocks[i]}\n-----END CERTIFICATE-----`).join("\n") + "\n";
}

/** First certificate only - used to present a chain that carries no attestation. */
function singleCertPem(pem: string): string {
  const match = /-----BEGIN CERTIFICATE-----\s*([\s\S]*?)\s*-----END CERTIFICATE-----/.exec(pem);
  assert.ok(match, "fixture had no certificate");
  return `-----BEGIN CERTIFICATE-----\n${match![1]}\n-----END CERTIFICATE-----\n`;
}

/**
 * The seam that exists because Supabase's edge runtime (Deno 2.1.4) throws
 * `DOMException: Not implemented` for crypto.subtle.verify on P-384. Measured per-link on
 * the deployed function: leaf←intermediate (P-256) verifies there, intermediate←root
 * (P-384) does not. So ECDSA verification has to be injectable, letting production supply
 * a pure-JS implementation while the default stays WebCrypto.
 *
 * The captured chain contributes exactly two ECDSA links - the third is the root's
 * RSA-SHA256 self-signature, which is not routed through this seam. Asserting the ordered
 * list, rather than "was called", is what pins the seam to the real fixture: a chain whose
 * P-384 link silently kept going to WebCrypto would show up as a missing entry.
 */
test("chain signature verification can route ECDSA through an injected verifier", async () => {
  const seen: string[] = [];
  const ok = await verifyChainSignatures(deviceChainPem, async ({ namedCurve }) => {
    seen.push(namedCurve);
    return true;
  });
  assert.deepEqual(seen, ["P-256", "P-384"]);
  assert.equal(ok, true, "every link verified: two injected ECDSA links plus the RSA root");
});

test("an injected verifier that rejects the P-384 link fails the whole chain", async () => {
  // The seam must be load-bearing, not decorative. Denying only the link the failing
  // runtime cannot check is the precise condition production has to react to.
  const ok = await verifyChainSignatures(
    deviceChainPem,
    async ({ namedCurve }) => namedCurve !== "P-384",
  );
  assert.equal(ok, false);
});

test("the production pure-JS verifier accepts the real chain and rejects the tampered one", async () => {
  // This is the verifier index.ts binds in Deno, where crypto.subtle.verify throws
  // `Not implemented` for P-384. Constructed here from the npm devDependency so the point
  // extraction, the digest, and the option choices are proven under Node against the SAME
  // fixture WebCrypto verifies: two independent implementations of the same link have to
  // agree, or one of them is wrong about a real Google signature.
  //
  // Only the import line differs per runtime — makeNobleEcdsaVerifier holds the glue, so
  // the tested code and the deployed code cannot quietly diverge.
  const { p256, p384, p521 } = await import("@noble/curves/nist.js");
  const noble = makeNobleEcdsaVerifier({ "P-256": p256, "P-384": p384, "P-521": p521 });

  assert.equal(
    await verifyChainSignatures(deviceChainPem, noble), true,
    "pure-JS verification must reproduce the WebCrypto result on the captured chain",
  );
  assert.equal(await verifyChainToPinnedRoot(deviceChainPem, noble), true);
  // Not a rubber stamp: a leaf public key altered by one byte breaks the signature the
  // intermediate made over it, and this path has to notice.
  assert.equal(await verifyChainSignatures(tamperWithLeafPublicKey(deviceChainPem), noble), false);
});

/**
 * Flips one byte inside the leaf's P-256 public key. Observed DER (probe-cert.mjs):
 * subjectPublicKeyInfo holds `03 42 00 04 <64 coordinate bytes>` - a BIT STRING of
 * 66 content bytes: unused-bits, the 0x04 uncompressed-point tag, then X and Y.
 * Structure survives so the parser still reads the certificate; the issuer's
 * signature over it cannot.
 */
function tamperWithLeafPublicKey(chainPem: string): string {
  const blocks = [...chainPem.matchAll(/-----BEGIN CERTIFICATE-----\s*([\s\S]*?)\s*-----END CERTIFICATE-----/g)]
    .map((m) => Buffer.from(m[1].replace(/\s+/g, ""), "base64"));
  const leaf = blocks[0];
  const marker = Buffer.from([0x03, 0x42, 0x00, 0x04]);
  let at = -1;
  for (let i = 0; i + marker.length < leaf.length; i++) {
    if (
      leaf[i] === marker[0] && leaf[i + 1] === marker[1] &&
      leaf[i + 2] === marker[2] && leaf[i + 3] === marker[3]
    ) { at = i; break; }
  }
  assert.ok(at > 0, "no P-256 public key BIT STRING found in the leaf certificate");
  // +6 lands inside the X coordinate, past the BIT STRING header and point tag.
  leaf[at + marker.length + 2] ^= 0xff;

  const rebuilt = [leaf, ...blocks.slice(1)]
    .map((der) => {
      const b64 = der.toString("base64").replace(/(.{64})/g, "$1\n").replace(/\n$/, "");
      return `-----BEGIN CERTIFICATE-----\n${b64}\n-----END CERTIFICATE-----`;
    })
    .join("\n");
  return rebuilt + "\n";
}
