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
