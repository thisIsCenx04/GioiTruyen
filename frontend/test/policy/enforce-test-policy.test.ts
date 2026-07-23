import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { inspectTestSource } from "../../scripts/enforce-test-policy.mjs";

describe("deterministic test policy", () => {
  it("blocks the seeded focused, sleeping, network-dependent test", async () => {
    const fixturePath = resolve(
      "test/policy/fixtures/flaky.test.ts.fixture",
    );
    const fixture = await readFile(fixturePath, "utf8");

    expect(
      inspectTestSource(fixturePath, fixture).map(({ code }) => code),
    ).toEqual([
      "test-file-name",
      "focused-or-disabled",
      "real-time-wait",
      "direct-network",
    ]);
  });
});
