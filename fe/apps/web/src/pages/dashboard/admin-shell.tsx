"use client";

import { Link, useLocation, useNavigate } from "react-router-dom";
import { useEffect, type ReactNode, type JSX } from "react";
import { isAdminUser, isLoggedIn } from "@/lib/auth";
import "./globals.css";

type AdminTabKey =
  | "dashboard"
  | "stories"
  | "categories"
  | "teams"
  | "users"
  | "cash-flow"
  | "affiliate-links"
  | "quests"
  | "payment-methods"
  | "topups"
  | "promotions"
  | "pr-disputes"
  | "withdrawals"
  | "authors";

const adminTabs: Array<{
  key: AdminTabKey;
  label: string;
  icon: JSX.Element;
  href: string;
  match: (pathname: string) => boolean;
}> = [
  {
    key: "dashboard",
    label: "Thống kê tổng quan",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="9" rx="1" />
        <rect x="14" y="3" width="7" height="5" rx="1" />
        <rect x="14" y="12" width="7" height="9" rx="1" />
        <rect x="3" y="16" width="7" height="5" rx="1" />
      </svg>
    ),
    href: "/dashboard",
    match: (p) => p === "/dashboard" || p === "/dashboard/",
  },
  {
    key: "stories",
    label: "Quản lý truyện",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" />
      </svg>
    ),
    href: "/dashboard/content/stories",
    match: (p) => p.startsWith("/dashboard/content/stories"),
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
    href: "/dashboard/content/categories",
    match: (p) => p.startsWith("/dashboard/content/categories"),
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
    href: "/dashboard/content/teams",
    match: (p) => p.startsWith("/dashboard/content/teams"),
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
    href: "/dashboard/content/users",
    match: (p) => p.startsWith("/dashboard/content/users"),
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
    href: "/dashboard/finance",
    match: (p) => p.startsWith("/dashboard/finance"),
  },
  {
    key: "affiliate-links",
    label: "Quản lý link affiliate",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
      </svg>
    ),
    href: "/dashboard/affiliate-links",
    match: (p) => p.startsWith("/dashboard/affiliate-links"),
  },
  {
    key: "quests",
    label: "Quản lý nhiệm vụ",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" />
        <circle cx="12" cy="12" r="6" />
        <circle cx="12" cy="12" r="2" />
      </svg>
    ),
    href: "/dashboard/quests",
    match: (p) => p.startsWith("/dashboard/quests"),
  },
  {
    key: "payment-methods",
    label: "Phương thức thanh toán",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="2" y="5" width="20" height="14" rx="2" />
        <line x1="2" y1="10" x2="22" y2="10" />
        <line x1="6" y1="15" x2="10" y2="15" />
      </svg>
    ),
    href: "/dashboard/payment-methods",
    match: (p) => p.startsWith("/dashboard/payment-methods"),
  },
  {
    key: "topups",
    label: "Duyệt nạp xu",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2v20" />
        <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
      </svg>
    ),
    href: "/dashboard/topups",
    match: (p) => p.startsWith("/dashboard/topups"),
  },
  {
    key: "promotions",
    label: "Duyệt bố cáo",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m3 11 18-5v12L3 14v-3z" />
        <path d="M11.6 16.8a3 3 0 1 1-5.8-1.6" />
      </svg>
    ),
    href: "/dashboard/promotions",
    match: (p) => p.startsWith("/dashboard/promotions"),
  },
  {
    key: "withdrawals",
    label: "Duyệt rút tiền",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="2" y="5" width="20" height="14" rx="2" />
        <path d="M2 10h20" />
        <path d="M6 15h4" />
      </svg>
    ),
    href: "/dashboard/withdrawals",
    match: (p) => p.startsWith("/dashboard/withdrawals"),
  },
  {
    key: "pr-disputes",
    label: "Khiếu nại PR",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        <path d="M12 8v4" />
        <path d="M12 16h.01" />
      </svg>
    ),
    href: "/dashboard/pr-disputes",
    match: (p) => p.startsWith("/dashboard/pr-disputes"),
  },
  {
    key: "authors",
    label: "Duyệt đăng ký đăng truyện",
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 20h9" />
        <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
      </svg>
    ),
    href: "/dashboard/authors",
    match: (p) => p.startsWith("/dashboard/authors"),
  },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const isAdmin = isAdminUser();
  const isLoginRoute = pathname === "/dashboard/login";

  // A signed-out visitor (or an expired session) goes straight to the login
  // page instead of staring at a permission notice they cannot act on.
  useEffect(() => {
    if (!isLoginRoute && !isAdmin && !isLoggedIn()) {
      navigate(`/dashboard/login?returnTo=${encodeURIComponent(pathname)}`, { replace: true });
    }
  }, [isAdmin, isLoginRoute, navigate, pathname]);

  if (isLoginRoute) return children;

  if (!isAdmin) {
    return (
      <main className="adminDashboardShell" style={{ justifyContent: "center", alignItems: "center", minHeight: "100vh", padding: "2rem" }}>
        <div style={{ background: "#1e293b", border: "1px solid rgba(239, 68, 68, 0.3)", borderRadius: "16px", padding: "2.5rem", maxWidth: "480px", textAlign: "center", boxShadow: "0 20px 40px rgba(0,0,0,0.5)", color: "#f8fafc" }}>
          <div style={{ background: "rgba(239, 68, 68, 0.15)", borderRadius: "50%", width: "64px", height: "64px", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 1.5rem" }}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <h2 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "0.75rem", color: "#f8fafc" }}>Yêu Cầu Quyền Quản Trị (ADMIN)</h2>
          <p style={{ color: "#94a3b8", fontSize: "0.95rem", lineHeight: 1.6, marginBottom: "2rem" }}>
            Bạn cần đăng nhập bằng tài khoản Quản trị viên (ADMIN) để truy cập giao diện Dashboard này.
          </p>
          <div style={{ display: "flex", gap: "1rem", justifyContent: "center" }}>
            <Link to="/dashboard/login" style={{ background: "#0f6bff", color: "#fff", padding: "0.75rem 1.25rem", borderRadius: "8px", fontWeight: 600, textDecoration: "none", fontSize: "0.9rem" }}>
              Đăng Nhập Admin
            </Link>
            <Link to="/" style={{ background: "rgba(255, 255, 255, 0.1)", color: "#94a3b8", padding: "0.75rem 1.25rem", borderRadius: "8px", fontWeight: 600, textDecoration: "none", fontSize: "0.9rem" }}>
              Về Trang Chủ
            </Link>
          </div>
        </div>
      </main>
    );
  }

  const active = adminTabs.find((tab) => tab.match(pathname)) ?? adminTabs[0]!;

  async function handleLogout() {
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {
      // Ignore
    }
    document.cookie = "logged_in=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "access_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "refresh_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "is_admin=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
    localStorage.removeItem("is_admin");
    window.location.href = "/dashboard/login";
  }

  return (
    <main className="adminDashboardShell">
      <aside className="adminSidebar">
        {/* Brand */}
        <div className="adminBrand">
          <div className="adminBrandLogo">
            <img
              src="/logo-icon.png"
              alt="Giới Truyện logo"
              className="adminLogoImg"
              style={{ height: "36px", objectFit: "contain" }}
            />
          </div>
          <div className="adminBrandText">
            <strong>Giới Truyện</strong>
            <small>Admin Panel</small>
          </div>

          {/* Chỉ hiện ở khổ điện thoại. Trên mobile thanh bên thu lại còn một dải
              logo + tab, còn hai lối "Về trang chủ" và "Đăng xuất" nằm ở chân thanh
              bên thì bị ẩn đi - không còn cách nào thoát khỏi dashboard ngoài việc
              gõ tay địa chỉ. Đặt ngang hàng với logo vì đó là hàng duy nhất còn
              lại ở khổ này. */}
          <div className="adminBrandActions">
            <Link aria-label="Về trang chủ web" title="Về trang chủ web" to="/">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                <polyline points="9 22 9 12 15 12 15 22" />
              </svg>
            </Link>
            <button aria-label="Đăng xuất" onClick={() => void handleLogout()} title="Đăng xuất" type="button">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" x2="9" y1="12" y2="12" />
              </svg>
            </button>
          </div>
        </div>

        {/* Section label */}
        <p className="adminNavLabel">ADMIN NAVIGATION</p>

        <nav aria-label="Admin tabs">
          {adminTabs.map((tab) => (
            <Link aria-current={tab.key === active.key ? "page" : undefined} to={tab.href} key={tab.key}>
              <span className="navIcon">{tab.icon}</span>
              <strong>{tab.label}</strong>
            </Link>
          ))}
        </nav>

        <div className="adminSidebarSpacer" />

        {/* Back to Public Web */}
        <div className="adminSidebarFooterLink">
          <Link to="/">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            <span>← Về Trang Chủ Web</span>
          </Link>
        </div>

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


