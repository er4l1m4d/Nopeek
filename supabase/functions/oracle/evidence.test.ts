import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import {
  decodeEvidence,
  encodeEvidence,
  evidenceHash,
  type EvidenceFields,
} from "./evidence.ts";

const vector = JSON.parse(
  readFileSync(new URL("./fixtures/evidence-vector.json", import.meta.url), "utf8"),
);

function vectorFields(): EvidenceFields {
  return vector.fields as EvidenceFields;
}

test("encodeEvidence matches the shared vector byte for byte", () => {
  const hex = Buffer.from(encodeEvidence(vectorFields())).toString("hex");
  assert.equal(
    hex,
    vector.expectedHex,
    "encoder output diverged from the pinned vector — the two languages no longer agree",
  );
});

test("decodeEvidence round-trips encodeEvidence", () => {
  assert.deepEqual(decodeEvidence(encodeEvidence(vectorFields())), vectorFields());
});

test("evidenceHash hashes the exact bytes handed to it, not a re-encode", async () => {
  // Arbitrary bytes that are NOT a valid canonical payload. If evidenceHash
  // re-encoded or normalized anything before hashing, this would not match.
  const arbitrary = new Uint8Array([0, 1, 2, 255, 65, 66, 67, 128]);
  assert.equal(
    await evidenceHash(arbitrary),
    createHash("sha256").update(arbitrary).digest("hex"),
  );
  assert.equal(await evidenceHash(encodeEvidence(vectorFields())), vector.expectedHash);
});

test("tamper signals of 0, 1 and 3 entries all survive the round trip", () => {
  for (const signals of [[], ["PERMISSION_REVOKED"], ["CLOCK_CHANGED", "REPORT_LATE", "APP_UNINSTALLED"]]) {
    const f = { ...vectorFields(), tamperSignals: signals };
    assert.deepEqual(decodeEvidence(encodeEvidence(f)).tamperSignals, signals);
  }
});

test("changing one field changes every byte-position-sensitive hash", async () => {
  const base = encodeEvidence(vectorFields());
  const mutated = encodeEvidence({ ...vectorFields(), usedSeconds: 43 });
  assert.notDeepEqual(Array.from(base), Array.from(mutated));
  assert.notEqual(await evidenceHash(base), await evidenceHash(mutated));
});

test("a non-ASCII package name is rejected, not silently re-encoded", () => {
  // Java maps an unpaired/non-ASCII char to "?" and TextEncoder to U+FFFD, so the
  // two languages would produce different bytes and different hashes for the same
  // logical field. Every real field is base58, hex, base64 or an Android package
  // name — all ASCII — so the format refuses anything else rather than guessing.
  assert.throws(() => encodeEvidence({ ...vectorFields(), targetPackage: "com.exämple.app" }));
});

test("decodeEvidence reads the pinned vector bytes back into named fields", () => {
  const bytes = new Uint8Array(Buffer.from(vector.expectedHex, "hex"));
  const f = vector.fields as Record<string, unknown>;
  const d = decodeEvidence(bytes);
  // Field by field, so a future reordering fails as a named field rather than as
  // an opaque blob mismatch that a symmetric swap would keep green.
  assert.equal(d.version, f.version);
  assert.equal(d.challengePubkey, f.challengePubkey);
  assert.equal(d.walletPubkey, f.walletPubkey);
  assert.equal(d.packageName, f.packageName);
  assert.equal(d.targetPackage, f.targetPackage);
  assert.equal(d.windowStartMs, f.windowStartMs);
  assert.equal(d.windowEndMs, f.windowEndMs);
  assert.equal(d.usedSeconds, f.usedSeconds);
  assert.equal(d.deviceActiveSeconds, f.deviceActiveSeconds);
  assert.equal(d.eventCount, f.eventCount);
  assert.equal(d.eventSetDigestHex, f.eventSetDigestHex);
  assert.deepEqual(d.tamperSignals, f.tamperSignals);
  assert.equal(d.nonceB64, f.nonceB64);
});

test("encoder refuses tamper signals that would collide after joining", () => {
  // ["A,B"] and ["A","B"] joined are the same bytes, so two distinct reports would
  // share one evidence hash.
  assert.throws(() => encodeEvidence({ ...vectorFields(), tamperSignals: ["A,B"] }));
  assert.throws(() => encodeEvidence({ ...vectorFields(), tamperSignals: [""] }));
});

test("encoder refuses a version that is not one byte", () => {
  // `version & 0xff` would silently map 256 -> 0 and 300 -> 44.
  assert.throws(() => encodeEvidence({ ...vectorFields(), version: 256 }));
  assert.throws(() => encodeEvidence({ ...vectorFields(), version: -1 }));
  assert.throws(() => encodeEvidence({ ...vectorFields(), version: 1.5 }));
});

test("decoder accepts only version 1", () => {
  assert.throws(() => decodeEvidence(encodeEvidence({ ...vectorFields(), version: 2 })));
  assert.throws(() => decodeEvidence(encodeEvidence({ ...vectorFields(), version: 0 })));
});

test("decoder rejects numeric fields the encoder could never produce", () => {
  for (const raw of ["", "+42", "007", "1e3", "0x10", " 5 ", "42.", "-1", "NaN", "Infinity"]) {
      assert.throws(
      () => decodeEvidence(canonicalBytesWithUsedSeconds(raw)),
      /evidence/,
      `non-canonical numeric ${JSON.stringify(raw)} must be rejected`,
    );
  }
});

test("decoder rejects negative values for the non-negative fields", () => {
  assert.throws(() => encodeEvidence({ ...vectorFields(), usedSeconds: -1 }));
  assert.throws(() => encodeEvidence({ ...vectorFields(), deviceActiveSeconds: -1 }));
  assert.throws(() => encodeEvidence({ ...vectorFields(), eventCount: -1 }));
  assert.throws(() => encodeEvidence({ ...vectorFields(), windowStartMs: -1 }));
});

test("decoder rejects a count too large for the client's 32-bit field", () => {
  assert.throws(() => decodeEvidence(canonicalBytesWithUsedSeconds("2147483648")), /evidence/);
});

/** Builds length-prefixed bytes with a raw, possibly non-canonical numeric field. */
function canonicalBytesWithUsedSeconds(raw: string): Uint8Array {
  const f = vectorFields();
  const values = [
    f.challengePubkey, f.walletPubkey, f.packageName, f.targetPackage,
    String(f.windowStartMs), String(f.windowEndMs), raw,
    String(f.deviceActiveSeconds), String(f.eventCount), f.eventSetDigestHex,
    f.tamperSignals.join(","), f.nonceB64,
  ];
  const enc = new TextEncoder();
  const parts = values.map((v) => enc.encode(v));
  const out = new Uint8Array(1 + parts.reduce((n, p) => n + 4 + p.length, 0));
  let c = 0;
  out[c++] = f.version;
  for (const p of parts) {
    out[c++] = (p.length >>> 24) & 0xff;
    out[c++] = (p.length >>> 16) & 0xff;
    out[c++] = (p.length >>> 8) & 0xff;
    out[c++] = p.length & 0xff;
    out.set(p, c);
    c += p.length;
  }
  return out;
}

test("decoder rejects a truncated field and trailing bytes", () => {
  const bytes = encodeEvidence(vectorFields());
  assert.throws(() => decodeEvidence(bytes.subarray(0, bytes.length - 1)));
  const withTrailer = new Uint8Array(bytes.length + 1);
  withTrailer.set(bytes);
  assert.throws(() => decodeEvidence(withTrailer));
});
