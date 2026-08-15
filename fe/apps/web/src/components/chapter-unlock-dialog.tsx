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
import { ArrowLeft, Coins, KeyRound, Lock } from "lucide-react";
import { XuIcon } from "./currency-icons";

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
        style={{
          background: "var(--surface-card)",
          borderRadius: "1.25rem",
          boxShadow: "0 25px 50px -12px rgba(15, 23, 42, 0.35)",
          border: "1px solid var(--border-subtle)",
          maxWidth: "25rem",
          padding: "1.6rem",
          width: "100%",
          position: "relative",
          overflow: "hidden",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.3rem" }}>
          <div
            style={{
              width: "2rem",
              height: "2rem",
              borderRadius: "50%",
              background: "#fffbe6",
              border: "1px solid #fef3c7",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#d97706",
            }}
          >
            <Lock aria-hidden="true" size={16} />
          </div>
          <span
            style={{
              color: "var(--text-muted)",
              fontSize: "0.72rem",
              fontWeight: 800,
              letterSpacing: "0.08em",
              textTransform: "uppercase",
            }}
          >
            Chương cần mở khóa
          </span>
        </div>

        <h2
          id="chapterUnlockTitle"
          style={{
            fontSize: "1.2rem",
            fontWeight: 850,
            color: "var(--text-primary)",
            margin: "0.4rem 0 1rem",
            lineHeight: 1.3,
          }}
        >
          {chapterTitle}
        </h2>

        <div
          style={{
            background: "var(--surface-sunken)",
            border: "1px solid var(--border-subtle)",
            borderRadius: "0.85rem",
            padding: "1rem 1.1rem",
            margin: "0 0 1.25rem",
            display: "flex",
            flexDirection: "column",
            gap: "0.75rem",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ color: "var(--text-secondary)", fontSize: "0.85rem", fontWeight: 600, display: "flex", alignItems: "center", gap: "0.35rem" }}>
              <XuIcon size={18} /> Giá chương
            </span>
            <span
              style={{
                background: "#fffbe6",
                color: "#b45309",
                border: "1px solid #fef3c7",
                fontSize: "0.9rem",
                fontWeight: 850,
                padding: "0.25rem 0.65rem",
                borderRadius: "6px",
                display: "inline-flex",
                alignItems: "center",
                gap: "0.3rem",
              }}
            >
              {coinPrice.toLocaleString("vi-VN")} <XuIcon size={16} />
            </span>
          </div>

          {authenticated ? (
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderTop: "1px solid #edf2f7", paddingTop: "0.6rem" }}>
              <span style={{ color: "var(--text-secondary)", fontSize: "0.85rem", fontWeight: 600 }}>Số dư của bạn</span>
              <span
                style={{
                  background: "var(--accent-soft)",
                  color: "var(--accent)",
                  border: "1px solid #dbeafe",
                  fontSize: "0.9rem",
                  fontWeight: 850,
                  padding: "0.25rem 0.65rem",
                  borderRadius: "6px",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "0.3rem",
                }}
              >
                {balance === null ? "…" : <>{balance.toLocaleString("vi-VN")} <XuIcon size={16} /></>}
              </span>
            </div>
          ) : null}
        </div>

        {cannotAfford ? (
          <div
            style={{
              background: "#fff1f2",
              border: "1px solid #fecdd3",
              borderRadius: "0.65rem",
              color: "#be123c",
              fontSize: "0.82rem",
              fontWeight: 700,
              marginBottom: "1rem",
              padding: "0.65rem 0.85rem",
            }}
          >
            ⚠️ Bạn còn thiếu <strong>{shortfall.toLocaleString("vi-VN")} xu</strong> để mở khóa chương này.
          </div>
        ) : null}

        {error ? (
          <div
            style={{
              background: "var(--danger-soft)",
              border: "1px solid #fca5a5",
              borderRadius: "0.65rem",
              color: "#991b1b",
              fontSize: "0.82rem",
              fontWeight: 700,
              marginBottom: "1rem",
              padding: "0.65rem 0.85rem",
            }}
            role="alert"
          >
            {error}
          </div>
        ) : null}

        <div style={{ display: "flex", flexDirection: "column", gap: "0.65rem", width: "100%" }}>
          {!authenticated ? (
            <Link
              className="chapterUnlockPrimary"
              to={`/login?returnTo=${encodeURIComponent(returnTo)}` as string}
              style={{
                background: "linear-gradient(135deg, #2563eb 0%, var(--accent) 100%)",
                color: "var(--surface-card)",
                border: "none",
                borderRadius: "0.75rem",
                padding: "0.75rem 1rem",
                fontSize: "0.88rem",
                fontWeight: 800,
                textAlign: "center",
                textDecoration: "none",
                boxShadow: "0 4px 14px rgba(37, 99, 235, 0.35)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "0.4rem",
                cursor: "pointer",
              }}
            >
              <KeyRound size={16} /> Đăng nhập để mở khóa
            </Link>
          ) : null}

          {authenticated && !cannotAfford ? (
            <button
              className="chapterUnlockPrimary"
              disabled={busy || balance === null}
              onClick={() => void unlock()}
              type="button"
              style={{
                background: busy || balance === null
                  ? "var(--border-strong)"
                  : "linear-gradient(135deg, #2563eb 0%, var(--accent) 100%)",
                color: "var(--surface-card)",
                border: "none",
                borderRadius: "0.75rem",
                padding: "0.75rem 1rem",
                fontSize: "0.88rem",
                fontWeight: 800,
                textAlign: "center",
                boxShadow: busy || balance === null ? "none" : "0 4px 14px rgba(37, 99, 235, 0.35)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "0.45rem",
                cursor: busy || balance === null ? "not-allowed" : "pointer",
                width: "100%",
                transition: "all 160ms ease",
              }}
            >
              <KeyRound size={17} /> {busy ? "Đang mở khóa…" : `Dùng ${coinPrice.toLocaleString("vi-VN")} xu để mở khóa`}
            </button>
          ) : null}

          {cannotAfford ? (
            <Link
              className="chapterUnlockPrimary"
              to="/wallet"
              style={{
                background: "linear-gradient(135deg, #d97706 0%, #b45309 100%)",
                color: "var(--surface-card)",
                border: "none",
                borderRadius: "0.75rem",
                padding: "0.75rem 1rem",
                fontSize: "0.88rem",
                fontWeight: 800,
                textAlign: "center",
                textDecoration: "none",
                boxShadow: "0 4px 14px rgba(217, 119, 6, 0.35)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "0.4rem",
                cursor: "pointer",
              }}
            >
              <Coins size={17} /> Nạp xu ngay
            </Link>
          ) : null}

          <button
            className="chapterUnlockSecondary"
            onClick={onClose}
            type="button"
            style={{
              background: "var(--surface-sunken)",
              color: "var(--text-secondary)",
              border: "1px solid var(--border-strong)",
              borderRadius: "0.75rem",
              padding: "0.6rem 1rem",
              fontSize: "0.84rem",
              fontWeight: 750,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "0.35rem",
              width: "100%",
              marginTop: "0.2rem",
              transition: "all 160ms ease",
            }}
          >
            <ArrowLeft size={15} /> Quay lại
          </button>
        </div>
      </div>
    </div>
  );
}
