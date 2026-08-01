"use client";

import Link from "next/link";
import type { Route } from "next";
import { usePathname } from "next/navigation";
import type { ReactNode, JSX } from "react";

type AdminTabKey = "dashboard" | "stories" | "categories" | "teams" | "users" | "cash-flow" | "moderation";

const adminTabs: Array<{
  key: AdminTabKey;
  label: string;
  icon: JSX.Element;
  href: Route;
  match: (pathname: string) => boolean;
}> = [
  {
    key: "dashboard",
    label: "Thống kê",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="9" rx="1" />
        <rect x="14" y="3" width="7" height="5" rx="1" />
        <rect x="14" y="12" width="7" height="9" rx="1" />
        <rect x="3" y="16" width="7" height="5" rx="1" />
      </svg>
    ),
    href: "/",
    match: (p) => p === "/",
  },
  {
    key: "stories",
    label: "Quản lý truyện",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" />
      </svg>
    ),
    href: "/content/stories",
    match: (p) => p.startsWith("/content/stories"),
  },
  {
    key: "categories",
    label: "Quản lý thể loại",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l5.58-5.58c.94-.94.94-2.48 0-3.42L12 2Z" />
        <circle cx="7" cy="7" r="1.2" fill="currentColor" />
      </svg>
    ),
    href: "/content/categories" as Route,
    match: (p) => p.startsWith("/content/categories"),
  },
  {
    key: "teams",
    label: "Quản lý nhóm",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    ),
    href: "/content/teams",
    match: (p) => p.startsWith("/content/teams"),
  },
  {
    key: "users",
    label: "Quản lý người dùng",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
        <circle cx="12" cy="7" r="4" />
      </svg>
    ),
    href: "/content/users",
    match: (p) => p.startsWith("/content/users"),
  },
  {
    key: "cash-flow",
    label: "Quản lý dòng tiền",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="2" y="5" width="20" height="14" rx="2" />
        <line x1="2" y1="10" x2="22" y2="10" />
      </svg>
    ),
    href: "/finance",
    match: (p) => p.startsWith("/finance"),
  },
  {
    key: "moderation",
    label: "Kiểm duyệt",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
    ),
    href: "/moderation",
    match: (p) => p.startsWith("/moderation"),
  },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (pathname === "/login") return children;
  const active = adminTabs.find((tab) => tab.match(pathname)) ?? adminTabs[0]!;

  async function handleLogout() {
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {
      // Ignore
    }
    window.location.href = "/login";
  }

  return (
    <main className="adminDashboardShell">
      <aside className="adminSidebar">
        {/* Brand */}
        <div className="adminBrand">
          <div className="adminBrandLogo">
            <img
              src="/dashboard/logo-icon.jpg"
              onError={(e) => {
                const img = e.currentTarget;
                if (img.src.includes("/dashboard/")) {
                  img.src = "/logo-icon.jpg";
                }
              }}
              alt="Giới Truyện logo"
              className="adminLogoImg"
            />
          </div>
          <div className="adminBrandText">
            <strong>Giới Truyện</strong>
            <small>Admin Panel</small>
          </div>
        </div>

        {/* Section label */}
        <p className="adminNavLabel">NAVIGATION</p>

        <nav aria-label="Admin tabs">
          {adminTabs.map((tab) => (
            <Link aria-current={tab.key === active.key ? "page" : undefined} href={tab.href} key={tab.key} prefetch>
              <span className="navIcon">{tab.icon}</span>
              <strong>{tab.label}</strong>
            </Link>
          ))}
        </nav>

        <div className="adminSidebarSpacer" />

        {/* Operator card */}
        <div className="adminOperator">
          <div className="adminOperatorAvatar">A</div>
          <div className="adminOperatorInfo">
            <strong>Quản trị Giới Truyện</strong>
            <small>{active.label}</small>
          </div>
          <button className="adminSignIn" onClick={() => void handleLogout()} type="button" title="Đăng xuất">
            ↵
          </button>
        </div>
      </aside>

      <section className="adminContentShell" aria-label={active.label}>
        {children}
      </section>
    </main>
  );
}
