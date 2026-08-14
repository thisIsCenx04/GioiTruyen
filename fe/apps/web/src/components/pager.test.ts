import { describe, expect, it } from "vitest";

import { pageWindow } from "./pager";

describe("pageWindow", () => {
  it("lists every page while they all fit", () => {
    expect(pageWindow(1, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it("keeps the last page reachable from the first", () => {
    // 1000 chapters at 20 a page is 50 pages: the reader must be able to jump.
    expect(pageWindow(1, 50)).toEqual([1, 2, 3, null, 50]);
  });

  it("keeps the first page reachable from the last", () => {
    expect(pageWindow(50, 50)).toEqual([1, null, 48, 49, 50]);
  });

  it("windows around the middle with both ends present", () => {
    expect(pageWindow(25, 50)).toEqual([1, null, 23, 24, 25, 26, 27, null, 50]);
  });

  it("writes out a single skipped page instead of eliding it", () => {
    // Page 4 is the only gap, and "…" would take the same room as "4".
    expect(pageWindow(6, 20)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, null, 20]);
  });

  it("renders nothing to page through", () => {
    expect(pageWindow(1, 1)).toEqual([1]);
    expect(pageWindow(1, 0)).toEqual([]);
  });
});
