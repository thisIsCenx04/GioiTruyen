
import { VerifyEmailJourney } from "../../../components/auth-journeys";
import { AuthShell, JourneyHeading } from "../../../components/auth-shell";

/* metadata removed */

export default async function VerifyPage({ searchParams }: Readonly<{ searchParams: Promise<{ token?: string }> }>) {
  const { token = "" } = await searchParams;
  return (
    <AuthShell chapter="Chương 00 · Đối chiếu" eyebrow="Xác minh người đọc" lead="Chỉ một bước ngắn để chắc rằng địa chỉ nhận thông báo chương mới thực sự thuộc về bạn." title="Đóng dấu" titleAccent="bản đọc.">
      <JourneyHeading lead="Mã xác minh chỉ dùng một lần và sẽ hết hạn theo chính sách bảo mật.">Xác minh email.</JourneyHeading>
      <VerifyEmailJourney token={token} />
    </AuthShell>
  );
}
