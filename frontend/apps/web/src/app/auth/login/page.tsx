import type { Metadata } from "next";

import { LoginJourney } from "../../../components/auth-journeys";
import { AuthShell, JourneyHeading } from "../../../components/auth-shell";

export const metadata: Metadata = { title: "Đăng nhập" };

export default function LoginPage() {
  return (
    <AuthShell chapter="Chương 01 · Trở lại" eyebrow="Bản đọc của bạn" lead="Đăng nhập để tiếp tục đúng chương đang đọc, quản lý tủ truyện và giữ an toàn cho những lần quay lại." title="Đọc tiếp" titleAccent="từ dấu trang.">
      <JourneyHeading lead="Thông tin đăng nhập được gửi qua kết nối bảo mật và token không được lưu trong trình duyệt.">Chào bạn trở lại.</JourneyHeading>
      <LoginJourney />
    </AuthShell>
  );
}
