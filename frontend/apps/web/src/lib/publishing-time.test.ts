import { describe, expect, it } from "vitest";
import { localDateTimeWithOffset } from "./publishing-time";

describe("publishing local time", () => {
  it("retains the browser offset for backend IANA validation", () => {
    expect(localDateTimeWithOffset("2026-07-25T07:30", -420)).toBe(
      "2026-07-25T07:30:00+07:00",
    );
    expect(localDateTimeWithOffset("2026-12-25T07:30:15", 300)).toBe(
      "2026-12-25T07:30:15-05:00",
    );
  });

  it("rejects malformed or impossible offsets", () => {
    expect(() => localDateTimeWithOffset("tomorrow", 0)).toThrow();
    expect(() => localDateTimeWithOffset("2026-07-25T07:30", 900)).toThrow();
  });
});
