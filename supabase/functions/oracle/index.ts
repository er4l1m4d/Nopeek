import { createClient } from "npm:@supabase/supabase-js@2";
import {
  determineResult,
  settleOutcome,
  type Verdict,
} from "./determine_result.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ORACLE_API_SECRET = Deno.env.get("ORACLE_API_SECRET");

const db = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

const BASE58 = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/;
const MAX_USED_SECONDS = 2_592_000; // 30 days
const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, content-type, x-oracle-secret",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

function authorize(req: Request): Response | null {
  if (!ORACLE_API_SECRET) return null;
  if (req.headers.get("x-oracle-secret") === ORACLE_API_SECRET) return null;
  return json({ error: "unauthorized" }, 401);
}

interface SubmitReportBody {
  challengePubkey: string;
  walletPubkey: string;
  usedSeconds: number;
  tamperSignals?: string[];
  windowStart?: string;
  windowEnd?: string;
}

function validateReport(
  raw: unknown,
): { ok: true; body: SubmitReportBody } | { ok: false; error: string } {
  if (typeof raw !== "object" || raw === null) return { ok: false, error: "body must be a JSON object" };
  const b = raw as Record<string, unknown>;
  if (typeof b.challengePubkey !== "string" || !BASE58.test(b.challengePubkey)) {
    return { ok: false, error: "challengePubkey must be base58 (32-44 chars)" };
  }
  if (typeof b.walletPubkey !== "string" || !BASE58.test(b.walletPubkey)) {
    return { ok: false, error: "walletPubkey must be base58 (32-44 chars)" };
  }
  if (
    typeof b.usedSeconds !== "number" ||
    !Number.isInteger(b.usedSeconds) ||
    b.usedSeconds < 0 ||
    b.usedSeconds > MAX_USED_SECONDS
  ) {
    return { ok: false, error: "usedSeconds must be an integer 0..2592000" };
  }
  const tamperSignals = b.tamperSignals ?? [];
  if (
    !Array.isArray(tamperSignals) ||
    tamperSignals.some((s) => typeof s !== "string") ||
    tamperSignals.length > 16
  ) {
    return { ok: false, error: "tamperSignals must be a string array (max 16)" };
  }
  for (const w of [b.windowStart, b.windowEnd]) {
    if (w !== undefined && (typeof w !== "string" || Number.isNaN(Date.parse(w)))) {
      return { ok: false, error: "windowStart/windowEnd must be ISO timestamps" };
    }
  }
  return {
    ok: true,
    body: {
      challengePubkey: b.challengePubkey,
      walletPubkey: b.walletPubkey,
      usedSeconds: b.usedSeconds,
      tamperSignals: tamperSignals as string[],
      windowStart: b.windowStart as string | undefined,
      windowEnd: b.windowEnd as string | undefined,
    },
  };
}

async function handleSubmitReport(req: Request): Promise<Response> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "invalid JSON" }, 400);
  }
  const parsed = validateReport(raw);
  if (!parsed.ok) return json({ error: parsed.error }, 400);
  const b = parsed.body;

  const { data, error } = await db
    .from("usage_reports")
    .insert({
      challenge_pubkey: b.challengePubkey,
      wallet_pubkey: b.walletPubkey,
      used_seconds: b.usedSeconds,
      tamper_signals: b.tamperSignals ?? [],
      window_start: b.windowStart ?? null,
      window_end: b.windowEnd ?? null,
    })
    .select("id")
    .single();
  if (error) return json({ error: error.message }, 500);
  return json({ ok: true, id: data.id });
}

interface RegisterChallengeBody {
  challengePubkey: string;
  creatorPubkey: string;
  opponentPubkey: string;
  targetApp: string;
  limitSeconds: number;
  windowSeconds: number;
  stakeAmount: number;
  endTime?: string;
}

async function handleRegisterChallenge(req: Request): Promise<Response> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "invalid JSON" }, 400);
  }
  if (typeof raw !== "object" || raw === null) return json({ error: "body must be a JSON object" }, 400);
  const b = raw as Record<string, unknown>;
  const str = (v: unknown, name: string) =>
    typeof v === "string" && v.length > 0 && v.length <= 200 ? null : `${name} must be a 1..200 char string`;
  const num = (v: unknown, name: string, max: number) =>
    typeof v === "number" && Number.isInteger(v) && v > 0 && v <= max ? null : `${name} must be an integer 1..${max}`;

  const errors = [
    typeof b.challengePubkey === "string" && BASE58.test(b.challengePubkey) ? null : "challengePubkey must be base58",
    typeof b.creatorPubkey === "string" && BASE58.test(b.creatorPubkey) ? null : "creatorPubkey must be base58",
    typeof b.opponentPubkey === "string" && BASE58.test(b.opponentPubkey) ? null : "opponentPubkey must be base58",
    str(b.targetApp, "targetApp"),
    num(b.limitSeconds, "limitSeconds", 604800),
    num(b.windowSeconds, "windowSeconds", 604800),
    num(b.stakeAmount, "stakeAmount", Number.MAX_SAFE_INTEGER),
  ].filter(Boolean);
  if (errors.length > 0) return json({ error: errors.join("; ") }, 400);

  const { error } = await db.from("challenges").upsert(
    {
      challenge_pubkey: b.challengePubkey as string,
      creator_pubkey: b.creatorPubkey as string,
      opponent_pubkey: b.opponentPubkey as string,
      target_app: b.targetApp as string,
      limit_seconds: b.limitSeconds as number,
      window_seconds: b.windowSeconds as number,
      stake_amount: b.stakeAmount as number,
      end_time: typeof b.endTime === "string" ? b.endTime : null,
      status: "ACTIVE",
    },
    { onConflict: "challenge_pubkey" },
  );
  if (error) return json({ error: error.message }, 500);
  return json({ ok: true });
}

function verdictOf(
  report: { used_seconds: number; tamper_signals: string[] | null } | undefined,
  limitSeconds: number,
): Verdict {
  if (!report) return "UNVERIFIED";
  return determineResult(
    { usedSeconds: report.used_seconds, tamperSignals: report.tamper_signals ?? [] },
    { limitSeconds },
  );
}

async function handleSettle(req: Request): Promise<Response> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return json({ error: "invalid JSON" }, 400);
  }
  const pk = (raw as Record<string, unknown> | null)?.challengePubkey;
  if (typeof pk !== "string" || !BASE58.test(pk)) {
    return json({ error: "challengePubkey must be base58 (32-44 chars)" }, 400);
  }

  const { data: challenge, error: chErr } = await db
    .from("challenges")
    .select("creator_pubkey, opponent_pubkey, limit_seconds, status")
    .eq("challenge_pubkey", pk)
    .maybeSingle();
  if (chErr) return json({ error: chErr.message }, 500);
  if (!challenge) return json({ error: "challenge not found - call register-challenge first" }, 404);
  if (challenge.status !== "ACTIVE") {
    return json({ error: `challenge is ${challenge.status}, expected ACTIVE` }, 409);
  }

  const { data: reports, error: rErr } = await db
    .from("usage_reports")
    .select("wallet_pubkey, used_seconds, tamper_signals, reported_at")
    .eq("challenge_pubkey", pk)
    .order("reported_at", { ascending: false });
  if (rErr) return json({ error: rErr.message }, 500);

  const latest = (pubkey: string) =>
    (reports ?? []).find((r) => r.wallet_pubkey === pubkey);

  const creatorVerdict = verdictOf(latest(challenge.creator_pubkey), challenge.limit_seconds);
  const opponentVerdict = verdictOf(latest(challenge.opponent_pubkey), challenge.limit_seconds);
  const outcome = settleOutcome(creatorVerdict, opponentVerdict);

  const { error: upErr } = await db.from("settlement_results").upsert(
    {
      challenge_pubkey: pk,
      status: outcome.status,
      both_fail: outcome.bothFail,
      settled: outcome.settled,
      creator_pubkey: challenge.creator_pubkey,
      opponent_pubkey: challenge.opponent_pubkey,
      creator_result: creatorVerdict,
      opponent_result: opponentVerdict,
      creator_code: outcome.creatorCode,
      opponent_code: outcome.opponentCode,
      status_code: outcome.statusCode,
      settled_at: new Date().toISOString(),
    },
    { onConflict: "challenge_pubkey" },
  );
  if (upErr) return json({ error: upErr.message }, 500);

  const nextStatus = outcome.bothFail ? "BOTH_FAIL_LOCKED" : "SETTLED";
  const { error: stErr } = await db
    .from("challenges")
    .update({ status: nextStatus })
    .eq("challenge_pubkey", pk);
  if (stErr) return json({ error: stErr.message }, 500);

  return json({
    ok: true,
    challengePubkey: pk,
    status: outcome.status,
    bothFail: outcome.bothFail,
    settled: outcome.settled,
    creatorVerdict,
    opponentVerdict,
    creatorResult: outcome.creatorCode,
    opponentResult: outcome.opponentCode,
    settlementResult: outcome.statusCode,
  });
}

function route(pathname: string): string {
  const p = pathname.replace(/^\/oracle/, "").replace(/\/+$/, "");
  return p === "" ? "/health" : p;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS_HEADERS });
  const denied = authorize(req);
  if (denied) return denied;

  const path = route(new URL(req.url).pathname);
  try {
    if (req.method === "GET" && path === "/health") {
      return json({ status: "alive", time: new Date().toISOString() });
    }
    if (req.method === "POST" && path === "/submit-report") {
      return await handleSubmitReport(req);
    }
    if (req.method === "POST" && path === "/register-challenge") {
      return await handleRegisterChallenge(req);
    }
    if (req.method === "POST" && path === "/settle") {
      return await handleSettle(req);
    }
    return json({ error: `no route for ${req.method} ${path}` }, 404);
  } catch (e) {
    return json({ error: e instanceof Error ? e.message : "internal error" }, 500);
  }
});
