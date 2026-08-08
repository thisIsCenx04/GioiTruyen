"use client";

import {
  BookMarked,
  KeyRound,
  LogOut,
  ShieldCheck,
  Sparkles,
  User,
  Wallet,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { BrandMark } from "@gioitruyen/ui";
import { isAdminUser } from "@/lib/auth";

export default function UserProfilePage() {
  const [userEmail, setUserEmail] = useState<string | null>(null);

  useEffect(() => {
    // Attempt to read session email if available
    async function loadSession() {
      try {
        const res = await fetch("/api/auth/sessions", { credentials: "same-origin" });
        if (!res.ok) return;
        // Optionally populate session details
      } catch {
        // Fallback
      }
    }
    void loadSession();
  }, []);

  async function handleLogout() {
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {
      // Ignore network errors
    }
    document.cookie = "logged_in=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "access_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "refresh_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    window.location.href = "/";
  }

  return (
    <div
      style={{
        background: "linear-gradient(180deg, #0b1528 0%, #070d18 100%)",
        color: "#f8fafc",
        minHeight: "100vh",
        padding: "2rem 1rem",
      }}
    >
      <div style={{ margin: "0 auto", maxWidth: "800px" }}>
        <header
          style={{
            alignItems: "center",
            display: "flex",
            justifyContent: "space-between",
            marginBottom: "2rem",
          }}
        >
          <Link to={"/" as string}>
            <BrandMark inverse />
          </Link>
          <Link to={"/" as string}
            style={{
              color: "#94a3b8",
              fontSize: "0.9rem",
              textDecoration: "none",
            }}
          >
            ← Về trang chủ
          </Link>
        </header>

        <main
          style={{
            background: "rgba(30, 41, 59, 0.7)",
            border: "1px solid rgba(255, 255, 255, 0.1)",
            borderRadius: "16px",
            boxShadow: "0 20px 40px rgba(0, 0, 0, 0.4)",
            backdropFilter: "blur(12px)",
            padding: "2rem",
          }}
        >
          {/* User Banner */}
          <div
            style={{
              alignItems: "center",
              borderBottom: "1px solid rgba(255, 255, 255, 0.1)",
              display: "flex",
              gap: "1.5rem",
              paddingBottom: "1.5rem",
            }}
          >
            <div
              style={{
                alignItems: "center",
                background: "linear-gradient(135deg, #0f6bff, #00b8a9)",
                borderRadius: "999px",
                display: "inline-flex",
                height: "4rem",
                justifyContent: "center",
                width: "4rem",
              }}
            >
              <User style={{ color: "#fff", height: "2rem", width: "2rem" }} />
            </div>
            <div>
              <h1 style={{ fontSize: "1.5rem", fontWeight: 800, margin: 0 }}>
                Hồ Sơ Thành Viên
              </h1>
              <p
                style={{
                  color: "#94a3b8",
                  fontSize: "0.9rem",
                  margin: "0.25rem 0 0",
                }}
              >
                Tài khoản hoạt động chính thức trên Giới Truyện
              </p>
            </div>
          </div>

          {/* Quick Actions Grid */}
          <div
            style={{
              display: "grid",
              gap: "1rem",
              gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
              marginTop: "2rem",
            }}
          >
            {isAdminUser() && (
              <Link to="/dashboard/content/stories"
                style={{
                  background: "linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(59, 130, 246, 0.15))",
                  border: "1px solid rgba(16, 185, 129, 0.4)",
                  borderRadius: "12px",
                  color: "#f8fafc",
                  display: "block",
                  padding: "1.25rem",
                  textDecoration: "none",
                }}
              >
                <ShieldCheck
                  style={{ color: "#10b981", height: "1.5rem", width: "1.5rem" }}
                />
                <strong style={{ display: "block", marginTop: "0.75rem", color: "#10b981" }}>
                  Bảng Quản Trị (Dashboard)
                </strong>
                <small style={{ color: "#94a3b8", fontSize: "0.8rem" }}>
                  Truy cập giao diện Admin để quản trị hệ thống
                </small>
              </Link>
            )}

            <Link to={"/library" as string}
              style={{
                background: "rgba(15, 23, 42, 0.6)",
                border: "1px solid rgba(255, 255, 255, 0.08)",
                borderRadius: "12px",
                color: "#f8fafc",
                display: "block",
                padding: "1.25rem",
                textDecoration: "none",
                transition: "transform 0.2s",
              }}
            >
              <BookMarked
                style={{ color: "#38bdf8", height: "1.5rem", width: "1.5rem" }}
              />
              <strong style={{ display: "block", marginTop: "0.75rem" }}>
                Tủ truyện cá nhân
              </strong>
              <small style={{ color: "#94a3b8", fontSize: "0.8rem" }}>
                Xem danh sách truyện đã lưu & theo dõi
              </small>
            </Link>

            <Link to={"/wallet" as string}
              style={{
                background: "rgba(15, 23, 42, 0.6)",
                border: "1px solid rgba(255, 255, 255, 0.08)",
                borderRadius: "12px",
                color: "#f8fafc",
                display: "block",
                padding: "1.25rem",
                textDecoration: "none",
              }}
            >
              <Wallet
                style={{ color: "#f59e0b", height: "1.5rem", width: "1.5rem" }}
              />
              <strong style={{ display: "block", marginTop: "0.75rem" }}>
                Ví của bạn
              </strong>
              <small style={{ color: "#94a3b8", fontSize: "0.8rem" }}>
                Quản lý số dư Xu & Lịch sử giao dịch
              </small>
            </Link>

            <Link to={"/publishing" as string}
              style={{
                background: "rgba(15, 23, 42, 0.6)",
                border: "1px solid rgba(255, 255, 255, 0.08)",
                borderRadius: "12px",
                color: "#f8fafc",
                display: "block",
                padding: "1.25rem",
                textDecoration: "none",
              }}
            >
              <Sparkles
                style={{ color: "#a855f7", height: "1.5rem", width: "1.5rem" }}
              />
              <strong style={{ display: "block", marginTop: "0.75rem" }}>
                Sáng tác & Xuất bản
              </strong>
              <small style={{ color: "#94a3b8", fontSize: "0.8rem" }}>
                Giao diện dành cho Tác giả & Nhóm dịch
              </small>
            </Link>
          </div>

          {/* Account Security & Logout */}
          <div
            style={{
              borderTop: "1px solid rgba(255, 255, 255, 0.1)",
              display: "flex",
              gap: "1rem",
              justifyContent: "space-between",
              marginTop: "2rem",
              paddingTop: "1.5rem",
            }}
          >
            <span
              style={{
                alignItems: "center",
                color: "#10b981",
                display: "inline-flex",
                fontSize: "0.85rem",
                gap: "0.4rem",
              }}
            >
              <ShieldCheck style={{ height: "1.2rem", width: "1.2rem" }} />
              Tài khoản đã được bảo vệ
            </span>
            <button
              onClick={handleLogout}
              style={{
                alignItems: "center",
                background: "rgba(239, 68, 68, 0.15)",
                border: "1px solid rgba(239, 68, 68, 0.4)",
                borderRadius: "8px",
                color: "#ef4444",
                cursor: "pointer",
                display: "inline-flex",
                fontSize: "0.9rem",
                fontWeight: 600,
                gap: "0.4rem",
                padding: "0.6rem 1.2rem",
              }}
              type="button"
            >
              <LogOut style={{ height: "1.1rem", width: "1.1rem" }} />
              Đăng xuất
            </button>
          </div>
        </main>
      </div>
    </div>
  );
}
