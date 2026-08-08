import { MessagesSquare, PenLine, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";

import { CommunityChat } from "@/components/community-chat";
import { PublicShell } from "@/components/site-chrome";

export default function CommunityPage() {
  return (
    <PublicShell>
      <section className="catalogPage" style={{ maxWidth: "1000px", margin: "0 auto" }}>
        <header className="pageIntro compactIntro" style={{ marginBottom: "1.5rem" }}>
          <p>Cộng đồng Giới Truyện</p>
          <h1>Nơi người đọc gặp những người làm nên câu chuyện.</h1>
        </header>
        <div className="communityGrid" style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "1rem", marginBottom: "2rem" }}>
          <Link to="/teams" style={{ background: "white", padding: "1.2rem", borderRadius: "0.75rem", border: "1px solid var(--line)", textDecoration: "none" }}>
            <UsersRound aria-hidden="true" style={{ color: "#0f5fff", marginBottom: "0.5rem" }} />
            <strong style={{ display: "block", color: "#0f172a" }}>Nhóm dịch & Tác giả</strong>
            <span style={{ fontSize: "0.75rem", color: "#64748b" }}>Mở hồ sơ nhóm, xem các nhóm đang hoạt động và gửi đăng ký.</span>
          </Link>
          <Link to={"/stories" as string} style={{ background: "white", padding: "1.2rem", borderRadius: "0.75rem", border: "1px solid var(--line)", textDecoration: "none" }}>
            <PenLine aria-hidden="true" style={{ color: "#0f5fff", marginBottom: "0.5rem" }} />
            <strong style={{ display: "block", color: "#0f172a" }}>Sáng tác & Xuất bản</strong>
            <span style={{ fontSize: "0.75rem", color: "#64748b" }}>Theo dõi truyện nguyên bản, lịch đăng và các chương mới.</span>
          </Link>
          <Link to="/notifications" style={{ background: "white", padding: "1.2rem", borderRadius: "0.75rem", border: "1px solid var(--line)", textDecoration: "none" }}>
            <MessagesSquare aria-hidden="true" style={{ color: "#0f5fff", marginBottom: "0.5rem" }} />
            <strong style={{ display: "block", color: "#0f172a" }}>Thông báo & Tương tác</strong>
            <span style={{ fontSize: "0.75rem", color: "#64748b" }}>Nhận thông báo chương mới, phản hồi và hoạt động.</span>
          </Link>
        </div>

        <CommunityChat compact={false} />
      </section>
    </PublicShell>
  );
}
