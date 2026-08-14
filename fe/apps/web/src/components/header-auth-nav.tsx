"use client";

import { BookMarked, LogOut, ShieldCheck, Target, UserRound, WalletCards } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import {
  getAccessToken,
  isAdminUser,
  isLoggedIn as checkIsLoggedIn,
  loginHref,
  refreshAccessToken,
} from "@/lib/auth";

const money = new Intl.NumberFormat("vi-VN");

type HeaderWallet = { coinBalance: number; gemBalance: number };

export function HeaderAuthNav() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [wallet, setWallet] = useState<HeaderWallet | null>(null);

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

  // Loaded once the reader is known to be signed in; a guest has no balance to
  // fetch and asking would only produce a 401 on every page.
  useEffect(() => {
    if (!isLoggedIn) {
      setWallet(null);
      return undefined;
    }
    let cancelled = false;
    const load = async () => {
      const send = (token: string | null) =>
        fetch("/api/v1/wallets/me", {
          headers: token
            ? { Accept: "application/json", Authorization: `Bearer ${token}` }
            : { Accept: "application/json" },
        });
      try {
        let response = await send(getAccessToken());
        if (response.status === 401) {
          const renewed = await refreshAccessToken();
          if (renewed) response = await send(renewed);
        }
        if (response.ok && !cancelled) setWallet((await response.json()) as HeaderWallet);
      } catch {
        // A balance is decoration in the header; failing to read it must not
        // break the navigation around it.
      }
    };
    void load();
    return () => { cancelled = true; };
  }, [isLoggedIn]);

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
        <Link className="headerLoginBtn" to={loginHref()}>
          Đăng nhập
        </Link>
        <Link className="headerRegisterBtn" to={"/register" as string}>
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
        <Link to={"/quests" as string}>
          <Target aria-hidden="true" />
          Nhiệm vụ của tôi
        </Link>
        <Link to={"/library" as string}>
          <BookMarked aria-hidden="true" />
          Tủ truyện
        </Link>
        <Link to={"/wallet" as string}>
          <WalletCards aria-hidden="true" />
          {/* Label and balance stack in their own column: side by side, a
              six-figure balance pushed "Ví của bạn" onto a second line. */}
          <span className="headerWalletText">
            <span>Ví của bạn</span>
            {wallet ? (
              <small className="headerWalletBalance">
                {money.format(wallet.coinBalance)} xu · {money.format(wallet.gemBalance)} ngọc
              </small>
            ) : null}
          </span>
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
