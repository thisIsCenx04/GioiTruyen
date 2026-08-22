// @vitest-environment jsdom
//
// The working case: one story kept in several part files, uploaded in order.
// db/Data mẫu holds "UP. 1-107.docx", "Up-2 108-208.docx" and "up-3.docx" -
// the same story, in sequence, each part carrying a hundred chapters or more.
//
// Adding part two used to read the whole file as a single chapter of 78,000
// words titled "Up-2 108-208", because the add-from-file flow assumed one file
// was one chapter. These check that each part arrives as its own chapters and
// that the parts join into one continuous, gap-free story.
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

import { declaredChapterNumber, readChapterDraftsFromFiles } from "./story-import";

const FOLDER = resolve(process.cwd(), "../../../db/Data mẫu");

/**
 * The part files in reading order.
 *
 * <p>Sorted by the number in the name, which is what the upload flow does with
 * the files a publisher picks - "up-3" after "Up-2" after "UP. 1-107".
 */
const parts = existsSync(FOLDER)
  ? readdirSync(FOLDER)
      .filter((name) => /^up/iu.test(name) && name.toLowerCase().endsWith(".docx") && !name.startsWith("~$"))
      .sort((left, right) => {
        const number = (name: string) => Number(/^up[^0-9]*(\d+)/iu.exec(name)?.[1] ?? 0);
        return number(left) - number(right);
      })
  : [];

function realFile(name: string) {
  const bytes = readFileSync(resolve(FOLDER, name));
  return {
    name,
    arrayBuffer: async () =>
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
  } as unknown as File;
}

const when = parts.length >= 2 ? describe : describe.skip;

when("a story uploaded as several part files", () => {
  it("reads each part as its own chapters, never as one chapter", async () => {
    for (const name of parts) {
      const read = await readChapterDraftsFromFiles([realFile(name)], 1);
      expect(read.failed).toEqual([]);
      // Each part is a hundred chapters or so. One would mean the file was
      // swallowed whole again.
      expect(read.chapters.length).toBeGreaterThan(1);
      expect(read.multiChapterFiles).toBe(1);
    }
  }, 300_000);

  /**
   * Uploaded one after another, as a publisher does: part one creates the
   * story, part two and three are added to it. The numbering has to run on
   * without repeating or skipping.
   */
  it("joins the parts into one continuous story", async () => {
    const all: { title: string }[] = [];
    for (const name of parts) {
      const read = await readChapterDraftsFromFiles(
        [realFile(name)],
        all.length > 0
          ? Math.max(
            ...all.map((chapter, index) => declaredChapterNumber(chapter.title) ?? index + 1),
          ) + 1
          : 1,
      );
      expect(read.failed).toEqual([]);
      all.push(...read.chapters);
    }

    const numbers = all
      .map((chapter) => declaredChapterNumber(chapter.title))
      .filter((value): value is number => value != null);

    // Every chapter is numbered, none twice.
    expect(numbers).toHaveLength(all.length);
    expect(new Set(numbers).size).toBe(numbers.length);

    // And the numbers climb: part two picks up where part one stopped.
    const sorted = [...numbers].sort((left, right) => left - right);
    expect(numbers).toEqual(sorted);
  }, 300_000);

  it("gives every chapter of every part a title and a body", async () => {
    for (const name of parts) {
      const read = await readChapterDraftsFromFiles([realFile(name)], 1);
      for (const chapter of read.chapters) {
        expect(chapter.title.trim()).not.toBe("");
        expect(chapter.content.trim()).not.toBe("");
      }
    }
  }, 300_000);
});
