import type { Metadata } from "next";

import { RegisterJourney } from "../../../components/auth-journeys";
import { AuthShell, JourneyHeading } from "../../../components/auth-shell";

export const metadata: Metadata = { title: "Tạo tài khoản" };

export default function RegisterPage() {
  return (
    <AuthShell chapter="Chương 00 · Lời mở" eyebrow="Bắt đầu bản đọc" lead="Một tài khoản gọn nhẹ để lưu tiến độ, theo dõi tác giả và nhận chương mới mà không đánh mất nhịp truyện." title="Mở một" titleAccent="thế giới riêng.">
      <JourneyHeading lead="Tạo tài khoản để lưu tiến độ đọc và sử dụng ngay sau khi đăng ký.">Tạo tài khoản.</JourneyHeading>
      <RegisterJourney />
    </AuthShell>
  );
}
