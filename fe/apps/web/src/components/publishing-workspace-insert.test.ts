import { describe, expect, it } from "vitest";

import {
  chapterInsertIndex,
  composeChapterTitle,
  draftChapterNumber,
  normalizeChapterPricing,
} from "@/components/publishing-workspace";

/**
 * Chèn một chương vào giữa, và giữ cho giá với loại chương luôn khớp nhau.
 *
 * <p>Hai việc này đứng cùng một chỗ vì cùng một lý do: cả hai từng là những
 * phép tính rải rác trong phần giao diện, nơi không ai kiểm được. Chèn sai chỗ
 * thì độc giả đọc nhảy cóc; giá lệch loại thì cả lần lưu bảy trăm chương bị máy
 * chủ từ chối vì một ô chưa điền.
 */

const rows = (...titles: string[]) => titles.map((title) => ({ title }));

describe("draftChapterNumber", () => {
  it("lấy số từ tiêu đề", () => {
    expect(draftChapterNumber(rows("Chương 86: Gặp lại"), 0)).toBe(86);
  });

  /* Tiêu đề không ghi số thì vị trí trong danh sách chính là số chương. */
  it("không có số trong tiêu đề thì lấy vị trí", () => {
    expect(draftChapterNumber(rows("Mở đầu", "Kết"), 1)).toBe(2);
  });

  it("đọc được số lẻ của ngoại truyện", () => {
    expect(draftChapterNumber(rows("Chương 86.5: Ngoại truyện"), 0)).toBe(86.5);
  });
});

describe("chapterInsertIndex", () => {
  const list = rows("Chương 1", "Chương 2", "Chương 85", "Chương 87", "Chương 88");

  it("chèn vào đúng giữa hai chương", () => {
    expect(chapterInsertIndex(list, 86)).toBe(3);
  });

  it("số nhỏ nhất về đầu danh sách", () => {
    expect(chapterInsertIndex(list, 0)).toBe(0);
  });

  it("số lớn nhất về cuối danh sách", () => {
    expect(chapterInsertIndex(list, 999)).toBe(list.length);
  });

  it("danh sách rỗng thì chèn ở vị trí đầu", () => {
    expect(chapterInsertIndex([], 5)).toBe(0);
  });

  /* Ngoại truyện 86.5 phải rơi đúng giữa 86 và 87, không phải sau cả hai. */
  it("số lẻ nằm giữa hai số nguyên", () => {
    const withEighty = rows("Chương 85", "Chương 86", "Chương 87");
    expect(chapterInsertIndex(withEighty, 86.5)).toBe(2);
  });

  /* Thêm một chương trùng số thì nó nằm ngay sau bản cũ, chứ không đẩy bản cũ
     xuống - người thêm đang bổ sung, không phải thay thế. */
  it("trùng số thì nằm ngay sau bản đã có", () => {
    expect(chapterInsertIndex(rows("Chương 1", "Chương 2", "Chương 3"), 2)).toBe(2);
  });

  it("danh sách chưa đánh số vẫn chèn được theo vị trí", () => {
    expect(chapterInsertIndex(rows("Mở đầu", "Diễn biến", "Kết"), 2)).toBe(2);
  });
});

describe("composeChapterTitle", () => {
  it("ghép số vào tên người nhập gõ", () => {
    expect(composeChapterTitle(86, "Gặp lại cố nhân")).toBe("Chương 86: Gặp lại cố nhân");
  });

  /* Người nhập gõ sẵn "Chương 86" thì không lồng thêm một lần nữa. */
  it("không lồng hai lần khi tên đã có số", () => {
    expect(composeChapterTitle(86, "Chương 86: Gặp lại")).toBe("Chương 86: Gặp lại");
  });

  it("bỏ trống tên thì vẫn có tiêu đề", () => {
    expect(composeChapterTitle(86, "   ")).toBe("Chương 86");
  });
});

describe("normalizeChapterPricing", () => {
  /* Đây là lỗi người dùng gặp: chọn "Trả phí" xong quên gõ giá, và cả lần lưu
     bị máy chủ từ chối. Một luật duy nhất thì không thể lệch. */
  it("giá 0 là chương miễn phí, dù đang để trả phí", () => {
    expect(normalizeChapterPricing({ accessType: "PAID", coinPrice: 0 }))
      .toEqual({ accessType: "FREE", coinPrice: 0 });
  });

  it("có giá thì thành chương trả phí, dù đang để miễn phí", () => {
    expect(normalizeChapterPricing({ accessType: "FREE", coinPrice: 50 }))
      .toEqual({ accessType: "PAID", coinPrice: 50 });
  });

  it("giá âm bị kéo về 0 và thành miễn phí", () => {
    expect(normalizeChapterPricing({ accessType: "PAID", coinPrice: -10 }))
      .toEqual({ accessType: "FREE", coinPrice: 0 });
  });

  it("giá lẻ được làm tròn xuống số nguyên", () => {
    expect(normalizeChapterPricing({ accessType: "PAID", coinPrice: 12.7 }))
      .toEqual({ accessType: "PAID", coinPrice: 12 });
  });

  it("giá không phải số thì coi như miễn phí", () => {
    expect(normalizeChapterPricing({ accessType: "PAID", coinPrice: Number.NaN }))
      .toEqual({ accessType: "FREE", coinPrice: 0 });
  });

  it("giữ nguyên các trường khác của chương", () => {
    const chapter = { accessType: "FREE" as const, coinPrice: 30, content: "abc", title: "Chương 1" };
    expect(normalizeChapterPricing(chapter))
      .toEqual({ accessType: "PAID", coinPrice: 30, content: "abc", title: "Chương 1" });
  });
});
