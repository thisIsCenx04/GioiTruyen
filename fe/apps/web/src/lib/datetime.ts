/**
 * Một chỗ duy nhất đọc và hiển thị mốc thời gian máy chủ gửi xuống.
 *
 * <p>Trước đây mỗi màn hình tự gọi `new Date(value)` rồi `getHours()`, và cả
 * hai bước đều sai theo cùng một kiểu:
 *
 * <ul>
 *   <li>Máy chủ từng trả về `"2026-08-26 01:13:44.0"` - chuỗi không mang múi
 *       giờ. Trình duyệt đọc nó là giờ địa phương, nên một thông báo vừa gửi
 *       xong ở Việt Nam hiện thành 01:13 kèm dòng "7 giờ trước".</li>
 *   <li>`getHours()` trả về giờ của máy người xem. Một quản trị viên đang ở
 *       múi giờ khác sẽ đọc sổ cái lệch hẳn so với đồng nghiệp, mà không có gì
 *       trên màn hình nói rằng hai người đang xem hai mốc khác nhau.</li>
 * </ul>
 *
 * <p>Luật ở đây: mốc thời gian không mang múi giờ luôn là UTC, và mọi thứ hiện
 * ra màn hình đều theo giờ Việt Nam.
 */

/** Múi giờ mọi mốc thời gian hiển thị theo, bất kể máy người xem đặt giờ gì. */
export const SITE_TIME_ZONE = "Asia/Ho_Chi_Minh";

/**
 * Đọc mốc thời gian máy chủ gửi xuống thành một {@link Date}.
 *
 * <p>Chấp nhận cả ISO-8601 có múi giờ (dạng máy chủ đang trả về) lẫn dạng
 * `"yyyy-MM-dd HH:mm:ss"` không mang múi giờ của các bản cũ; dạng thứ hai được
 * hiểu là UTC, vì đó là điều nó vốn luôn là.
 */
export function parseServerInstant(value: string | null | undefined): Date | null {
  if (!value) return null;
  const raw = String(value).trim();
  if (!raw) return null;
  const hasZone = /(?:z|[+-]\d{2}:?\d{2})$/iu.test(raw);
  const parsed = new Date(hasZone ? raw : `${raw.replace(" ", "T")}Z`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

type Parts = {
  day: string;
  hour: string;
  minute: string;
  month: string;
  second: string;
  year: string;
};

/** Từng phần ngày giờ của một mốc, đọc theo giờ Việt Nam. */
export function siteTimeParts(date: Date): Parts {
  const parts = new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    hour: "2-digit",
    hour12: false,
    minute: "2-digit",
    month: "2-digit",
    second: "2-digit",
    timeZone: SITE_TIME_ZONE,
    year: "numeric",
  }).formatToParts(date);
  const read = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return {
    day: read("day"),
    // Một số phiên bản ICU trả nửa đêm là "24" thay vì "00".
    hour: read("hour") === "24" ? "00" : read("hour"),
    minute: read("minute"),
    month: read("month"),
    second: read("second"),
    year: read("year"),
  };
}

/** `dd/mm/yy` theo giờ Việt Nam, hoặc `—` khi không có mốc nào. */
export function formatSiteDate(value: string | null | undefined): string {
  const date = parseServerInstant(value);
  if (!date) return value ? String(value) : "—";
  const { day, month, year } = siteTimeParts(date);
  return `${day}/${month}/${year.slice(-2)}`;
}

/**
 * `HH:mm:ss dd/mm/yyyy` theo giờ Việt Nam.
 *
 * <p>Có cả giây: sổ cái xu được đối chiếu với sao kê ngân hàng và với log máy
 * chủ, và hai bút toán của cùng một lần nạp có thể cách nhau vài giây. Chỉ hiện
 * tới phút thì hai dòng đó trông y hệt nhau và không đối chiếu được.
 */
export function formatSiteDateTime(value: string | null | undefined): string {
  const date = parseServerInstant(value);
  if (!date) return value ? String(value) : "—";
  const { day, hour, minute, month, second, year } = siteTimeParts(date);
  return `${hour}:${minute}:${second} ${day}/${month}/${year}`;
}
