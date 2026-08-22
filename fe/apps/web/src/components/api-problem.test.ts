import { describe, expect, it } from "vitest";

import { problemOutcome } from "@/components/api-problem";

/**
 * Lỗi từ máy chủ, dịch sang câu người dùng làm được gì tiếp.
 *
 * <p>Chỗ hay hỏng nhất không phải lỗi đã biết mà là lỗi chưa biết: một mã mới
 * thêm ở backend, một trang 502 của nginx không phải JSON, một phản hồi rỗng.
 * Những trường hợp đó vẫn phải ra được một câu đọc hiểu, không phải hộp trống.
 */

function response(status: number): Response {
  return { status } as Response;
}

describe("problemOutcome", () => {
  it("dùng detail của máy chủ làm lý do", () => {
    const outcome = problemOutcome("Không nhận được nhiệm vụ", response(409), {
      code: "pr.no_slot_left",
      detail: "Nhiệm vụ đã hết suất.",
    });

    expect(outcome.kind).toBe("error");
    expect(outcome.title).toBe("Không nhận được nhiệm vụ");
    expect(outcome.details[0]).toBe("Nhiệm vụ đã hết suất.");
  });

  /* Lý do nói chuyện gì đã xảy ra; hint nói giờ làm gì. Thiếu vế thứ hai là
     người dùng đứng nhìn một câu thông báo mà không biết bấm gì tiếp. */
  it("ghép bước kế tiếp theo mã lỗi", () => {
    const outcome = problemOutcome("Không nhận được nhiệm vụ", response(409), {
      code: "pr.no_slot_left",
      detail: "Nhiệm vụ đã hết suất.",
    });

    expect(outcome.hint).toContain("Tải lại bảng");
  });

  it("mã lạ vẫn hiện được, rơi về gợi ý theo mã HTTP", () => {
    const outcome = problemOutcome("Publish thất bại", response(409), {
      code: "pr.some_code_added_next_year",
      detail: "Một lỗi chưa từng thấy.",
    });

    expect(outcome.details[0]).toBe("Một lỗi chưa từng thấy.");
    expect(outcome.hint).toContain("Tải lại trang");
  });

  /* Nginx trả 502 kèm một trang HTML: response.json() hỏng, thân lỗi rỗng. */
  it("thân lỗi rỗng vẫn ra câu theo mã HTTP", () => {
    const outcome = problemOutcome("Publish thất bại", response(503), {});

    expect(outcome.details[0]).toBe("Máy chủ đang bận. Thử lại sau ít phút.");
  });

  it("mã HTTP lạ và thân rỗng thì ít nhất cũng nói ra con số", () => {
    const outcome = problemOutcome("Publish thất bại", response(418), {});

    expect(outcome.details[0]).toBe("Máy chủ trả về lỗi HTTP 418.");
    expect(outcome.hint).toBeUndefined();
  });

  it("detail chỉ có khoảng trắng bị coi như không có", () => {
    const outcome = problemOutcome("Publish thất bại", response(500), { detail: "   " });

    expect(outcome.details[0]).toContain("Máy chủ gặp sự cố");
  });

  /* traceId là sợi dây duy nhất nối màn hình này với dòng log trên máy chủ. */
  it("đưa mã sự cố cho người dùng khi lỗi thuộc phía máy chủ", () => {
    const outcome = problemOutcome("Publish thất bại", response(500), {
      detail: "Không xử lý được.",
      traceId: "abc-123",
    });

    expect(outcome.details).toContain("Mã sự cố: abc-123");
  });

  it("không làm phiền bằng mã sự cố khi lỗi là của người dùng", () => {
    const outcome = problemOutcome("Không nhận được nhiệm vụ", response(409), {
      code: "pr.already_claimed",
      detail: "Bạn đã nhận rồi.",
      traceId: "abc-123",
    });

    expect(outcome.details).toHaveLength(1);
  });
});
