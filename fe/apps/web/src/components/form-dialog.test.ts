import { describe, expect, it } from "vitest";

import { validateFields, type DialogField } from "@/components/form-dialog";

/**
 * Cửa chặn của hộp thoại hỏi.
 *
 * <p>window.prompt cũ không kiểm gì cả: bấm OK với ô trống là gửi đi một lý do
 * từ chối rỗng, một khiếu nại rỗng, một kết luận rỗng. Những dòng trống đó rơi
 * thẳng vào hồ sơ mà người ở đầu kia phải đọc để biết mình sai chỗ nào.
 */

const reason: DialogField = {
  label: "Lý do từ chối",
  name: "reason",
  required: true,
  requiredMessage: "Từ chối mà không nói lý do là căn cứ để người nhận khiếu nại thắng.",
};

const penalty: DialogField = {
  kind: "number",
  label: "Xu bồi thường",
  max: 120_000,
  min: 0,
  name: "penalty",
};

describe("validateFields", () => {
  it("chấp nhận khi mọi trường bắt buộc đã điền", () => {
    expect(validateFields([reason], { reason: "Video mới 320 view" })).toEqual({});
  });

  it("chặn trường bắt buộc bỏ trống, kèm đúng câu đã soạn", () => {
    expect(validateFields([reason], { reason: "" })).toEqual({
      reason: "Từ chối mà không nói lý do là căn cứ để người nhận khiếu nại thắng.",
    });
  });

  /* Gõ mấy dấu cách rồi bấm gửi là cách dễ nhất để lách một ô bắt buộc. */
  it("coi chuỗi chỉ có khoảng trắng là bỏ trống", () => {
    expect(validateFields([reason], { reason: "   \n  " })).toHaveProperty("reason");
  });

  it("chặn khi trường bắt buộc không có trong giá trị", () => {
    expect(validateFields([reason], {})).toHaveProperty("reason");
  });

  it("dùng nhãn để tự soạn câu lỗi khi không có requiredMessage", () => {
    const bare: DialogField = { label: "Số / link liên hệ", name: "contactHandle", required: true };
    expect(validateFields([bare], {})).toEqual({
      contactHandle: "Cần điền “Số / link liên hệ”.",
    });
  });

  it("bắt lỗi mọi trường cùng lúc, không dừng ở trường đầu tiên", () => {
    const errors = validateFields(
      [reason, { ...penalty, required: true }],
      { penalty: "", reason: "" },
    );
    expect(Object.keys(errors).sort()).toEqual(["penalty", "reason"]);
  });

  describe("ô số", () => {
    it("nhận số trong khoảng", () => {
      expect(validateFields([penalty], { penalty: "60000" })).toEqual({});
    });

    it("nhận đúng hai đầu mút", () => {
      expect(validateFields([penalty], { penalty: "0" })).toEqual({});
      expect(validateFields([penalty], { penalty: "120000" })).toEqual({});
    });

    /* Số Xu bồi thường không bắt buộc: để trống nghĩa là "không phạt", và
       không phạt là một quyết định hợp lệ, không phải một ô điền thiếu. */
    it("bỏ qua ô số không bắt buộc khi để trống", () => {
      expect(validateFields([penalty], { penalty: "" })).toEqual({});
    });

    it("từ chối chữ", () => {
      expect(validateFields([penalty], { penalty: "sáu mươi nghìn" }))
        .toEqual({ penalty: "Chỉ nhập số." });
    });

    it("từ chối số âm và nói rõ mức sàn", () => {
      expect(validateFields([penalty], { penalty: "-5000" }))
        .toEqual({ penalty: "Không được nhỏ hơn 0." });
    });

    it("từ chối số vượt trần và nói rõ mức trần", () => {
      expect(validateFields([penalty], { penalty: "500000" }))
        .toEqual({ penalty: "Không được lớn hơn 120.000." });
    });
  });
});
