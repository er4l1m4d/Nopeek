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
  issuer: string;
  serial: string;
}

export interface ChainLink {
  subjectDn: string;
  issuerDn: string;
  serialHex: string;
  signatureAlgorithmOid: string;
  issuerFound: boolean;
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
    .map((m) => Uint8Array.from(Buffer.from(m[1].replace(/\s+/g, ""), "base64")));
}

function parseCertificate(der: Uint8Array): Certificate {
  const cert = readTlv(der, 0);
  const top = childrenOf(der, cert);
  const tbsTlv = top[0];
  const sigValueTlv = top[2];

  const tbsChildren = childrenOf(der, tbsTlv);
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
  return pemToDerBlocks(chainPem).map(parseCertificate);
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
const EC_CURVES: Record<string, { name: string; coordinateBytes: number }> = {
  "1.2.840.10045.3.1.7": { name: "P-256", coordinateBytes: 32 },
  "1.3.132.0.34": { name: "P-384", coordinateBytes: 48 },
  "1.3.132.0.35": { name: "P-521", coordinateBytes: 66 },
};

/** ecdsa-with-SHA{256,384,512} -> the WebCrypto hash name. */
const ECDSA_HASH: Record<string, string> = {
  "1.2.840.10045.4.3.2": "SHA-256",
  "1.2.840.10045.4.3.3": "SHA-384",
  "1.2.840.10045.4.3.4": "SHA-512",
};

function ecCurveOf(spkiDer: Uint8Array): { name: string; coordinateBytes: number } | null {
  const spki = readTlv(spkiDer, 0);
  const [algId] = childrenOf(spkiDer, spki);
  const params = childrenOf(spkiDer, algId).at(1);
  if (!params) return null;
  return EC_CURVES[readOid(bytesOf(spkiDer, params))] ?? null;
}

async function verifyWithSpki(
  spkiDer: Uint8Array,
  algorithmOid: string,
  message: Uint8Array,
  signature: Uint8Array,
): Promise<boolean> {
  try {
    if (algorithmOid in ECDSA_HASH) {
      const curve = ecCurveOf(spkiDer);
      if (!curve) return false; // unknown curve: refuse rather than guess
      const key = await crypto.subtle.importKey("spki", spkiDer as BufferSource,
        { name: "ECDSA", namedCurve: curve.name }, false, ["verify"]);
      return await crypto.subtle.verify(
        { name: "ECDSA", hash: ECDSA_HASH[algorithmOid] }, key,
        derEcdsaSigToRaw(signature, curve.coordinateBytes) as BufferSource,
        message as BufferSource,
      );
    }
    if (algorithmOid === OID_RSA_SHA256) {
      const key = await crypto.subtle.importKey("spki", spkiDer as BufferSource,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
      return await crypto.subtle.verify({ name: "RSASSA-PKCS1-v1_5" }, key,
        signature as BufferSource, message as BufferSource);
    }
    // Ed448/Ed25519 and anything else: refuse rather than treat as verified. Callers
    // fail closed on false, so an unsupported algorithm can never mint a PASS.
    return false;
  } catch {
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
  const spki = Uint8Array.from(Buffer.from(spkiB64, "base64"));
  const signature = Uint8Array.from(Buffer.from(signatureB64, "base64"));
  try {
    const key = await crypto.subtle.importKey("spki", spki as BufferSource,
      { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    // Public API takes raw P-1363 signatures, exactly what the device produces with
    // "SHA256withECDSA"; no DER unwrapping on this path.
    return await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key,
      signature as BufferSource, bytes as BufferSource);
  } catch {
    return false;
  }
}

/**
 * Verifies each certificate's signature against its issuer's public key, in order,
 * without checking where the chain ends. Linking is confirmed by DN agreement, so a
 * reordered or substituted chain fails rather than being optimistically assumed.
 */
export async function verifyChainSignatures(chainPem: string): Promise<boolean> {
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
    );
    if (!ok) return false;
  }
  return true;
}

/**
 * Chain signatures must verify AND the terminal certificate must be byte-identical to
 * the pinned root. Identity is the only thing trusted without a signature check; the
 * root's self-signature proves nothing about who issued it.
 */
export async function verifyChainToRoot(chainPem: string, rootPem: string): Promise<boolean> {
  if (!(await verifyChainSignatures(chainPem))) return false;
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
export async function describeChain(chainPem: string): Promise<ChainLink[]> {
  const certs = parseChain(chainPem);
  const out: ChainLink[] = [];
  for (const cert of certs) {
    // Issuer is located by subject DN rather than by position, so a reordered or
    // substituted chain reports a broken link instead of being optimistically assumed.
    const issuer = certs.find((candidate) => candidate.subjectDn === cert.issuerDn) ?? null;
    const signatureValid = issuer
      ? await verifyWithSpki(issuer.spkiDer, cert.signatureAlgorithmOid, cert.tbs, cert.signatureDer)
      : false;
    out.push({
      subjectDn: cert.subjectDn,
      issuerDn: cert.issuerDn,
      serialHex: cert.serialHex,
      signatureAlgorithmOid: cert.signatureAlgorithmOid,
      issuerFound: issuer !== null,
      signatureValid,
    });
  }
  return out;
}

export function serialNumbers(chainPem: string): string[] {
  return parseChain(chainPem).map((c) => c.serialHex);
}

/**
 * Google's published revocation entries are hex serials, sometimes zero-padded and
 * sometimes carrying a leading "0x"; compare on normalised hex so formatting noise
 * cannot let a revoked batch attestation key through.
 */
export function isSerialRevoked(chainPem: string, revoked: RevokedEntry[]): boolean {
  if (revoked.length === 0) return false;
  const normalise = (value: string) => {
    let hex = value.trim().toLowerCase().replace(/^0x/, "");
    if (hex.length % 2 === 1) hex = `0${hex}`;
    while (hex.length > 2 && hex.startsWith("00")) hex = hex.slice(2);
    return hex;
  };
  const blocked = new Set(revoked.map((entry) => normalise(entry.serial)));
  return serialNumbers(chainPem).some((serial) => blocked.has(normalise(serial)));
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
  if (attestationLevel !== keymasterLevel) {
    // An attestation that calls itself software while the key claims trusted
    // environment is not something to move money on. Fail closed.
    throw new Error(`x509: security level disagreement (${attestationLevel} vs ${keymasterLevel})`);
  }

  const software = fields[6];
  const hardware = fields[7];

  // RootOfTrust must come from the HARDWARE-enforced list; reading boot state from
  // the software list would let a software key assert a locked bootloader.
  const rotOuter = findTag(buf, hardware, 704);
  if (!rotOuter) throw new Error("x509: RootOfTrust absent from hardwareEnforced");
  const rot = readTlv(buf, rotOuter.contentStart);
  const [, lockedTlv, bootStateTlv] = childrenOf(buf, rot);
  const bootStateIndex = bytesOf(buf, bootStateTlv).at(-1) as number;

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
    attestationChallengeB64: Buffer.from(bytesOf(buf, challengeTlv)).toString("base64"),
    securityLevel: attestationLevel,
    deviceLocked: bytesOf(buf, lockedTlv).at(-1) !== 0,
    verifiedBootState: VERIFIED_BOOT_STATE[bootStateIndex] ?? `Unknown(${bootStateIndex})`,
    applicationPackageName: new TextDecoder().decode(packageName),
    signingCertDigestB64: Buffer.from(digest).toString("base64"),
    leafSpkiB64: Buffer.from(leaf.spkiDer).toString("base64"),
    rootIssuerDn: certs[certs.length - 1].issuerDn,
  };
}
