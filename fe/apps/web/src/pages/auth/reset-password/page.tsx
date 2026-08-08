
import { ResetPasswordJourney } from "../../../components/auth-journeys";
import { AuthShell, JourneyHeading } from "../../../components/auth-shell";

/* metadata removed */

export default async function ResetPasswordPage({ searchParams }: Readonly<{ searchParams: Promise<{ token?: string }> }>) {
  const { token = "" } = await searchParams;
  return (
    <AuthShell chapter="Ngoại truyện · Trang mới" eyebrow="Mật khẩu mới" lead="Đổi mật khẩu cũng thu hồi toàn bộ phiên cũ, để câu chuyện chỉ tiếp tục trên những thiết bị bạn tin cậy." title="Viết lại" titleAccent="chìa khóa.">
      <JourneyHeading lead="Chọn một cụm từ dài ít nhất 12 ký tự và không dùng lại ở dịch vụ khác.">Đặt mật khẩu mới.</JourneyHeading>
      <ResetPasswordJourney token={token} />
    </AuthShell>
  );
}
