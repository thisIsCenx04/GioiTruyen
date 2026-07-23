import { describe, expect, it } from "vitest";

import {
  assertSeededFindingWasBlocked,
  buildSeededSecret,
  SEEDED_EXIT_CODE,
  SEEDED_RULE_ID,
} from "../../scripts/verify-secret-gate.mjs";

describe("secret scanning negative control", () => {
  it("assembles a deterministic value without storing a usable secret", () => {
    const seededSecret = buildSeededSecret();

    expect(seededSecret).toHaveLength(55);
    expect(seededSecret.startsWith("GIOITRUYEN_TEST_")).toBe(true);
  });

  it("accepts only the dedicated scanner exit code and seeded rule", () => {
    expect(() =>
      assertSeededFindingWasBlocked(SEEDED_EXIT_CODE, [
        {
          RuleID: SEEDED_RULE_ID,
        },
      ]),
    ).not.toThrow();

    expect(() => assertSeededFindingWasBlocked(0, [])).toThrow(
      "instead of",
    );
    expect(() =>
      assertSeededFindingWasBlocked(SEEDED_EXIT_CODE, []),
    ).toThrow("did not report");
  });
});
