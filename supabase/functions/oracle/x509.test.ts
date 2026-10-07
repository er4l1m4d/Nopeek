import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  isSerialRevoked,
  readAttestationExtension,
  serialNumbers,
  verifyChainSignatures,
  verifyChainToRoot,
  verifyEcdsaSignature,
} from "./x509.ts";

const dir = new URL("./fixtures/", import.meta.url);
const deviceChainPem = readFileSync(new URL("device-chain.pem", dir), "utf8");
const pinnedRootPem = readFileSync(new URL("google-attestation-root.pem", dir), "utf8");
// A second, genuinely valid Google attestation root. This device's chain does NOT
// terminate here, so accepting it would prove the check is "is this any old Google
// root" rather than "does this chain end at the root we pinned".
const otherGoogleRootPem = readFileSync(
  new URL("google-attestation-root-ca1-edwards.pem", dir),
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
  const bytes = new TextEncoder().encode("nopeek evidence payload");
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

test("isSerialRevoked flags a batch attestation key Google has published as leaked", () => {
  const serials = serialNumbers(deviceChainPem);
  const middle = serials[1];
  assert.equal(isSerialRevoked(deviceChainPem, []), false);
  // Google's revocation entries are { issuer, serial } pairs; issuer is matched
  // case- and padding-insensitively, so test both the bare and 0-padded form.
  const padded = middle.startsWith("0") ? middle : `0${middle}`;
  for (const candidate of [middle, padded]) {
    assert.equal(
      isSerialRevoked(deviceChainPem, [{ issuer: "any", serial: candidate }]),
      true,
      `serial ${candidate} should have been flagged`,
    );
  }
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
