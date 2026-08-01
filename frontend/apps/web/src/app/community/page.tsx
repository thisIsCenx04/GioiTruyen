import { MessagesSquare, PenLine, UsersRound } from "lucide-react";
import type { Metadata, Route } from "next";
import Link from "next/link";

import { PublicShell } from "@/components/site-chrome";

export const metadata: Metadata = {
  description: "Không gian cộng đồng cho người đọc, tác giả và nhóm dịch trên Giới Truyện.",
  title: "Cộng đồng",
};

export default function CommunityPage() {
  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Cộng đồng</p>
          <h1>Nơi người đọc gặp những người làm nên câu chuyện.</h1>
        </header>
        <div className="communityGrid">
          <Link href="/teams">
            <UsersRound aria-hidden="true" />
            <strong>Nhóm dịch</strong>
            <span>Mở hồ sơ nhóm, xem các nhóm đang hoạt động và gửi đăng ký chờ duyệt.</span>
          </Link>
          <Link href={"/stories" as Route}>
            <PenLine aria-hidden="true" />
            <strong>Sáng tác</strong>
            <span>Theo dõi truyện nguyên bản, lịch đăng và các chương mới.</span>
          </Link>
          <Link href="/notifications">
            <MessagesSquare aria-hidden="true" />
            <strong>Tương tác</strong>
            <span>Nhận thông báo chương mới, phản hồi và hoạt động cộng đồng.</span>
          </Link>
        </div>
      </section>
    </PublicShell>
  );
}
