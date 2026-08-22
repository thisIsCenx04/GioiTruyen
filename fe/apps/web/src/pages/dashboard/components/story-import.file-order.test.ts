// A story is often kept as one file per chapter, and the publisher selects the
// whole folder at once. The file picker hands them over in the operating
// system's order, which is alphabetical - so "Chương 10" arrives before
// "Chương 2" and the story is published with its chapters scrambled from the
// tenth onwards. These cover the order the files are actually read in.
import { describe, expect, it } from "vitest";

import {
  chapterNumberFromFileName,
  chapterTitleFromFileName,
  chapterTitlesForUpload,
  lastChapterNumber,
  sortChapterFiles,
} from "./story-import";

const order = (names: string[]) =>
  sortChapterFiles(names.map((name) => ({ name }))).map((file) => file.name);

describe("chapterNumberFromFileName", () => {
  it("reads the number after a chapter word", () => {
    expect(chapterNumberFromFileName("Chương 12.docx")).toBe(12);
    expect(chapterNumberFromFileName("chuong-7-ten-chuong.txt")).toBe(7);
    expect(chapterNumberFromFileName("Chapter 3.docx")).toBe(3);
    expect(chapterNumberFromFileName("C005.docx")).toBe(5);
  });

  /** A side story sits between two chapters and is numbered to say so. */
  it("keeps a decimal number", () => {
    expect(chapterNumberFromFileName("Chương 12.5 - Ngoại truyện.docx")).toBe(12.5);
    expect(chapterNumberFromFileName("Chuong 12,5.docx")).toBe(12.5);
  });

  /** The chapter word decides, so a volume number does not win. */
  it("prefers the chapter number over another number in the name", () => {
    expect(chapterNumberFromFileName("Quyen 3 - Chuong 12.docx")).toBe(12);
  });

  it("falls back to a bare number", () => {
    expect(chapterNumberFromFileName("012.docx")).toBe(12);
    expect(chapterNumberFromFileName("42 - Tên chương.txt")).toBe(42);
  });

  it("reports nothing when the name carries no number", () => {
    expect(chapterNumberFromFileName("mo-dau.docx")).toBeNull();
    expect(chapterNumberFromFileName("Ngoại truyện.txt")).toBeNull();
  });

  /** The extension is not the chapter number. */
  it("ignores the extension", () => {
    expect(chapterNumberFromFileName("mo-dau.mp3")).toBeNull();
  });
});

describe("sortChapterFiles", () => {
  it("puts ten after two, which alphabetical order does not", () => {
    expect(order(["Chương 10.docx", "Chương 2.docx", "Chương 1.docx"])).toEqual([
      "Chương 1.docx",
      "Chương 2.docx",
      "Chương 10.docx",
    ]);
  });

  it("orders a hundred chapters by number, not by name", () => {
    const names = Array.from({ length: 100 }, (_, index) => `Chuong ${index + 1}.txt`);
    const shuffled = [...names].sort();
    expect(order(shuffled)).toEqual(names);
  });

  it("slots a side story between its neighbours", () => {
    expect(order(["Chương 13.docx", "Chương 12.5.docx", "Chương 12.docx"])).toEqual([
      "Chương 12.docx",
      "Chương 12.5.docx",
      "Chương 13.docx",
    ]);
  });

  /**
   * A file with no number cannot be placed by number, so it goes last rather
   * than landing somewhere arbitrary in the middle of the story.
   */
  it("puts unnumbered files after the numbered ones", () => {
    expect(order(["Ngoại truyện.docx", "Chương 2.docx", "Chương 1.docx"])).toEqual([
      "Chương 1.docx",
      "Chương 2.docx",
      "Ngoại truyện.docx",
    ]);
  });

  it("sorts unnumbered files naturally among themselves", () => {
    expect(order(["Phần B.docx", "Phần A.docx"])).toEqual(["Phần A.docx", "Phần B.docx"]);
  });

  it("does not change the given array", () => {
    const files = [{ name: "Chương 2.docx" }, { name: "Chương 1.docx" }];
    sortChapterFiles(files);
    expect(files[0]?.name).toBe("Chương 2.docx");
  });
});

describe("chapterTitleFromFileName", () => {
  it("keeps the number the filename declares and the name beside it", () => {
    expect(chapterTitleFromFileName("Chương 12 - Gặp lại.docx", 99)).toBe("Chương 12: Gặp lại");
    expect(chapterTitleFromFileName("chuong-7-ten-chuong.txt", 99)).toBe("Chương 7: ten-chuong");
  });

  it("uses the fallback number when the filename has none", () => {
    expect(chapterTitleFromFileName("Bung ra chuong.docx", 75)).toBe("Chương 75: Bung ra chuong");
  });

  it("gives a bare number a title of its own", () => {
    expect(chapterTitleFromFileName("012.docx", 99)).toBe("Chương 12");
  });

  it("keeps a side story's decimal", () => {
    expect(chapterTitleFromFileName("Chương 12.5 - Ngoại truyện.docx", 99))
      .toBe("Chương 12.5: Ngoại truyện");
  });
});

describe("lastChapterNumber", () => {
  it("reads the highest number from the titles", () => {
    expect(lastChapterNumber([{ title: "Chương 1: A" }, { title: "Chương 74: B" }])).toBe(74);
  });

  /** A file that started at chapter 40 must not be renumbered from its length. */
  it("follows the numbering, not the row count", () => {
    expect(lastChapterNumber([{ title: "Chương 40: A" }, { title: "Chương 41: B" }])).toBe(41);
  });

  it("falls back to position for an untitled row", () => {
    expect(lastChapterNumber([{ title: "" }, { title: "" }, { title: "" }])).toBe(3);
  });

  it("is zero for an empty story", () => {
    expect(lastChapterNumber([])).toBe(0);
  });
});

describe("chapterTitlesForUpload", () => {
  /** The case from the report: 74 chapters, one more file with no number. */
  it("numbers an unnumbered file on from the last chapter", () => {
    expect(chapterTitlesForUpload([{ name: "Bung ra chuong.docx" }], 75))
      .toEqual(["Chương 75: Bung ra chuong"]);
  });

  it("numbers a batch of unnumbered files consecutively", () => {
    expect(chapterTitlesForUpload(
      [{ name: "mot.docx" }, { name: "hai.docx" }, { name: "ba.docx" }],
      75,
    )).toEqual(["Chương 75: mot", "Chương 76: hai", "Chương 77: ba"]);
  });

  /** A file naming its own chapter is believed, whatever the running count. */
  it("leaves a declared number alone", () => {
    expect(chapterTitlesForUpload(
      [{ name: "Chương 3 - Ba.docx" }, { name: "khong-so.docx" }],
      75,
    )).toEqual(["Chương 3: Ba", "Chương 75: khong-so"]);
  });

  /** Two chapters must never land on the same number. */
  it("skips a number another file in the batch already claims", () => {
    expect(chapterTitlesForUpload(
      [{ name: "khong-so.docx" }, { name: "Chương 75 - Bảy Lăm.docx" }],
      75,
    )).toEqual(["Chương 76: khong-so", "Chương 75: Bảy Lăm"]);
  });
});
