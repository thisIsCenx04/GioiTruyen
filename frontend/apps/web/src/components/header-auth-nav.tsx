"use client";

import { BookMarked, LogOut, UserRound, WalletCards } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";
import { useEffect, useState } from "react";

export function HeaderAuthNav() {
  const [mounted, setMounted] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    setMounted(true);
    const hasToken =
      document.cookie.includes("access_token=") ||
      document.cookie.includes("refresh_token=");
    setIsLoggedIn(hasToken);
  }, []);

  async function handleLogout() {
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {
      // Ignore network errors on logout
    }
    // Clear auth cookies
    document.cookie = "access_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    document.cookie = "refresh_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    window.location.href = "/";
  }

  if (!mounted) {
    return (
      <div className="headerAuthBtns">
        <Link className="headerLoginBtn" href={"/auth/login" as Route}>
          Đăng nhập
        </Link>
        <Link className="headerRegisterBtn" href={"/auth/register" as Route}>
          Đăng ký
        </Link>
      </div>
    );
  }

  if (!isLoggedIn) {
    return (
      <div className="headerAuthBtns">
        <Link className="headerLoginBtn" href={"/auth/login" as Route}>
          Đăng nhập
        </Link>
        <Link className="headerRegisterBtn" href={"/auth/register" as Route}>
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
          <strong>Tài khoản</strong>
          <small>Cá nhân</small>
        </span>
      </summary>
      <div>
        <Link href={"/library" as Route}>
          <BookMarked aria-hidden="true" />
          Tủ truyện
        </Link>
        <Link href={"/wallet" as Route}>
          <WalletCards aria-hidden="true" />
          Ví của bạn
        </Link>
        <Link href={"/account/sessions" as Route}>
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
