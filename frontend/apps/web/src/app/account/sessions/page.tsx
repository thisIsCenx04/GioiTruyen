import type { Metadata } from "next";

import { SessionsJourney } from "../../../components/auth-journeys";
import { AuthShell, JourneyHeading } from "../../../components/auth-shell";

export const metadata: Metadata = { title: "Phiên đăng nhập" };

export default function SessionsPage() {
  return (
    <AuthShell chapter="Mục lục · Thiết bị" eyebrow="An toàn tài khoản" lead="Mỗi lần đăng nhập là một dấu đọc riêng. Thu hồi ngay những phiên bạn không nhận ra hoặc không còn sử dụng." title="Những nơi" titleAccent="đang đọc.">
      <JourneyHeading lead="Danh sách chỉ hiển thị mã phiên rút gọn và thời gian hoạt động, không lưu thông tin nhạy cảm của thiết bị.">Phiên đăng nhập.</JourneyHeading>
      <SessionsJourney />
    </AuthShell>
  );
}
