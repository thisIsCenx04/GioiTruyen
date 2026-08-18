"use client";

import { createBrowserStoryRelationClient, StoryApiError } from "@gioitruyen/api-client";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useRef, useState } from "react";
import { NgocIcon } from "./currency-icons";

import { isLoggedIn, loginHref } from "@/lib/auth";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import styles from "./story-recommend.module.css";

/** Preset gifts, so the common case is one tap rather than typing a number. */
const AMOUNTS = [10, 50, 100, 500] as const;

function errorMessage(cause: unknown) {
  if (cause instanceof StoryApiError) {
    return cause.problem.detail ?? "Chưa đề cử được. Vui lòng thử lại.";
  }
  return "Không thể kết nối máy chủ. Hãy thử lại.";
}

/**
 * Recommending a story with gems.
 */
export function StoryRecommend({ storyId }: Readonly<{ storyId: string }>) {
  const api = useMemo(
    () => createBrowserStoryRelationClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }),
    [],
  );
  const [open, setOpen] = useState(false);
  const [mine, setMine] = useState<number | null>(null);
  const [balance, setBalance] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedAmount, setSelectedAmount] = useState<number | null>(null);
  /**
   * The free-entry box, held as text rather than a number so it can be empty
   * while being typed in - binding a number makes the field impossible to
   * clear, because "" parses to 0 and React writes the 0 straight back.
   */
  const [customGems, setCustomGems] = useState("");
  const customValue = customGems === "" ? 0 : Number(customGems);

  const dialogRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!isLoggedIn()) return undefined;
    let cancelled = false;
    api.myRecommendation(storyId)
      .then((value) => { if (!cancelled) setMine(value.myGemAmount); })
      .catch(() => undefined);
    return () => { cancelled = true; };
  }, [api, storyId]);

  // Fetch wallet balance when opening modal
  useEffect(() => {
    if (!open || !isLoggedIn()) return;
    authedFetch(`${API_BASE_URL}/wallets/me`)
      .then((res) => res.ok ? res.json() : null)
      .then((data) => {
        if (data && typeof data.gemBalance === "number") {
          setBalance(data.gemBalance);
        }
      })
      .catch(() => undefined);
  }, [open]);

  // Escape closes it
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    dialogRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = previousOverflow; };
  }, [open]);

  async function handleConfirmGive() {
    if (busy || selectedAmount === null || selectedAmount <= 0) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const receipt = await api.recommend(storyId, selectedAmount);
      setBalance(receipt.gemBalance);
      setMine((current) => (current ?? 0) + receipt.gemAmount);
      setNotice(`Đã đề cử thành công ${selectedAmount} ngọc. Cảm ơn bạn!`);
      setSelectedAmount(null);
      setCustomGems("");
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  function handleCloseModal() {
    setOpen(false);
    setSelectedAmount(null);
    setCustomGems("");
    setError("");
    setNotice("");
  }

  if (!isLoggedIn()) {
    return (
      <Link className={styles.trigger} to={loginHref()}>
        <NgocIcon size={18} /> Đề cử
      </Link>
    );
  }

  return (
    <>
      <button className={styles.trigger} onClick={() => setOpen(true)} type="button">
        <NgocIcon size={18} /> Đề cử
        {mine ? <small>({mine})</small> : null}
      </button>

      {open ? (
        <div className={styles.overlay} onClick={handleCloseModal} role="presentation">
          <section
            aria-label="Đề cử truyện bằng ngọc"
            className={styles.dialog}
            ref={dialogRef}
            tabIndex={-1}
            onClick={(event) => event.stopPropagation()}
            role="dialog"
            style={{ borderRadius: "1.25rem", padding: "1.5rem" }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.4rem" }}>
              <NgocIcon size={24} />
              <h2 style={{ fontSize: "1.25rem", fontWeight: 800, margin: 0, color: "var(--text-primary)" }}>Đề cử truyện</h2>
            </div>
            <p className={styles.lede} style={{ fontSize: "0.88rem", color: "var(--text-muted)", marginBottom: "1.2rem" }}>
              Ủng hộ cho tác giả có thêm động lực.
            </p>

            {notice ? (
              <div style={{ background: "var(--success-soft)", border: "1px solid var(--success-soft)", color: "var(--success)", padding: "0.75rem 1rem", borderRadius: "0.75rem", fontSize: "0.88rem", fontWeight: 700, marginBottom: "1rem" }}>
                {notice}
              </div>
            ) : null}

            {error ? (
              <div style={{ background: "var(--danger-soft)", border: "1px solid var(--danger-soft)", color: "var(--danger)", padding: "0.75rem 1rem", borderRadius: "0.75rem", fontSize: "0.88rem", fontWeight: 700, marginBottom: "1rem" }}>
                {error}
              </div>
            ) : null}

            {selectedAmount === null ? (
              /* Step 1: Select amount */
              <>
                <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 700, color: "var(--text-secondary)", marginBottom: "0.6rem" }}>
                  Chọn số ngọc muốn đề cử:
                </label>

                <div className={styles.amounts} style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: "0.65rem", marginBottom: "1.2rem" }}>
                  {AMOUNTS.map((amount) => (
                    <button
                      key={amount}
                      onClick={() => {
                        setError("");
                        setNotice("");
                        setSelectedAmount(amount);
                      }}
                      type="button"
                      style={{
                        background: "var(--accent-soft)",
                        border: "1.5px solid var(--border-subtle)",
                        borderRadius: "0.75rem",
                        color: "var(--accent)",
                        padding: "0.75rem 0.5rem",
                        fontWeight: 800,
                        fontSize: "0.95rem",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        gap: "0.4rem",
                        cursor: "pointer",
                        transition: "all 150ms ease",
                      }}
                    >
                      {amount} <NgocIcon size={18} />
                    </button>
                  ))}
                </div>

                {/* Free entry, for a figure the four presets do not cover. */}
                <div
                  style={{
                    border: "1.5px solid var(--border-subtle)",
                    borderRadius: "0.75rem",
                    display: "grid",
                    gridTemplateColumns: "1fr auto",
                    marginBottom: "1.2rem",
                    padding: "0.6rem 0.85rem",
                  }}
                >
                  <label
                    htmlFor="recommend-custom-gems"
                    style={{
                      color: "var(--text-muted)",
                      fontSize: "0.78rem",
                      fontWeight: 700,
                      gridColumn: "1 / -1",
                      marginBottom: "0.2rem",
                    }}
                  >
                    Hoặc nhập số ngọc bất kỳ
                  </label>
                  <input
                    id="recommend-custom-gems"
                    inputMode="numeric"
                    onChange={(event) => {
                      setError("");
                      setNotice("");
                      setCustomGems(event.currentTarget.value.replace(/[^\d]/gu, ""));
                    }}
                    placeholder="Ví dụ: 250"
                    type="text"
                    value={customGems}
                    style={{
                      background: "none",
                      border: "none",
                      color: "var(--text-primary)",
                      fontSize: "1rem",
                      fontWeight: 800,
                      minWidth: 0,
                      outline: "none",
                      padding: "0.2rem 0",
                      width: "100%",
                    }}
                  />
                  <button
                    disabled={customValue < 1}
                    onClick={() => {
                      setError("");
                      setNotice("");
                      setSelectedAmount(customValue);
                    }}
                    type="button"
                    style={{
                      background: customValue < 1 ? "var(--border-strong)" : "var(--accent)",
                      border: "none",
                      borderRadius: "0.5rem",
                      color: "var(--text-on-accent, #fff)",
                      cursor: customValue < 1 ? "not-allowed" : "pointer",
                      fontSize: "0.8rem",
                      fontWeight: 800,
                      padding: "0.4rem 0.75rem",
                    }}
                  >
                    Đề cử
                  </button>
                </div>

                <div className={styles.facts} style={{ background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)", borderRadius: "0.85rem", padding: "0.85rem 1rem", marginBottom: "1.25rem" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.4rem" }}>
                    <span style={{ color: "var(--text-muted)", fontSize: "0.85rem", fontWeight: 600 }}>Bạn đã đề cử truyện này</span>
                    <span style={{ fontWeight: 800, color: "var(--text-primary)", display: "inline-flex", alignItems: "center", gap: "0.25rem" }}>
                      {mine ?? 0} <NgocIcon size={16} />
                    </span>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <span style={{ color: "var(--text-muted)", fontSize: "0.85rem", fontWeight: 600 }}>Số dư ngọc của bạn</span>
                    <span style={{ fontWeight: 800, color: "var(--accent)", display: "inline-flex", alignItems: "center", gap: "0.25rem" }}>
                      {balance === null ? "…" : <>{balance.toLocaleString("vi-VN")} <NgocIcon size={16} /></>}
                    </span>
                  </div>
                </div>

                <div className={styles.actions} style={{ display: "flex", gap: "0.6rem", justifyContent: "flex-end" }}>
                  <Link className={styles.secondary} to="/wallet" style={{ display: "inline-flex", alignItems: "center", gap: "0.3rem" }}>
                    Nạp ngọc
                  </Link>
                  <button className={styles.secondary} onClick={handleCloseModal} type="button">
                    Đóng
                  </button>
                </div>
              </>
            ) : (
              /* Step 2: Confirm donation */
              <div style={{ background: "var(--accent-soft)", border: "1.5px solid var(--border-subtle)", borderRadius: "1rem", padding: "1.1rem", marginBottom: "1.25rem" }}>
                <h3 style={{ fontSize: "1rem", fontWeight: 850, color: "var(--accent-hover)", margin: "0 0 0.5rem" }}>
                  ⚠️ Xác nhận đề cử truyện
                </h3>
                <p style={{ fontSize: "0.88rem", color: "var(--text-secondary)", margin: "0 0 1rem", lineHeight: 1.4 }}>
                  Bạn có chắc chắn muốn dùng <strong style={{ color: "var(--accent)" }}>{selectedAmount} ngọc</strong> để đề cử cho bộ truyện này không?
                </p>

                <div style={{ background: "var(--surface-card)", border: "1px solid var(--accent-soft)", borderRadius: "0.75rem", padding: "0.75rem 1rem", marginBottom: "1.1rem" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.88rem", fontWeight: 700, color: "var(--text-secondary)", marginBottom: "0.3rem" }}>
                    <span>Số ngọc đề cử:</span>
                    <span style={{ color: "var(--accent)", display: "inline-flex", alignItems: "center", gap: "0.25rem" }}>
                      {selectedAmount} <NgocIcon size={18} />
                    </span>
                  </div>
                  {balance !== null ? (
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.85rem", color: "var(--text-muted)" }}>
                      <span>Số dư ngọc sau khi tặng:</span>
                      <span style={{ fontWeight: 700, color: balance >= selectedAmount ? "var(--success)" : "var(--danger)" }}>
                        {Math.max(0, balance - selectedAmount).toLocaleString("vi-VN")} ngọc
                      </span>
                    </div>
                  ) : null}
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: "0.65rem" }}>
                  <button
                    disabled={busy || (balance !== null && balance < selectedAmount)}
                    onClick={() => void handleConfirmGive()}
                    type="button"
                    style={{
                      background: balance !== null && balance < selectedAmount
                        ? "var(--border-strong)"
                        : "linear-gradient(135deg, var(--accent) 0%, var(--accent-hover) 100%)",
                      color: "var(--text-on-accent, #fff)",
                      border: "none",
                      borderRadius: "0.75rem",
                      padding: "0.75rem 1rem",
                      fontWeight: 850,
                      fontSize: "0.92rem",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: "0.45rem",
                      cursor: busy || (balance !== null && balance < selectedAmount) ? "not-allowed" : "pointer",
                      boxShadow: balance !== null && balance < selectedAmount ? "none" : "0 4px 14px rgba(2, 132, 199, 0.35)",
                    }}
                  >
                    <NgocIcon size={18} />
                    {busy ? "Đang xử lý…" : balance !== null && balance < selectedAmount ? "Không đủ ngọc để đề cử" : `Xác nhận đề cử ${selectedAmount} ngọc`}
                  </button>

                  <button
                    disabled={busy}
                    onClick={() => setSelectedAmount(null)}
                    type="button"
                    style={{
                      background: "var(--surface-card)",
                      border: "1px solid var(--border-strong)",
                      borderRadius: "0.75rem",
                      color: "var(--text-secondary)",
                      padding: "0.6rem 1rem",
                      fontWeight: 700,
                      fontSize: "0.85rem",
                      cursor: "pointer",
                    }}
                  >
                    ← Chọn lại số ngọc khác
                  </button>
                </div>
              </div>
            )}
          </section>
        </div>
      ) : null}
    </>
  );
}
