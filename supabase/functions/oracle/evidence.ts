/**
 * The exact bytes a device signs and this oracle hashes (spec 4.2).
 *
 * Layout, shared byte-for-byte with the Kotlin encoder in
 * `app/src/main/java/com/nopeek/app/data/attest/CanonicalPayload.kt`: one raw
 * `version` byte, then each remaining field as a big-endian `u32` byte-count
 * followed by its UTF-8 bytes, in the spec's order. Numeric fields are written
 * as their canonical decimal string.
 *
 * The device signs these bytes and the oracle hashes the bytes it received —
 * neither side re-serializes. That is why both languages are pinned to
 * `fixtures/evidence-vector.json` by tests: a settlement must be able to point
 * at evidence it can reproduce.
 */

export interface EvidenceFields {
  version: number;
  challengePubkey: string;
  walletPubkey: string;
  packageName: string;
  targetPackage: string;
  windowStartMs: number;
  windowEndMs: number;
  usedSeconds: number;
  deviceActiveSeconds: number;
  eventCount: number;
  eventSetDigestHex: string;
  tamperSignals: string[];
  nonceB64: string;
}

const FIELD_COUNT = 12;

export function encodeEvidence(f: EvidenceFields): Uint8Array {
  const enc = new TextEncoder();
  const values: string[] = [
    f.challengePubkey,
    f.walletPubkey,
    f.packageName,
    f.targetPackage,
    decimal(f.windowStartMs),
    decimal(f.windowEndMs),
    decimal(f.usedSeconds),
    decimal(f.deviceActiveSeconds),
    decimal(f.eventCount),
    f.eventSetDigestHex,
    f.tamperSignals.join(","),
    f.nonceB64,
  ];
  let size = 1;
  const encoded = values.map((v) => {
    const raw = enc.encode(v);
    size += 4 + raw.length;
    return raw;
  });

  const out = new Uint8Array(size);
  let cursor = 0;
  out[cursor++] = f.version & 0xff;
  encoded.forEach((raw) => {
    const len = raw.length;
    out[cursor++] = (len >>> 24) & 0xff;
    out[cursor++] = (len >>> 16) & 0xff;
    out[cursor++] = (len >>> 8) & 0xff;
    out[cursor++] = len & 0xff;
    out.set(raw, cursor);
    cursor += raw.length;
  });
  return out;
}

export function decodeEvidence(bytes: Uint8Array): EvidenceFields {
  const dec = new TextDecoder("utf-8", { fatal: false });
  let cursor = 0;
  const readByte = (): number => {
    if (cursor >= bytes.length) throw new Error("truncated canonical payload");
    return bytes[cursor++];
  };
  const version = readByte();

  const fields: string[] = [];
  for (let i = 0; i < FIELD_COUNT; i++) {
    const len =
      ((readByte() << 24) | (readByte() << 16) | (readByte() << 8) | readByte()) >>> 0;
    if (cursor + len > bytes.length) throw new Error("truncated field in canonical payload");
    fields.push(dec.decode(bytes.subarray(cursor, cursor + len)));
    cursor += len;
  }
  if (cursor !== bytes.length) throw new Error("trailing bytes after canonical payload");

  const signals = fields[10].length === 0 ? [] : fields[10].split(",");
  return {
    version,
    challengePubkey: fields[0],
    walletPubkey: fields[1],
    packageName: fields[2],
    targetPackage: fields[3],
    windowStartMs: integer(fields[4]),
    windowEndMs: integer(fields[5]),
    usedSeconds: integer(fields[6]),
    deviceActiveSeconds: integer(fields[7]),
    eventCount: integer(fields[8]),
    eventSetDigestHex: fields[9],
    tamperSignals: signals,
    nonceB64: fields[11],
  };
}

/** Lowercase hex sha-256 of the bytes given — the value written on-chain at settle. */
export async function evidenceHash(bytes: Uint8Array): Promise<string> {
  // Copy into a tight ArrayBuffer so WebCrypto accepts it, without altering the
  // bytes: the hash must be of exactly what was received.
  const buffer = new Uint8Array(bytes.length);
  buffer.set(bytes);
  const digest = await crypto.subtle.digest("SHA-256", buffer.buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function decimal(n: number): string {
  if (!Number.isSafeInteger(n)) {
    throw new Error(`evidence fields must be safe integers, got ${n}`);
  }
  return String(n);
}

function integer(s: string): number {
  const n = Number(s);
  if (!Number.isSafeInteger(n)) throw new Error(`corrupt numeric evidence field: "${s}"`);
  return n;
}
