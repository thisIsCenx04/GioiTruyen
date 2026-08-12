"use client";

import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams } from "react-router-dom";
import { useState } from "react";

import { API_BASE_URL } from "@/lib/api-base";
import { getAccessToken, refreshAccessToken } from "@/lib/auth";

export type ChapterAccessView = Readonly<{
  chapterId: string;
  storyId: string;
  chapterTitle: string;
  priceXu: number;
  unlocked: boolean;
  authenticated: boolean;
  availableXu: number;
}>;

export function ChapterUnlock({
  access,
  returnTo = `/read/${access.chapterId}`,
  storyHref = `/stories/${access.storyId}`,
}: Readonly<{
  access: ChapterAccessView;
  returnTo?: string;
  storyHref?: string;
}>) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function unlock() {
    setBusy(true);
    setError("");
    try {
      // Unlocking spends the reader's coins, so the request must be
      // authenticated; without the token it came back 401 every time.
      const send = (token: string | null) => {
        const headers = new Headers({ Accept: "application/json" });
        if (token) headers.set("Authorization", `Bearer ${token}`);
        return fetch(`${API_BASE_URL}/chapters/${access.chapterId}/unlock`, {
          credentials: "same-origin",
          headers,
          method: "POST",
        });
      };
      let response = await send(getAccessToken());
      if (response.status === 401) {
        const renewed = await refreshAccessToken();
        if (renewed) response = await send(renewed);
      }
      if (!response.ok) {
        const problem = await response.json().catch(() => null) as
          { detail?: string; title?: string } | null;
        throw new Error(problem?.detail ?? problem?.title ?? "Không thể mở khóa chương.");
      }
      navigate(0);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể mở khóa chương.");
    } finally {
      setBusy(false);
    }
  }

  const shortfall = access.priceXu - access.availableXu;
  const cannotAfford = access.authenticated && shortfall > 0;

  return (
    <main className="chapterUnlockPage">
      <section>
        <p>Chương dành cho thành viên</p>
        <h1>{access.chapterTitle}</h1>
        <span>Mở khóa một lần và đọc lại bất cứ lúc nào trên tài khoản của bạn.</span>
        <dl>
          <div><dt>Giá chương</dt><dd>{access.priceXu.toLocaleString("vi-VN")} XU</dd></div>
          {access.authenticated && <div><dt>Số dư hiện tại</dt><dd>{access.availableXu.toLocaleString("vi-VN")} XU</dd></div>}
        </dl>
        {cannotAfford && (
          <p className="unlockShortfall">
            Bạn còn thiếu <b>{shortfall.toLocaleString("vi-VN")} xu</b> để mở khóa chương này.
          </p>
        )}
        {error && <p className="unlockError" role="alert">{error}</p>}
        <div className="unlockActions">
          {!access.authenticated && (
            <Link className="unlockPrimary" to={`/login?returnTo=${encodeURIComponent(returnTo)}` as string}>
              Đăng nhập để mở khóa
            </Link>
          )}
          {access.authenticated && !cannotAfford && (
            <button className="unlockPrimary" disabled={busy} onClick={() => void unlock()} type="button">
              {busy ? "Đang mở khóa..." : `Dùng ${access.priceXu.toLocaleString("vi-VN")} xu để mở khóa`}
            </button>
          )}
          {cannotAfford && <Link className="unlockPrimary" to="/wallet">Nạp xu</Link>}
          <Link className="unlockSecondary" to={storyHref as string}>Quay lại</Link>
        </div>
      </section>
    </main>
  );
}
