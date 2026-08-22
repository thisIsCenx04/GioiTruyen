// Bulk chapter selection and pricing.
//
// A finished story runs to hundreds of chapters shown twenty to a page, so
// "select all" and "tick one at a time" were the only two ways to reach a set
// of them - pricing a single volume meant twenty clicks and a mistake nobody
// would notice until a reader was charged for a chapter that should have been
// free.
import { describe, expect, it } from "vitest";

import { chapterRangeIndices, freeThenPaidPricing } from "./publishing-workspace";

const chapters = (count: number) =>
  Array.from({ length: count }, () => ({ accessType: "FREE" as const, coinPrice: 0 }));

describe("chapterRangeIndices", () => {
  it("covers the run, inclusive of both ends", () => {
    expect(chapterRangeIndices(2, 4, 10)).toEqual([1, 2, 3]);
  });

  it("selects a single chapter when both ends match", () => {
    expect(chapterRangeIndices(3, 3, 10)).toEqual([2]);
  });

  /** Typed the wrong way round is still the run the publisher meant. */
  it("does not care which end was typed first", () => {
    expect(chapterRangeIndices(6, 2, 10)).toEqual(chapterRangeIndices(2, 6, 10));
  });

  /** Asking past the end selects up to the end rather than nothing. */
  it("clamps to the chapters that exist", () => {
    expect(chapterRangeIndices(8, 999, 10)).toEqual([7, 8, 9]);
    expect(chapterRangeIndices(0, 2, 10)).toEqual([0, 1]);
  });

  it("selects nothing when there is nothing to select", () => {
    expect(chapterRangeIndices(1, 5, 0)).toEqual([]);
    expect(chapterRangeIndices(Number.NaN, 5, 10)).toEqual([]);
  });

  it("selects nothing when the run starts past the end", () => {
    expect(chapterRangeIndices(20, 30, 10)).toEqual([]);
  });
});

describe("freeThenPaidPricing", () => {
  it("leaves the opening chapters free and prices the rest", () => {
    const priced = freeThenPaidPricing(chapters(5), 2, 7);
    expect(priced.map((chapter) => chapter.accessType))
      .toEqual(["FREE", "FREE", "PAID", "PAID", "PAID"]);
    expect(priced.map((chapter) => chapter.coinPrice)).toEqual([0, 0, 7, 7, 7]);
  });

  it("can price every chapter", () => {
    const priced = freeThenPaidPricing(chapters(3), 0, 5);
    expect(priced.every((chapter) => chapter.accessType === "PAID")).toBe(true);
  });

  /**
   * A PAID chapter priced at zero unlocks for nothing, and the server refuses
   * it. Zero has to mean free, not "paid but worthless".
   */
  it("treats a price of zero as free rather than as a paid chapter", () => {
    const priced = freeThenPaidPricing(chapters(3), 0, 0);
    expect(priced.every((chapter) => chapter.accessType === "FREE")).toBe(true);
    expect(priced.every((chapter) => chapter.coinPrice === 0)).toBe(true);
  });

  it("leaves everything free when the free count covers the story", () => {
    const priced = freeThenPaidPricing(chapters(3), 10, 9);
    expect(priced.every((chapter) => chapter.accessType === "FREE")).toBe(true);
  });

  it("ignores a negative count or price rather than inverting the rule", () => {
    const priced = freeThenPaidPricing(chapters(2), -5, -1);
    expect(priced.every((chapter) => chapter.accessType === "FREE")).toBe(true);
  });

  /** Pricing must not disturb anything else the chapter carries. */
  it("keeps the rest of each chapter untouched", () => {
    const rows = [{ accessType: "FREE" as const, coinPrice: 0, title: "Chương 1", id: "a" }];
    expect(freeThenPaidPricing(rows, 0, 3)[0]).toMatchObject({ id: "a", title: "Chương 1" });
  });

  it("does not change the array it was given", () => {
    const rows = chapters(2);
    freeThenPaidPricing(rows, 0, 5);
    expect(rows[0]?.accessType).toBe("FREE");
  });
});
