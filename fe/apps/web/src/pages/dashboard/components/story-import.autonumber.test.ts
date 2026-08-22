// @vitest-environment jsdom
import { strToU8, zipSync } from "fflate";
import { describe, expect, it } from "vitest";

import { parseStoryDocument, readChaptersInFile } from "./story-import";

/**
 * File Word đánh số chương tự động.
 *
 * <p>Word có hai cách viết một tiêu đề chương. Tác giả có thể gõ thẳng "Chương
 * 711: Tựa đề" thành chữ, hoặc dùng đánh số tự động - lúc đó phần "Chương 711"
 * do Word sinh ra khi hiển thị, còn trong tệp chỉ có tựa đề trần cùng một kiểu
 * Heading. Ngăn điều hướng của Word hiện y hệt nhau ở cả hai cách, nên người
 * dùng không có cách nào biết tệp của mình thuộc loại nào.
 *
 * <p>Một tệp dài trộn cả hai cách là chuyện thường: tác giả gõ tay vài chương
 * đầu rồi chuyển sang đánh số tự động, hoặc ghép nhiều tệp lại. Bộ đọc phải
 * nhận được cả hai.
 */

/** Một đoạn văn thường. */
function para(text: string): string {
  return `<w:p><w:r><w:t xml:space="preserve">${text}</w:t></w:r></w:p>`;
}

/** Tiêu đề Word đánh số tự động: chữ chỉ có tựa, số nằm trong w:numPr. */
function autoNumberedHeading(title: string): string {
  return `<w:p><w:pPr><w:pStyle w:val="Heading1"/><w:numPr><w:ilvl w:val="0"/>`
    + `<w:numId w:val="1"/></w:numPr><w:outlineLvl w:val="0"/></w:pPr>`
    + `<w:r><w:t xml:space="preserve">${title}</w:t></w:r></w:p>`;
}

/** Tiêu đề tác giả gõ tay: cả "Chương N" lẫn tựa đều nằm trong chữ. */
function typedHeading(number: number, title: string): string {
  return `<w:p><w:pPr><w:pStyle w:val="Heading1"/><w:outlineLvl w:val="0"/></w:pPr>`
    + `<w:r><w:t xml:space="preserve">Chương ${number}: ${title}</w:t></w:r></w:p>`;
}

function docx(bodyXml: string, name = "test.docx"): File {
  const document = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>`
    + `<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">`
    + `<w:body>${bodyXml}</w:body></w:document>`;
  const zipped = zipSync({
    "[Content_Types].xml": strToU8('<?xml version="1.0"?><Types/>'),
    "word/document.xml": strToU8(document),
  });
  return new File([zipped], name, {
    type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  });
}

const BODY = "Hắn bước qua cánh cửa gỗ mục và nhìn thấy bóng người quen đứng đó. ".repeat(30);

describe("file Word đánh số chương tự động", () => {
  it("nhận tiêu đề đánh số tự động khi cả tệp đều dùng cách đó", async () => {
    const body = Array.from({ length: 5 }, (_, index) =>
      autoNumberedHeading(`Tựa đề ${index + 1}`) + para(BODY)).join("");

    const { chapters } = await readChaptersInFile(docx(body));

    expect(chapters).toHaveLength(5);
  });

  it("nhận tiêu đề gõ tay khi cả tệp đều gõ tay", async () => {
    const body = Array.from({ length: 5 }, (_, index) =>
      typedHeading(index + 1, `Tựa đề ${index + 1}`) + para(BODY)).join("");

    const { chapters } = await readChaptersInFile(docx(body));

    expect(chapters).toHaveLength(5);
  });

  /**
   * Đây là tệp người dùng gặp lỗi: vài chương gõ tay, phần còn lại đánh số tự
   * động. Bộ đọc chỉ nhận đúng những chương gõ tay và nuốt hết phần còn lại vào
   * chương liền trước - một tệp bảy trăm chương về còn chín mươi.
   */
  it("nhận CẢ HAI khi tệp trộn hai cách", async () => {
    const pieces: string[] = [];
    // Ba chương đầu gõ tay, như tác giả bắt đầu bằng tay rồi mới bật đánh số.
    for (let index = 1; index <= 3; index += 1) {
      pieces.push(typedHeading(index, `Tựa đề ${index}`), para(BODY));
    }
    for (let index = 4; index <= 40; index += 1) {
      pieces.push(autoNumberedHeading(`Tựa đề ${index}`), para(BODY));
    }

    const { chapters } = await readChaptersInFile(docx(pieces.join("")));

    expect(chapters).toHaveLength(40);
  });

  /**
   * Hệ quả thứ hai của cùng một lỗi: chữ của những chương bị bỏ sót dồn hết vào
   * chương gõ tay gần nhất, nên một "chương" phình lên gấp nhiều lần bình
   * thường mà không có gì báo cho người dùng biết.
   */
  it("không dồn chữ của chương bị bỏ sót vào chương liền trước", async () => {
    const pieces = [typedHeading(1, "Tựa đề 1"), para(BODY)];
    for (let index = 2; index <= 10; index += 1) {
      pieces.push(autoNumberedHeading(`Tựa đề ${index}`), para(BODY));
    }

    const { chapters } = await readChaptersInFile(docx(pieces.join("")));
    const words = (text: string) => text.split(/\s+/u).filter(Boolean).length;

    expect(words(chapters[0].content)).toBeLessThan(words(BODY) * 2);
  });
});

/**
 * Phần giới thiệu chạy nhiều dòng.
 *
 * <p>Văn án của truyện dài dăm bảy dòng là chuyện thường, và người ta viết nó
 * ngay sau dấu hai chấm rồi xuống dòng viết tiếp. Bản trước đóng nhãn ngay khi
 * thấy chữ trên cùng dòng, nên chỉ giữ được dòng đầu - mọi truyện nhập từ file
 * đều mất gần hết văn án mà không có gì báo.
 */
describe("giới thiệu nhiều dòng", () => {
  const BLURB = [
    "Đường Kiều là vai ác một bộ truyện tiên hiệp.",
    "Nàng cẩn thận đi theo cốt truyện, cho đến đêm trước khi chết.",
    "Dựa theo những gì hệ thống nói, chỉ cần nam nữ chính giết lên tới núi.",
    "Cả đời này vô cùng thuận lợi, điều khiển nàng khó chịu nhất là đối thủ.",
  ];

  it("giữ đủ các dòng khi văn án bắt đầu ngay sau dấu hai chấm", async () => {
    const body = [
      para("Tên truyện: Đường Kiều"),
      para(`Giới thiệu: ${BLURB[0]}`),
      ...BLURB.slice(1).map(para),
      typedHeading(1, "Mở đầu"),
      para(BODY),
    ].join("");

    const { chapters } = await readChaptersInFile(docx(body));
    const story = await parseStoryDocument(docx(body));

    expect(story.synopsis.split("\n")).toEqual(BLURB);
    expect(chapters).toHaveLength(1);
  });

  it("vẫn giữ đủ khi nhãn đứng một mình rồi mới tới văn án", async () => {
    const body = [
      para("Tên truyện: Đường Kiều"),
      para("Giới thiệu:"),
      ...BLURB.map(para),
      typedHeading(1, "Mở đầu"),
      para(BODY),
    ].join("");

    const story = await parseStoryDocument(docx(body));

    expect(story.synopsis.split("\n")).toEqual(BLURB);
  });

  /* Tên truyện và tác giả là giá trị một dòng: cho chúng nối tiếp thì dòng kế
     bên bị nuốt vào, và truyện mang cái tên dài hai dòng. */
  it("không cho tên truyện nuốt dòng kế tiếp", async () => {
    const body = [
      para("Tên truyện: Đường Kiều"),
      para("Tác giả: Mộ Thần"),
      para("Giới thiệu: Một câu."),
      typedHeading(1, "Mở đầu"),
      para(BODY),
    ].join("");

    const story = await parseStoryDocument(docx(body));

    expect(story.title).toBe("Đường Kiều");
    expect(story.authorName).toBe("Mộ Thần");
    expect(story.synopsis).toBe("Một câu.");
  });
});
