/**
 * X.509 / ASN.1-DER reading and chain verification for Nopeek's compliance evidence.
 *
 * Mechanism decision (plan Task 3 Step 3): a dependency-free DER walker, rather than
 * option (a) `@peculiar/x509` + `asn1js`. Reasons, in order of weight:
 *   - one code path for both runtimes. The oracle is deployed on Deno and tested on
 *     Node; an `npm:` specifier that behaves differently in each is exactly the kind
 *     of thing that passes locally and fails deployed, which is the failure this
 *     whole project exists to avoid.
 *   - $0 budget and a public repo: no node_modules, no lockfile, no install step.
 *   - The subset needed is small and closed: walk TLVs, locate the attestation
 *     extension, verify signatures. Nothing here invents ASN.1 semantics.
 *
 * Trust model: the anchor is trusted by *identity* (the terminal certificate must be
 * byte-identical to the pinned Google root, obtained out of band from
 * https://android.googleapis.com/attestation/root - never from the presented chain).
 * Everything below the anchor is trusted by *signature*. A chain that is merely
 * self-consistent therefore verifies nothing, which is why `verifyChainToRoot` fails
 * against a different-but-valid Google root.
 *
 * Enum values are cited from AOSP (source.android.com/docs/security/features/keystore/
 * attestation) rather than recalled:
 *   SecurityLevel     ::= ENUMERATED { Software(0), TrustedEnvironment(1), StrongBox(2) }
 *   VerifiedBootState ::= ENUMERATED { Verified(0), SelfSigned(1), Unverified(2) }
 */

const ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17";
const OID_RSA_SHA256 = "1.2.840.113549.1.1.11";

const SECURITY_LEVEL = ["Software", "TrustedEnvironment", "StrongBox"] as const;
const VERIFIED_BOOT_STATE = ["Verified", "SelfSigned", "Unverified"] as const;

export interface AttestationInfo {
  attestationChallengeB64: string;
  deviceLocked: boolean;
  verifiedBootState: string;
  applicationPackageName: string;
  signingCertDigestB64: string;
  securityLevel: string;
  leafSpkiB64: string;
  rootIssuerDn: string;
}

export interface RevokedEntry {
  /**
   * Serial as the feed writes it — decimal or hexadecimal, see feedSerialValues.
   */
  serial: string;
  /**
   * Optional because Google's published feed has no issuer field at all: measured
   * 2026-10-11, its entries are { status, reason } keyed by serial. A matcher that
   * *required* an issuer would then fail every comparison and report "not revoked" for
   * all 1759 published keys — a fail-open dressed up as strictness. What protects an
   * honest device is the leaf exclusion in isSerialRevoked, plus honouring an issuer
   * whenever a caller actually has one.
   */
  issuer?: string;
}

/**
 * The trust anchor, held in code rather than passed in by a caller.
 *
 * verifyChainToRoot(chain, rootPem) is fine as a primitive and dangerous as the only
 * option: one wiring slip in the oracle - trusting a root from the request body, or
 * from the device's own enrolled chain - collapses the whole design into the
 * self-consistency check the plan rejected as ledger Row 9. This constant is the
 * anchor, fetched out of band from https://android.googleapis.com/attestation/root
 * and cross-checked against fixtures/google-attestation-root.pem by test.
 */
export const PINNED_ATTESTATION_ROOT_PEM = `-----BEGIN CERTIFICATE-----
MIIFHDCCAwSgAwIBAgIJAPHBcqaZ6vUdMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMjIwMzIwMTgwNzQ4WhcNNDIwMzE1MTgw
NzQ4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
AGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1Ud
IwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYD
VR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQB8cMqTllHc8U+qCrOlg3H7
174lmaCsbo/bJ0C17JEgMLb4kvrqsXZs01U3mB/qABg/1t5Pd5AORHARs1hhqGIC
W/nKMav574f9rZN4PC2ZlufGXb7sIdJpGiO9ctRhiLuYuly10JccUZGEHpHSYM2G
tkgYbZba6lsCPYAAP83cyDV+1aOkTf1RCp/lM0PKvmxYN10RYsK631jrleGdcdkx
oSK//mSQbgcWnmAEZrzHoF1/0gso1HZgIn0YLzVhLSA/iXCX4QT2h3J5z3znluKG
1nv8NQdxei2DIIhASWfu804CA96cQKTTlaae2fweqXjdN1/v2nqOhngNyz1361mF
mr4XmaKH/ItTwOe72NI9ZcwS1lVaCvsIkTDCEXdm9rCNPAY10iTunIHFXRh+7KPz
lHGewCq/8TOohBRn0/NNfh7uRslOSZ/xKbN9tMBtw37Z8d2vvnXq/YWdsm1+JLVw
n6yYD/yacNJBlwpddla8eaVMjsF6nBnIgQOf9zKSe06nSTqvgwUHosgOECZJZ1Eu
zbH4yswbt02tKtKEFhx+v+OTge/06V+jGsqTWLsfrOCNLuA8H++z+pUENmpqnnHo
vaI47gC+TNpkgYGkkBT6B/m/U01BuOBBTzhIlMEZq9qkDWuM2cA5kW5V3FJUcfHn
w1IdYIg2Wxg7yHcQZemFQg==
-----END CERTIFICATE-----
`;

/**
 * base64 without a runtime global.
 *
 * `Buffer` works on Deno 2 but x509.ts is the only non-test file in this directory
 * that would have needed it, and the whole reason for a hand-written parser is one
 * code path across Node (tests) and Deno (deployed). atob/btoa are defined in both.
 */
function b64Decode(value: string): Uint8Array {
  const binary = atob(value.replace(/[^A-Za-z0-9+/=]/g, ""));
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function b64Encode(bytes: Uint8Array): string {
  let binary = "";
  // Chunked so a large chain cannot blow the argument limit.
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}

/**
 * WebCrypto takes ownership of the exact bytes handed to it, and this module passes
 * views whose byteOffset is non-zero. evidence.ts already copies for the same reason;
 * the deployed runtime is the one that would otherwise differ from the tested one.
 */
function tight(bytes: Uint8Array): Uint8Array {
  const copy = new Uint8Array(bytes.length);
  copy.set(bytes);
  return copy;
}

export interface ChainLink {
  subjectDn: string;
  issuerDn: string;
  serialHex: string;
  signatureAlgorithmOid: string;
  issuerFound: boolean;
  /** True only when the issuer is the certificate immediately following this one. */
  linkIsNextCertificate: boolean;
  signatureValid: boolean;
}

interface Tlv {
  cls: number;
  constructed: boolean;
  tagNumber: number;
  /** absolute offset where the content begins */
  contentStart: number;
  /** absolute offset one past the last content byte */
  contentEnd: number;
  /** absolute offset where this TLV begins (its identifier byte) */
  start: number;
}

interface Certificate {
  der: Uint8Array;
  tbs: Uint8Array;
  spkiDer: Uint8Array;
  serialHex: string;
  subjectDn: string;
  issuerDn: string;
  signatureAlgorithmOid: string;
  /** signature bytes, DER-wrapped for ECDSA exactly as present in the certificate */
  signatureDer: Uint8Array;
  extensions: Tlv[];
}

// ---------------------------------------------------------------------------
// DER reading
// ---------------------------------------------------------------------------

function readLength(buf: Uint8Array, at: number): { len: number; size: number } {
  const first = buf[at];
  if (first === undefined) throw new Error("der: truncated length");
  if (first < 0x80) return { len: first, size: 1 };
  const count = first & 0x7f;
  if (count === 0 || count > 4) throw new Error("der: unsupported or indefinite length form");
  let len = 0;
  for (let i = 0; i < count; i++) len = (len << 8) | buf[at + 1 + i];
  return { len, size: 1 + count };
}

function readTlv(buf: Uint8Array, at: number): Tlv {
  const start = at;
  const first = buf[at++];
  if (first === undefined) throw new Error("der: truncated identifier");
  const cls = (first >> 6) & 0x03;
  const constructed = (first & 0x20) !== 0;
  let tagNumber = first & 0x1f;
  if (tagNumber === 0x1f) {
    tagNumber = 0;
    let byte: number;
    do {
      byte = buf[at++];
      tagNumber = (tagNumber << 7) | (byte & 0x7f);
    } while (byte & 0x80);
  }
  const { len, size } = readLength(buf, at);
  at += size;
  const contentStart = at;
  const contentEnd = contentStart + len;
  if (contentEnd > buf.length) throw new Error("der: content longer than container");
  return { cls, constructed, tagNumber, contentStart, contentEnd, start };
}

function childrenOf(buf: Uint8Array, tlv: Tlv): Tlv[] {
  const out: Tlv[] = [];
  let at = tlv.contentStart;
  while (at < tlv.contentEnd) {
    const child = readTlv(buf, at);
    out.push(child);
    at = child.contentEnd;
  }
  return out;
}

function bytesOf(buf: Uint8Array, tlv: Tlv): Uint8Array {
  return buf.subarray(tlv.contentStart, tlv.contentEnd);
}

function toHex(buf: Uint8Array): string {
  return [...buf].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** DER INTEGER content -> canonical lowercase hex, leading zero padding stripped. */
function integerToHex(buf: Uint8Array): string {
  let hex = toHex(buf);
  while (hex.length > 2 && hex.startsWith("00")) hex = hex.slice(2);
  return hex === "00" ? "0" : hex;
}

function readOid(buf: Uint8Array): string {
  const parts: number[] = [];
  let value = 0;
  const bytes = [...buf];
  for (let i = 0; i < bytes.length; i++) {
    value = (value << 7) | (bytes[i] & 0x7f);
    if (!(bytes[i] & 0x80)) {
      parts.push(value);
      value = 0;
    }
  }
  if (parts.length === 0) return "";
  const first = parts.shift() as number;
  return [Math.floor(first / 40), first % 40, ...parts].join(".");
}

/** Renders an RDN's attribute types and printable values, enough to identify a CA. */
function nameToText(buf: Uint8Array, name: Tlv): string {
  const shortNames: Record<string, string> = {
    "2.5.4.3": "CN", "2.5.4.5": "serialNumber", "2.5.4.6": "C",
    "2.5.4.10": "O", "2.5.4.11": "OU", "2.5.4.12": "title",
  };
  const pieces: string[] = [];
  for (const set of childrenOf(buf, name)) {
    for (const atv of childrenOf(buf, set)) {
      const attrs = childrenOf(buf, atv);
      if (attrs.length < 2) continue;
      const oid = readOid(bytesOf(buf, attrs[0]));
      const valueTlv = attrs[1];
      const raw = bytesOf(buf, valueTlv);
      const printable = valueTlv.tagNumber === 19 || valueTlv.tagNumber === 12 || valueTlv.tagNumber === 26;
      const value = printable ? new TextDecoder().decode(raw) : toHex(raw);
      pieces.push(`${shortNames[oid] ?? oid}=${value}`);
    }
  }
  return pieces.join(",");
}

// ---------------------------------------------------------------------------
// Certificate parsing
// ---------------------------------------------------------------------------

export function pemToDerBlocks(pem: string): Uint8Array[] {
  return [...pem.matchAll(/-----BEGIN CERTIFICATE-----\s*([\s\S]*?)\s*-----END CERTIFICATE-----/g)]
    .map((m) => b64Decode(m[1]));
}

function parseCertificate(der: Uint8Array): Certificate {
  const cert = readTlv(der, 0);
  // Guard the structural preconditions so garbage input raises a domain error the
  // handler can map to a rejection, rather than a TypeError about reading 'der' of
  // undefined which index.ts would surface as a retryable HTTP 500.
  if (cert.cls !== 0 || !cert.constructed || cert.tagNumber !== 16) {
    throw new Error("x509: certificate is not an outer SEQUENCE");
  }
  const top = childrenOf(der, cert);
  if (top.length < 3) throw new Error("x509: certificate has fewer than 3 top-level fields");
  const tbsTlv = top[0];
  const sigValueTlv = top[2];

  const tbsChildren = childrenOf(der, tbsTlv);
  if (tbsChildren.length < (tbsChildren[0].cls === 2 ? 7 : 6)) {
    throw new Error("x509: TBSCertificate is truncated");
  }
  // TBSCertificate is [version]? serialNumber signature issuer validity subject
  // subjectPublicKeyInfo ..., so an explicit [0] version shifts everything by one.
  const i = tbsChildren[0].cls === 2 && tbsChildren[0].tagNumber === 0 ? 1 : 0;
  const [serialTlv, sigAlgInner, issuerTlv, , subjectTlv, spkiTlv] = tbsChildren.slice(i, i + 6);

  let extensions: Tlv[] = [];
  const extBlock = tbsChildren.slice(i + 6).find((c) => c.cls === 2 && c.tagNumber === 3);
  if (extBlock) extensions = childrenOf(der, childrenOf(der, extBlock)[0]);

  const sigAlgChildren = childrenOf(der, sigAlgInner);
  return {
    der,
    tbs: der.subarray(tbsTlv.start, tbsTlv.contentEnd),
    spkiDer: der.subarray(spkiTlv.start, spkiTlv.contentEnd),
    serialHex: integerToHex(bytesOf(der, serialTlv)),
    subjectDn: nameToText(der, subjectTlv),
    issuerDn: nameToText(der, issuerTlv),
    signatureAlgorithmOid: readOid(bytesOf(der, sigAlgChildren[0])),
    signatureDer: Uint8Array.from(bytesOf(der, sigValueTlv).subarray(1)), // skip BIT STRING unused-bits byte
    extensions,
  };
}

function parseChain(chainPem: string): Certificate[] {
  const blocks = pemToDerBlocks(chainPem);
  if (blocks.length === 0) throw new Error("x509: no certificates in the presented chain");
  return blocks.map(parseCertificate);
}

function findExtension(leaf: Certificate, oid: string): Tlv | null {
  for (const ext of leaf.extensions) {
    const members = childrenOf(leaf.der, ext);
    if (members.length >= 2 && readOid(bytesOf(leaf.der, members[0])) === oid) {
      // extnValue is an OCTET STRING holding the extension's own DER.
      const valueTlv = members.find((m) => m.tagNumber === 4 && m.cls === 0);
      if (valueTlv) return valueTlv;
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Signature verification
// ---------------------------------------------------------------------------

/** DER SEQUENCE{INTEGER r, INTEGER s} -> fixed-width P-1363 r||s for WebCrypto. */
function derEcdsaSigToRaw(der: Uint8Array, width: number): Uint8Array {
  const seq = readTlv(der, 0);
  const [rTlv, sTlv] = childrenOf(der, seq);
  const out = new Uint8Array(width * 2);
  for (const [tlv, offset] of [[rTlv, 0], [sTlv, width]] as const) {
    let value = bytesOf(der, tlv);
    while (value.length > 1 && value[0] === 0x00) value = value.subarray(1);
    if (value.length > width) throw new Error("der: ECDSA coordinate wider than curve");
    out.set(value, offset + (width - value.length));
  }
  return out;
}

/**
 * EC curve OIDs, and the field size each implies for signature coordinates.
 *
 * The curve must be read from the issuer's own SPKI, not assumed. Google's TEE batch
 * attestation key is P-384 while a device key is P-256, so a hardcoded P-256 import
 * makes exactly one middle link fail - which is what this code initially did.
 */
type NamedCurve = "P-256" | "P-384" | "P-521";
type HashName = "SHA-256" | "SHA-384" | "SHA-512";

const EC_CURVES: Record<string, { name: NamedCurve; coordinateBytes: number }> = {
  "1.2.840.10045.3.1.7": { name: "P-256", coordinateBytes: 32 },
  "1.3.132.0.34": { name: "P-384", coordinateBytes: 48 },
  "1.3.132.0.35": { name: "P-521", coordinateBytes: 66 },
};

/** ecdsa-with-SHA{256,384,512} -> the WebCrypto hash name. */
const ECDSA_HASH: Record<string, HashName> = {
  "1.2.840.10045.4.3.2": "SHA-256",
  "1.2.840.10045.4.3.3": "SHA-384",
  "1.2.840.10045.4.3.4": "SHA-512",
};

/**
 * One ECDSA link, handed over fully parsed: curve, hash, issuer key, signature and the
 * signed bytes. The implementer only has to do the maths, because that is the part a
 * runtime may not have.
 *
 * The seam exists for a measured reason, not as general flexibility. Supabase's edge
 * runtime (Deno 2.1.4) throws `DOMException: Not implemented` from crypto.subtle.verify
 * for P-384 while Node 26 accepts the same bytes, so the deployed oracle rejected a
 * genuine Redmi chain at the batch←intermediate link, which is verified under the
 * intermediate's P-384 key. That key is Google's and is not ours to downgrade, so
 * production injects a pure-JS verifier and the default stays WebCrypto.
 *
 * `hash` travels with the link rather than being implied by the curve, because the hash is
 * dictated by the certificate's own signatureAlgorithm OID: a P-384 key can legitimately
 * carry ecdsa-with-SHA-256, and an implementation that assumed SHA-384 from the curve would
 * reject a valid certificate.
 */
export interface EcdsaLink {
  namedCurve: NamedCurve;
  hash: HashName;
  /** DER SubjectPublicKeyInfo of the issuer, for runtimes that import keys. */
  spkiDer: Uint8Array;
  /** r||s, fixed-width for the curve (WebCrypto's P-1363 form), converted by x509. */
  rawSignature: Uint8Array;
  /** The exact bytes the issuer signed: TBSCertificate. */
  message: Uint8Array;
}

export type EcdsaVerifier = (link: EcdsaLink) => Promise<boolean>;

/** The host's own ECDSA verify. Default everywhere except the Deno edge runtime. */
export const verifyEcdsaWithWebCrypto: EcdsaVerifier = async ({
  namedCurve, hash, spkiDer, rawSignature, message,
}) => {
  const key = await crypto.subtle.importKey(
    "spki", tight(spkiDer) as BufferSource, { name: "ECDSA", namedCurve }, false, ["verify"],
  );
  return await crypto.subtle.verify(
    { name: "ECDSA", hash }, key,
    rawSignature as BufferSource, tight(message) as BufferSource,
  );
};

function ecCurveOf(spkiDer: Uint8Array): { name: NamedCurve; coordinateBytes: number } | null {
  const spki = readTlv(spkiDer, 0);
  const [algId] = childrenOf(spkiDer, spki);
  const params = childrenOf(spkiDer, algId).at(1);
  if (!params) return null;
  return EC_CURVES[readOid(bytesOf(spkiDer, params))] ?? null;
}

/**
 * The uncompressed SEC1 point (0x04||X||Y) out of a SubjectPublicKeyInfo, validated against
 * the curve the SPKI itself declares. Throws on anything that does not match, because a
 * silently mis-sliced public key verifies nothing and looks like a forgery.
 */
function publicPointFromSpki(spkiDer: Uint8Array): Uint8Array {
  const spki = readTlv(spkiDer, 0);
  const bitString = childrenOf(spkiDer, spki)[1];
  const curve = ecCurveOf(spkiDer);
  if (!bitString || !curve) throw new Error("malformed SubjectPublicKeyInfo");
  if (bitString.tagNumber !== 3) {
    throw new Error(`SPKI public key is not a BIT STRING (tag 3), got tag ${bitString.tagNumber}`);
  }
  const content = bytesOf(spkiDer, bitString);
  const point = content.subarray(1); // drop the unused-bits count
  const expected = 1 + curve.coordinateBytes * 2;
  if (content[0] !== 0 || point.length !== expected || point[0] !== 0x04) {
    throw new Error(
      `expected an uncompressed ${curve.name} point of ${expected} bytes, ` +
        `got ${point.length} starting 0x${(point[0] ?? -1).toString(16)}`,
    );
  }
  return Uint8Array.from(point);
}

/** The part of a @noble/curves instance this module uses, stated so x509.ts stays dependency-free. */
export interface NobleCurve {
  verify(
    signature: Uint8Array,
    messageDigest: Uint8Array,
    publicKeyPoint: Uint8Array,
    opts?: { prehash?: boolean; lowS?: boolean; format?: "compact" | "recovered" | "der" },
  ): boolean;
}

/**
 * Builds the ECDSA verifier production binds in Deno from curve objects the caller passes
 * in. The caller does the importing, because that is the only runtime-specific part:
 * `@noble/curves/nist.js` resolves under Node and `npm:@noble/curves@…/nist.js` under Deno,
 * and neither resolves in the other. Everything that could get wrong — point extraction,
 * which hash, which signature encoding — lives here, so the Node test and the deployed
 * function exercise the same code paths.
 */
export function makeNobleEcdsaVerifier(
  curves: Partial<Record<NamedCurve, NobleCurve>>,
): EcdsaVerifier {
  return async ({ namedCurve, hash, spkiDer, rawSignature, message }) => {
    const curve = curves[namedCurve];
    if (!curve) {
      // Throw rather than quietly fall back to WebCrypto. Silent fallback is precisely how
      // this bug hid: the host that cannot check P-384 would be used for P-384 anyway, and
      // the caller could not tell a refusal from a missing implementation. verifyWithSpki
      // catches this into a rejection that also logs why.
      throw new Error(`no pure-JS verifier supplied for ${namedCurve}`);
    }
    // The digest is computed here from the certificate's own signatureAlgorithm OID, and
    // noble is told not to hash (prehash: false) — its default would apply the *curve's*
    // hash, while a P-384 key legally carries ecdsa-with-SHA-256.
    const digest = new Uint8Array(
      await crypto.subtle.digest(hash, tight(message) as BufferSource),
    );
    return curve.verify(rawSignature, digest, publicPointFromSpki(spkiDer), {
      prehash: false,
      // noble defaults lowS to true, rejecting s > n/2. Chain signatures have no
      // malleability exposure (nothing treats these bytes as an identifier), and honouring
      // the default risks refusing a genuine Google certificate that every browser accepts.
      lowS: false,
      // x509 already converted DER -> fixed-width r||s before handing the link over.
      format: "compact",
    });
  };
}

async function verifyWithSpki(
  spkiDer: Uint8Array,
  algorithmOid: string,
  message: Uint8Array,
  signature: Uint8Array,
  ecdsa: EcdsaVerifier = verifyEcdsaWithWebCrypto,
): Promise<boolean> {
  try {
    if (algorithmOid in ECDSA_HASH) {
      const curve = ecCurveOf(spkiDer);
      if (!curve) return false; // unknown curve: refuse rather than guess
      // DER->raw conversion and the curve/hash read stay here, where the DER parsing
      // lives. The injected verifier never gets to reinterpret the certificate.
      return await ecdsa({
        namedCurve: curve.name,
        hash: ECDSA_HASH[algorithmOid],
        spkiDer,
        rawSignature: derEcdsaSigToRaw(signature, curve.coordinateBytes),
        message,
      });
    }
    if (algorithmOid === OID_RSA_SHA256) {
      const key = await crypto.subtle.importKey("spki", tight(spkiDer) as BufferSource,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
      return await crypto.subtle.verify({ name: "RSASSA-PKCS1-v1_5" }, key,
        tight(signature) as BufferSource, tight(message) as BufferSource);
    }
    // Anything unrecognised - including the Ed25519/Ed448 roots Google also publishes -
    // returns false rather than being treated as verified. Callers fail closed on
    // false, so an unsupported algorithm can never mint a PASS. Note this branch is
    // currently exercised only by the unknown-curve case: no EdDSA root fixture exists
    // in this repo, so do not claim coverage of it.
    return false;
  } catch (e) {
    // Fail closed exactly as before - but say why. Returning false here without a trace
    // made a Deno-vs-Node signature failure indistinguishable from a genuine forgery for
    // the better part of a day. The reason is still never surfaced to a caller; it only
    // reaches the function logs.
    console.log(`VERIFY_INNER_ERROR ${e instanceof Error ? `${e.constructor.name}: ${e.message}` : String(e)}`);
    return false;
  }
}

/**
 * Verifies the evidence payload signature. Unlike chain verification this curve and
 * hash are fixed by *our* protocol: the app always generates P-256 keys and always
 * signs with SHA-256, so pinning them here is a constraint rather than an assumption.
 */
export async function verifyEcdsaSignature(
  spkiB64: string,
  bytes: Uint8Array,
  signatureB64: string,
): Promise<boolean> {
  const spki = b64Decode(spkiB64);
  const signature = b64Decode(signatureB64);
  try {
    const key = await crypto.subtle.importKey("spki", tight(spki) as BufferSource,
      { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    // Public API takes raw P-1363 signatures, exactly what the device produces with
    // "SHA256withECDSA"; no DER unwrapping on this path.
    return await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key,
      tight(signature) as BufferSource, tight(bytes) as BufferSource);
  } catch {
    return false;
  }
}

/**
 * Verifies each certificate's signature against its issuer's public key, in order,
 * without checking where the chain ends. Linking is confirmed by DN agreement, so a
 * reordered or substituted chain fails rather than being optimistically assumed.
 */
export async function verifyChainSignatures(
  chainPem: string,
  ecdsa: EcdsaVerifier = verifyEcdsaWithWebCrypto,
): Promise<boolean> {
  let certs: Certificate[];
  try {
    certs = parseChain(chainPem);
  } catch {
    return false;
  }
  if (certs.length < 2) return false;
  for (let i = 0; i < certs.length - 1; i++) {
    if (certs[i].issuerDn !== certs[i + 1].subjectDn) return false;
    const ok = await verifyWithSpki(
      certs[i + 1].spkiDer, certs[i].signatureAlgorithmOid, certs[i].tbs, certs[i].signatureDer,
      ecdsa,
    );
    if (!ok) return false;
  }
  return true;
}

/**
 * The pinned-root form of chain verification. This is the function the oracle must
 * call: verifyChainToRoot's rootPem argument is a primitive that is easy to wire to
 * the wrong bytes, and wiring it wrong is silent and total.
 */
export async function verifyChainToPinnedRoot(
  chainPem: string, ecdsa: EcdsaVerifier = verifyEcdsaWithWebCrypto,
): Promise<boolean> {
  return verifyChainToRoot(chainPem, PINNED_ATTESTATION_ROOT_PEM, ecdsa);
}

/**
 * Chain signatures verify AND the terminal certificate is byte-identical to
 * `rootPem`. Identity is the only thing trusted without a signature check; a root's
 * self-signature proves nothing about who issued it.
 */
export async function verifyChainToRoot(
  chainPem: string, rootPem: string, ecdsa: EcdsaVerifier = verifyEcdsaWithWebCrypto,
): Promise<boolean> {
  if (!(await verifyChainSignatures(chainPem, ecdsa))) return false;
  const roots = pemToDerBlocks(rootPem);
  const last = parseChain(chainPem).at(-1);
  if (!last) return false;
  return roots.some((root) => toHex(root) === toHex(last.der));
}

/**
 * Chain signature verification returns a bare boolean, which is the right thing for
 * settlement and a terrible thing to debug, and useless on the Evidence screen where a
 * user needs to know *which* link broke. This exposes the same walk per certificate.
 */
export async function describeChain(
  chainPem: string, ecdsa: EcdsaVerifier = verifyEcdsaWithWebCrypto,
): Promise<ChainLink[]> {
  const certs = parseChain(chainPem);
  const out: ChainLink[] = [];
  for (const [index, cert] of certs.entries()) {
    const issuer = certs.find((candidate) => candidate.subjectDn === cert.issuerDn) ?? null;
    // DN lookup alone cannot express ordering: on a reordered chain every issuer is
    // still present somewhere, so all links read valid while verifyChainSignatures
    // says false. The Evidence screen would then contradict the settlement decision,
    // so ordering is reported explicitly.
    const next = certs[index + 1] ?? null;
    const linkIsNextCertificate = next !== null && issuer !== null && next.subjectDn === issuer.subjectDn;
    const signatureValid = issuer
      ? await verifyWithSpki(issuer.spkiDer, cert.signatureAlgorithmOid, cert.tbs, cert.signatureDer, ecdsa)
      : false;
    out.push({
      subjectDn: cert.subjectDn,
      issuerDn: cert.issuerDn,
      serialHex: cert.serialHex,
      signatureAlgorithmOid: cert.signatureAlgorithmOid,
      issuerFound: issuer !== null,
      linkIsNextCertificate,
      signatureValid,
    });
  }
  return out;
}

export function serialNumbers(chainPem: string): string[] {
  return parseChain(chainPem).map((c) => c.serialHex);
}

/**
 * The numeric reading(s) of a serial as a feed writes it.
 *
 * The live feed mixes bases: of its 1759 keys, 979 are 17-20 digits with no letters
 * (decimal) and 780 are 30-32 characters containing a-f (hexadecimal). A key with letters
 * can only be hex; a digits-only key is ambiguous, so BOTH values are returned. Picking one
 * base and applying it everywhere leaves the other group permanently un-compared, which
 * fails open — a published leaked batch key would keep verifying forever.
 *
 * The cost of the extra reading is a false positive only if two numbers of ~10^38 magnitude
 * coincide, and a false positive here refuses a report rather than paying one out.
 *
 * Throws on anything that is not a non-negative integer in either base, so a format change
 * is loud rather than an ignored entry.
 */
function feedSerialValues(value: string): bigint[] {
  const trimmed = value.trim().toLowerCase();
  const explicitHex = trimmed.startsWith("0x");
  const body = explicitHex ? trimmed.slice(2) : trimmed;
  if (body.length === 0 || !/^[0-9a-f]+$/.test(body)) {
    throw new Error(`x509: revoked serial is not a decimal or hexadecimal integer: "${value}"`);
  }
  if (explicitHex || /[a-f]/.test(body)) return [BigInt(`0x${body}`)];
  return [BigInt(body), BigInt(`0x${body}`)];
}

/**
 * Parses Google's attestation-status feed (android.googleapis.com/attestation/status).
 *
 * Returns `null` when the envelope is not recognised, `[]` when it is recognised and holds
 * no revocations. The distinction is the whole point: `[]` legitimately means "nothing is
 * revoked today" while `null` means "we cannot tell", and the caller must fail closed on
 * null. This code read `body.serials` for a day against a feed whose real key is
 * `entries` — the guess was wrong, and only the fail-closed contract made that visible
 * instead of silently disabling revocation.
 *
 * Lives here, next to the parser it feeds, so the unit tests can run against a real
 * captured slice (fixtures/revoked-feed-shape.json) rather than against the edge function.
 */
export function parseRevokedFeed(body: unknown): RevokedEntry[] | null {
  if (typeof body !== "object" || body === null) return null;
  const entries = (body as { entries?: unknown }).entries;
  if (typeof entries !== "object" || entries === null || Array.isArray(entries)) return null;

  const out: RevokedEntry[] = [];
  for (const [key, rawValue] of Object.entries(entries as Record<string, unknown>)) {
    const status = (rawValue as { status?: unknown } | null)?.status;
    if (typeof status !== "string") {
      throw new Error(`x509: revocation entry "${key}" has no status field`);
    }
    // Validated even though it is not filtered on: a key this module cannot read means the
    // feed changed under us, and that has to surface while the rest still looks parseable.
    feedSerialValues(key);
    // Every entry disqualifies its key. Presence in this feed is the signal — Google has
    // stopped trusting that attestation key — and the status only explains why (REVOKED, or
    // SUSPENDED for a temporary removal). Filtering on status would silently drop the others,
    // which is a FALSE ACCEPT: a report signed on a key Google flagged would settle money.
    // This is also why a missing status throws while an unusual one is kept: the asymmetry of
    // failing loudly for one shape of surprise and quietly for the other is how the filter
    // looked safe for longer than it was.
    out.push({ serial: key });
  }
  return out;
}

/**
 * Serial uniqueness is only guaranteed per issuer, and this project's own device leaf
 * carries serial "01" — so the leaf is not compared at all. Its serial is assigned per
 * device, while the feed lists CA and batch keys; matching a CA-level entry against "01"
 * would mark a genuine, locked, verified-boot device revoked, and under the fail-closed
 * rule that forfeits an honest user's stake. Google's own keys run 17-32 characters, so no
 * real entry collides with a device serial.
 */
export function isSerialRevoked(chainPem: string, revoked: RevokedEntry[]): boolean {
  if (revoked.length === 0) return false;
  const normaliseIssuer = (value: string) => value.trim().toLowerCase().replace(/\s+/g, "");
  const certs = parseChain(chainPem);
  // Index 0 is the leaf: see the note above.
  return certs.slice(1).some((cert) => {
    // A DER INTEGER serial is unambiguous — the parser rendered it hex.
    const serial = BigInt(`0x${cert.serialHex}`);
    return revoked.some((entry) => {
      const issuerGiven = typeof entry.issuer === "string" && entry.issuer.trim() !== "";
      if (issuerGiven && normaliseIssuer(entry.issuer as string) !== normaliseIssuer(cert.issuerDn)) {
        return false;
      }
      return feedSerialValues(entry.serial).includes(serial);
    });
  });
}

// ---------------------------------------------------------------------------
// Attestation extension
// ---------------------------------------------------------------------------

function collectOctetStrings(buf: Uint8Array, tlv: Tlv, out: Uint8Array[]): void {
  if (tlv.cls === 0 && tlv.tagNumber === 4) out.push(bytesOf(buf, tlv));
  if (tlv.constructed) {
    for (const child of childrenOf(buf, tlv)) collectOctetStrings(buf, child, out);
  }
}

function findTag(buf: Uint8Array, parent: Tlv, tagNumber: number): Tlv | null {
  if (!parent.constructed) return null;
  for (const child of childrenOf(buf, parent)) {
    if (child.cls === 2 && child.tagNumber === tagNumber) return child;
  }
  return null;
}

/** Follows any number of OCTET STRING wrappers (max 2) to reach the SEQUENCE inside. */
function unwrapSequence(buf: Uint8Array, at: number): Tlv {
  let tlv = readTlv(buf, at);
  for (let depth = 0; depth <= 2; depth++) {
    if (tlv.cls === 0 && tlv.constructed && tlv.tagNumber === 16) return tlv;
    if (tlv.cls === 0 && !tlv.constructed && tlv.tagNumber === 4) {
      tlv = readTlv(buf, tlv.contentStart);
      continue;
    }
    throw new Error(
      `x509: expected a SEQUENCE, found tag ${tlv.cls}/${tlv.tagNumber} at offset ${tlv.start}`,
    );
  }
  throw new Error(`x509: no SEQUENCE within 2 wrappers at offset ${at}`);
}

function isPrintable(value: Uint8Array): boolean {
  return value.length > 0 && [...value].every((b) => b >= 0x20 && b <= 0x7e);
}

export async function readAttestationExtension(chainPem: string): Promise<AttestationInfo> {
  const certs = parseChain(chainPem);
  const leaf = certs[0];
  const buf = leaf.der;
  const extValue = findExtension(leaf, ATTESTATION_OID);
  if (!extValue) throw new Error("x509: no Android Key Attestation extension on the leaf");

  // extnValue's content is an OCTET STRING wrapping the KeyDescription SEQUENCE, but
  // some stacks emit the SEQUENCE directly. Unwrap whatever is actually there instead
  // of assuming a depth - assuming it is what made this function read an INTEGER as a
  // SEQUENCE and report an undefined field.
  const description = unwrapSequence(buf, extValue.contentStart);
  const fields = childrenOf(buf, description);
  if (fields.length !== 8) {
    throw new Error(
      `x509: KeyDescription expected 8 fields, found ${fields.length}`,
    );
  }
  // KeyDescription order: attestationVersion, attestationSecurityLevel,
  // keymasterVersion, keymasterSecurityLevel, attestationChallenge, uniqueId,
  // softwareEnforced, hardwareEnforced.
  const [, attestationLevelTlv, , keymasterLevelTlv, challengeTlv] = fields;

  const securityLevelOf = (tlv: Tlv) => {
    const raw = bytesOf(buf, tlv);
    const index = raw[raw.length - 1];
    return SECURITY_LEVEL[index] ?? `Unknown(${index})`;
  };

  const attestationLevel = securityLevelOf(attestationLevelTlv);
  const keymasterLevel = securityLevelOf(keymasterLevelTlv);
  // Reject a software attestation outright. Deliberately *not* a strict-equality
  // check: a key held in StrongBox and attested by the TEE is expected to report
  // different levels for the two fields, and requiring equality would forfeit a
  // stronger device's honest duel. Whether the pair can legitimately differ is an
  // enum-semantics question that must be cited against AOSP before Task 14 relies on
  // it for a StrongBox device; this device is TEE-only, so nothing here is exercised.
  if (attestationLevel.startsWith("Software") || keymasterLevel.startsWith("Software")) {
    throw new Error(
      `x509: software-level attestation (${attestationLevel}/${keymasterLevel}) is not settleable`,
    );
  }

  const software = fields[6];
  const hardware = fields[7];

  // RootOfTrust must come from the HARDWARE-enforced list; reading boot state from
  // the software list would let a software key assert a locked bootloader.
  const rotOuter = findTag(buf, hardware, 704);
  if (!rotOuter) throw new Error("x509: RootOfTrust absent from hardwareEnforced");
  const rot = readTlv(buf, rotOuter.contentStart);
  const [, lockedTlv, bootStateTlv] = childrenOf(buf, rot);
  // Strict single-byte reads. "Take the last byte" on an unvalidated field defaults to
  // the permissive value for a zero-length BOOLEAN, and these two fields are the
  // hardware-enforced half of spec 4.3 checks 6-7 - the ones that decide whether a
  // report can be trusted at all. A malformed field must raise, never guess true.
  const lockedBytes = bytesOf(buf, lockedTlv);
  if (lockedBytes.length !== 1 || (lockedBytes[0] !== 0xff && lockedBytes[0] !== 0x00)) {
    throw new Error("x509: deviceLocked is not a single-byte DER BOOLEAN");
  }
  const bootBytes = bytesOf(buf, bootStateTlv);
  if (bootBytes.length !== 1 || bootBytes[0] >= VERIFIED_BOOT_STATE.length) {
    throw new Error("x509: verifiedBootState is not a known single-byte ENUMERATED");
  }

  const appIdOuter = findTag(buf, software, 709);
  if (!appIdOuter) throw new Error("x509: attestationApplicationId absent from softwareEnforced");
  const appIdWrapper = readTlv(buf, appIdOuter.contentStart); // OCTET STRING
  const appId = readTlv(buf, appIdWrapper.contentStart); // SEQUENCE
  const octets: Uint8Array[] = [];
  collectOctetStrings(buf, appId, octets);
  const packageName = octets.find((o) => isPrintable(o));
  // The signing-cert digest is a SHA-256, so exactly 32 bytes and never printable.
  const digest = octets.find((o) => o.length === 32 && !isPrintable(o));
  if (!packageName || !digest) {
    throw new Error("x509: attestationApplicationId missing package name or signature digest");
  }

  return {
    attestationChallengeB64: b64Encode(bytesOf(buf, challengeTlv)),
    securityLevel: attestationLevel,
    deviceLocked: lockedBytes[0] === 0xff,
    verifiedBootState: VERIFIED_BOOT_STATE[bootBytes[0]],
    applicationPackageName: new TextDecoder().decode(packageName),
    signingCertDigestB64: b64Encode(digest),
    leafSpkiB64: b64Encode(leaf.spkiDer),
    rootIssuerDn: certs[certs.length - 1].issuerDn,
  };
}
