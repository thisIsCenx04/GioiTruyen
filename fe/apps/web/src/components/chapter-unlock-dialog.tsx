"use client";

import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { API_BASE_URL } from "@/lib/api-base";
import { getAccessToken, isLoggedIn, refreshAccessToken } from "@/lib/auth";

/**
 * Asks the reader to spend coins on a chapter.
 *
 * The balance is fetched when the dialog opens rather than passed in, so the
 * table of contents does not have to know anything about wallets.
 */
export function ChapterUnlockDialog({
  chapterId,
  chapterTitle,
  coinPrice,
  onClose,
  returnTo,
}: Readonly<{
  chapterId: string;
  chapterTitle: string;
  coinPrice: number;
  onClose: () => void;
  returnTo: string;
}>) {
  const navigate = useNavigate();
  const authenticated = isLoggedIn();
  const [balance, setBalance] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!authenticated) return;
    let cancelled = false;
    void (async () => {
      try {
        const token = getAccessToken();
        const response = await fetch(`${API_BASE_URL}/wallets/me`, {
          credentials: "same-origin",
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (!response.ok) throw new Error("wallet");
        const wallet = (await response.json()) as { coinBalance?: number };
        if (!cancelled) setBalance(wallet.coinBalance ?? 0);
      } catch {
        if (!cancelled) setBalance(0);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [authenticated]);

  // Escape closes the dialog, as a reader expects of any modal.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  async function unlock() {
    setBusy(true);
    setError("");
    try {
      const send = (token: string | null) => {
        const headers = new Headers({ Accept: "application/json" });
        if (token) headers.set("Authorization", `Bearer ${token}`);
        return fetch(`${API_BASE_URL}/chapters/${chapterId}/unlock`, {
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
        throw new Error(problem?.detail ?? problem?.title ?? "Không mở khóa được chương này.");
      }
      navigate(returnTo as string);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không mở khóa được chương này.");
      setBusy(false);
    }
  }

  const shortfall = balance === null ? 0 : coinPrice - balance;
  const cannotAfford = authenticated && balance !== null && shortfall > 0;

  return (
    <div className="chapterUnlockOverlay" onClick={onClose} role="presentation">
      <div
        aria-labelledby="chapterUnlockTitle"
        aria-modal="true"
        className="chapterUnlockDialog"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <p className="chapterUnlockEyebrow">Chương cần mở khóa</p>
        <h2 id="chapterUnlockTitle">{chapterTitle}</h2>

        <dl className="chapterUnlockFacts">
          <div>
            <dt>Giá chương</dt>
            <dd>{coinPrice.toLocaleString("vi-VN")} xu</dd>
          </div>
          {authenticated ? (
            <div>
              <dt>Số dư của bạn</dt>
              <dd>{balance === null ? "…" : `${balance.toLocaleString("vi-VN")} xu`}</dd>
            </div>
          ) : null}
        </dl>

        {cannotAfford ? (
          <p className="chapterUnlockShortfall">
            Bạn còn thiếu <b>{shortfall.toLocaleString("vi-VN")} xu</b>.
          </p>
        ) : null}
        {error ? <p className="chapterUnlockError" role="alert">{error}</p> : null}

        <div className="chapterUnlockActions">
          {!authenticated ? (
            <Link className="chapterUnlockPrimary" to={`/login?returnTo=${encodeURIComponent(returnTo)}` as string}>
              Đăng nhập để mở khóa
            </Link>
          ) : null}
          {authenticated && !cannotAfford ? (
            <button
              className="chapterUnlockPrimary"
              disabled={busy || balance === null}
              onClick={() => void unlock()}
              type="button"
            >
              {busy ? "Đang mở khóa…" : `Dùng ${coinPrice.toLocaleString("vi-VN")} xu để mở khóa`}
            </button>
          ) : null}
          {cannotAfford ? <Link className="chapterUnlockPrimary" to="/wallet">Nạp xu</Link> : null}
          <button className="chapterUnlockSecondary" onClick={onClose} type="button">Quay lại</button>
        </div>
      </div>
    </div>
  );
}
