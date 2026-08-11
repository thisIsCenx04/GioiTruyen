import { strFromU8, unzipSync } from "fflate";

export type ImportedChapter = { content: string; title: string };

function isChapterHeading(line: string) {
  return /^(?:ch(?:ương|uong)|chapter|第)?\s*\d+(?:\s*[:.\-]\s*.*)?$/iu.test(line.trim());
}

function chapterTitle(line: string, index: number) {
  const clean = line.trim();
  const number = clean.match(/\d+/u)?.[0] ?? String(index + 1);
  const suffix = clean.replace(/^(?:ch(?:ương|uong)|chapter|第)?\s*\d+\s*[:.\-]?\s*/iu, "").trim();
  return suffix ? `Chương ${number}: ${suffix}` : `Chương ${number}`;
}

function splitChapterBlocks(lines: string[], fallbackTitle: string): ImportedChapter[] {
  const blocks: Array<{ content: string[]; heading: string }> = [];
  const preamble: string[] = [];
  let current: { content: string[]; heading: string } | null = null;

  for (const line of lines) {
    if (isChapterHeading(line)) {
      current = { content: blocks.length === 0 ? [...preamble] : [], heading: line };
      blocks.push(current);
    } else if (current) {
      current.content.push(line);
    } else if (line.trim()) {
      preamble.push(line);
    }
  }

  if (blocks.length === 0) return [{ content: preamble.join("\n").trim(), title: fallbackTitle }];
  return blocks.map((block, index) => ({
    content: block.content.join("\n").replace(/\n{3,}/gu, "\n\n").trim(),
    title: block.heading === fallbackTitle ? fallbackTitle : chapterTitle(block.heading, index),
  }));
}

function docxLines(bytes: Uint8Array) {
  const files = unzipSync(bytes);
  const documentXml = files["word/document.xml"];
  if (!documentXml) throw new Error("File Word không có document.xml hợp lệ.");
  const document = new DOMParser().parseFromString(strFromU8(documentXml), "application/xml");
  return Array.from(document.getElementsByTagNameNS("*", "p"))
    .map((paragraph) => Array.from(paragraph.getElementsByTagNameNS("*", "t"), (node) => node.textContent ?? "").join(""))
    .map((line) => line.trim())
    .filter(Boolean);
}

export async function parseStoryFile(file: File): Promise<ImportedChapter[]> {
  const fallbackTitle = file.name.replace(/\.[^.]+$/u, "").trim() || "Chương mới";
  if (file.name.toLowerCase().endsWith(".docx")) {
    return splitChapterBlocks(docxLines(new Uint8Array(await file.arrayBuffer())), fallbackTitle);
  }
  return splitChapterBlocks((await file.text()).split(/\r?\n/gu), fallbackTitle);
}

export type ImportedStory = {
  title: string;
  authorName: string;
  synopsis: string;
  chapters: ImportedChapter[];
  /** COMPLETED when the file declares it, otherwise ONGOING. */
  completionStatus: "COMPLETED" | "ONGOING";
  /** True when the chapters came from word-count splitting rather than headings. */
  autoSplit: boolean;
};

/** Words per chapter when a document has no chapter headings of its own. */
export const WORDS_PER_CHAPTER = 800;

/**
 * Phrases an uploaded file uses to declare the story finished. Matched against
 * the first non-empty line with diacritics stripped, so "Đã hoàn thành",
 * "da hoan thanh" and "DA HOAN THANH" all count.
 */
const COMPLETED_MARKERS = ["da hoan thanh", "hoan thanh", "full", "completed"];

function stripDiacritics(value: string) {
  return value
    .toLocaleLowerCase("vi-VN")
    .normalize("NFD")
    .replace(/[̀-ͯ]/gu, "")
    .replace(/đ/gu, "d");
}

/**
 * Reads the completion marker off the first meaningful line. The line is
 * consumed only when it is *just* the marker; a title that happens to contain
 * the words keeps its place as the title.
 */
function readCompletionStatus(lines: string[]): {
  completionStatus: "COMPLETED" | "ONGOING";
  markerLineIndex: number;
} {
  for (let index = 0; index < lines.length; index++) {
    const raw = lines[index]?.trim() ?? "";
    if (!raw) continue;

    const normalised = stripDiacritics(raw).replace(/[()[\]{}:.,-]/gu, " ").replace(/\s+/gu, " ").trim();
    if (COMPLETED_MARKERS.some((marker) => normalised.includes(marker))) {
      // A short line is a standalone marker; a long one is a title that merely
      // mentions the words, so it must not be swallowed.
      return {
        completionStatus: "COMPLETED",
        markerLineIndex: normalised.length <= 24 ? index : -1,
      };
    }
    // Only the first meaningful line is inspected.
    return { completionStatus: "ONGOING", markerLineIndex: -1 };
  }
  return { completionStatus: "ONGOING", markerLineIndex: -1 };
}

/**
 * Splits continuous prose into fixed-size chapters when the document carries no
 * chapter headings. Paragraphs are kept whole: a chapter ends at the first
 * paragraph boundary at or past the word budget, so no sentence is cut in half.
 */
export function splitByWordCount(lines: string[], wordsPerChapter = WORDS_PER_CHAPTER): ImportedChapter[] {
  const paragraphs = lines.map((line) => line.trim()).filter(Boolean);
  if (paragraphs.length === 0) return [];

  const chapters: ImportedChapter[] = [];
  let current: string[] = [];
  let words = 0;

  for (const paragraph of paragraphs) {
    current.push(paragraph);
    words += paragraph.split(/\s+/u).filter(Boolean).length;
    if (words >= wordsPerChapter) {
      chapters.push({ content: current.join("\n\n"), title: `Chương ${chapters.length + 1}` });
      current = [];
      words = 0;
    }
  }

  // Trailing text becomes the last chapter; if it is very short, it joins the
  // previous one rather than standing alone as a stub.
  if (current.length > 0) {
    const tail = current.join("\n\n");
    const previous = chapters.at(-1);
    if (previous && words < wordsPerChapter / 4) {
      previous.content = `${previous.content}\n\n${tail}`;
    } else {
      chapters.push({ content: tail, title: `Chương ${chapters.length + 1}` });
    }
  }

  return chapters;
}

/** Labelled header lines the uploaded file may carry before the first chapter. */
const METADATA_LABELS: Record<string, "authorName" | "synopsis" | "title"> = {
  "giới thiệu": "synopsis",
  "gioi thieu": "synopsis",
  "mô tả": "synopsis",
  "mo ta": "synopsis",
  "tóm tắt": "synopsis",
  "tom tat": "synopsis",
  "tác giả": "authorName",
  "tac gia": "authorName",
  "tên truyện": "title",
  "ten truyen": "title",
  author: "authorName",
  description: "synopsis",
  summary: "synopsis",
  title: "title",
};

function readLines(bytes: Uint8Array | null, text: string | null) {
  if (bytes) return docxLines(bytes);
  return (text ?? "").split(/\r?\n/gu);
}

/**
 * Reads story metadata from the top of an uploaded file so the admin does not
 * have to retype it. Recognises "Tác giả: ..." style headers before the first
 * chapter heading; anything it cannot identify is left for manual entry.
 */
export async function parseStoryDocument(file: File): Promise<ImportedStory> {
  const isDocx = file.name.toLowerCase().endsWith(".docx");
  const bytes = isDocx ? new Uint8Array(await file.arrayBuffer()) : null;
  const text = isDocx ? null : await file.text();
  const rawLines = readLines(bytes, text);

  // "Đã hoàn thành" on the opening line marks the story finished. When that line
  // is nothing but the marker it is dropped, so it cannot become the title.
  const { completionStatus, markerLineIndex } = readCompletionStatus(rawLines);
  const lines = markerLineIndex >= 0
    ? rawLines.filter((_, index) => index !== markerLineIndex)
    : rawLines;

  const fallbackTitle = file.name.replace(/\.[^.]+$/u, "").trim() || "Truyện mới";
  const metadata: { authorName: string; synopsis: string; title: string } = {
    authorName: "",
    synopsis: "",
    title: "",
  };

  // Chapter headings decide everything, so they are located first.
  let firstChapterIndex = lines.length;
  for (let index = 0; index < lines.length; index++) {
    if (isChapterHeading(lines[index] ?? "")) {
      firstChapterIndex = index;
      break;
    }
  }
  const hasHeadings = firstChapterIndex < lines.length;

  // Where the header block ends. With headings it runs up to the first one;
  // without them it stops at the first blank line, otherwise the entire story
  // would be absorbed into the synopsis and nothing would remain to split.
  let bodyStartIndex = firstChapterIndex;
  if (!hasHeadings) {
    let sawContent = false;
    bodyStartIndex = lines.length;
    for (let index = 0; index < lines.length; index++) {
      const line = lines[index] ?? "";
      if (sawContent && !line.trim()) {
        bodyStartIndex = index + 1;
        break;
      }
      if (line.trim()) sawContent = true;
    }
  }

  const headerLines = lines.slice(0, hasHeadings ? firstChapterIndex : bodyStartIndex);

  const unlabelled: string[] = [];
  for (const line of headerLines) {
    const match = line.match(/^\s*([^:]{1,20})\s*:\s*(.+)$/u);
    const key = match ? METADATA_LABELS[match[1].trim().toLowerCase()] : undefined;
    if (match && key) {
      if (!metadata[key]) metadata[key] = match[2].trim();
    } else if (line.trim()) {
      unlabelled.push(line.trim());
    }
  }

  // Without labels, treat the first line as the title and the rest as synopsis.
  if (!metadata.title && unlabelled.length > 0) {
    metadata.title = unlabelled.shift() ?? "";
  }
  if (!metadata.synopsis && unlabelled.length > 0) {
    metadata.synopsis = unlabelled.join("\n");
  }

  // A document with real chapter headings keeps them. One long block of prose
  // gets cut into fixed-size chapters instead, numbered and untitled.
  const bodyLines = lines.slice(hasHeadings ? firstChapterIndex : bodyStartIndex);

  const chapters = hasHeadings
    ? splitChapterBlocks(bodyLines, metadata.title || fallbackTitle)
    : splitByWordCount(bodyLines);

  return {
    authorName: metadata.authorName,
    autoSplit: !hasHeadings,
    chapters,
    completionStatus,
    synopsis: metadata.synopsis,
    title: metadata.title || fallbackTitle,
  };
}

