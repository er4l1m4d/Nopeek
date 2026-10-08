import { test } from "node:test";
import assert from "node:assert/strict";
import {
  determineResult,
  settleOutcome,
  type Verdict,
  type UsageReport,
} from "./determine_result.ts";

const rule = { limitSeconds: 1800 };

function report(usedSeconds: number, tamperSignals: string[] = []): UsageReport {
  return { usedSeconds, tamperSignals };
}

test("usedSeconds <= limit -> PASS", () => {
  assert.equal(determineResult(report(1800), rule), "PASS");
  assert.equal(determineResult(report(0), rule), "PASS");
  assert.equal(determineResult(report(1799), rule), "PASS");
});

test("usedSeconds > limit -> FAIL", () => {
  assert.equal(determineResult(report(1801), rule), "FAIL");
  assert.equal(determineResult(report(999999), rule), "FAIL");
});

test("PERMISSION_REVOKED -> UNVERIFIED", () => {
  assert.equal(determineResult(report(100, ["PERMISSION_REVOKED"]), rule), "UNVERIFIED");
});

test("CLOCK_CHANGED -> UNVERIFIED", () => {
  assert.equal(determineResult(report(100, ["CLOCK_CHANGED"]), rule), "UNVERIFIED");
});

test("UNVERIFIED wins over usage numbers even when under limit", () => {
  assert.equal(
    determineResult(report(10, ["PERMISSION_REVOKED", "APP_UNINSTALLED"]), rule),
    "UNVERIFIED",
  );
});

test("CLOCK_JUMPED_FORWARD -> UNVERIFIED", () => {
  // The forward jump is the attack that ends a window early or back-dates a session.
  // It reaches the oracle by name, so the name has to be blocking here or the signal
  // is decoration: the device would flag itself and still keep its stake.
  assert.equal(determineResult(report(10, ["CLOCK_JUMPED_FORWARD"]), rule), "UNVERIFIED");
});

test("REPORT_LATE -> UNVERIFIED", () => {
  assert.equal(determineResult(report(10, ["REPORT_LATE"]), rule), "UNVERIFIED");
});

test("an unrecognised tamper name fails closed", () => {
  // Not a policy hole, a name the app has not invented yet. The device owns this
  // vocabulary, so an allowlist here means a new tamper flag is silently a no-op
  // until someone remembers to edit the oracle. Everything this system has decided
  // about inputs it cannot positively establish is: reject.
  assert.equal(determineResult(report(10, ["APP_UNINSTALLED"]), rule), "UNVERIFIED");
  assert.equal(determineResult(report(10, ["SIGNAL_INVENTED_NEXT_YEAR"]), rule), "UNVERIFIED");
  // And an empty list still means no tamper was found.
  assert.equal(determineResult(report(10, []), rule), "PASS");
});

test("creator PASS, opponent FAIL -> CREATOR_WON with codes 1/2", () => {
  const o = settleOutcome("PASS", "FAIL");
  assert.equal(o.status, "CREATOR_WON");
  assert.equal(o.bothFail, false);
  assert.equal(o.creatorCode, 1);
  assert.equal(o.opponentCode, 2);
});

test("creator FAIL, opponent PASS -> OPPONENT_WON with codes 2/1", () => {
  const o = settleOutcome("FAIL", "PASS");
  assert.equal(o.status, "OPPONENT_WON");
  assert.equal(o.creatorCode, 2);
  assert.equal(o.opponentCode, 1);
});

test("both PASS -> BOTH_PASS_REFUND, settled", () => {
  const o = settleOutcome("PASS", "PASS");
  assert.equal(o.status, "BOTH_PASS_REFUND");
  assert.equal(o.settled, true);
  assert.equal(o.bothFail, false);
  assert.equal(o.creatorCode, 1);
  assert.equal(o.opponentCode, 1);
});

test("both FAIL -> BOTH_FAIL_LOCKED, not settled", () => {
  const o = settleOutcome("FAIL", "FAIL");
  assert.equal(o.status, "BOTH_FAIL_LOCKED");
  assert.equal(o.bothFail, true);
  assert.equal(o.settled, false);
});

test("UNVERIFIED counts as FAIL for outcome but keeps code 3", () => {
  const c = settleOutcome("UNVERIFIED", "FAIL" as Verdict);
  assert.equal(c.status, "BOTH_FAIL_LOCKED");
  assert.equal(c.bothFail, true);
  assert.equal(c.creatorCode, 3);
  assert.equal(c.opponentCode, 2);

  const d = settleOutcome("UNVERIFIED", "PASS");
  assert.equal(d.status, "OPPONENT_WON");
  assert.equal(d.creatorCode, 3);
  assert.equal(d.opponentCode, 1);
});

test("statusCode aligns with on-chain settlement result codes", () => {
  assert.equal(settleOutcome("PASS", "FAIL").statusCode, 1); // RESULT_CREATOR_WON
  assert.equal(settleOutcome("FAIL", "PASS").statusCode, 2); // RESULT_OPPONENT_WON
  assert.equal(settleOutcome("PASS", "PASS").statusCode, 3); // RESULT_BOTH_PASS
  assert.equal(settleOutcome("FAIL", "FAIL").statusCode, 4); // RESULT_BOTH_FAIL
  assert.equal(settleOutcome("UNVERIFIED", "UNVERIFIED").statusCode, 4);
});
