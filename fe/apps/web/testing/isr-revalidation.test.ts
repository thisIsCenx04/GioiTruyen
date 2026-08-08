import { describe, expect, it } from "vitest";
import {
  authorizeRevalidation,
  validRevalidationBody,
} from "./isr-revalidation";

describe("ISR revalidation boundary", () => {
  const token = "x".repeat(32);

  it("requires an exact constant-time bearer token", () => {
    expect(authorizeRevalidation(`Bearer ${token}`, token)).toBe(true);
    expect(authorizeRevalidation("Bearer wrong", token)).toBe(false);
    expect(authorizeRevalidation(null, token)).toBe(false);
    expect(authorizeRevalidation(`Bearer ${token}`, "short")).toBe(false);
  });

  it("accepts only bounded event and target identifiers", () => {
    expect(
      validRevalidationBody({
        eventId: "event-1",
        targets: ["chapter:chapter-1"],
      }),
    ).toBe(true);
    expect(validRevalidationBody({ eventId: "../bad", targets: [] })).toBe(
      false,
    );
    expect(
      validRevalidationBody({
        eventId: "event-1",
        targets: Array.from({ length: 101 }, () => "story:one"),
      }),
    ).toBe(false);
  });
});
