"use client";

import {
  BookMarked,
  Edit3,
  KeyRound,
  LogOut,
  ShieldCheck,
  Sparkles,
  User as UserIcon,
  Wallet,
  X,
  Coins,
  CheckCircle2,
  AlertCircle
} from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useState, type FormEvent } from "react";
import { BrandMark } from "@gioitruyen/ui";
import { getAccessToken, isAdminUser } from "@/lib/auth";

interface UserAccount {
  id?: string;
  email?: string;
  username?: string;
  displayName?: string;
  avatarUrl?: string;
  role?: string;
  status?: string;
}

interface UserProfile {
  bio?: string;
  coverUrl?: string;
  gender?: string;
  birthday?: string;
  websiteUrl?: string;
}

interface WalletData {
  availableBalance?: number;
  totalDeposited?: number;
  totalSpent?: number;
}

export default function UserProfilePage() {
  const [user, setUser] = useState<UserAccount | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [wallet, setWallet] = useState<WalletData | null>(null);
  const [loading, setLoading] = useState(true);

  // Edit Profile Modal
  const [showEditModal, setShowEditModal] = useState(false);
  const [editDisplayName, setEditDisplayName] = useState("");
  const [editBio, setEditBio] = useState("");
  const [editAvatarUrl, setEditAvatarUrl] = useState("");
  const [editGender, setEditGender] = useState("OTHER");
  const [editWebsite, setEditWebsite] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileMsg, setProfileMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);

  // Change Password Modal
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [changingPass, setChangingPass] = useState(false);
  const [passMsg, setPassMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const fetchUserData = async () => {
    setLoading(true);
    const token = getAccessToken();
    const headers: Record<string, string> = { Accept: "application/json" };
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    try {
      // 1. Fetch Account Info
      const meRes = await fetch("/api/v1/me", { headers, credentials: "same-origin" }).catch(() => null);
      if (meRes && meRes.ok) {
        const meData = await meRes.json();
        setUser(meData);
        setEditDisplayName(meData.displayName || meData.username || "");
        setEditAvatarUrl(meData.avatarUrl || "");
      }

      // 2. Fetch Profile Info
      const profileRes = await fetch("/api/v1/me/profile", { headers, credentials: "same-origin" }).catch(() => null);
      if (profileRes && profileRes.ok) {
        const pData = await profileRes.json();
        setProfile(pData);
        setEditBio(pData.bio || "");
        setEditGender(pData.gender || "OTHER");
        setEditWebsite(pData.websiteUrl || "");
      }

      // 3. Fetch Wallet Info
      const walletRes = await fetch("/api/v1/wallet", { headers, credentials: "same-origin" }).catch(() => null);
      if (walletRes && walletRes.ok) {
        const wData = await walletRes.json();
        setWallet(wData);
      }
    } catch {
      // Fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchUserData();
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

  async function handleSaveProfile(e: FormEvent) {
    e.preventDefault();
    setSavingProfile(true);
    setProfileMsg(null);
    const token = getAccessToken();
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json"
    };
    if (token) headers["Authorization"] = `Bearer ${token}`;

    try {
      const res = await fetch("/api/v1/me/profile", {
        method: "PATCH",
        headers,
        credentials: "same-origin",
        body: JSON.stringify({
          bio: editBio,
          gender: editGender,
          websiteUrl: editWebsite,
        })
      });

      if (res.ok) {
        setProfileMsg({ type: "success", text: "Cập nhật hồ sơ thành công!" });
        setUser((prev) => prev ? { ...prev, displayName: editDisplayName, avatarUrl: editAvatarUrl } : null);
        setProfile((prev) => prev ? { ...prev, bio: editBio, gender: editGender, websiteUrl: editWebsite } : null);
        setTimeout(() => setShowEditModal(false), 1200);
      } else {
        const err = await res.json().catch(() => null);
        setProfileMsg({ type: "error", text: err?.detail || err?.message || "Không thể cập nhật hồ sơ." });
      }
    } catch {
      setProfileMsg({ type: "error", text: "Lỗi kết nối máy chủ." });
    } finally {
      setSavingProfile(false);
    }
  }

  async function handleChangePassword(e: FormEvent) {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      setPassMsg({ type: "error", text: "Mật khẩu xác nhận không khớp!" });
      return;
    }
    if (newPassword.length < 6) {
      setPassMsg({ type: "error", text: "Mật khẩu mới phải có ít nhất 6 ký tự!" });
      return;
    }
    setChangingPass(true);
    setPassMsg(null);
    const token = getAccessToken();
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json"
    };
    if (token) headers["Authorization"] = `Bearer ${token}`;

    try {
      const res = await fetch("/api/v1/auth/change-password", {
        method: "POST",
        headers,
        credentials: "same-origin",
        body: JSON.stringify({ currentPassword, newPassword })
      });

      if (res.ok) {
        setPassMsg({ type: "success", text: "Đổi mật khẩu thành công!" });
        setCurrentPassword("");
        setNewPassword("");
        setConfirmPassword("");
        setTimeout(() => setShowPasswordModal(false), 1200);
      } else {
        const err = await res.json().catch(() => null);
        setPassMsg({ type: "error", text: err?.detail || err?.message || "Mật khẩu hiện tại không đúng." });
      }
    } catch {
      setPassMsg({ type: "error", text: "Không thể kết nối máy chủ." });
    } finally {
      setChangingPass(false);
    }
  }

  return (
    <div
      className="profilePage"
      style={{
        background: "linear-gradient(180deg, #0b1528 0%, #070d18 100%)",
        color: "#f8fafc",
        minHeight: "100vh",
        padding: "2rem 1rem",
      }}
    >
      <div style={{ margin: "0 auto", maxWidth: "860px" }}>
        {/* Header */}
        <header
          className="profileHeader"
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
          <Link
            to={"/" as string}
            style={{
              color: "#94a3b8",
              fontSize: "0.9rem",
              textDecoration: "none",
              display: "inline-flex",
              alignItems: "center",
              gap: "0.4rem",
              transition: "color 0.2s"
            }}
          >
            ← Về trang chủ
          </Link>
        </header>

        {/* Main Card */}
        <main
          className="profileCard"
          style={{
            background: "rgba(30, 41, 59, 0.75)",
            border: "1px solid rgba(255, 255, 255, 0.12)",
            borderRadius: "20px",
            boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.5)",
            backdropFilter: "blur(16px)",
            padding: "2.5rem",
          }}
        >
          {/* User Banner */}
          <div
            className="profileBanner"
            style={{
              alignItems: "center",
              borderBottom: "1px solid rgba(255, 255, 255, 0.1)",
              display: "flex",
              justifyContent: "space-between",
              flexWrap: "wrap",
              gap: "1.5rem",
              paddingBottom: "2rem",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "1.25rem" }}>
              <div
                style={{
                  alignItems: "center",
                  background: user?.avatarUrl
                    ? `url(${user.avatarUrl}) center/cover`
                    : "linear-gradient(135deg, #0f6bff, #00b8a9)",
                  borderRadius: "999px",
                  boxShadow: "0 0 20px rgba(15, 107, 255, 0.4)",
                  display: "inline-flex",
                  height: "4.5rem",
                  justifyContent: "center",
                  width: "4.5rem",
                  border: "2px solid rgba(255,255,255,0.2)",
                  flexShrink: 0,
                }}
              >
                {!user?.avatarUrl && <UserIcon style={{ color: "#fff", height: "2.2rem", width: "2.2rem" }} />}
              </div>
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
                  <h1 style={{ fontSize: "1.6rem", fontWeight: 800, margin: 0, color: "#f8fafc" }}>
                    {user?.displayName || user?.username || "Thành Viên Giới Truyện"}
                  </h1>
                  {isAdminUser() && (
                    <span
                      style={{
                        background: "rgba(16, 185, 129, 0.2)",
                        border: "1px solid rgba(16, 185, 129, 0.5)",
                        color: "#34d399",
                        fontSize: "0.75rem",
                        fontWeight: 700,
                        padding: "0.15rem 0.6rem",
                        borderRadius: "999px",
                        textTransform: "uppercase"
                      }}
                    >
                      ADMIN
                    </span>
                  )}
                </div>
                <p style={{ color: "#94a3b8", fontSize: "0.9rem", margin: "0.25rem 0 0" }}>
                  {user?.email || "Chưa xác thực email"}
                </p>
                {profile?.bio && (
                  <p style={{ color: "#cbd5e1", fontSize: "0.875rem", marginTop: "0.5rem", fontStyle: "italic" }}>
                    "{profile.bio}"
                  </p>
                )}
              </div>
            </div>

            <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
              <button
                onClick={() => setShowEditModal(true)}
                type="button"
                style={{
                  alignItems: "center",
                  background: "rgba(15, 107, 255, 0.15)",
                  border: "1px solid rgba(15, 107, 255, 0.4)",
                  borderRadius: "10px",
                  color: "#38bdf8",
                  cursor: "pointer",
                  display: "inline-flex",
                  fontSize: "0.875rem",
                  fontWeight: 600,
                  gap: "0.4rem",
                  padding: "0.6rem 1.1rem",
                  transition: "all 0.2s"
                }}
              >
                <Edit3 style={{ height: "1.1rem", width: "1.1rem" }} />
                Sửa hồ sơ
              </button>
              <button
                onClick={() => setShowPasswordModal(true)}
                type="button"
                style={{
                  alignItems: "center",
                  background: "rgba(255, 255, 255, 0.06)",
                  border: "1px solid rgba(255, 255, 255, 0.15)",
                  borderRadius: "10px",
                  color: "#e2e8f0",
                  cursor: "pointer",
                  display: "inline-flex",
                  fontSize: "0.875rem",
                  fontWeight: 600,
                  gap: "0.4rem",
                  padding: "0.6rem 1.1rem",
                  transition: "all 0.2s"
                }}
              >
                <KeyRound style={{ height: "1.1rem", width: "1.1rem" }} />
                Đổi mật khẩu
              </button>
            </div>
          </div>

          {/* Wallet Balance Widget */}
          <div
            style={{
              background: "linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(217, 119, 6, 0.05))",
              border: "1px solid rgba(245, 158, 11, 0.3)",
              borderRadius: "14px",
              padding: "1.25rem 1.5rem",
              marginTop: "1.5rem",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              flexWrap: "wrap",
              gap: "1rem"
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
              <div
                style={{
                  background: "#f59e0b",
                  borderRadius: "12px",
                  padding: "0.6rem",
                  display: "flex",
                  color: "#000"
                }}
              >
                <Coins style={{ height: "1.5rem", width: "1.5rem" }} />
              </div>
              <div>
                <span style={{ fontSize: "0.8rem", color: "#fcd34d", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  Số Dư Xu Hiện Có
                </span>
                <div style={{ fontSize: "1.5rem", fontWeight: 800, color: "#fff" }}>
                  {wallet?.availableBalance ? new Intl.NumberFormat("vi-VN").format(wallet.availableBalance) : 0} <span style={{ fontSize: "1rem", color: "#f59e0b" }}>Xu</span>
                </div>
              </div>
            </div>
            <Link
              to="/wallet"
              style={{
                background: "linear-gradient(135deg, #f59e0b, #d97706)",
                borderRadius: "8px",
                color: "#000",
                fontWeight: 700,
                fontSize: "0.875rem",
                padding: "0.6rem 1.25rem",
                textDecoration: "none",
                boxShadow: "0 4px 12px rgba(245, 158, 11, 0.3)",
                display: "inline-flex",
                alignItems: "center",
                gap: "0.4rem"
              }}
            >
              <Wallet style={{ height: "1rem", width: "1rem" }} />
              Nạp Xu Ngay
            </Link>
          </div>

          {/* Quick Actions Grid */}
          <div
            className="profileActionGrid"
            style={{
              display: "grid",
              gap: "1.25rem",
              gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
              marginTop: "2rem",
            }}
          >
            {isAdminUser() && (
              <Link
                to="/dashboard/content/stories"
                style={{
                  background: "linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(59, 130, 246, 0.15))",
                  border: "1px solid rgba(16, 185, 129, 0.4)",
                  borderRadius: "14px",
                  color: "#f8fafc",
                  display: "block",
                  padding: "1.25rem",
                  textDecoration: "none",
                  transition: "transform 0.2s, boxShadow 0.2s",
                }}
              >
                <ShieldCheck style={{ color: "#10b981", height: "1.6rem", width: "1.6rem" }} />
                <strong style={{ display: "block", marginTop: "0.75rem", color: "#10b981", fontSize: "1.05rem" }}>
                  Bảng Quản Trị (Dashboard)
                </strong>
                <small style={{ color: "#94a3b8", fontSize: "0.825rem" }}>
                  Quản lý truyện, thể loại, thành viên & doanh thu
                </small>
              </Link>
            )}

            <Link
              to="/library"
              style={{
                background: "rgba(15, 23, 42, 0.6)",
                border: "1px solid rgba(255, 255, 255, 0.08)",
                borderRadius: "14px",
                color: "#f8fafc",
                display: "block",
                padding: "1.25rem",
                textDecoration: "none",
              }}
            >
              <BookMarked style={{ color: "#38bdf8", height: "1.6rem", width: "1.6rem" }} />
              <strong style={{ display: "block", marginTop: "0.75rem", fontSize: "1.05rem" }}>
                Tủ truyện cá nhân
              </strong>
              <small style={{ color: "#94a3b8", fontSize: "0.825rem" }}>
                Danh sách truyện đã lưu & theo dõi
              </small>
            </Link>

            <Link
              to="/wallet"
              style={{
                background: "rgba(15, 23, 42, 0.6)",
                border: "1px solid rgba(255, 255, 255, 0.08)",
                borderRadius: "14px",
                color: "#f8fafc",
                display: "block",
                padding: "1.25rem",
                textDecoration: "none",
              }}
            >
              <Wallet style={{ color: "#f59e0b", height: "1.6rem", width: "1.6rem" }} />
              <strong style={{ display: "block", marginTop: "0.75rem", fontSize: "1.05rem" }}>
                Ví & Giao dịch
              </strong>
              <small style={{ color: "#94a3b8", fontSize: "0.825rem" }}>
                Quản lý Xu, lịch sử nạp & mở khóa chương
              </small>
            </Link>

            <Link
              to="/teams"
              style={{
                background: "rgba(15, 23, 42, 0.6)",
                border: "1px solid rgba(255, 255, 255, 0.08)",
                borderRadius: "14px",
                color: "#f8fafc",
                display: "block",
                padding: "1.25rem",
                textDecoration: "none",
              }}
            >
              <Sparkles style={{ color: "#a855f7", height: "1.6rem", width: "1.6rem" }} />
              <strong style={{ display: "block", marginTop: "0.75rem", fontSize: "1.05rem" }}>
                Sáng tác & Nhóm dịch
              </strong>
              <small style={{ color: "#94a3b8", fontSize: "0.825rem" }}>
                Quản lý team dịch & Đăng tải truyện mới
              </small>
            </Link>
          </div>

          {/* Account Security & Logout */}
          <div
            className="profileSecurityRow"
            style={{
              borderTop: "1px solid rgba(255, 255, 255, 0.1)",
              display: "flex",
              gap: "1rem",
              justifyContent: "space-between",
              alignItems: "center",
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
              Tài khoản được mã hóa và bảo vệ 256-bit
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

      {/* Edit Profile Modal */}
      {showEditModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 9999,
            background: "rgba(0,0,0,0.75)",
            backdropFilter: "blur(8px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "1rem"
          }}
        >
          <div
            style={{
              background: "#1e293b",
              border: "1px solid rgba(255,255,255,0.15)",
              borderRadius: "16px",
              width: "100%",
              maxWidth: "520px",
              padding: "1.75rem",
              boxShadow: "0 25px 50px -12px rgba(0,0,0,0.7)",
              position: "relative"
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.25rem" }}>
              <h2 style={{ fontSize: "1.25rem", fontWeight: 700, margin: 0, color: "#fff" }}>
                Chỉnh Sửa Hồ Sơ Cá Nhân
              </h2>
              <button
                onClick={() => setShowEditModal(false)}
                type="button"
                style={{ background: "none", border: "none", color: "#94a3b8", cursor: "pointer", padding: "0.25rem" }}
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleSaveProfile} style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
              <div>
                <label style={{ display: "block", fontSize: "0.85rem", color: "#cbd5e1", marginBottom: "0.4rem" }}>
                  Tên hiển thị
                </label>
                <input
                  type="text"
                  value={editDisplayName}
                  onChange={(e) => setEditDisplayName(e.target.value)}
                  placeholder="Nhập tên hiển thị..."
                  style={{
                    width: "100%",
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    background: "#0f172a",
                    border: "1px solid #334155",
                    color: "#fff",
                    fontSize: "0.9rem"
                  }}
                />
              </div>

              <div>
                <label style={{ display: "block", fontSize: "0.85rem", color: "#cbd5e1", marginBottom: "0.4rem" }}>
                  URL Ảnh đại diện (Avatar)
                </label>
                <input
                  type="text"
                  value={editAvatarUrl}
                  onChange={(e) => setEditAvatarUrl(e.target.value)}
                  placeholder="https://example.com/avatar.jpg"
                  style={{
                    width: "100%",
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    background: "#0f172a",
                    border: "1px solid #334155",
                    color: "#fff",
                    fontSize: "0.9rem"
                  }}
                />
              </div>

              <div>
                <label style={{ display: "block", fontSize: "0.85rem", color: "#cbd5e1", marginBottom: "0.4rem" }}>
                  Tiểu sử (Bio)
                </label>
                <textarea
                  rows={3}
                  value={editBio}
                  onChange={(e) => setEditBio(e.target.value)}
                  placeholder="Giới thiệu ngắn về bản thân..."
                  style={{
                    width: "100%",
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    background: "#0f172a",
                    border: "1px solid #334155",
                    color: "#fff",
                    fontSize: "0.9rem"
                  }}
                />
              </div>

              {profileMsg && (
                <div
                  style={{
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    fontSize: "0.85rem",
                    display: "flex",
                    alignItems: "center",
                    gap: "0.5rem",
                    background: profileMsg.type === "success" ? "rgba(16, 185, 129, 0.15)" : "rgba(239, 68, 68, 0.15)",
                    color: profileMsg.type === "success" ? "#34d399" : "#f87171",
                    border: profileMsg.type === "success" ? "1px solid rgba(16, 185, 129, 0.3)" : "1px solid rgba(239, 68, 68, 0.3)"
                  }}
                >
                  {profileMsg.type === "success" ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                  {profileMsg.text}
                </div>
              )}

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.75rem", marginTop: "0.5rem" }}>
                <button
                  type="button"
                  onClick={() => setShowEditModal(false)}
                  style={{
                    padding: "0.6rem 1.2rem",
                    borderRadius: "8px",
                    background: "#334155",
                    color: "#cbd5e1",
                    border: "none",
                    cursor: "pointer",
                    fontWeight: 600,
                    fontSize: "0.875rem"
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={savingProfile}
                  style={{
                    padding: "0.6rem 1.4rem",
                    borderRadius: "8px",
                    background: "linear-gradient(135deg, #0f6bff, #00b8a9)",
                    color: "#fff",
                    border: "none",
                    cursor: "pointer",
                    fontWeight: 700,
                    fontSize: "0.875rem"
                  }}
                >
                  {savingProfile ? "Đang lưu..." : "Lưu thay đổi"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Change Password Modal */}
      {showPasswordModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 9999,
            background: "rgba(0,0,0,0.75)",
            backdropFilter: "blur(8px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "1rem"
          }}
        >
          <div
            style={{
              background: "#1e293b",
              border: "1px solid rgba(255,255,255,0.15)",
              borderRadius: "16px",
              width: "100%",
              maxWidth: "460px",
              padding: "1.75rem",
              boxShadow: "0 25px 50px -12px rgba(0,0,0,0.7)",
              position: "relative"
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.25rem" }}>
              <h2 style={{ fontSize: "1.25rem", fontWeight: 700, margin: 0, color: "#fff" }}>
                Đổi Mật Khẩu
              </h2>
              <button
                onClick={() => setShowPasswordModal(false)}
                type="button"
                style={{ background: "none", border: "none", color: "#94a3b8", cursor: "pointer", padding: "0.25rem" }}
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleChangePassword} style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
              <div>
                <label style={{ display: "block", fontSize: "0.85rem", color: "#cbd5e1", marginBottom: "0.4rem" }}>
                  Mật khẩu hiện tại
                </label>
                <input
                  type="password"
                  required
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  style={{
                    width: "100%",
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    background: "#0f172a",
                    border: "1px solid #334155",
                    color: "#fff",
                    fontSize: "0.9rem"
                  }}
                />
              </div>

              <div>
                <label style={{ display: "block", fontSize: "0.85rem", color: "#cbd5e1", marginBottom: "0.4rem" }}>
                  Mật khẩu mới
                </label>
                <input
                  type="password"
                  required
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  style={{
                    width: "100%",
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    background: "#0f172a",
                    border: "1px solid #334155",
                    color: "#fff",
                    fontSize: "0.9rem"
                  }}
                />
              </div>

              <div>
                <label style={{ display: "block", fontSize: "0.85rem", color: "#cbd5e1", marginBottom: "0.4rem" }}>
                  Xác nhận mật khẩu mới
                </label>
                <input
                  type="password"
                  required
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  style={{
                    width: "100%",
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    background: "#0f172a",
                    border: "1px solid #334155",
                    color: "#fff",
                    fontSize: "0.9rem"
                  }}
                />
              </div>

              {passMsg && (
                <div
                  style={{
                    padding: "0.6rem 0.8rem",
                    borderRadius: "8px",
                    fontSize: "0.85rem",
                    display: "flex",
                    alignItems: "center",
                    gap: "0.5rem",
                    background: passMsg.type === "success" ? "rgba(16, 185, 129, 0.15)" : "rgba(239, 68, 68, 0.15)",
                    color: passMsg.type === "success" ? "#34d399" : "#f87171",
                    border: passMsg.type === "success" ? "1px solid rgba(16, 185, 129, 0.3)" : "1px solid rgba(239, 68, 68, 0.3)"
                  }}
                >
                  {passMsg.type === "success" ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                  {passMsg.text}
                </div>
              )}

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.75rem", marginTop: "0.5rem" }}>
                <button
                  type="button"
                  onClick={() => setShowPasswordModal(false)}
                  style={{
                    padding: "0.6rem 1.2rem",
                    borderRadius: "8px",
                    background: "#334155",
                    color: "#cbd5e1",
                    border: "none",
                    cursor: "pointer",
                    fontWeight: 600,
                    fontSize: "0.875rem"
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={changingPass}
                  style={{
                    padding: "0.6rem 1.4rem",
                    borderRadius: "8px",
                    background: "linear-gradient(135deg, #0f6bff, #00b8a9)",
                    color: "#fff",
                    border: "none",
                    cursor: "pointer",
                    fontWeight: 700,
                    fontSize: "0.875rem"
                  }}
                >
                  {changingPass ? "Đang xử lý..." : "Cập nhật mật khẩu"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
