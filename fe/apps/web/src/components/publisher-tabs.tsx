import { BarChart3, BookOpen, Megaphone, Users } from "lucide-react";
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
}: Readonly<{ teamId: string; active: "dashboard" | "stories" | "team" }>) {
  const tabs = [
    { id: "dashboard" as const, label: "Thống kê", icon: BarChart3, to: `/teams/${teamId}/dashboard` },
    { id: "stories" as const, label: "Quản lý truyện", icon: BookOpen, to: `/teams/${teamId}/stories` },
    { id: "team" as const, label: "Quản lý nhóm", icon: Users, to: `/teams/${teamId}/manage` },
  ];

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
