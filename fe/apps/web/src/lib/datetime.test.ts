import { describe, expect, it } from "vitest";

import { formatSiteDate, formatSiteDateTime, parseServerInstant, siteTimeParts } from "./datetime";

/**
 * 08:48 giờ Việt Nam ngày 26/08/2026 chính là 01:48 UTC.
 *
 * <p>Đó là con số đã lộ ra lỗi: hộp thư hiện 01:13 kèm dòng "7 giờ trước" cho
 * một thông báo vừa gửi xong. Hai dạng chuỗi dưới đây phải cho ra cùng một giờ,
 * vì máy chủ cũ và máy chủ mới trả về hai dạng khác nhau và một tab đang mở có
 * thể gặp cả hai.
 */
describe("mốc thời gian máy chủ", () => {
  it("đọc chuỗi không mang múi giờ là UTC, không phải giờ máy người xem", () => {
    const { day, hour, minute, month } = siteTimeParts(parseServerInstant("2026-08-26 01:48:00.0")!);

    expect(`${hour}:${minute} ${day}/${month}`).toBe("08:48 26/08");
  });

  it("đọc ISO-8601 có chữ Z ra cùng một giờ", () => {
    const { hour, minute } = siteTimeParts(parseServerInstant("2026-08-26T01:48:00Z")!);

    expect(`${hour}:${minute}`).toBe("08:48");
  });

  it("giữ nguyên giờ Việt Nam kể cả khi máy người xem đặt múi giờ khác", () => {
    // Cùng một mốc, viết theo ba múi giờ khác nhau.
    const utc = formatSiteDateTime("2026-08-26T01:48:07Z");

    expect(formatSiteDateTime("2026-08-26T03:48:07+02:00")).toBe(utc);
    expect(formatSiteDateTime("2026-08-25T20:48:07-05:00")).toBe(utc);
  });

  it("sổ cái xu hiện đủ giờ phút giây để đối chiếu", () => {
    expect(formatSiteDateTime("2026-08-26T01:48:07Z")).toBe("08:48:07 26/08/2026");
  });

  it("ngày rơi sang hôm sau theo giờ Việt Nam chứ không theo UTC", () => {
    // 23:30 UTC ngày 25 đã là 06:30 ngày 26 ở Việt Nam.
    expect(formatSiteDate("2026-08-25T23:30:00Z")).toBe("26/08/26");
  });

  it("không có mốc nào thì hiện gạch ngang", () => {
    expect(formatSiteDate(null)).toBe("—");
    expect(formatSiteDateTime("")).toBe("—");
    expect(parseServerInstant("khong phai ngay thang")).toBeNull();
  });
});
