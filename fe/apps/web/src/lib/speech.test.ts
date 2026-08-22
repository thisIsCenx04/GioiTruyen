// @vitest-environment jsdom
import { describe, expect, it } from "vitest";

import {
  CHUNK_LIMIT,
  chapterParagraphs,
  chapterSpeech,
  chapterText,
  estimateSeconds,
  formatDuration,
  isVietnamese,
  paragraphChunks,
  pickVoice,
  pickVoiceByGender,
  voiceGender,
} from "@/lib/speech";

/**
 * Điều kiện sống còn của bản đọc: chữ đưa cho bộ đọc phải bằng đúng chữ của
 * chương. Một chữ bị đổi là một chỗ người nghe nghe sai truyện mà không hề biết
 * - không có cách nào họ phát hiện ra, vì họ đang nghe chứ không đang đọc.
 */

describe("chapterParagraphs", () => {
  it("giữ nguyên từng chữ, chỉ bỏ thẻ", () => {
    expect(chapterParagraphs("<p>Diệp Phàm <strong>bước</strong> vào căn phòng.</p>"))
      .toEqual(["Diệp Phàm bước vào căn phòng."]);
  });

  it("giữ đúng thứ tự các đoạn", () => {
    expect(chapterParagraphs("<p>Một.</p><p>Hai.</p><p>Ba.</p>"))
      .toEqual(["Một.", "Hai.", "Ba."]);
  });

  /* Thẻ bọc ngoài chứa các đoạn con sẽ lặp lại y nguyên chữ của con nó. */
  it("không đọc lặp khi đoạn nằm trong thẻ bọc", () => {
    expect(chapterParagraphs("<div><p>Một.</p><p>Hai.</p></div>"))
      .toEqual(["Một.", "Hai."]);
  });

  it("bỏ script và style, không đọc mã nguồn thành lời", () => {
    expect(chapterParagraphs("<p>Chào.</p><script>alert(1)</script><style>p{color:red}</style>"))
      .toEqual(["Chào."]);
  });

  /* <br> là ranh giới đoạn. Bản trước đổi nó thành ". " - tức là chèn thêm một
     dấu chấm mà tác giả không viết. Giờ nó chỉ tách đoạn. */
  it("coi <br> là ranh giới đoạn, không chèn dấu chấm", () => {
    expect(chapterParagraphs("<p>Dòng một<br>Dòng hai</p>"))
      .toEqual(["Dòng một", "Dòng hai"]);
  });

  it("không chèn dấu câu vào cuối đoạn chưa có dấu", () => {
    expect(chapterParagraphs("<p>Trời tối</p><p>Hắn đi</p>"))
      .toEqual(["Trời tối", "Hắn đi"]);
  });

  it("gộp khoảng trắng thừa nhưng không đụng tới chữ", () => {
    expect(chapterParagraphs("<p>Hắn   \n\n  đi\tvề</p>")).toEqual(["Hắn đi về"]);
  });

  it("bỏ đoạn rỗng dùng để giãn dòng", () => {
    expect(chapterParagraphs("<p>Một.</p><p></p><p>  </p><p>Hai.</p>"))
      .toEqual(["Một.", "Hai."]);
  });

  it("chương chỉ có chữ trần vẫn tách được", () => {
    expect(chapterParagraphs("Một.\nHai.")).toEqual(["Một.", "Hai."]);
  });

  it("HTML rỗng ra danh sách rỗng", () => {
    expect(chapterParagraphs("")).toEqual([]);
    expect(chapterParagraphs("<p></p>")).toEqual([]);
  });
});

describe("paragraphChunks", () => {
  it("đoạn ngắn giữ nguyên một mẩu", () => {
    expect(paragraphChunks("Hắn đi. Nàng ở lại."))
      .toEqual(["Hắn đi. Nàng ở lại."]);
  });

  /* Vượt ngưỡng là Chrome ngắt câm giữa chừng, không nổ onend, trình phát treo. */
  it("không mẩu nào vượt ngưỡng, kể cả với đoạn rất dài", () => {
    const long = "Hắn bước qua cánh cửa gỗ mục và nhìn thấy bóng người quen. ".repeat(120);
    const chunks = paragraphChunks(long);

    expect(chunks.length).toBeGreaterThan(20);
    for (const chunk of chunks) expect(chunk.length).toBeLessThanOrEqual(CHUNK_LIMIT);
  });

  it("cắt được cả một câu dài không có dấu chấm nào", () => {
    const chunks = paragraphChunks("tiếng nói vọng lại từ phía sau lưng ".repeat(40));

    expect(chunks.length).toBeGreaterThan(1);
    for (const chunk of chunks) expect(chunk.length).toBeLessThanOrEqual(CHUNK_LIMIT);
  });

  it("không cắt giữa một từ", () => {
    const chunks = paragraphChunks("khoảnh khắc ấy hắn hiểu ra mọi chuyện đã muộn ".repeat(30));
    for (const chunk of chunks) expect(chunk.trim()).toBe(chunk);
  });

  it("đoạn rỗng không sinh mẩu nào", () => {
    expect(paragraphChunks("   ")).toEqual([]);
  });
});

describe("chapterSpeech", () => {
  const html = "<p>Diệp Phàm bước vào căn phòng.</p><p>Bên trong tối om.</p>";

  it("mỗi mẩu chỉ đúng về đoạn chứa nó", () => {
    const { chunks, paragraphs } = chapterSpeech(html);

    expect(paragraphs).toHaveLength(2);
    expect(chunks.map((chunk) => chunk.paragraph)).toEqual([0, 1]);
    for (const chunk of chunks) {
      expect(paragraphs[chunk.paragraph]).toContain(chunk.text);
    }
  });

  it("một đoạn dài sinh nhiều mẩu nhưng vẫn chỉ về một đoạn", () => {
    const long = `<p>${"Hắn bước rất chậm về phía cánh cửa đang khép hờ. ".repeat(30)}</p>`;
    const { chunks, paragraphs } = chapterSpeech(long);

    expect(paragraphs).toHaveLength(1);
    expect(chunks.length).toBeGreaterThan(1);
    expect(new Set(chunks.map((chunk) => chunk.paragraph))).toEqual(new Set([0]));
  });

  /* ĐÂY LÀ BÀI KIỂM QUAN TRỌNG NHẤT của cả tệp: ghép mọi mẩu lại phải ra đúng
     toàn văn chương. Nó bắt được mọi kiểu hỏng - dịch, viết lại, thêm dấu câu,
     nuốt chữ, đảo thứ tự - bằng một phép so sánh duy nhất. */
  it("ghép mọi mẩu lại ra đúng toàn văn chương, không thêm không bớt", () => {
    const chapter = "<p>Diệp Phàm bước vào căn phòng.</p>"
      + "<p>“Ai đó?” hắn hỏi, giọng khàn đặc.</p>"
      + `<p>${"Không có ai trả lời, chỉ có tiếng gió lùa qua khe cửa sổ vỡ. ".repeat(20)}</p>`
      + "<p>Hắn đếm: 1, 2, 3.</p>";
    const { chunks, paragraphs } = chapterSpeech(chapter);

    const spoken = paragraphs
      .map((_, index) => chunks
        .filter((chunk) => chunk.paragraph === index)
        .map((chunk) => chunk.text)
        .join(" "))
      .join("\n");

    expect(spoken).toBe(chapterText(chapter));
  });

  it("giữ nguyên tên riêng, số và dấu ngoặc kép", () => {
    const { chunks } = chapterSpeech("<p>Diệp Phàm nói: “Ta có 3 viên đan dược.”</p>");
    expect(chunks[0].text).toBe("Diệp Phàm nói: “Ta có 3 viên đan dược.”");
  });
});

describe("estimateSeconds", () => {
  it("150 tiếng là một phút", () => {
    expect(estimateSeconds(Array(150).fill("chữ").join(" "))).toBe(60);
  });

  it("đọc nhanh gấp rưỡi thì ngắn đi tương ứng", () => {
    expect(estimateSeconds(Array(150).fill("chữ").join(" "), 1.5)).toBe(40);
  });
});

describe("formatDuration", () => {
  it("dưới một phút tính bằng giây", () => {
    expect(formatDuration(48)).toBe("48 giây");
  });

  /* Làm tròn xuống 0 giây rồi hiện "0 giây" trông như hỏng, nên sàn là 1. */
  it("không bao giờ hiện 0 giây", () => {
    expect(formatDuration(0)).toBe("1 giây");
  });

  it("dưới một giờ tính bằng phút", () => {
    expect(formatDuration(720)).toBe("12 phút");
  });

  it("từ một giờ trở lên tách giờ và phút", () => {
    expect(formatDuration(3840)).toBe("1 giờ 04 phút");
  });
});

describe("isVietnamese", () => {
  it("nhận mọi cách viết mã tiếng Việt", () => {
    expect(isVietnamese({ lang: "vi-VN" })).toBe(true);
    expect(isVietnamese({ lang: "VI-vn" })).toBe(true);
    expect(isVietnamese({ lang: "vi_VN" })).toBe(true);
    expect(isVietnamese({ lang: "vi" })).toBe(true);
  });

  it("loại mọi thứ tiếng khác", () => {
    for (const lang of ["en-US", "en-GB", "en-AU", "zh-CN", "ja-JP", "ko-KR", "fr-FR", "de-DE"]) {
      expect(isVietnamese({ lang })).toBe(false);
    }
  });

  /* "vi" là tiền tố của "vi-VN" nhưng cũng của những mã chẳng liên quan. */
  it("không nhầm mã khác bắt đầu bằng chữ vi", () => {
    expect(isVietnamese({ lang: "vic-XX" })).toBe(false);
  });
});

describe("pickVoice", () => {
  const en = { lang: "en-US", localService: true, name: "Microsoft David - English (United States)" };
  const zh = { lang: "zh-CN", localService: true, name: "Microsoft Huihui - Chinese" };
  const viLocal = { lang: "vi-VN", localService: true, name: "Microsoft An - Vietnamese" };
  const viOnline = { lang: "vi-VN", localService: false, name: "Microsoft HoaiMy Online (Natural) - Vietnamese" };

  /* Đây là lỗi người nghe gặp phải: chữ tiếng Việt đọc bằng giọng tiếng Anh ra
     một tràng âm vô nghĩa. Thà không phát còn hơn phát thứ đó. */
  it("máy không có giọng tiếng Việt thì trả về null, không lấy giọng nước ngoài", () => {
    expect(pickVoice([en, zh])).toBeNull();
  });

  it("không có giọng nào cũng trả về null", () => {
    expect(pickVoice([])).toBeNull();
  });

  it("bỏ qua giọng nước ngoài khi có giọng tiếng Việt", () => {
    expect(pickVoice([en, zh, viLocal])).toBe(viLocal);
  });

  /* Giọng cài sẵn đọc được cả khi mất mạng và không có độ trễ gọi máy chủ. */
  it("ưu tiên giọng cài sẵn trong máy hơn giọng phải tải", () => {
    expect(pickVoice([viOnline, viLocal])).toBe(viLocal);
  });

  it("cùng hạng thì lấy giọng nghe tự nhiên hơn", () => {
    const cu = { lang: "vi-VN", localService: false, name: "Vietnamese Robot" };
    expect(pickVoice([cu, viOnline])).toBe(viOnline);
  });
});

describe("voiceGender", () => {
  it("nhận ra giọng nữ", () => {
    expect(voiceGender({ name: "Microsoft HoaiMy Online (Natural) - Vietnamese" })).toBe("female");
    expect(voiceGender({ name: "vi-vn-x-vif-local" })).toBe("female");
    expect(voiceGender({ name: "Linh" })).toBe("female");
  });

  it("nhận ra giọng nam", () => {
    expect(voiceGender({ name: "Microsoft NamMinh Online (Natural) - Vietnamese" })).toBe("male");
    expect(voiceGender({ name: "Microsoft An - Vietnamese" })).toBe("male");
    expect(voiceGender({ name: "vi-vn-x-vim-local" })).toBe("male");
  });

  it("tên lạ thì không đoán bừa", () => {
    expect(voiceGender({ name: "Google Tiếng Việt" })).toBeNull();
  });
});

describe("pickVoiceByGender", () => {
  const en = { lang: "en-US", localService: true, name: "Microsoft David - English" };
  const nu = { lang: "vi-VN", localService: true, name: "Microsoft HoaiMy - Vietnamese" };
  const nam = { lang: "vi-VN", localService: true, name: "Microsoft NamMinh - Vietnamese" };

  it("chọn đúng giọng theo giới tính", () => {
    expect(pickVoiceByGender([en, nu, nam], "female")).toBe(nu);
    expect(pickVoiceByGender([en, nu, nam], "male")).toBe(nam);
  });

  /* Nghe bằng giọng khác giới vẫn hiểu được truyện; không nghe được gì thì không. */
  it("máy chỉ có một giọng tiếng Việt thì vẫn dùng giọng đó", () => {
    expect(pickVoiceByGender([en, nu], "male")).toBe(nu);
  });

  it("không có giọng tiếng Việt nào thì trả về null", () => {
    expect(pickVoiceByGender([en], "female")).toBeNull();
  });
});
