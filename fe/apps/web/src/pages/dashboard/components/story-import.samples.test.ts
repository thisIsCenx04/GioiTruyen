// @vitest-environment jsdom
//
// Runs the parser over every real .docx sitting in db/Data mẫu.
//
// That folder is a working scratch pad: files are re-exported, split, renamed
// and replaced while the upload flow is being tried out. Suites that named one
// file and hard-coded "385 chapters" broke every time a sample was swapped, for
// reasons that had nothing to do with the parser - so this one discovers what
// is there and checks the properties that must hold for any story file:
// no line is lost, the chapters are the ones the document marks up, the
// numbering is reported as written, and no chapter arrives empty.
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

import {
  declaredChapterNumber,
  parseStoryDocument,
  readDocumentLines,
  WORDS_PER_CHAPTER,
} from "./story-import";

const FOLDER = resolve(process.cwd(), "../../../db/Data mẫu");

const samples = existsSync(FOLDER)
  ? readdirSync(FOLDER).filter((name) => name.toLowerCase().endsWith(".docx") && !name.startsWith("~$"))
  : [];

function realFile(name: string) {
  const bytes = readFileSync(resolve(FOLDER, name));
  return {
    name,
    arrayBuffer: async () =>
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  } as unknown as File;
}

// A checked-out tree with no samples must not fail the build; the synthetic
// coverage in story-import.docx.test.ts stands on its own.
const when = samples.length > 0 ? describe : describe.skip;

when.each(samples)("parseStoryDocument on %s", (name) => {
  /**
   * Chapter titles are the document's own lines. A file can mark chapters with
   * Word's heading style or just write "Chương 108" as ordinary text, and both
   * count - so the check is that every title the parser produced is a line that
   * exists in the file, not that it came from a styled heading.
   */
  it("takes every chapter title from a line of the document", async () => {
    const lines = await readDocumentLines(realFile(name));
    const imported = await parseStoryDocument(realFile(name), WORDS_PER_CHAPTER);

    if (imported.autoSplit) {
      // Cut by the word budget: the titles are generated, so there is nothing
      // to match against.
      expect(imported.chapters.length).toBeGreaterThan(0);
      return;
    }
    const texts = new Set(lines.map((line) => line.text.trim()));
    for (const chapter of imported.chapters) {
      expect(texts.has(chapter.title.trim())).toBe(true);
    }
  }, 200_000);

  /**
   * The hard guarantee: an upload may never drop text. Word counts alone
   * cannot show it - a heading line becomes a title and leaves the body - so
   * every source line from the first heading on is matched against a chapter.
   */
  it("puts every line of the document into a chapter", async () => {
    const lines = await readDocumentLines(realFile(name));
    const imported = await parseStoryDocument(realFile(name), WORDS_PER_CHAPTER);

    const kept = new Map<string, number>();
    for (const chapter of imported.chapters) {
      for (const piece of [chapter.title, ...chapter.content.split("\n")]) {
        const key = piece.trim();
        if (!key) continue;
        kept.set(key, (kept.get(key) ?? 0) + 1);
      }
    }

    // Counted from the first chapter, because what sits above it is the header
    // block - title, author, genres, blurb - which is read into the form
    // fields rather than into a chapter, and is not lost by being there.
    const firstTitle = imported.chapters[0]?.title.trim();
    const firstChapter = firstTitle
      ? lines.findIndex((line) => line.text.trim() === firstTitle)
      : -1;
    const from = firstChapter < 0 ? 0 : firstChapter;
    const dropped: string[] = [];
    for (let index = from; index < lines.length; index += 1) {
      const key = lines[index]!.text.trim();
      if (!key) continue;
      const left = kept.get(key) ?? 0;
      if (left <= 0) dropped.push(key);
      else kept.set(key, left - 1);
    }

    expect(dropped).toEqual([]);
  }, 200_000);

  /**
   * A file that skips a chapter number is missing that chapter's text
   * entirely. Nothing is lost by importing it, so it is reported rather than
   * refused - but it must be reported, or the story goes up with a hole only a
   * reader would find. What to expect is read off the document itself.
   */
  it("reports exactly the chapter numbers the file skips", async () => {
    const imported = await parseStoryDocument(realFile(name), WORDS_PER_CHAPTER);
    const numbers = imported.chapters
      .map((chapter) => declaredChapterNumber(chapter.title))
      .filter((value): value is number => value != null);

    const warning = imported.warnings.find((entry) => entry.includes("thiếu"));
    if (numbers.length < 2) {
      expect(warning).toBeUndefined();
      return;
    }

    const present = new Set(numbers);
    const gaps: number[] = [];
    for (let n = Math.min(...numbers); n <= Math.max(...numbers); n += 1) {
      if (!present.has(n)) gaps.push(n);
    }

    if (gaps.length === 0) {
      expect(warning).toBeUndefined();
      return;
    }
    expect(warning).toBeDefined();
    expect(warning).toContain(`thiếu ${gaps.length} chương`);
    expect(warning).toContain(`chương ${gaps[0]}`);
  }, 200_000);

  it("gives every chapter a title and a body, and refuses nothing valid", async () => {
    const imported = await parseStoryDocument(realFile(name), WORDS_PER_CHAPTER);
    expect(imported.errors).toEqual([]);
    expect(imported.chapters.length).toBeGreaterThan(0);
    for (const chapter of imported.chapters) {
      expect(chapter.title.trim()).not.toBe("");
      expect(chapter.content.trim()).not.toBe("");
    }
  }, 200_000);

  /** The label names the synopsis; it is not part of it. */
  it("does not leave a metadata label inside the synopsis", async () => {
    const imported = await parseStoryDocument(realFile(name), WORDS_PER_CHAPTER);
    expect(imported.synopsis.startsWith("Văn án")).toBe(false);
    expect(imported.synopsis.startsWith("Tên truyện")).toBe(false);
  }, 200_000);
});
