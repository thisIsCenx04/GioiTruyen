"use client";

import { BookMarked, LogOut, ShieldCheck, UserRound, WalletCards } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { isAdminUser, isLoggedIn as checkIsLoggedIn } from "@/lib/auth";

export function HeaderAuthNav() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    const updateAuthState = () => {
      setIsLoggedIn(checkIsLoggedIn());
      setIsAdmin(isAdminUser());
    };

    updateAuthState();

    window.addEventListener("focus", updateAuthState);
    window.addEventListener("storage", updateAuthState);
    window.addEventListener("auth-change", updateAuthState);
    return () => {
      window.removeEventListener("focus", updateAuthState);
      window.removeEventListener("storage", updateAuthState);
      window.removeEventListener("auth-change", updateAuthState);
    };
  }, []);

  async function handleLogout() {
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {
      // Ignore network errors on logout
    }
    // Clear auth cookies & storage
    document.cookie = "logged_in=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "access_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "refresh_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "is_admin=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
    localStorage.removeItem("is_admin");
    window.dispatchEvent(new Event("auth-change"));
    window.location.href = "/";
  }

  if (!isLoggedIn) {
    return (
      <div className="headerAuthBtns">
        <Link className="headerLoginBtn" to={"/login" as string}>
          Đăng nhập
        </Link>
        <Link className="headerRegisterBtn" to={"/auth/register" as string}>
          Đăng ký
        </Link>
      </div>
    );
  }

  return (
    <details className="profileMenu">
      <summary>
        <span className="profileAvatar">
          <UserRound aria-hidden="true" />
        </span>
        <span>
          <strong>{isAdmin ? "Quản trị viên" : "Tài khoản"}</strong>
          <small>{isAdmin ? "Admin Panel" : "Cá nhân"}</small>
        </span>
      </summary>
      <div>
        {isAdmin && (
          <Link to="/dashboard" style={{ color: "#10b981", fontWeight: 600 }}>
            <ShieldCheck aria-hidden="true" />
            Bảng quản trị (Dashboard)
          </Link>
        )}
        <Link to={"/library" as string}>
          <BookMarked aria-hidden="true" />
          Tủ truyện
        </Link>
        <Link to={"/wallet" as string}>
          <WalletCards aria-hidden="true" />
          Ví của bạn
        </Link>
        <Link to={"/account" as string}>
          <UserRound aria-hidden="true" />
          Hồ sơ
        </Link>
        <button className="headerLogoutBtn" onClick={handleLogout} type="button">
          <LogOut aria-hidden="true" />
          Đăng xuất
        </button>
      </div>
    </details>
  );
}
