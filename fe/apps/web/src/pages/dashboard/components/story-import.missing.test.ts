// A story can be uploaded, saved and published while missing chapters from the
// middle of it. One real file ran from "Chương 1" to "Chương 386" with no
// chapter 87 in it at all. Nothing about the saved story reveals that: the
// chapter_number column is reassigned by position on save, so it always reads
// as a gap-free 1…N. The holes are only visible in the titles.
import { describe, expect, it } from "vitest";

import { chapterNumberGaps, missingChapterNumbers } from "./story-import";

describe("chapterNumberGaps", () => {
  it("finds nothing in a continuous run", () => {
    expect(chapterNumberGaps([1, 2, 3, 4])).toEqual([]);
  });

  it("finds a single hole", () => {
    expect(chapterNumberGaps([1, 2, 4])).toEqual([3]);
  });

  it("finds several holes, in order", () => {
    expect(chapterNumberGaps([1, 5, 6, 9])).toEqual([2, 3, 4, 7, 8]);
  });

  /** A story starting at 40 is a later volume, not one missing 1-39. */
  it("ignores what is outside the range", () => {
    expect(chapterNumberGaps([40, 41, 42])).toEqual([]);
  });

  it("has nothing to say about a single chapter", () => {
    expect(chapterNumberGaps([7])).toEqual([]);
    expect(chapterNumberGaps([])).toEqual([]);
  });

  /** Chapters do not have to arrive sorted for the holes to be right. */
  it("does not depend on the order given", () => {
    expect(chapterNumberGaps([4, 1, 2])).toEqual([3]);
  });
});

describe("missingChapterNumbers", () => {
  /** The shape a publisher describes: "thiếu chương 7, 10, 11, 100". */
  it("reads the holes out of the chapter titles", () => {
    const titles: string[] = [];
    for (let number = 1; number <= 120; number += 1) {
      if ([7, 10, 11, 100].includes(number)) continue;
      titles.push(`Chương ${number}: Nội dung`);
    }
    expect(missingChapterNumbers(titles)).toEqual([7, 10, 11, 100]);
  });

  /** The 385-chapter story that skips 87 and therefore runs to 386. */
  it("finds the one chapter a 385-chapter story is missing", () => {
    const titles: string[] = [];
    for (let number = 1; number <= 386; number += 1) {
      if (number === 87) continue;
      titles.push(`Chương ${number}: Phần`);
    }
    expect(titles).toHaveLength(385);
    expect(missingChapterNumbers(titles)).toEqual([87]);
  });

  it("says nothing about a complete story", () => {
    const titles = Array.from({ length: 50 }, (_, index) => `Chương ${index + 1}`);
    expect(missingChapterNumbers(titles)).toEqual([]);
  });

  /** Untitled drafts carry no number, so there is nothing to conclude. */
  it("ignores titles with no chapter number", () => {
    expect(missingChapterNumbers(["Lời tựa", "Ngoại truyện"])).toEqual([]);
  });

  /** A title without a number must not be read as a hole in the numbering. */
  it("does not count an unnumbered chapter as missing", () => {
    expect(missingChapterNumbers(["Chương 1", "Ngoại truyện", "Chương 2"])).toEqual([]);
  });
});
