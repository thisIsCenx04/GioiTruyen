import { describe, expect, it } from "vitest";

import { splitByWordCount, WORDS_PER_CHAPTER_ZHIHU } from "./story-import";

/**
 * Truyện Zhihu về sai định dạng còn truyện dài thì không, và lý do nằm ở đây.
 *
 * <p>Truyện dài đi qua nhánh cắt theo tiêu đề chương, nơi các dòng được nối lại
 * bằng một dấu xuống dòng và chỉ dòng trống thừa mới bị gộp - tức là giữ nguyên
 * bản gốc. Zhihu không có tiêu đề chương nên phải cắt theo số từ, và nhánh đó
 * vốn bỏ hết dòng trống rồi nối mọi dòng bằng hai dấu xuống dòng: dòng trống
 * ngắt cảnh biến mất, còn mỗi dòng của một đoạn bị bẻ dòng cứng lại thành một
 * đoạn riêng.
 */

const line = (words: number, tag: string) =>
  Array.from({ length: words }, (_, index) => `${tag}${index}`).join(" ");

describe("splitByWordCount cho truyện Zhihu", () => {
  it("giữ nguyên cách xuống dòng của bản gốc", () => {
    const source = [
      "Dòng đầu của đoạn một",
      "dòng sau của cùng đoạn đó",
      "",
      "Đoạn hai sau một dòng trống",
    ];

    const [chapter] = splitByWordCount(source, WORDS_PER_CHAPTER_ZHIHU, true);

    expect(chapter?.content).toBe(source.join("\n"));
  });

  it("không biến mỗi dòng thành một đoạn riêng", () => {
    const source = ["Dòng một", "Dòng hai", "Dòng ba"];

    const [chapter] = splitByWordCount(source, WORDS_PER_CHAPTER_ZHIHU, true);

    expect(chapter?.content).not.toContain("\n\n");
  });

  it("gộp ba dòng trống trở lên thành một, đúng luật của truyện dài", () => {
    const source = ["Đoạn một", "", "", "", "Đoạn hai"];

    const [chapter] = splitByWordCount(source, WORDS_PER_CHAPTER_ZHIHU, true);

    expect(chapter?.content).toBe("Đoạn một\n\nĐoạn hai");
  });

  it("cắt chương tại dòng trống gần nhất sau khi đủ số từ", () => {
    // Hai khối 1000 từ: khối đầu vượt ngân sách 1400 ngay giữa khối hai, nhưng
    // chỗ cắt phải là dòng trống chứ không phải giữa đoạn.
    const source = [line(1000, "a"), "", line(1000, "b"), "", line(1000, "c")];

    const chapters = splitByWordCount(source, WORDS_PER_CHAPTER_ZHIHU, true);

    expect(chapters).toHaveLength(2);
    expect(chapters[0]!.content).toBe(`${line(1000, "a")}\n\n${line(1000, "b")}`);
    expect(chapters[1]!.content).toBe(line(1000, "c"));
  });

  it("vẫn cắt được văn bản viết liền không có dòng trống nào", () => {
    // Không có chỗ ngắt nào để chờ, nên chương phải cắt ở ranh giới dòng thay vì
    // dồn hết thành một chương khổng lồ.
    const source = Array.from({ length: 12 }, (_, index) => line(500, `d${index}_`));

    const chapters = splitByWordCount(source, WORDS_PER_CHAPTER_ZHIHU, true);

    expect(chapters.length).toBeGreaterThan(1);
    for (const chapter of chapters) {
      // Mỗi dòng nguồn phải còn nguyên vẹn trong đúng một chương.
      expect(chapter.content.split("\n").every((row) => row.trim().length > 0)).toBe(true);
    }
    expect(chapters.map((chapter) => chapter.content).join("\n")).toBe(source.join("\n"));
  });

  it("bỏ dòng trống thừa ở đầu và cuối", () => {
    const chapters = splitByWordCount(["", "  ", "Nội dung", ""], WORDS_PER_CHAPTER_ZHIHU, true);

    expect(chapters).toHaveLength(1);
    expect(chapters[0]!.content).toBe("Nội dung");
  });

  it("không đổi hành vi của truyện dài khi không bật cờ", () => {
    const source = ["Dòng một", "Dòng hai"];

    const [chapter] = splitByWordCount(source, WORDS_PER_CHAPTER_ZHIHU);

    expect(chapter?.content).toBe("Dòng một\n\nDòng hai");
  });
});
