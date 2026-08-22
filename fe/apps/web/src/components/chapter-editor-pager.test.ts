import { describe, expect, it } from "vitest";

import {
  chapterPageCount,
  chapterPageLabel,
  chapterPageSlice,
  chapterPageWindow,
  CHAPTERS_PER_EDIT_PAGE,
} from "./chapter-editor-pager";

const chapters = (count: number) =>
  Array.from({ length: count }, (_, index) => ({ title: `Chương ${index + 1}` }));

describe("chapterPageCount", () => {
  it("counts a full page as one page", () => {
    expect(chapterPageCount(CHAPTERS_PER_EDIT_PAGE)).toBe(1);
  });

  it("gives a remainder its own page", () => {
    expect(chapterPageCount(CHAPTERS_PER_EDIT_PAGE + 1)).toBe(2);
  });

  // 2,500 chapters is a real story on this site, not a hypothetical.
  it("handles a very long story", () => {
    expect(chapterPageCount(2500)).toBe(125);
  });

  // A story with nothing in it must still render one empty page, not zero.
  it("never reports fewer than one page", () => {
    expect(chapterPageCount(0)).toBe(1);
  });
});

describe("chapterPageSlice", () => {
  it("returns the chapters on the page", () => {
    const page = chapterPageSlice(chapters(50), 2);
    expect(page).toHaveLength(CHAPTERS_PER_EDIT_PAGE);
    expect(page[0]?.chapter.title).toBe("Chương 21");
  });

  /**
   * The index is the position in the whole list, not in the page. Editing
   * chapter 21 must write to entry 20 of the draft array; using the position
   * within the page would silently rewrite chapter 1 instead.
   */
  it("reports each chapter's index in the full list", () => {
    const page = chapterPageSlice(chapters(50), 3);
    expect(page[0]?.index).toBe(40);
    expect(page.at(-1)?.index).toBe(49);
  });

  it("gives the last page only what is left", () => {
    expect(chapterPageSlice(chapters(45), 3)).toHaveLength(5);
  });

  it("returns nothing past the end", () => {
    expect(chapterPageSlice(chapters(10), 5)).toEqual([]);
  });
});

describe("chapterPageWindow", () => {
  // 125 pages listed in full is the same scrolling problem one level up.
  it("shows the ends and a window around the current page", () => {
    expect(chapterPageWindow(60, 125)).toEqual([1, 59, 60, 61, 125]);
  });

  it("does not repeat a page when the window touches an end", () => {
    expect(chapterPageWindow(1, 125)).toEqual([1, 2, 125]);
    expect(chapterPageWindow(125, 125)).toEqual([1, 124, 125]);
  });

  it("lists every page when there are few", () => {
    expect(chapterPageWindow(2, 3)).toEqual([1, 2, 3]);
  });

  it("stays inside the range", () => {
    expect(chapterPageWindow(1, 1)).toEqual([1]);
  });
});

/**
 * The label above the chapter list.
 *
 * <p>It used to count positions in the array while the chapters on screen
 * showed their own numbers, so a real 385-chapter story - uploaded from a file
 * that skipped chapter 87, and therefore numbered up to 386 - displayed
 * "Chương 381–385 / 385" above a chapter titled "Chương 386".
 */
describe("chapterPageLabel", () => {
  it("names the page by the chapters' own numbers", () => {
    const titles = Array.from({ length: 40 }, (_, index) => `Chương ${index + 1}: A`);
    expect(chapterPageLabel(titles, 1, 40)).toBe("Chương 1–20 · 40 chương");
    expect(chapterPageLabel(titles, 2, 40)).toBe("Chương 21–40 · 40 chương");
  });

  /** The reported case: 385 chapters, numbered to 386 because 87 is missing. */
  it("follows numbering that skips a chapter", () => {
    const titles: string[] = [];
    for (let number = 1; number <= 386; number += 1) {
      if (number === 87) continue;
      titles.push(`Chương ${number}: Phần`);
    }
    expect(titles).toHaveLength(385);
    // Last page: positions 381-385, whose own numbers are 382-386.
    expect(chapterPageLabel(titles, 20, 385)).toBe("Chương 382–386 · 385 chương");
  });

  /** A story whose file begins at chapter 40 is a later volume, not chapter 1. */
  it("follows numbering that does not start at one", () => {
    const titles = Array.from({ length: 20 }, (_, index) => `Chương ${index + 40}: B`);
    expect(chapterPageLabel(titles, 1, 20)).toBe("Chương 40–59 · 20 chương");
  });

  /** Untitled drafts have no number to read, so position is all there is. */
  it("falls back to position when a title carries no number", () => {
    expect(chapterPageLabel(["", "", ""], 1, 3)).toBe("Chương 1–3 · 3 chương");
  });

  it("says nothing about titles it was not given", () => {
    expect(chapterPageLabel([], 1, 50)).toBe("Chương 1–20 · 50 chương");
  });
});
