export type Verdict = "PASS" | "FAIL" | "UNVERIFIED";

export interface UsageReport {
  usedSeconds: number;
  tamperSignals: string[];
}

export interface ChallengeRule {
  limitSeconds: number;
}

export interface SettlementOutcome {
  status:
    | "CREATOR_WON"
    | "OPPONENT_WON"
    | "BOTH_PASS_REFUND"
    | "BOTH_FAIL_LOCKED";
  bothFail: boolean;
  settled: boolean;
  /** Code to sign on-chain: 1=PASS 2=FAIL 3=UNVERIFIED */
  creatorCode: number;
  opponentCode: number;
  /** On-chain settlement result code: 1=CreatorWon 2=OpponentWon 3=BothPass 4=BothFail */
  statusCode: number;
}

export const REPORT_CODE: Record<Verdict, number> = {
  PASS: 1,
  FAIL: 2,
  UNVERIFIED: 3,
};

const BLOCKING_TAMPERS = ["PERMISSION_REVOKED", "CLOCK_CHANGED"];

/** Plan 6.1 Step 3: tamper -> UNVERIFIED; used <= limit -> PASS; else FAIL. */
export function determineResult(
  report: UsageReport,
  rule: ChallengeRule,
): Verdict {
  if (report.tamperSignals.some((s) => BLOCKING_TAMPERS.includes(s))) {
    return "UNVERIFIED";
  }
  return report.usedSeconds <= rule.limitSeconds ? "PASS" : "FAIL";
}

/** Plan 6.2 Step 2: both non-PASS -> BOTH_FAIL_LOCKED. UNVERIFIED counts as non-PASS. */
export function settleOutcome(creator: Verdict, opponent: Verdict): SettlementOutcome {
  const creatorCode = REPORT_CODE[creator];
  const opponentCode = REPORT_CODE[opponent];
  const creatorPass = creator === "PASS";
  const opponentPass = opponent === "PASS";

  if (creatorPass && opponentPass) {
    return {
      status: "BOTH_PASS_REFUND",
      bothFail: false,
      settled: true,
      creatorCode,
      opponentCode,
      statusCode: 3,
    };
  }
  if (creatorPass && !opponentPass) {
    return {
      status: "CREATOR_WON",
      bothFail: false,
      settled: true,
      creatorCode,
      opponentCode,
      statusCode: 1,
    };
  }
  if (!creatorPass && opponentPass) {
    return {
      status: "OPPONENT_WON",
      bothFail: false,
      settled: true,
      creatorCode,
      opponentCode,
      statusCode: 2,
    };
  }
  return {
    status: "BOTH_FAIL_LOCKED",
    bothFail: true,
    settled: false,
    creatorCode,
    opponentCode,
    statusCode: 4,
  };
}
