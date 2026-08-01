import type { Metadata, Route } from "next";
import Link from "next/link";

import { PublicShell } from "@/components/site-chrome";

export const metadata: Metadata = {
  description: "Quy định bản quyền, chất lượng và trách nhiệm khi đăng truyện trên Giới Truyện.",
  title: "Quy định đăng truyện",
};

export default function PublishingRulesPage() {
  return (
    <PublicShell>
      <article className="informationPage policyPage">
        <header>
          <p>Quy định xuất bản</p>
          <h1>Những điều cần biết trước khi đăng truyện.</h1>
          <span>Mọi tác phẩm đều được xét duyệt để bảo vệ bản quyền, người đọc và uy tín của nhóm xuất bản.</span>
        </header>
        <section><h2>1. Quyền đăng tải</h2><p>Bạn phải là chủ sở hữu tác phẩm hoặc có văn bản cho phép chuyển ngữ, biên tập và phát hành. Khi được yêu cầu, nhóm xuất bản cần cung cấp tài liệu chứng minh quyền sử dụng nội dung và hình ảnh.</p></section>
        <section><h2>2. Tiêu chuẩn nội dung</h2><p>Không đăng nội dung vi phạm pháp luật, xâm phạm đời tư, kích động thù ghét, mô tả tình dục liên quan đến người chưa thành niên, lừa đảo hoặc hướng dẫn hành vi nguy hiểm. Nội dung nhạy cảm phải được gắn cảnh báo phù hợp.</p></section>
        <section><h2>3. Chất lượng bản đăng</h2><p>Mỗi chương cần có tiêu đề, nội dung hoàn chỉnh, định dạng dễ đọc và không chèn quảng cáo gây gián đoạn. Tóm tắt, thể loại, tác giả và trạng thái tác phẩm phải chính xác.</p></section>
        <section><h2>4. Kiểm duyệt và xử lý vi phạm</h2><p>Truyện mới hoặc chương bị báo cáo có thể được tạm ẩn trong thời gian xem xét. Nhóm xuất bản có quyền gửi giải trình; quyết định và lịch sử xử lý được lưu lại để đối soát.</p></section>
        <section><h2>5. Doanh thu và độc quyền</h2><p>Tác phẩm độc quyền chia 90% doanh thu cho tác giả hoặc nhóm xuất bản và 10% cho nền tảng. Tác phẩm không độc quyền áp dụng tỷ lệ 70% và 30%. Giao dịch ủng hộ, mở khóa và quảng bá đều được ghi trong lịch sử XU.</p></section>
        <section><h2>6. Đăng ký nhóm xuất bản</h2><p>Hồ sơ mới luôn ở trạng thái chờ xét duyệt. Quyền đăng truyện chỉ được mở sau khi quản trị viên xác minh thông tin liên hệ, mô tả hoạt động và cam kết bản quyền.</p></section>
        <footer><Link href="/teams">Đăng ký nhóm xuất bản</Link><Link href={"/about" as Route}>Tìm hiểu về Giới Truyện</Link></footer>
      </article>
    </PublicShell>
  );
}
