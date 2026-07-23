import { describe, expect, it } from "vitest";

import {
  inspectActionReference,
  inspectWorkflowSource,
} from "../../scripts/enforce-action-pinning.mjs";

describe("GitHub Action pinning policy", () => {
  it("accepts local actions and immutable external references", () => {
    expect(inspectActionReference("./.github/actions/build")).toBeNull();
    expect(
      inspectActionReference(
        "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
      ),
    ).toBeNull();
    expect(
      inspectActionReference(
        "docker://alpine@sha256:"
          + "0123456789abcdef0123456789abcdef"
          + "0123456789abcdef0123456789abcdef",
      ),
    ).toBeNull();
  });

  it("rejects mutable tags and reports the workflow line", () => {
    const violations = inspectWorkflowSource(
      "security.yml",
      [
        "steps:",
        "  - uses: actions/checkout@v7",
        "  - uses: ./.github/actions/build",
      ].join("\n"),
    );

    expect(violations).toEqual([
      {
        filePath: "security.yml",
        line: 2,
        message: "GitHub Action is not pinned to a full commit SHA",
        reference: "actions/checkout@v7",
      },
    ]);
  });
});
