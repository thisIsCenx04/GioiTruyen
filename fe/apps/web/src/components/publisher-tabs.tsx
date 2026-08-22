import { BarChart3, BookOpen, Megaphone, Users, Video } from "lucide-react";
import { Link } from "react-router-dom";

/**
 * Navigation between the publisher's surfaces.
 *
 * Both pages previously offered only a single button pointing at the other one,
 * which gave no sense of being inside one workspace. These tabs use the same
 * underline treatment as the ranking and rankings-page controls.
 */
export function PublisherTabs({
  teamId,
  active,
  memberRole,
}: Readonly<{ teamId: string; active: "dashboard" | "stories" | "team" | "pr"; memberRole?: string }>) {
  const ownerAccess = !memberRole || memberRole === "OWNER" || memberRole === "ADMIN";
  const tabs = [
    { id: "dashboard" as const, label: "Thống kê", icon: BarChart3, to: `/teams/${teamId}/dashboard` },
    { id: "stories" as const, label: "Quản lý truyện", icon: BookOpen, to: `/teams/${teamId}/stories` },
    { id: "team" as const, label: "Quản lý nhóm", icon: Users, to: `/teams/${teamId}/manage` },
    // PR quests spend the team wallet and commit it to strangers, so the tab
    // follows the same owner-only rule as the rest of the money surfaces.
    { id: "pr" as const, label: "Chiến dịch PR", icon: Video, to: `/teams/${teamId}/pr` },
  ].filter((tab) => ownerAccess || tab.id === "stories");

  return (
    <nav aria-label="Bảng điều khiển đăng truyện" className="pubTabs">
      {tabs.map((tab) => (
        <Link
          aria-current={active === tab.id ? "page" : undefined}
          className={active === tab.id ? "pubTab isActive" : "pubTab"}
          key={tab.id}
          to={tab.to}
        >
          <tab.icon aria-hidden="true" size={15} />
          {tab.label}
        </Link>
      ))}
      {/*
        Booking a bố cáo slot is something a publisher decides while looking at
        their own catalogue, so the way in belongs here rather than only on the
        home page. It is a link, not a tab: /bo-cao is a separate screen and
        marking it "active" would misdescribe where the reader is.
      */}
      <Link className="pubTab pubTabPromote" to="/bo-cao">
        <Megaphone aria-hidden="true" size={15} />
        Đăng ký bố cáo
      </Link>
    </nav>
  );
}
