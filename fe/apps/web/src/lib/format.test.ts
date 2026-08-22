import { describe, expect, it } from "vitest";

import { formatXu } from "./format";

describe("formatXu", () => {
  // A combo price runs to five figures; "37450" is read wrong at a glance.
  it("groups thousands", () => {
    expect(formatXu(37450)).toBe("37,450");
    expect(formatXu(30000)).toBe("30,000");
    expect(formatXu(1234567)).toBe("1,234,567");
  });

  it("leaves small amounts alone", () => {
    expect(formatXu(0)).toBe("0");
    expect(formatXu(999)).toBe("999");
  });

  // Prices arrive from form inputs as strings as often as numbers.
  it("accepts a numeric string", () => {
    expect(formatXu("30000")).toBe("30,000");
  });

  // A missing price must read as free, never as "NaN".
  it("reads a missing or unusable amount as zero", () => {
    expect(formatXu(null)).toBe("0");
    expect(formatXu(undefined)).toBe("0");
    expect(formatXu("khong-phai-so")).toBe("0");
  });

  it("does not show fractions of a coin", () => {
    expect(formatXu(1500.6)).toBe("1,501");
  });
});
