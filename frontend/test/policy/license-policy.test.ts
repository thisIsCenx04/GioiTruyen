import { describe, expect, it } from "vitest";

import { findBlockingLicenseFindings } from "../../scripts/enforce-license-policy.mjs";

describe("dependency license policy", () => {
  it("allows the reviewed optional Sharp Windows runtime finding", () => {
    expect(
      findBlockingLicenseFindings({
        Results: [
          {
            Licenses: [
              {
                Severity: "HIGH",
                PkgName: "@img/sharp-win32-x64",
                Name: "Apache-2.0 AND LGPL-3.0-or-later",
              },
            ],
          },
        ],
      }),
    ).toEqual([]);
  });

  it("blocks an unreviewed high or critical restricted license", () => {
    const finding = {
      Severity: "CRITICAL",
      PkgName: "unreviewed-package",
      Name: "AGPL-3.0-only",
    };

    expect(
      findBlockingLicenseFindings({
        Results: [
          {
            Licenses: [finding],
          },
        ],
      }),
    ).toEqual([finding]);
  });
});
