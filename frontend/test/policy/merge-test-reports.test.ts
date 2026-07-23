import { describe, expect, it } from "vitest";

import { mergeJUnitSuites } from "../../scripts/merge-test-reports.mjs";

describe("JUnit report merger", () => {
  it("combines backend and frontend suite totals deterministically", () => {
    const backend =
      '<testsuite name="backend" tests="3" failures="1" errors="0" skipped="0" time="0.4"></testsuite>';
    const frontend =
      '<testsuite name="frontend" tests="2" failures="0" errors="0" skipped="1" time="0.2"></testsuite>';

    const merged = mergeJUnitSuites([backend, frontend]);

    expect(merged).toContain(
      '<testsuites name="GioiTruyen" tests="5" failures="1" errors="0" skipped="1" time="0.600">',
    );
    expect(merged).toContain(backend);
    expect(merged).toContain(frontend);
  });
});
