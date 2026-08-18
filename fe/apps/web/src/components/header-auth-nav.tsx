"use client";

import { BookMarked, LogOut, PenLine, ShieldCheck, Target, UserRound, WalletCards } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import {
  getAccessToken,
  isAdminUser,
  isLoggedIn as checkIsLoggedIn,
  loginHref,
  refreshAccessToken,
} from "@/lib/auth";

import { XuIcon, NgocIcon } from "./currency-icons";

const money = new Intl.NumberFormat("vi-VN");

type HeaderWallet = { coinBalance: number; gemBalance: number };
type UserProfile = { id?: string; email?: string; displayName?: string; username?: string; avatarUrl?: string };

/**
 * Where the "Đăng truyện" entry leads, decided server-side by GET /me/publishing:
 * an approved publisher goes to their team workspace, anyone else to the
 * application form. `entryPath` already carries the resolved target, so the menu
 * does not have to reconstruct it from the team id.
 */
type PublishingAccess = {
  canPublish: boolean;
  teamId: string | null;
  teamName: string | null;
  applicationStatus: "PENDING" | "APPROVED" | "REJECTED" | null;
  entryPath: string;
};

function publishingLabel(access: PublishingAccess): string {
  if (access.canPublish) return "Đăng truyện";
  if (access.applicationStatus === "PENDING") return "Đăng truyện (chờ duyệt)";
  if (access.applicationStatus === "REJECTED") return "Đăng truyện (cần bổ sung)";
  return "Đăng truyện";
}

export function HeaderAuthNav() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [wallet, setWallet] = useState<HeaderWallet | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [publishing, setPublishing] = useState<PublishingAccess | null>(null);

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

  useEffect(() => {
    if (!isLoggedIn) {
      setWallet(null);
      setUserProfile(null);
      setPublishing(null);
      return undefined;
    }
    let cancelled = false;
    const load = async () => {
      const fetchWithAuth = (url: string, token: string | null) =>
        fetch(url, {
          headers: token
            ? { Accept: "application/json", Authorization: `Bearer ${token}` }
            : { Accept: "application/json" },
        });

      try {
        let token = getAccessToken();
        let walletRes = await fetchWithAuth("/api/v1/wallets/me", token);
        let meRes = await fetchWithAuth("/api/v1/me", token);
        let publishingRes = await fetchWithAuth("/api/v1/me/publishing", token);

        if (walletRes.status === 401 || meRes.status === 401 || publishingRes.status === 401) {
          const renewed = await refreshAccessToken();
          if (renewed) {
            token = renewed;
            walletRes = await fetchWithAuth("/api/v1/wallets/me", token);
            meRes = await fetchWithAuth("/api/v1/me", token);
            publishingRes = await fetchWithAuth("/api/v1/me/publishing", token);
          }
        }

        if (!cancelled) {
          if (walletRes.ok) setWallet((await walletRes.json()) as HeaderWallet);
          if (meRes.ok) setUserProfile((await meRes.json()) as UserProfile);
          if (publishingRes.ok) setPublishing((await publishingRes.json()) as PublishingAccess);
        }
      } catch {
        // Fallback
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

  const displayName = userProfile?.displayName || userProfile?.username || (isAdmin ? "Quản trị viên" : "Tài khoản");
  const avatarUrl = userProfile?.avatarUrl;

  return (
    <details className="profileMenu">
      <summary>
        <span className="profileAvatar" style={{ overflow: "hidden", display: "inline-flex", alignItems: "center", justifyContent: "center", borderRadius: "50%" }}>
          {avatarUrl ? (
            <img src={avatarUrl} alt={displayName} style={{ width: "100%", height: "100%", objectFit: "cover" }}  decoding="async" loading="lazy" />
          ) : (
            <UserRound aria-hidden="true" />
          )}
        </span>
        <span>
          <strong style={{ maxWidth: "7.5rem", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", display: "block" }}>{displayName}</strong>
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
        {/* Always present, whether or not the account publishes yet: it is how a
            reader discovers they can. The destination is what changes - the team
            registration form until an application is approved, the publisher
            dashboard afterwards. Registration is also the fallback while
            /me/publishing is still in flight or unreachable, so the entry never
            disappears and never leads somewhere the account cannot open. */}
        <Link to={publishing?.entryPath ?? "/dang-ky-dang-truyen"}>
          <PenLine aria-hidden="true" />
          {publishing ? publishingLabel(publishing) : "Đăng truyện"}
        </Link>
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
          <span className="headerWalletText">
            <span>Ví của bạn</span>
            {wallet ? (
              <small className="headerWalletBalance" style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem" }}>
                {money.format(wallet.coinBalance)} <XuIcon size={14} /> · {money.format(wallet.gemBalance)} <NgocIcon size={14} />
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
