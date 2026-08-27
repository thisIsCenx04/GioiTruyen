import { BadgeCheck } from "lucide-react";

/**
 * Dấu tích xanh của một nhóm đã được ban quản trị xác nhận.
 *
 * <p>Dùng thẳng icon <code>BadgeCheck</code> của lucide - đúng cái nút "Xác
 * nhận" trong bảng quản lý nhóm dùng. Không vẽ lại bằng SVG riêng: một bản vẽ
 * tay dù chép sát đến đâu cũng sẽ trôi khỏi nút kia ngay lần đổi thư viện icon
 * đầu tiên, mà độc giả nhận ra dấu xác minh bằng hình dạng của nó - hai hình
 * hơi khác nhau là hai dấu khác nhau.
 *
 * <p>Một chỗ duy nhất vẽ dấu này, để danh bạ nhóm, trang nhóm và bảng quản trị
 * không trôi thành ba hình.
 */
export function VerifiedBadge({
  size = "1em",
  title = "Nhóm đã được xác nhận",
}: Readonly<{ size?: number | string; title?: string }>) {
  return (
    <BadgeCheck
      aria-label={title}
      color="#1d4ed8"
      role="img"
      size={size}
      // Không bị chữ cạnh nó kéo giãn khi tên nhóm dài phải xuống dòng, và ngồi
      // đúng đường chân chữ thay vì nhô lên trên.
      style={{ flexShrink: 0, verticalAlign: "-0.135em" }}
    />
  );
}
