import type { Metadata } from "next";

import { ForgotPasswordJourney } from "../../../components/auth-journeys";
import { AuthShell, JourneyHeading } from "../../../components/auth-shell";

export const metadata: Metadata = { title: "Quên mật khẩu" };

export default function ForgotPasswordPage() {
  return (
    <AuthShell chapter="Ngoại truyện · Tìm lại" eyebrow="Khôi phục bản đọc" lead="Chúng tôi sẽ gửi một liên kết ngắn hạn nếu địa chỉ này gắn với tài khoản hợp lệ." title="Tìm lại" titleAccent="lối vào.">
      <JourneyHeading lead="Kết quả luôn giống nhau để không ai có thể dò tìm tài khoản từ biểu mẫu này.">Quên mật khẩu?</JourneyHeading>
      <ForgotPasswordJourney />
    </AuthShell>
  );
}
