
import { MfaJourney } from "../../../components/auth-journeys";
import { AuthShell, JourneyHeading } from "../../../components/auth-shell";

/* metadata removed */

export default function MfaPage() {
  return (
    <AuthShell chapter="Phụ lục · Hai lớp" eyebrow="Bảo vệ tài khoản" lead="Thêm một mã ngắn từ ứng dụng xác thực để mật khẩu bị lộ vẫn chưa đủ mở bản đọc của bạn." title="Khóa thêm" titleAccent="một lớp.">
      <JourneyHeading lead="Khóa thiết lập và mã khôi phục chỉ hiển thị trong lúc kích hoạt. Hãy cất chúng ngoài thiết bị này.">Xác thực hai bước.</JourneyHeading>
      <MfaJourney />
    </AuthShell>
  );
}
