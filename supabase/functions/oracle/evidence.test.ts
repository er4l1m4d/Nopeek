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

test("a non-ASCII package name survives encode/decode", () => {
  const f = { ...vectorFields(), targetPackage: "com.exämple.app" };
  assert.equal(decodeEvidence(encodeEvidence(f)).targetPackage, "com.exämple.app");
});
