import type { Metadata } from "next";
import Link from "next/link";

import { PublicShell } from "@/components/site-chrome";

export const metadata: Metadata = {
  description: "Câu chuyện, nguyên tắc vận hành và cam kết của Giới Truyện.",
  title: "Về Giới Truyện",
};

export default function AboutPage() {
  return (
    <PublicShell>
      <article className="informationPage">
        <header>
          <p>Về Giới Truyện</p>
          <h1>Đưa câu chuyện hay đến đúng người đọc.</h1>
          <span>Giới Truyện là nền tảng đọc và xuất bản truyện số dành cho tác giả, nhóm chuyển ngữ và cộng đồng yêu truyện Việt.</span>
        </header>
        <section>
          <h2>Điều chúng tôi theo đuổi</h2>
          <p>Chúng tôi xây dựng một thư viện dễ khám phá, dễ đọc và minh bạch về nguồn gốc nội dung. Người đọc có thể theo dõi tiến độ, lưu truyện, nhận thông báo chương mới và ủng hộ người làm nội dung bằng XU.</p>
        </section>
        <section>
          <h2>Cam kết với người đọc</h2>
          <p>Thông tin tác phẩm, tác giả, nhóm xuất bản, trạng thái hoàn thành và các chỉ số tương tác được trình bày rõ ràng. Nội dung vi phạm bản quyền hoặc tiêu chuẩn cộng đồng sẽ được tiếp nhận báo cáo và xử lý theo quy trình kiểm duyệt.</p>
        </section>
        <section>
          <h2>Cam kết với người làm nội dung</h2>
          <p>Doanh thu, lượt đọc hợp lệ và lịch sử giao dịch được ghi nhận minh bạch. Tác phẩm độc quyền nhận 90% doanh thu; tác phẩm không độc quyền nhận 70%, theo chính sách hiện hành của nền tảng.</p>
        </section>
        <footer><Link href="/stories">Khám phá thư viện</Link><Link href="/teams">Đăng ký nhóm xuất bản</Link></footer>
      </article>
    </PublicShell>
  );
}
