import { describe, expect, it } from "vitest";

import {
  parseStoryDocument,
  splitByWordCount,
  WORDS_PER_CHAPTER,
  WORDS_PER_CHAPTER_ZHIHU,
} from "./story-import";

/**
 * Minimal stand-in for the File the drawer hands to the parser.
 *
 * <p>Carries real bytes, because the parser decides the format from them rather
 * than from the extension.
 */
function fakeFile(name: string, text: string) {
  const bytes = new TextEncoder().encode(text);
  return {
    name,
    arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
    text: async () => text,
  } as unknown as File;
}

const paragraphOf30Words = Array.from({ length: 30 }, (_, index) => `tu${index}`).join(" ");
const paragraphs = (count: number) => Array.from({ length: count }, () => paragraphOf30Words);

describe("splitByWordCount", () => {
  it("cuts continuous prose into fixed-size chapters", () => {
    // 60 paragraphs x 30 words = 1800 words, so two chapters at 800 words each.
    const chapters = splitByWordCount(paragraphs(60), WORDS_PER_CHAPTER);

    expect(chapters).toHaveLength(2);
    expect(chapters.map((chapter) => chapter.title)).toEqual(["Chương 1", "Chương 2"]);
  });

  it("numbers chapters sequentially without inventing titles", () => {
    const chapters = splitByWordCount(paragraphs(120), WORDS_PER_CHAPTER);

    chapters.forEach((chapter, index) => {
      expect(chapter.title).toBe(`Chương ${index + 1}`);
    });
  });

  it("never splits a paragraph across two chapters", () => {
    const chapters = splitByWordCount(paragraphs(60), WORDS_PER_CHAPTER);

    for (const chapter of chapters) {
      for (const block of chapter.content.split("\n\n")) {
        expect(block).toBe(paragraphOf30Words);
      }
    }
  });

  it("folds a tiny trailing remainder into the previous chapter", () => {
    // 27 paragraphs = 810 words: one full chapter plus a 10-word scrap that must
    // not become a chapter of its own.
    const chapters = splitByWordCount(paragraphs(27), WORDS_PER_CHAPTER);

    expect(chapters).toHaveLength(1);
  });

  it("returns nothing for an empty document", () => {
    expect(splitByWordCount([])).toEqual([]);
  });
});

describe("parseStoryDocument", () => {
  it("auto-splits a document that has no chapter headings", async () => {
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Tuyết Tận Kiến Quân Tâm",
      "Giới thiệu ngắn.",
      "",
      ...paragraphs(60),
    ].join("\n")));

    expect(imported.autoSplit).toBe(true);
    expect(imported.chapters.length).toBeGreaterThan(1);
    expect(imported.chapters[0]!.title).toBe("Chương 1");
    // The header must not leak into the body.
    expect(imported.chapters[0]!.content).not.toContain("Giới thiệu ngắn.");
  });

  it("keeps the author's own chapter headings when the file has them", async () => {
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Tên Truyện",
      "",
      "Chương 1: Mở đầu",
      "Nội dung một.",
      "",
      "Chương 2: Tiếp theo",
      "Nội dung hai.",
    ].join("\n")));

    expect(imported.autoSplit).toBe(false);
    expect(imported.chapters).toHaveLength(2);
    expect(imported.chapters[0]!.title).toBe("Chương 1: Mở đầu");
  });

  it.each([
    ["no separator", "Chương 1 Mở đầu", "Chương 2 Tiếp theo"],
    ["en dash", "Chương 1 – Mở đầu", "Chương 2 – Tiếp theo"],
    ["decorated", "*** Chương 1: Mở đầu ***", "*** Chương 2: Tiếp theo ***"],
    ["hồi", "Hồi 1: Mở đầu", "Hồi 2: Tiếp theo"],
  ])("splits headings written with %s", async (_style, first, second) => {
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Tên Truyện",
      "",
      first,
      "Nội dung một.",
      "",
      second,
      "Nội dung hai.",
    ].join("\n")));

    expect(imported.chapters).toHaveLength(2);
    expect(imported.chapters[1]!.content).toBe("Nội dung hai.");
  });

  it("keeps the body of a file that has no blank line after its title", async () => {
    // Without a blank separator the whole file used to count as header, so the
    // story arrived with no chapters at all.
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Tên Truyện",
      ...paragraphs(60),
    ].join("\n")));

    expect(imported.chapters.length).toBeGreaterThan(1);
  });

  /**
   * One heading over the whole document has not divided anything, so it is not
   * treated as a chapter boundary and the text is cut by word count instead.
   * The parts are numbered plainly rather than as "Chương 1: Mở đầu (1/2)",
   * which claimed the author had written a division they had not.
   */
  it("cuts a document whose single heading covers everything", async () => {
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Tên Truyện",
      "",
      "Chương 1: Mở đầu",
      ...paragraphs(60),
    ].join("\n")));

    expect(imported.chapters.length).toBeGreaterThan(1);
    expect(imported.chapters.map((chapter) => chapter.title))
      .toEqual(["Chương 1", "Chương 2"]);
    expect(imported.autoSplit).toBe(true);
  });

  /**
   * Lines that look like headings without being any - a stray number, a
   * numbered list - would otherwise carve a novel into a couple of enormous
   * "chapters". Two headings across 1,800 words means chapters of 900; two
   * across 24,000 means they are not headings.
   */
  it("ignores headings that do not actually divide the document", async () => {
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Tên Truyện",
      "",
      "1",
      ...paragraphs(400),
      "3. Một mục trong danh sách",
      ...paragraphs(400),
    ].join("\n")));

    // 24,000 words at 800 each, not two chapters of 12,000.
    expect(imported.chapters.length).toBeGreaterThan(20);
    expect(imported.autoSplit).toBe(true);
    for (const chapter of imported.chapters) {
      expect(chapter.content.split(/\s+/u).filter(Boolean).length)
        .toBeLessThan(WORDS_PER_CHAPTER * 2);
    }
  });

  it("uses the Zhihu budget when one is given", async () => {
    // 1800 words at 1400 per chapter is two; at 800 it would have been three.
    const imported = await parseStoryDocument(
      fakeFile("truyen.txt", ["Tên Truyện", "Giới thiệu.", "", ...paragraphs(60)].join("\n")),
      WORDS_PER_CHAPTER_ZHIHU,
    );

    expect(imported.chapters).toHaveLength(2);
  });

  it("marks the story completed when the first line says so", async () => {
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Đã hoàn thành",
      "Tuyết Tận Kiến Quân Tâm",
      "",
      "Chương 1",
      "Nội dung.",
    ].join("\n")));

    expect(imported.completionStatus).toBe("COMPLETED");
    // The marker line is consumed, so the real title survives.
    expect(imported.title).toBe("Tuyết Tận Kiến Quân Tâm");
  });

  it("treats a story with no marker as still running", async () => {
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      "Một Truyện Đang Ra",
      "",
      "Chương 1",
      "Nội dung.",
    ].join("\n")));

    expect(imported.completionStatus).toBe("ONGOING");
  });

  it("does not mistake a long title mentioning the words for a marker", async () => {
    const title = "Hành trình hoàn thành giấc mơ của cô gái nhỏ tuổi hai mươi";
    const imported = await parseStoryDocument(fakeFile("truyen.txt", [
      title,
      "",
      "Chương 1",
      "Nội dung.",
    ].join("\n")));

    expect(imported.title).toBe(title);
  });
});
