import { strFromU8, unzipSync } from "fflate";

export type ImportedChapter = { content: string; title: string };

/** Words an uploaded file may put in front of a chapter number. */
const CHAPTER_WORD = "(?:ch(?:ương|uong)|chapter|hồi|hoi|kỳ|ky|第)";

/** Decoration real documents wrap headings in, e.g. "*** Chương 1 ***". */
const HEADING_DECORATION = /^[\s*#=~_]+|[\s*#=~_]+$/gu;

/**
 * A heading followed by its number, then optionally a title. The separator is
 * optional and may be any dash: "Chương 1 Mở đầu" and "Chương 1 – Mở đầu" are
 * as common in uploaded files as "Chương 1: Mở đầu", and requiring a colon or
 * hyphen made those files parse as a single chapter.
 */
const HEADING_PREFIX = new RegExp(
  `^${CHAPTER_WORD}?\\s*(\\d+)\\s*章?\\s*[:.\\-–—]?\\s*`,
  "iu",
);

const NUMBERED_HEADING = new RegExp(`^${CHAPTER_WORD}\\s*\\d+`, "iu");

/**
 * Longest a line may be and still count as a heading. The separator is no
 * longer required, so a paragraph opening with "Chương 3 kết thúc..." would
 * otherwise read as a new chapter; a real heading is never this long.
 */
const MAX_HEADING_LENGTH = 120;

/**
 * A paragraph, and whether the document itself marked it up as a heading.
 *
 * <p>Word styles, Markdown "#" and HTML h1-h3 all say "this is a heading" in a
 * way the text alone does not, and a file whose chapters are called "Mở đầu"
 * declares its structure just as clearly as one that writes "Chương 1".
 */
export type DocumentLine = { text: string; heading: boolean };

const plainLine = (text: string): DocumentLine => ({ heading: false, text: text.trim() });

/**
 * A date written as digits: 17.3.2015, 14/11/2022, 2015-03-17.
 *
 * <p>Diary and journal chapters are full of these, and every one of them used
 * to become a chapter: "17.3.2015." was read as chapter 17 titled "3.2015.".
 * One upload gained eleven chapters that way, each cutting a real chapter in
 * half at the date.
 */
const DATE_LINE = /^\d{1,4}\s*[./-]\s*\d{1,2}\s*[./-]\s*\d{1,4}\.?$/u;

/**
 * Whether a line starts a chapter.
 *
 * <p>{@code trustStyles} is set only when nothing in the document names a
 * chapter in words. A file that writes "Chương 1" has described its own
 * structure, and letting a styled paragraph or a stray number split it as well
 * would cut chapters the author never wrote.
 */
function isChapterHeading(line: DocumentLine, trustStyles = false) {
  const clean = line.text.trim().replace(HEADING_DECORATION, "");
  if (!clean || clean.length > MAX_HEADING_LENGTH) return false;
  // The word "Chương" (or Chapter, Hồi, 第…) followed by a number. This is what
  // a chapter heading looks like when the document says so in words, and it is
  // trusted ahead of everything else.
  if (NUMBERED_HEADING.test(clean)) return true;
  if (trustStyles && line.heading) return true;
  // A bare number alone on a line marks chapters in some raw exports, but it is
  // also how lists, footnotes and dates begin, so it counts only when nothing in
  // the document names a chapter properly - see the trustStyles gate.
  return trustStyles && !DATE_LINE.test(clean) && /^\d+(?:\s*[:.\-–—]\s*.*)?$/u.test(clean);
}

/** True when any line names a chapter in words, not merely in styling. */
function hasTextualHeadings(lines: readonly DocumentLine[]) {
  return lines.some((line) => isChapterHeading(line, false));
}

/**
 * How many times the budget a chapter may run to and still be believed.
 *
 * <p>A chapter the author wrote long is normal; one running to tens of
 * thousands of words is not a chapter at all.
 */
const OVERSIZE_FACTOR = 4;

function wordCount(text: string) {
  return text.split(/\s+/u).filter(Boolean).length;
}

/**
 * The chapter number a title declares, or null when it names none.
 *
 * <p>Read from the title rather than from the position, because the file's own
 * numbering is what a reader will compare against.
 */
export function declaredChapterNumber(title: string): number | null {
  const match = new RegExp(`^${CHAPTER_WORD}\\s*(\\d+)`, "iu").exec(title.trim());
  return match?.[1] ? Number(match[1]) : null;
}

/**
 * Checks the chapters read out of a file make sense as a story.
 *
 * <p>A file can parse without error and still be wrong: chapters saved in
 * reverse, a duplicated block, a number skipped by hundreds. Saving that puts
 * the story out of order for every reader, and unpicking it afterwards means
 * editing each chapter by hand - so it is worth saying before the save, not
 * after.
 */
/** Enough missing numbers to see the pattern without filling the dialog. */
const MAX_GAPS_LISTED = 12;

/**
 * Chapter numbers missing from a run.
 *
 * <p>Only holes *inside* the range count. A story whose chapters start at 40 is
 * a later volume, not a story missing the first thirty-nine, and one that stops
 * at 200 is simply still being written.
 */
export function chapterNumberGaps(numbers: readonly number[]): number[] {
  if (numbers.length < 2) return [];
  const present = new Set(numbers);
  const gaps: number[] = [];
  for (let number = Math.min(...numbers); number <= Math.max(...numbers); number += 1) {
    if (!present.has(number)) gaps.push(number);
  }
  return gaps;
}

/**
 * Chapter numbers a story is missing, read from its chapter titles.
 *
 * <p>The titles carry the numbering the publisher and the readers see. The
 * chapter_number column cannot answer this: it is assigned by position when a
 * story is saved, so it is always a gap-free 1…N however many chapters the
 * source actually skipped.
 */
export function missingChapterNumbers(titles: readonly string[]): number[] {
  return chapterNumberGaps(
    titles
      .map((title) => declaredChapterNumber(title))
      .filter((value): value is number => value != null),
  );
}

export function inspectChapterOrder(
  chapters: readonly ImportedChapter[],
): { errors: string[]; warnings: string[] } {
  const numbers = chapters
    .map((chapter) => declaredChapterNumber(chapter.title))
    .filter((value): value is number => value != null);
  if (numbers.length < 2) return { errors: [], warnings: [] };

  const errors: string[] = [];
  const warnings: string[] = [];

  // Every chapter smaller than the one before it: the file is back to front.
  // Importing it would publish the ending first, so this stops the import
  // rather than warning about it.
  const descending = numbers.every((value, index) => index === 0 || value < (numbers[index - 1] ?? 0));
  if (descending) {
    errors.push(
      `File xếp chương theo thứ tự ngược: bắt đầu từ chương ${numbers[0]} và kết thúc ở chương `
      + `${numbers.at(-1)}. Hãy sắp lại từ chương nhỏ đến chương lớn rồi upload lại.`,
    );
    return { errors, warnings };
  }

  // A few chapters out of place is something the publisher can fix in the form,
  // so it is said plainly and the import goes ahead.
  const outOfOrder = numbers.filter((value, index) => index > 0 && value < (numbers[index - 1] ?? 0)).length;
  if (outOfOrder > 0) {
    warnings.push(
      `Có ${outOfOrder} chương nằm sai thứ tự so với số chương ghi trong file. `
      + "Thứ tự đăng sẽ theo đúng thứ tự đang hiển thị trong danh sách bên dưới.",
    );
  }

  const duplicates = numbers.length - new Set(numbers).size;
  if (duplicates > 0) {
    warnings.push(
      `Có ${duplicates} số chương bị trùng trong file (ví dụ hai chương cùng đánh số). `
      + "Hãy sửa lại tiêu đề cho khỏi nhầm trước khi lưu.",
    );
  }

  // Numbers the file skips. A real upload of 385 chapters ran 1…386 with no
  // chapter 87 in it at all - the text of that chapter simply was not in the
  // document - and the import said nothing, so the story went up with a hole
  // that only a reader would find. Nothing is lost by importing it, so this is
  // said rather than refused; the publisher decides.
  const lowest = Math.min(...numbers);
  const highest = Math.max(...numbers);
  const gaps = chapterNumberGaps(numbers);
  if (gaps.length > 0) {
    const shown = gaps.slice(0, MAX_GAPS_LISTED).join(", ");
    warnings.push(
      `File thiếu ${gaps.length} chương trong khoảng ${lowest}–${highest}: chương ${shown}`
      + `${gaps.length > MAX_GAPS_LISTED ? "…" : ""}. `
      + "File không có nội dung của các chương này. Hãy bổ sung rồi upload lại nếu là thiếu sót.",
    );
  }

  return { errors, warnings };
}

/**
 * Whether the headings found actually divide the document into chapters.
 *
 * <p>Some lines look like headings without being any: a lone "1" left over from
 * a footnote, or a numbered list such as "3. Mua cùng lúc bún huyết vịt…". One
 * real upload of 96,000 words contained exactly two of these and nothing else,
 * so it was read as two chapters of 48,000 words each - too large for the
 * database column, and unreadable regardless. The give-away is the size: when
 * the typical chapter is many times a chapter's worth of prose, whatever those
 * lines are, they are not where chapters begin.
 *
 * <p>Measured on the median rather than the mean so one oversized chapter in an
 * otherwise well-marked file does not discard the file's own structure.
 */
function headingsPartitionDocument(
  chapters: readonly ImportedChapter[],
  wordsPerChapter: number,
): boolean {
  // A single heading covering everything structured nothing.
  if (chapters.length < 2) return false;
  const sizes = chapters.map((chapter) => wordCount(chapter.content)).sort((a, b) => a - b);
  const median = sizes[Math.floor(sizes.length / 2)] ?? 0;
  return median <= wordsPerChapter * OVERSIZE_FACTOR;
}

function chapterTitle(line: string, index: number) {
  const clean = line.trim().replace(HEADING_DECORATION, "");
  const match = HEADING_PREFIX.exec(clean);
  // A styled heading such as "Mở đầu" carries no number of its own, so it keeps
  // its own words and takes its position in the file as its number.
  if (!match) return clean || `Chương ${index + 1}`;
  const number = match[1] ?? String(index + 1);
  const suffix = clean.slice(match[0].length).trim();
  return suffix ? `Chương ${number}: ${suffix}` : `Chương ${number}`;
}

/**
 * Có nên tin vào kiểu tiêu đề mà tài liệu tự khai không.
 *
 * <p>Word có hai cách viết một tiêu đề chương. Tác giả gõ thẳng "Chương 711"
 * thành chữ, hoặc bật đánh số tự động - lúc đó phần "Chương 711" do Word sinh
 * ra khi hiển thị, còn trong tệp chỉ còn tựa đề trần mang kiểu Heading. Ngăn
 * điều hướng của Word hiện y hệt nhau ở cả hai cách, nên người upload không có
 * cách nào biết tệp của mình thuộc loại nào.
 *
 * <p>Luật cũ là được ăn cả ngã về không: hễ có MỘT dòng gọi tên chương bằng
 * chữ thì mọi tiêu đề theo kiểu đều bị bỏ qua. Một tệp trộn hai cách - tác giả
 * gõ tay mấy chương đầu rồi mới bật đánh số tự động - vì thế mất gần hết
 * chương, và chữ của chúng dồn vào chương gõ tay gần nhất: một tệp bảy trăm
 * chương về còn chín mươi, mỗi "chương" phình lên gấp mấy lần bình thường.
 *
 * <p>Nay quyết định dựa trên kết quả chứ không dựa trên sự có mặt. Cắt thử
 * bằng chữ trước; nếu cách đó đã chia được tài liệu thành những chương có kích
 * thước thật thì giữ nguyên - đúng điều luật cũ muốn bảo vệ. Chỉ khi nó cho ra
 * những khối to bất thường mới xét tới kiểu tiêu đề, và cũng chỉ nhận khi kiểu
 * tiêu đề vừa tìm được nhiều hơn, vừa chia ra hợp lý.
 */
function shouldTrustStyles(
  lines: readonly DocumentLine[],
  wordsPerChapter: number = WORDS_PER_CHAPTER,
): boolean {
  // Không dòng nào gọi tên chương bằng chữ: kiểu tiêu đề là tất cả những gì có.
  if (!hasTextualHeadings(lines)) return true;

  // Dấu hiệu của việc bỏ sót tiêu đề là những khối quá khổ: chữ của các chương
  // không nhận ra dồn hết vào chương nhận ra được gần nhất.
  //
  // Đếm số khối quá khổ chứ không lấy trung vị. Trong tệp trộn hai cách, phần
  // lớn chương gõ tay nằm ở đầu và chỉ một khối cuối ôm toàn bộ phần còn lại -
  // trung vị vẫn đẹp như thường trong khi tệp đã hỏng.
  const byText = buildChapterBlocks(lines, "", false);
  const textOversize = oversizeChapters(byText, wordsPerChapter);
  if (textOversize === 0) return false;

  // Chỉ đổi sang kiểu tiêu đề khi nó vừa tìm được nhiều chương hơn, vừa thật sự
  // gỡ được những khối quá khổ kia - chứ không phải chỉ cắt vụn tài liệu ra.
  const byStyle = buildChapterBlocks(lines, "", true);
  return byStyle.length > byText.length
    && oversizeChapters(byStyle, wordsPerChapter) < textOversize;
}

/** Số chương to đến mức không thể là một chương. */
function oversizeChapters(chapters: readonly ImportedChapter[], wordsPerChapter: number): number {
  const ceiling = wordsPerChapter * OVERSIZE_FACTOR;
  return chapters.filter((chapter) => wordCount(chapter.content) > ceiling).length;
}

function buildChapterBlocks(
  lines: readonly DocumentLine[],
  fallbackTitle: string,
  trustStyles: boolean,
): ImportedChapter[] {
  const blocks: Array<{ content: string[]; heading: string }> = [];
  const preamble: string[] = [];
  let current: { content: string[]; heading: string } | null = null;

  for (const line of lines) {
    if (isChapterHeading(line, trustStyles)) {
      current = { content: blocks.length === 0 ? [...preamble] : [], heading: line.text };
      blocks.push(current);
    } else if (current) {
      current.content.push(line.text);
    } else if (line.text.trim()) {
      preamble.push(line.text);
    }
  }

  if (blocks.length === 0) return [{ content: preamble.join("\n").trim(), title: fallbackTitle }];
  return blocks.map((block, index) => ({
    content: block.content.join("\n").replace(/\n{3,}/gu, "\n\n").trim(),
    title: block.heading === fallbackTitle ? fallbackTitle : chapterTitle(block.heading, index),
  }));
}

function splitChapterBlocks(
  lines: readonly DocumentLine[],
  fallbackTitle: string,
  wordsPerChapter: number = WORDS_PER_CHAPTER,
): ImportedChapter[] {
  return buildChapterBlocks(lines, fallbackTitle, shouldTrustStyles(lines, wordsPerChapter));
}

/* ── Reading whatever the publisher actually uploads ──────────────────────
   The picker advertises .txt, .md, .doc, .docx, .pdf and .epub, but only
   .docx and plain text were ever decoded; the rest were read as UTF-8 bytes
   and arrived as binary noise. Format is now decided by the leading bytes
   rather than the extension, because a renamed file is common and the bytes
   cannot lie. */

/** ZIP, and therefore .docx, .epub or .odt - the entries tell them apart. */
const ZIP_MAGIC = [0x50, 0x4b, 0x03, 0x04];
/** The pre-2007 Word compound file. */
const OLE2_MAGIC = [0xd0, 0xcf, 0x11, 0xe0];
const PDF_MAGIC = [0x25, 0x50, 0x44, 0x46];

function startsWith(bytes: Uint8Array, magic: number[]) {
  return magic.every((byte, index) => bytes[index] === byte);
}

/** Undoes XML entities left behind once tags are stripped. */
function decodeEntities(value: string) {
  return value
    .replace(/&lt;/gu, "<")
    .replace(/&gt;/gu, ">")
    .replace(/&quot;/gu, "\"")
    .replace(/&apos;/gu, "'")
    .replace(/&nbsp;/gu, " ")
    .replace(/&#(\d+);/gu, (_, code: string) => String.fromCodePoint(Number(code)))
    .replace(/&#x([0-9a-f]+);/giu, (_, code: string) => String.fromCodePoint(parseInt(code, 16)))
    .replace(/&amp;/gu, "&");
}

/**
 * Paragraphs of a Word document, carrying Word's own heading styles.
 *
 * <p>The style matters: a document whose chapters are marked Heading 1 but
 * titled "Mở đầu" declares its structure as plainly as one that writes
 * "Chương 1", and reading only the text threw that away.
 */
function docxLines(bytes: Uint8Array): DocumentLine[] {
  const files = unzipSync(bytes);
  const documentXml = files["word/document.xml"];
  if (!documentXml) throw new Error("File Word không có document.xml hợp lệ.");
  const document = new DOMParser().parseFromString(strFromU8(documentXml), "application/xml");

  // DOMParser không ném lỗi khi XML hỏng: nó trả về một tài liệu chứa thẻ
  // <parsererror>. Không kiểm chỗ này thì mọi lỗi đọc file đều đi tiếp thành
  // "không có đoạn nào", rồi hiện ra màn hình là "file trống" - một câu sai,
  // và người upload không có cách nào biết phải sửa gì.
  if (document.getElementsByTagName("parsererror").length > 0) {
    throw new Error(
      "Nội dung file Word bị lỗi định dạng nên không đọc được. "
      + "Mở bằng Word rồi lưu lại (Save As) thành .docx mới, sau đó upload lại.",
    );
  }

  const paragraphs = document.getElementsByTagNameNS("*", "p");
  if (paragraphs.length === 0) {
    throw new Error(
      "File Word này không có đoạn văn nào ở dạng đọc được. "
      + "Thường gặp khi file được xuất từ công cụ khác; mở bằng Word rồi lưu lại "
      + "thành .docx mới sẽ đọc được.",
    );
  }
  // Blank paragraphs are kept: they separate the header block from the story
  // body, and dropping them made a .docx without chapter headings parse as
  // header-only, so it arrived with no chapters at all.
  return Array.from(paragraphs).map((paragraph) => {
    const text = Array.from(
      paragraph.getElementsByTagNameNS("*", "t"),
      (node) => node.textContent ?? "",
    ).join("");
    const style = paragraph.getElementsByTagNameNS("*", "pStyle")[0]?.getAttribute("w:val") ?? "";
    // Word writes the style id, which is "Heading1" in English builds and
    // localised elsewhere; outlineLvl is the language-neutral signal and is
    // checked alongside it.
    const outlined = paragraph.getElementsByTagNameNS("*", "outlineLvl").length > 0;
    return { heading: /^(heading|tieu|title)/iu.test(style) || outlined, text: text.trim() };
  });
}

/**
 * Marks heading text while the tags that identify it are still present.
 *
 * <p>Once tags are stripped there is nothing left to tell an h2 from a
 * paragraph, so the flag has to be carried in the text itself until the lines
 * are built. U+0001 never occurs in prose.
 */
const HEADING_MARK = String.fromCharCode(1);

/** Body text of an HTML or XHTML document, with h1-h3 marked as headings. */
function htmlLines(markup: string): DocumentLine[] {
  const body = /<body[^>]*>([\s\S]*)<\/body>/iu.exec(markup)?.[1] ?? markup;
  return body
    .replace(/<(script|style)[\s\S]*?<\/\1>/giu, "")
    .replace(/<h([1-3])[^>]*>([\s\S]*?)<\/h\1>/giu, `\n${HEADING_MARK}$2\n`)
    .replace(/<br\s*\/?>/giu, "\n")
    .replace(/<\/(p|div|li|tr|h[1-6])>/giu, "\n")
    .replace(/<[^>]+>/gu, "")
    .split(/\n/u)
    .map((line) => {
      const heading = line.startsWith(HEADING_MARK);
      return { heading, text: decodeEntities(heading ? line.slice(1) : line).trim() };
    });
}

/** Body text of an RTF document. */
function rtfLines(markup: string): DocumentLine[] {
  return markup
    // Groups carrying no body text: fonts, colours, stylesheets, metadata.
    .replace(/\{\\\*[\s\S]*?\}/gu, "")
    .replace(/\{\\(?:fonttbl|colortbl|stylesheet|info)[\s\S]*?\}/gu, "")
    .replace(/\\par[d]?(?![a-z])/gu, "\n")
    .replace(/\\line(?![a-z])/gu, "\n")
    .replace(/\\u(-?\d+)\s?\??/gu, (_, code: string) => {
      const point = Number(code);
      return String.fromCharCode(point < 0 ? point + 65536 : point);
    })
    .replace(/\\[a-z]+-?\d*\s?/giu, "")
    .replace(/[{}]/gu, "")
    .split(/\n/u)
    .map((line) => plainLine(line));
}

/** Body text of an OpenDocument text file. */
function odtLines(files: Record<string, Uint8Array>): DocumentLine[] {
  const contentXml = files["content.xml"];
  if (!contentXml) throw new Error("File ODT không có content.xml hợp lệ.");
  const document = new DOMParser().parseFromString(strFromU8(contentXml), "application/xml");
  return [
    ...Array.from(document.getElementsByTagNameNS("*", "h"), (node) => ({
      heading: true,
      node,
    })),
    ...Array.from(document.getElementsByTagNameNS("*", "p"), (node) => ({
      heading: false,
      node,
    })),
  ]
    // Document order, so headings stay attached to the text that follows them.
    .sort((left, right) => (
      left.node.compareDocumentPosition(right.node) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1
    ))
    .map((entry) => ({ heading: entry.heading, text: (entry.node.textContent ?? "").trim() }));
}

/** Body text of an EPUB, its documents read in the order the spine gives. */
function epubLines(files: Record<string, Uint8Array>): DocumentLine[] {
  const container = files["META-INF/container.xml"];
  const opfPath = container
    ? /full-path="([^"]+)"/u.exec(strFromU8(container))?.[1]
    : Object.keys(files).find((name) => name.endsWith(".opf"));
  const opfBytes = opfPath ? files[opfPath] : undefined;
  if (!opfPath || !opfBytes) throw new Error("File EPUB không đọc được mục lục.");

  const opf = strFromU8(opfBytes);
  const base = opfPath.includes("/") ? opfPath.slice(0, opfPath.lastIndexOf("/") + 1) : "";
  const manifest = new Map<string, string>();
  for (const item of opf.matchAll(/<item\b[^>]*>/giu)) {
    const id = /id="([^"]+)"/u.exec(item[0])?.[1];
    const href = /href="([^"]+)"/u.exec(item[0])?.[1];
    if (id && href) manifest.set(id, base + href.replace(/^\.\//u, ""));
  }
  const spine = [...opf.matchAll(/<itemref\b[^>]*idref="([^"]+)"/giu)]
    .map((match) => manifest.get(match[1] ?? ""))
    .filter((path): path is string => !!path);
  const order = spine.length > 0
    ? spine
    : [...manifest.values()].filter((path) => /\.x?html?$/iu.test(path));

  const lines: DocumentLine[] = [];
  for (const path of order) {
    const entry = files[path];
    if (!entry) continue;
    lines.push(...htmlLines(strFromU8(entry)));
    // A blank line between documents, so one spine item cannot run into the next.
    lines.push(plainLine(""));
  }
  if (lines.length === 0) throw new Error("File EPUB không có nội dung đọc được.");
  return lines;
}

/** Decodes a text file, honouring a UTF-16 or UTF-8 byte-order mark. */
function decodeText(bytes: Uint8Array): string {
  if (bytes[0] === 0xff && bytes[1] === 0xfe) {
    return new TextDecoder("utf-16le").decode(bytes.subarray(2));
  }
  if (bytes[0] === 0xfe && bytes[1] === 0xff) {
    return new TextDecoder("utf-16be").decode(bytes.subarray(2));
  }
  const text = new TextDecoder("utf-8").decode(bytes);
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

/** Plain text, with Markdown ATX headings recognised as headings. */
function textLines(text: string): DocumentLine[] {
  return text.split(/\r?\n/u).map((line) => {
    const markdown = /^\s{0,3}(#{1,4})\s+(.*)$/u.exec(line);
    return markdown ? { heading: true, text: (markdown[2] ?? "").trim() } : plainLine(line);
  });
}

/**
 * Reads any uploaded story file into paragraphs.
 *
 * <p>Dispatches on the leading bytes, so a .docx saved under a .txt name still
 * parses, and a file that genuinely cannot be decoded says so by name instead
 * of arriving as noise.
 */
export async function readDocumentLines(file: File): Promise<DocumentLine[]> {
  const bytes = new Uint8Array(await file.arrayBuffer());

  if (startsWith(bytes, ZIP_MAGIC)) {
    const files = unzipSync(bytes);
    if (files["word/document.xml"]) return docxLines(bytes);
    if (files["META-INF/container.xml"] || Object.keys(files).some((n) => n.endsWith(".opf"))) {
      return epubLines(files);
    }
    if (files["content.xml"]) return odtLines(files);
    throw new Error("File nén này không phải DOCX, EPUB hay ODT.");
  }

  if (startsWith(bytes, OLE2_MAGIC) || file.name.toLowerCase().endsWith(".doc")) {
    throw new Error(
      "File .doc (Word 97-2003) không đọc trực tiếp được. "
      + "Mở bằng Word rồi lưu lại thành .docx, hoặc dán nội dung vào ô soạn thảo.",
    );
  }

  if (startsWith(bytes, PDF_MAGIC)) {
    throw new Error(
      "File PDF lưu chữ theo toạ độ nên không tách chương chính xác được. "
      + "Hãy dùng bản .docx, .epub hoặc .txt của truyện.",
    );
  }

  const text = decodeText(bytes);
  if (/^\s*\{?\\rtf/u.test(text)) return rtfLines(text);
  if (/^\s*(?:<\?xml|<!doctype html|<html)/iu.test(text) || /<\/(p|div|body|h[1-3])>/iu.test(text)) {
    return htmlLines(text);
  }
  return textLines(text);
}

export async function parseStoryFile(file: File): Promise<ImportedChapter[]> {
  const fallbackTitle = file.name.replace(/\.[^.]+$/u, "").trim() || "Chương mới";
  return splitChapterBlocks(await readDocumentLines(file), fallbackTitle);
}

/**
 * The chapter number written in a file's name, or null when it has none.
 *
 * <p>A number introduced by a chapter word wins over a bare one, so
 * "Quyen 3 - Chuong 12.docx" is chapter 12 rather than chapter 3. Decimals are
 * kept: side stories are routinely numbered 12.5.
 */
export function chapterNumberFromFileName(name: string): number | null {
  const stem = name.replace(/\.[^.]+$/u, "");
  const labelled = /(?:chương|chuong|chapter|chap|ch|c)\s*[.\-_]?\s*(\d+(?:[.,]\d+)?)/iu.exec(stem);
  const bare = /(\d+(?:[.,]\d+)?)/u.exec(stem);
  const found = labelled?.[1] ?? bare?.[1];
  if (found == null) return null;
  const value = Number(found.replace(",", "."));
  return Number.isFinite(value) ? value : null;
}

/**
 * Puts picked chapter files into reading order.
 *
 * <p>A publisher who keeps one file per chapter selects them all at once, and
 * the picker hands them over in the order the operating system lists them -
 * which is alphabetical, so "Chương 10" arrives before "Chương 2" and a
 * hundred-chapter upload lands scrambled. Numbering is what the names are for,
 * so it is what the order follows; names without a number fall back to a
 * natural comparison and sit after the numbered ones.
 */
export function sortChapterFiles<T extends { name: string }>(files: readonly T[]): T[] {
  const collator = new Intl.Collator("vi", { numeric: true, sensitivity: "base" });
  return [...files].sort((left, right) => {
    const leftNumber = chapterNumberFromFileName(left.name);
    const rightNumber = chapterNumberFromFileName(right.name);
    if (leftNumber != null && rightNumber != null && leftNumber !== rightNumber) {
      return leftNumber - rightNumber;
    }
    if (leftNumber != null && rightNumber == null) return -1;
    if (leftNumber == null && rightNumber != null) return 1;
    return collator.compare(left.name, right.name);
  });
}

/** "12" for a whole number, "12.5" for a side story. */
function formatChapterNumber(value: number): string {
  return String(value);
}

/**
 * The title a chapter uploaded as its own file should carry.
 *
 * <p>The title used to be the bare filename, so uploading one more chapter to a
 * 74-chapter story produced a chapter called "Bung ra chuong" - no number, no
 * place in the sequence, and nothing for the merge to match on next time. A
 * chapter is identified by its number, so the number is always in the title:
 * the one written in the filename when there is one, otherwise the next number
 * after what the story already has.
 *
 * @param name           the uploaded file's name
 * @param fallbackNumber number to use when the filename carries none
 */
export function chapterTitleFromFileName(name: string, fallbackNumber: number): string {
  const stem = name.replace(/\.[^.]+$/u, "").trim();
  const declared = chapterNumberFromFileName(name);
  const number = declared ?? fallbackNumber;

  // Whatever the name says besides the numbering: "Chương 12 - Gặp lại" leaves
  // "Gặp lại", and a name that is only a number leaves nothing.
  const rest = (declared == null ? stem : stem
    .replace(/^\s*(?:chương|chuong|chapter|chap|ch|c)?\s*[.\-_]?\s*\d+(?:[.,]\d+)?/iu, ""))
    .replace(/^[\s.\-–—_:]+/u, "")
    .replace(/[\s.\-–—_:]+$/u, "")
    .trim();

  return rest ? `Chương ${formatChapterNumber(number)}: ${rest}` : `Chương ${formatChapterNumber(number)}`;
}

/**
 * Titles for a batch of uploaded chapter files, in the order they will be read.
 *
 * <p>Files that name their own chapter keep that number; the rest are numbered
 * on from {@code startNumber}, skipping any number a file in the same batch has
 * already claimed so two chapters never land on one number.
 */
export function chapterTitlesForUpload(
  files: readonly { name: string }[],
  startNumber: number,
): string[] {
  const claimed = new Set(
    files
      .map((file) => chapterNumberFromFileName(file.name))
      .filter((value): value is number => value != null),
  );
  let next = startNumber;
  return files.map((file) => {
    if (chapterNumberFromFileName(file.name) != null) {
      return chapterTitleFromFileName(file.name, next);
    }
    while (claimed.has(next)) next += 1;
    claimed.add(next);
    return chapterTitleFromFileName(file.name, next);
  });
}

/**
 * The highest chapter number a list of drafts already uses.
 *
 * <p>Read from the titles rather than taken as the row count. A story whose
 * file began at chapter 40, or one carrying a 12.5 side story, has a last
 * number that is not its length - and numbering an upload from the count would
 * land it on a chapter that already exists.
 */
export function lastChapterNumber(rows: readonly { title?: string }[]): number {
  return rows.reduce((highest, row, index) => {
    const declared = declaredChapterNumber(row.title ?? "") ?? index + 1;
    return Math.max(highest, Math.floor(declared));
  }, 0);
}

/**
 * The chapters an uploaded file contains, split only where the file says so.
 *
 * <p>Used by the "add chapters from file" flow, where the file is whatever the
 * publisher has to hand: sometimes one chapter, sometimes a part of the story
 * carrying a hundred of them. Reading every file as a single chapter turned
 * "Up-2 108-208.docx" into one 78,000-word chapter named after the file.
 *
 * <p>The word budget is deliberately not applied. A file uploaded as chapters
 * is authoritative about where its chapters end - that is the rule for this
 * flow - so a file with no markers comes back as exactly one chapter holding
 * all of its text, however long, rather than being cut into 800-word pieces.
 */
export async function readChaptersInFile(
  file: File,
  wordsPerChapter: number = WORDS_PER_CHAPTER,
): Promise<{ chapters: ImportedChapter[]; warnings: string[] }> {
  // Chapter markers are found exactly the way they are when a story is first
  // created - same headings, same "Chương N" lines, same guard against a stray
  // number pretending to be a heading - so the two flows never disagree about
  // where a chapter begins. Only the story's own metadata is ignored here.
  const parsed = await parseStoryDocument(file, wordsPerChapter);
  if (!parsed.autoSplit && parsed.chapters.length > 0) {
    return {
      chapters: parsed.chapters.filter((chapter) => chapter.content.trim().length > 0),
      warnings: parsed.warnings,
    };
  }

  // No usable markers. The word budget is deliberately not applied here: a file
  // uploaded as a chapter is authoritative about where that chapter ends, so it
  // is kept whole however long it runs, rather than cut into 800-word pieces
  // nobody wrote.
  const content = await readChapterText(file);
  if (!content.trim()) return { chapters: [], warnings: [] };
  return {
    chapters: [{ content, title: file.name.replace(/\.[^.]+$/u, "").trim() }],
    warnings: [],
  };
}

/** One chapter as the upload flow hands it to a workspace. */
export type UploadedChapter = {
  title: string;
  content: string;
};

export type ChapterUploadResult = {
  chapters: UploadedChapter[];
  /** Files that could not be read, already worded for the publisher. */
  failed: string[];
  /** Anything the parser flagged, prefixed with the file it came from. */
  warnings: string[];
  /** How many of the picked files turned out to hold more than one chapter. */
  multiChapterFiles: number;
};

/**
 * Turns picked files into chapters, ready to append to a story.
 *
 * <p>The one implementation both workspaces use. The publisher screen and the
 * admin drawer each had their own copy, and they drifted: one sorted the files
 * and numbered them, the other read them in picker order and titled each
 * chapter after its file. Role and permission rules stay where they are - this
 * is only about reading files.
 *
 * <p>Files are read in chapter order, each file is split at its own chapter
 * markers, and a chapter whose title carries no number is numbered on from
 * {@code startNumber}.
 */
export async function readChapterDraftsFromFiles(
  picked: readonly File[],
  startNumber: number,
  wordsPerChapter: number = WORDS_PER_CHAPTER,
): Promise<ChapterUploadResult> {
  const files = sortChapterFiles(picked);
  const chapters: UploadedChapter[] = [];
  const failed: string[] = [];
  const warnings: string[] = [];
  let multiChapterFiles = 0;
  let spare = startNumber;

  // Titles for the single-chapter files, so a file that names no chapter still
  // lands on the next free number rather than keeping a bare filename.
  const fallbackTitles = chapterTitlesForUpload(files, startNumber);

  for (const [index, file] of files.entries()) {
    try {
      const read = await readChaptersInFile(file, wordsPerChapter);
      warnings.push(...read.warnings.map((entry) => `${file.name}: ${entry}`));
      if (read.chapters.length === 0) {
        failed.push(`${file.name} (không có nội dung)`);
        continue;
      }

      if (read.chapters.length > 1) {
        multiChapterFiles += 1;
        for (const chapter of read.chapters) {
          const declared = declaredChapterNumber(chapter.title) != null;
          chapters.push({
            content: chapter.content,
            title: declared ? chapter.title : `Chương ${spare++}: ${chapter.title}`,
          });
        }
        continue;
      }

      const only = read.chapters[0]!;
      chapters.push({
        content: only.content,
        title: declaredChapterNumber(only.title) != null
          ? only.title
          : fallbackTitles[index] ?? only.title,
      });
    } catch (cause) {
      failed.push(`${file.name} — ${cause instanceof Error ? cause.message : "không đọc được"}`);
    }
  }

  // Numbers already used by the chapters just read, so a later unnumbered file
  // in the same batch cannot be given one of them.
  const used = new Set(
    chapters
      .map((chapter) => declaredChapterNumber(chapter.title))
      .filter((value): value is number => value != null),
  );
  for (const chapter of chapters) {
    if (declaredChapterNumber(chapter.title) != null) continue;
    while (used.has(spare)) spare += 1;
    used.add(spare);
    chapter.title = `Chương ${spare}: ${chapter.title}`;
  }

  return { chapters, failed, multiChapterFiles, warnings };
}

/** Reads an uploaded file as one chapter's plain text. */
export async function readChapterText(file: File): Promise<string> {
  const lines = await readDocumentLines(file);
  return lines
    .map((line) => line.text)
    .join("\n")
    .replace(/\n{3,}/gu, "\n\n")
    .trim();
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
  /**
   * Genre names the file listed, as written. Matching them to the site's own
   * genres is the caller's job - it is the only one holding that list.
   */
  categoryNames: string[];
  /** Words in the file, and words kept, so the caller can prove nothing was lost. */
  sourceWords: number;
  keptWords: number;
  /**
   * Things worth telling the publisher before they save: text that did not make
   * it into a chapter, chapter numbers out of order, and so on. Empty when the
   * file read cleanly.
   */
  warnings: string[];
  /**
   * Reasons the file cannot be imported at all - a story saved back to front,
   * for instance. Non-empty means the caller must refuse the file and say why.
   */
  errors: string[];
};

/** Words per chapter for an ordinary long-form story. */
export const WORDS_PER_CHAPTER = 800;

/** Zhihu stories run to a longer chapter than the rest of the catalogue. */
export const WORDS_PER_CHAPTER_ZHIHU = 1400;

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
function readCompletionStatus(lines: readonly DocumentLine[]): {
  completionStatus: "COMPLETED" | "ONGOING";
  markerLineIndex: number;
} {
  for (let index = 0; index < lines.length; index++) {
    const raw = lines[index]?.text.trim() ?? "";
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
const METADATA_LABELS: Record<string, "authorName" | "categories" | "synopsis" | "title"> = {
  "giới thiệu": "synopsis",
  "gioi thieu": "synopsis",
  "mô tả": "synopsis",
  "mo ta": "synopsis",
  "tóm tắt": "synopsis",
  "tom tat": "synopsis",
  // "Văn án" heads the blurb in translated Chinese web novels, and its text
  // almost always sits on the lines below the label rather than after the colon.
  "văn án": "synopsis",
  "van an": "synopsis",
  "tác giả": "authorName",
  "tac gia": "authorName",
  "tên truyện": "title",
  "ten truyen": "title",
  // The genre list a file carries names the same genres the form asks the
  // publisher to tick, so it is read rather than retyped.
  "thể loại": "categories",
  "the loai": "categories",
  author: "authorName",
  description: "synopsis",
  genre: "categories",
  genres: "categories",
  summary: "synopsis",
  title: "title",
};


/**
 * Reads story metadata from the top of an uploaded file so the admin does not
 * have to retype it. Recognises "Tác giả: ..." style headers before the first
 * chapter heading; anything it cannot identify is left for manual entry.
 */
export async function parseStoryDocument(
  file: File,
  wordsPerChapter: number = WORDS_PER_CHAPTER,
): Promise<ImportedStory> {
  // One reader for every format; it decides from the bytes what it is holding.
  const rawLines = await readDocumentLines(file);

  // "Đã hoàn thành" on the opening line marks the story finished. When that line
  // is nothing but the marker it is dropped, so it cannot become the title.
  const { completionStatus, markerLineIndex } = readCompletionStatus(rawLines);
  const lines = markerLineIndex >= 0
    ? rawLines.filter((_, index) => index !== markerLineIndex)
    : rawLines;

  const fallbackTitle = file.name.replace(/\.[^.]+$/u, "").trim() || "Truyện mới";
  const metadata: { authorName: string; categories: string; synopsis: string; title: string } = {
    authorName: "",
    categories: "",
    synopsis: "",
    title: "",
  };

  // Chapter headings decide everything, so they are located first. The same
  // rule has to be used here and in splitChapterBlocks: deciding the header
  // block by one rule and the chapter boundaries by another puts the two out of
  // step, and the text between them is what goes missing.
  const trustStyles = shouldTrustStyles(lines);
  let firstChapterIndex = lines.length;
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    if (line && isChapterHeading(line, trustStyles)) {
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
    // No blank line anywhere means the file has no header block to skip; the
    // first line is the title and everything after it is story. Treating the
    // whole file as header left the story with no chapters at all.
    bodyStartIndex = lines.findIndex((line) => line.text.trim()) + 1;
    for (let index = 0; index < lines.length; index++) {
      const line = lines[index]?.text ?? "";
      if (sawContent && !line.trim()) {
        bodyStartIndex = index + 1;
        break;
      }
      if (line.trim()) sawContent = true;
    }
  }

  const headerLines = lines.slice(0, hasHeadings ? firstChapterIndex : bodyStartIndex);

  const unlabelled: string[] = [];
  // A label with nothing after the colon - "Văn án:" on its own line - owns
  // everything below it until the next label or the first chapter. Reading only
  // the text on the same line left the synopsis empty and pushed the blurb into
  // the unlabelled pile, where it competed with the title.
  let openLabel: "authorName" | "categories" | "synopsis" | "title" | null = null;
  for (const entry of headerLines) {
    const line = entry.text.trim();
    if (!line) continue;
    const match = /^\s*([^:]{1,20})\s*:\s*(.*)$/u.exec(line);
    const key = match ? METADATA_LABELS[(match[1] ?? "").trim().toLowerCase()] : undefined;

    // A label written without its colon - a line that is just "Văn án" - heads
    // the blurb exactly the same way. Requiring the colon left that word as the
    // first line of the synopsis, so every story imported from such a file
    // opened with the word "Văn án" before its own blurb.
    if (!key) {
      const bare = METADATA_LABELS[line.toLowerCase()];
      if (bare) {
        openLabel = bare;
        continue;
      }
    }

    if (match && key) {
      const inlineValue = (match[2] ?? "").trim();
      if (inlineValue) {
        if (!metadata[key]) metadata[key] = inlineValue;
        // Phần giới thiệu vẫn chạy tiếp xuống những dòng dưới, kể cả khi dòng
        // nhãn đã có sẵn chữ. Đóng nhãn ngay tại đây - như bản trước làm - thì
        // một văn án viết kiểu
        //
        //     Giới thiệu: Đường Kiều là vai ác một bộ truyện tiên hiệp.
        //     Nàng cẩn thận đi theo cốt truyện...
        //
        // chỉ giữ được đúng dòng đầu; những dòng sau rơi vào đống không nhãn và
        // bị bỏ luôn, vì đống đó chỉ dùng khi giới thiệu còn trống. Đó là lý do
        // truyện nào cũng chỉ còn vài dòng giới thiệu.
        //
        // Chỉ mở tiếp cho giới thiệu: tên truyện, tác giả và thể loại đều là
        // giá trị một dòng, cho chúng nối tiếp sẽ nuốt luôn dòng kế bên.
        openLabel = key === "synopsis" ? "synopsis" : null;
      } else {
        // Bare label: its value is on the following lines.
        openLabel = key;
      }
      continue;
    }

    if (openLabel) {
      metadata[openLabel] = metadata[openLabel]
        ? `${metadata[openLabel]}\n${line}`
        : line;
      continue;
    }
    unlabelled.push(line);
  }

  // Without labels, treat the first line as the title and the rest as synopsis.
  if (!metadata.title && unlabelled.length > 0) {
    metadata.title = unlabelled.shift() ?? "";
  }
  if (!metadata.synopsis && unlabelled.length > 0) {
    metadata.synopsis = unlabelled.join("\n");
  }

  const bodyLines = lines.slice(hasHeadings ? firstChapterIndex : bodyStartIndex);

  // A file that numbers its own chapters is believed, and its chapters are kept
  // exactly as written. The word budget used to apply on top of the headings,
  // so a document with twelve chapters of ~1,100 words each arrived as
  // "Chương 2: Đoán mệnh (1/2)", "Chương 2: Đoán mệnh (2/2)" and so on - the
  // upload silently disagreeing with the author about where a chapter ends.
  //
  // The budget survives only for a document whose single heading covers the
  // whole text: there the heading structured nothing, so cutting it is the only
  // way it does not arrive as one unreadable chapter.
  const headingChapters = hasHeadings
    ? splitChapterBlocks(bodyLines, metadata.title || fallbackTitle)
    : [];
  // The file's own chapters are kept exactly as written whenever those chapters
  // are real. When the lines that looked like headings turn out not to divide
  // the story - a stray "1", a numbered list - the document is treated as having
  // no headings at all and cut by word count, which is what it needed.
  const chapters: ImportedChapter[] = headingsPartitionDocument(headingChapters, wordsPerChapter)
    ? headingChapters
    : splitByWordCount(bodyLines.map((line) => line.text), wordsPerChapter);

  // Counted rather than assumed. Every earlier bug in this parser lost text
  // quietly - a heading rule that fired mid-chapter, a header block that
  // swallowed the opening - and the upload reported success either way. The two
  // figures below are what let the caller say the file arrived whole.
  const sourceWords = rawLines.reduce((sum, line) => sum + wordCount(line.text), 0);
  const headerWords = headerLines.reduce((sum, line) => sum + wordCount(line.text), 0);
  const keptWords = chapters.reduce((sum, chapter) => sum + wordCount(chapter.content), 0);
  // A heading line becomes the chapter's title, so its words are kept even
  // though they are not in the body. Counting only bodies made every file look
  // as though it had lost a few hundred words.
  const titleWords = chapters.reduce((sum, chapter) => sum + wordCount(chapter.title), 0);

  const warnings: string[] = [];
  // The header block is metadata, so it is expected not to be in a chapter;
  // anything beyond that is text the parser dropped.
  const missing = sourceWords - headerWords - keptWords - titleWords;
  if (missing > Math.max(20, sourceWords * 0.005)) {
    warnings.push(
      `Có khoảng ${missing.toLocaleString("vi-VN")} từ trong file không nằm trong chương nào. `
      + "Hãy kiểm tra lại file trước khi lưu.",
    );
  }
  const order = inspectChapterOrder(chapters);
  warnings.push(...order.warnings);

  return {
    authorName: metadata.authorName,
    // True when the numbering came from the word budget rather than the file.
    // The message shown to the publisher turns on this, so it has to describe
    // what actually happened: chapters came either straight from the file, or
    // from the word budget.
    autoSplit: chapters !== headingChapters,
    // A file writes its genres on one line separated by commas, or one per
    // line under a bare "Thể loại:" label; both arrive here as one string.
    categoryNames: metadata.categories
      .split(/[,;\n]/u)
      .map((name) => name.trim())
      .filter(Boolean),
    chapters,
    completionStatus,
    keptWords,
    sourceWords,
    errors: order.errors,
    synopsis: metadata.synopsis,
    title: metadata.title || fallbackTitle,
    warnings,
  };
}


