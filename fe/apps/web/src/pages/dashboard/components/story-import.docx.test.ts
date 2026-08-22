// @vitest-environment jsdom
//
// Runs against real .docx files a publisher actually uploaded, because the
// parser reads a zip and Word's XML - shapes a hand-written fixture does not
// reproduce. The one exception is the auto-split case at the bottom, which
// needs a document with no headings at all; that one is built here so the
// coverage does not depend on a sample file staying in the folder.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { zipSync, strToU8 } from "fflate";
import { describe, expect, it } from "vitest";

import {
  parseStoryDocument,
  readChaptersInFile,
  WORDS_PER_CHAPTER,
  WORDS_PER_CHAPTER_ZHIHU,
} from "./story-import";

function realFile(path: string, name: string) {
  const bytes = readFileSync(path);
  return {
    name,
    arrayBuffer: async () =>
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  } as unknown as File;
}

/**
 * A .docx carrying the given paragraphs, none of them styled as a heading.
 *
 * <p>Only word/document.xml is written: that is the single entry the reader
 * opens, so a fuller package would add nothing but noise to the fixture.
 */
function docxOf(paragraphs: readonly string[], name: string) {
  const body = paragraphs
    .map((text) => `<w:p><w:r><w:t xml:space="preserve">${text}</w:t></w:r></w:p>`)
    .join("");
  const xml = `<?xml version="1.0" encoding="UTF-8"?>`
    + `<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">`
    + `<w:body>${body}</w:body></w:document>`;
  const bytes = zipSync({ "word/document.xml": strToU8(xml) });
  return {
    name,
    arrayBuffer: async () =>
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  } as unknown as File;
}

/** Eight hundred words of filler, so a block is worth about one chapter. */
function prose(marker: string, words: number): string {
  return `${marker} ${Array.from({ length: words }, (_, index) => `từ${index}`).join(" ")}`;
}

describe("parseStoryDocument on a document with no real headings", () => {
  /**
   * A document with no "Chương N" line anywhere still has to arrive as readable
   * chapters, so the word budget cuts it. One real 96,000-word upload contained
   * a stray "1" on its own line and a numbered list item ("3. Mua cùng lúc bún
   * huyết vịt…"), both of which matched the bare-number heading rule; it was
   * read as two chapters of 48,000 words, too large for the database column and
   * unreadable regardless.
   */
  const unmarked = () =>
    docxOf(
      [
        "Truyện không đánh dấu chương",
        "1",
        ...Array.from({ length: 40 }, (_, index) => prose(`Đoạn ${index}.`, 400)),
        "3. Mua cùng lúc bún huyết vịt và chè đậu xanh.",
        ...Array.from({ length: 40 }, (_, index) => prose(`Đoạn sau ${index}.`, 400)),
      ],
      "khong-danh-dau.docx",
    );

  it("does not mistake a stray number or a list item for a chapter", async () => {
    const imported = await parseStoryDocument(unmarked(), WORDS_PER_CHAPTER);
    expect(imported.autoSplit).toBe(true);
    expect(imported.chapters.length).toBeGreaterThan(20);
  }, 30_000);

  // Every chapter has to fit what a chapter is, so none can be refused on save.
  it("keeps every chapter near the word budget", async () => {
    const imported = await parseStoryDocument(unmarked(), WORDS_PER_CHAPTER);
    for (const chapter of imported.chapters) {
      const words = chapter.content.split(/\s+/u).filter(Boolean).length;
      expect(words).toBeLessThan(WORDS_PER_CHAPTER * 2);
    }
  }, 30_000);

  // A Zhihu story is cut at the longer budget, from the same document.
  it("uses the Zhihu budget when asked for it", async () => {
    const normal = await parseStoryDocument(unmarked(), WORDS_PER_CHAPTER);
    const zhihu = await parseStoryDocument(unmarked(), WORDS_PER_CHAPTER_ZHIHU);
    expect(zhihu.chapters.length).toBeLessThan(normal.chapters.length);
  }, 30_000);

  it("loses no text to the split", async () => {
    const imported = await parseStoryDocument(unmarked(), WORDS_PER_CHAPTER);
    const total = imported.chapters.reduce(
      (sum, chapter) => sum + chapter.content.split(/\s+/u).filter(Boolean).length,
      0,
    );
    // 80 paragraphs of 400 filler words plus their markers, less the header
    // line the parser takes as the title.
    expect(total).toBeGreaterThan(32_000);
  }, 30_000);
});


/**
 * A .docx whose chapter lines carry Word's Heading 1 style.
 *
 * <p>outlineLvl is the language-neutral marker; the style id is localised, so
 * both are written the way Word writes them.
 */
function docxWithHeadings(rows: readonly { text: string; heading?: boolean }[], name: string) {
  const body = rows
    .map(({ text, heading }) => {
      const properties = heading
        ? `<w:pPr><w:pStyle w:val="Heading1"/><w:outlineLvl w:val="0"/></w:pPr>`
        : "";
      return `<w:p>${properties}<w:r><w:t xml:space="preserve">${text}</w:t></w:r></w:p>`;
    })
    .join("");
  const xml = `<?xml version="1.0" encoding="UTF-8"?>`
    + `<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">`
    + `<w:body>${body}</w:body></w:document>`;
  const bytes = zipSync({ "word/document.xml": strToU8(xml) });
  return {
    name,
    arrayBuffer: async () =>
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  } as unknown as File;
}

describe("parseStoryDocument on a Word file laid out with Heading 1", () => {
  /**
   * The shape every publisher file follows: a labelled header block, then
   * chapters marked as headings. The diary lines are the ones that broke it -
   * dates written "17.3.2015." matched the bare-number heading rule, so a real
   * 72-chapter upload arrived as 83 chapters, eleven of them a real chapter cut
   * in half at a date and titled "Chương 17: 3.2015.".
   */
  const sample = () =>
    docxWithHeadings(
      [
        { text: "Tên truyện: Nhật Ký Của Người Gác Đèn" },
        { text: "Tác giả: Vô Danh" },
        { text: "Thể loại: Ngôn Tình, Vô Hạn Lưu, Linh Dị Thần Quái" },
        { text: "Văn án" },
        { text: "Một người gác đèn biển ghi lại những gì mình thấy." },
        { text: "" },
        { heading: true, text: "Chương 1: Ngọn Đèn Đầu Tiên" },
        { text: "17.3.2015." },
        { text: "Biển động suốt đêm, không một con tàu nào đi qua." },
        { text: "24.3.2015." },
        { text: "Có tiếng gõ từ phía dưới mặt nước." },
        { heading: true, text: "Chương 2: Người Khách Lạ" },
        { text: "1.4.2015." },
        { text: "Sáng nay có người lên đảo, không nói mình từ đâu tới." },
      ],
      "nhat-ky.docx",
    );

  it("reads exactly the chapters the file marks up", async () => {
    const imported = await parseStoryDocument(sample(), WORDS_PER_CHAPTER);
    expect(imported.chapters.map((chapter) => chapter.title)).toEqual([
      "Chương 1: Ngọn Đèn Đầu Tiên",
      "Chương 2: Người Khách Lạ",
    ]);
    expect(imported.autoSplit).toBe(false);
  });

  it("does not split a chapter at a date", async () => {
    const imported = await parseStoryDocument(sample(), WORDS_PER_CHAPTER);
    for (const chapter of imported.chapters) {
      expect(chapter.title).not.toMatch(/\d+\.\d{4}/u);
    }
    // The dates stay where they belong: inside the chapter's text.
    expect(imported.chapters[0]?.content).toContain("17.3.2015.");
    expect(imported.chapters[0]?.content).toContain("24.3.2015.");
  });

  // "Tên truyện: ..." is a labelled header line, so the label is not part of
  // the title and the line is not part of chapter one.
  it("takes the story title and author from their labels", async () => {
    const imported = await parseStoryDocument(sample(), WORDS_PER_CHAPTER);
    expect(imported.title).toBe("Nhật Ký Của Người Gác Đèn");
    expect(imported.authorName).toBe("Vô Danh");
  });

  /**
   * "Văn án" sits alone on its line - here without a colon, as publishers
   * routinely write it - and the blurb runs underneath. The label names the
   * synopsis; it is not part of it, and neither is the genre line above it.
   */
  it("takes the synopsis from under its bare label", async () => {
    const imported = await parseStoryDocument(sample(), WORDS_PER_CHAPTER);
    expect(imported.synopsis).toBe("Một người gác đèn biển ghi lại những gì mình thấy.");
    expect(imported.synopsis).not.toContain("Văn án");
    expect(imported.synopsis).not.toContain("Thể loại");
  });

  // The genres a file lists are the genres the form should tick, so they are
  // read rather than left for the publisher to find and re-enter.
  it("reads the genre list the file declares", async () => {
    const imported = await parseStoryDocument(sample(), WORDS_PER_CHAPTER);
    expect(imported.categoryNames).toEqual(["Ngôn Tình", "Vô Hạn Lưu", "Linh Dị Thần Quái"]);
  });

  /**
   * Every word in the file is either header metadata, a chapter title, or
   * chapter text. Anything else has been dropped, and the publisher is told.
   */
  it("accounts for the whole file and reports no loss", async () => {
    const imported = await parseStoryDocument(sample(), WORDS_PER_CHAPTER);
    expect(imported.warnings).toEqual([]);
    expect(imported.errors).toEqual([]);
  });
});

/**
 * "Thêm file" on an existing story.
 *
 * <p>A supplemental upload is not one chapter per file. A publisher keeps a
 * long story in parts - "UP. 1-107.docx", "Up-2 108-208.docx" - and adding the
 * second part read the whole file as a single chapter of 78,000 words titled
 * "Up-2 108-208". The file's own chapter markers have to decide, exactly as
 * they do when the story is first created; only the story metadata is ignored.
 */
describe("readChaptersInFile", () => {
  it("splits a part file into the chapters it marks up", async () => {
    const rows = [{ text: "Phần 2" }];
    for (let number = 108; number <= 208; number += 1) {
      rows.push({ heading: true, text: `Chương ${number}: Phần Hai` });
      rows.push({ text: prose(`Nội dung chương ${number}.`, 120) });
    }
    const { chapters } = await readChaptersInFile(docxWithHeadings(rows, "Up-2 108-208.docx"));

    expect(chapters).toHaveLength(101);
    expect(chapters[0]?.title).toBe("Chương 108: Phần Hai");
    expect(chapters.at(-1)?.title).toBe("Chương 208: Phần Hai");
  }, 60_000);

  /**
   * A file with no chapter markers really is one chapter, and its text is kept
   * whole - the word budget must not cut it into pieces nobody wrote, however
   * far past 800 words it runs.
   */
  it("keeps a file with no chapter markers as one whole chapter", async () => {
    const { chapters } = await readChaptersInFile(
      docxOf([prose("Một chương rất dài.", 5000)], "Bung ra chuong.docx"),
    );

    expect(chapters).toHaveLength(1);
    const words = chapters[0]!.content.split(/\s+/u).filter(Boolean).length;
    expect(words).toBeGreaterThan(4000);
  }, 60_000);

  it("loses no chapter text when it splits", async () => {
    const rows = [{ text: "Phần 2" }];
    for (let number = 1; number <= 20; number += 1) {
      rows.push({ heading: true, text: `Chương ${number}` });
      rows.push({ text: `Đây là nội dung riêng của chương ${number}.` });
    }
    const { chapters } = await readChaptersInFile(docxWithHeadings(rows, "phan-2.docx"));

    expect(chapters).toHaveLength(20);
    for (let number = 1; number <= 20; number += 1) {
      expect(chapters[number - 1]?.content).toContain(`nội dung riêng của chương ${number}.`);
    }
  }, 60_000);
});
