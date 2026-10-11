/**
 * Which oracle routes are unauthenticated, and which need the shared secret.
 *
 * Lives in its own runtime-neutral module for one reason: index.ts is Deno-native and
 * excluded from the typecheck project, so a rule expressed only inside it is a rule no
 * test can see. Spec 7 inverts trust for the device-facing routes — the attestation
 * signature IS the authentication, because the device cannot hold a secret without
 * shipping it in the APK.
 *
 * Both this check and the router in index.ts read the SAME pathname string, which is what
 * makes the exemption safe: a path that slips past `isPublicRoute` by clever spelling
 * (e.g. "/evidence/../settle") also fails to match the settle route and 404s. If the
 * router ever normalises its path differently from here, that equivalence breaks and an
 * exempt prefix could dispatch a protected handler.
 */
const PUBLIC_ROUTES = [
  "/health",
  "/enroll",
  "/nonce",
  "/submit-report",
  "/evidence",    // /evidence/<hash> — the public publisher (Task 14)
  "/reputation",  // /reputation/<wallet> — public read (Task 17)
];

export function isPublicRoute(path: string): boolean {
  return PUBLIC_ROUTES.some((route) => path === route || path.startsWith(`${route}/`));
}

export interface Denied { status: number; error: string }

/**
 * `null` means allowed.
 *
 * KNOWN GAP, pinned by test rather than buried: when no secret is configured, protected
 * routes are allowed. That is the current deployed state, and it means /settle is open to
 * anyone who finds the URL. It cannot simply be flipped to fail-closed today because the
 * Kotlin app calls /register-challenge and /settle with no secret at all — enforcing it
 * would break the duel flow until those callers move behind the oracle. Deciding which
 * way to go is a human call; this function documents the choice instead of making it.
 */
export function authorizeRequest(
  path: string,
  headerValue: string | null,
  secret: string | undefined,
): Denied | null {
  if (isPublicRoute(path)) return null;
  if (!secret) return null;
  if (headerValue === secret) return null;
  return { status: 401, error: "unauthorized" };
}
