import Link from "next/link";
import type { Route } from "next";
import type { ReactNode } from "react";

type AdminTabKey = "dashboard" | "stories" | "teams" | "users" | "cash-flow" | "moderation";

const adminTabs: Array<{ key: AdminTabKey; label: string; href: Route; icon: string; description: string }> = [
  { key: "dashboard", label: "Thống kê", href: "/", icon: "TK", description: "Doanh thu, traffic, reader" },
  { key: "stories", label: "Truyện", href: "/content/stories", icon: "TR", description: "CRUD truyện và chương" },
  { key: "teams", label: "Team", href: "/content/teams", icon: "TE", description: "Duyệt hồ sơ team" },
  { key: "users", label: "User", href: "/content/users", icon: "US", description: "Tài khoản và quyền" },
  { key: "cash-flow", label: "Cash flow", href: "/finance", icon: "CF", description: "Nạp xu, donate, rút tiền" },
  { key: "moderation", label: "Kiểm duyệt", href: "/moderation", icon: "KD", description: "Review nội dung" },
];

export function AdminShell({ activeTab, children }: { activeTab: AdminTabKey; children: ReactNode }) {
  const active = adminTabs.find((tab) => tab.key === activeTab) ?? {
    description: "Admin workspace",
    href: "/" as Route,
    icon: "AD",
    key: "dashboard" as const,
    label: "Thống kê",
  };

  return (
    <main className="adminDashboardShell">
      <aside className="adminSidebar">
        <div className="adminBrand">
          <span>G</span>
          <div>
            <strong>Giới Truyện Admin</strong>
            <small>Control Center</small>
          </div>
        </div>
        <nav aria-label="Admin tabs">
          {adminTabs.map((tab) => (
            <Link aria-current={tab.key === activeTab ? "page" : undefined} href={tab.href} key={tab.key}>
              <span>{tab.icon}</span>
              <div>
                <strong>{tab.label}</strong>
                <small>{tab.description}</small>
              </div>
            </Link>
          ))}
        </nav>
        <div className="adminOperator">
          <span>AD</span>
          <div>
            <strong>Admin workspace</strong>
            <small>{active.label}</small>
          </div>
        </div>
        <small>Vào admin dashboard tại http://127.0.0.1:3001 sau khi chạy pnpm dev:admin.</small>
      </aside>
      <section className="adminContentShell" aria-label={active.label}>
        {children}
      </section>
    </main>
  );
}
