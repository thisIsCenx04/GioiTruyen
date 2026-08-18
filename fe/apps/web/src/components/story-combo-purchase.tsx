"use client";

import { useState, useEffect } from "react";
import { Sparkles, ShoppingBag, CheckCircle2, ShieldCheck, Tag } from "lucide-react";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

interface StoryComboPurchaseProps {
  storyId: string;
  storyTitle: string;
  completionStatus?: string;
  chaptersCount?: number;
  /**
   * "action" is the button in the story header; "row" is the full-width row that
   * sits above the chapter list. Both are the same purchase, so they share state
   * through the `combo-purchased` event rather than being two separate features.
   */
  variant?: "action" | "row";
}

/** Fired after a successful purchase so every mounted instance refreshes. */
const COMBO_PURCHASED_EVENT = "combo-purchased";

/** What GET /stories/{id}/combo-status reports about the price. */
type ComboPricing = {
  /** Cost of buying every chapter individually. */
  chapterTotalXu: number;
  /** What the combo charges - the sum, unless a total was configured. */
  comboPriceXu: number;
  /** True when someone set a combo total on purpose. */
  configured: boolean;
  /** 0 unless a configured total undercuts the sum. */
  discountPercent: number;
};

export function StoryComboPurchase({
  storyId,
  storyTitle,
  completionStatus = "COMPLETED",
  variant = "action",
  chaptersCount = 20,
}: StoryComboPurchaseProps) {
  const [isOpen, setIsOpen] = useState(false);
  /** Shown when the button is pressed but there is nothing to buy. */
  const [notice, setNotice] = useState("");
  const [isPurchased, setIsPurchased] = useState(false);
  const [busy, setBusy] = useState(false);
  const [successNotice, setSuccessNotice] = useState(false);

  // Pricing comes from the server, never from arithmetic here. This component
  // used to guess it - "first 3 chapters are free", 10 Xu per chapter, and a 30%
  // default discount - so the price shown had no relation to the chapter prices
  // in the database or to the amount the purchase endpoint actually charges.
  const [pricing, setPricing] = useState<ComboPricing | null>(null);

  const totalRetailXu = pricing?.chapterTotalXu ?? 0;
  const actualComboXu = pricing?.comboPriceXu ?? 0;
  const savingsXu = Math.max(0, totalRetailXu - actualComboXu);
  // Only a deliberately configured combo price is a discount. Left unset, the
  // combo simply costs the sum of its chapters and no saving is advertised.
  const hasDiscount = (pricing?.configured ?? false) && (pricing?.discountPercent ?? 0) > 0;
  const savingsPercent = pricing?.discountPercent ?? 0;

  useEffect(() => {
    let active = true;
    async function checkCombo() {
      try {
        const res = await authedFetch(`${API_BASE_URL}/stories/${storyId}/combo-status`);
        if (res.ok && active) {
          const data = (await res.json()) as ComboPricing & { purchased?: boolean };
          setPricing({
            chapterTotalXu: data.chapterTotalXu ?? 0,
            comboPriceXu: data.comboPriceXu ?? 0,
            configured: data.configured ?? false,
            discountPercent: data.discountPercent ?? 0,
          });
          if (data.purchased) setIsPurchased(true);
        }
      } catch {
        // Offline or unreachable: the button stays disabled rather than quoting
        // a price that was never confirmed by the server.
      }
    }
    void checkCombo();
    // Both render points are the same purchase, so each refreshes when either
    // one completes. Without this the header button would say "Đã Mua" while the
    // row above the chapter list still offered to sell it.
    const onPurchased = () => void checkCombo();
    window.addEventListener(COMBO_PURCHASED_EVENT, onPurchased);
    return () => {
      active = false;
      window.removeEventListener(COMBO_PURCHASED_EVENT, onPurchased);
    };
  }, [storyId]);

  /**
   * Why the combo cannot be bought right now, or "" when it can.
   *
   * The button is always on screen. Hiding it meant a reader could not tell a
   * story that has no bundle from one where the button failed to load, and
   * gave the author no hint the feature exists - so the reason is stated on
   * click instead of the control disappearing.
   */
  const unavailableReason = isPurchased
    ? ""
    : completionStatus !== "COMPLETED"
      ? "Truyện chưa hoàn thành nên chưa mở bán Combo. Combo chỉ bán khi truyện đã ra trọn bộ."
      : pricing === null
        ? "Đang tải giá combo, vui lòng thử lại sau giây lát."
        : !pricing.configured || actualComboXu <= 0
          ? "Truyện này chưa có combo. Nhóm đăng truyện chưa đặt giá Combo."
          : "";

  async function handleBuyCombo() {
    setBusy(true);
    try {
      const res = await authedFetch(`${API_BASE_URL}/stories/${storyId}/combo-purchase`, {
        method: "POST",
      });
      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        if (res.status === 409 && errData.code === "wallet.insufficient_coin") {
          alert("Số dư Xu của bạn không đủ để mua Combo. Vui lòng nạp thêm Xu!");
        } else {
          alert(errData.detail || "Không thể hoàn tất mua Combo. Vui lòng thử lại!");
        }
        setBusy(false);
        return;
      }
      setIsPurchased(true);
      setSuccessNotice(true);
      window.dispatchEvent(new Event("auth-change"));
      // Tells the other render point of this same combo to refresh.
      window.dispatchEvent(new Event(COMBO_PURCHASED_EVENT));
    } catch (error) {
      alert("Lỗi kết nối máy chủ. Vui lòng thử lại sau!");
    } finally {
      setBusy(false);
    }
  }

  // The row sits at the top of the chapter list; the action button sits in the
  // story header. Same purchase, same modal - only the trigger differs.
  const trigger = variant === "row" ? (
    <div className="comboRow">
      <div className="comboRowText">
        <strong>Mua cả truyện</strong>
        <small>
          {isPurchased
            ? "Bạn đã mở toàn bộ chương của truyện này."
            : unavailableReason
              // Without this the row advertised "Mở toàn bộ chương với 0 Xu",
              // which reads as a free offer rather than as no offer at all.
              ? "Truyện chưa mở bán combo."
              : hasDiscount
                // "tiết kiệm 30%" rather than "-30%": a bare minus sign next to
                // two prices reads as a subtraction, not as a saving.
                ? `Mở toàn bộ ${totalRetailXu} Xu chương chỉ với ${actualComboXu} Xu — tiết kiệm ${savingsPercent}%`
                : `Mở toàn bộ chương với ${actualComboXu} Xu`}
        </small>
      </div>
      {isPurchased ? (
        <span className="comboRowDone">
          <CheckCircle2 aria-hidden="true" size={15} />
          Đã mua
        </span>
      ) : (
        <button
          className="comboRowBtn"
          onClick={() => (unavailableReason ? setNotice(unavailableReason) : setIsOpen(true))}
          type="button"
        >
          <ShoppingBag aria-hidden="true" size={15} />
          {unavailableReason ? "Combo" : `${actualComboXu} Xu`}
        </button>
      )}
    </div>
  ) : isPurchased ? (
    <button
      type="button"
      className="storyAction"
      style={{
        background: "#ecfdf5",
        color: "#059669",
        borderColor: "#10b981",
        fontWeight: 800,
        cursor: "default",
      }}
    >
      <CheckCircle2 style={{ width: "1.1rem", height: "1.1rem" }} />
      Đã Mua Combo
    </button>
  ) : (
    <button
      type="button"
      onClick={() => (unavailableReason ? setNotice(unavailableReason) : setIsOpen(true))}
      className="storyAction"
      style={{
        // Always the same purple, whether or not a combo is on sale. Greying it
        // out made the button look broken rather than unavailable; the reason
        // is given on click instead.
        background: "linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%)",
        color: "var(--text-on-accent, #fff)",
        borderColor: "#4f46e5",
        fontWeight: 850,
        boxShadow: "0 4px 12px rgba(99, 102, 241, 0.35)",
        position: "relative",
      }}
    >
      <Sparkles style={{ width: "1.1rem", height: "1.1rem", color: "#fef08a" }} />
      Mua Combo
    </button>
  );

  return (
    <>
      {trigger}

      {/* Says why the combo is not on sale, in place of the control vanishing. */}
      {notice ? (
        <div
          onClick={() => setNotice("")}
          role="alertdialog"
          style={{
            position: "fixed", inset: 0, zIndex: 9999, display: "flex",
            background: "rgba(7, 23, 57, 0.55)", overflowY: "auto", padding: "1rem",
          }}
        >
          <div
            onClick={(event) => event.stopPropagation()}
            style={{
              background: "var(--surface-card)", borderRadius: "12px", margin: "auto",
              maxWidth: "22rem", padding: "1.35rem", width: "100%",
            }}
          >
            <strong style={{ display: "block", fontSize: "1rem", marginBottom: ".5rem" }}>
              Chưa mua được Combo
            </strong>
            <p style={{ color: "var(--text-secondary)", fontSize: ".88rem", lineHeight: 1.5, margin: "0 0 1.1rem" }}>
              {notice}
            </p>
            <button
              onClick={() => setNotice("")}
              style={{
                background: "var(--accent, #0f6bff)", border: 0, borderRadius: ".5rem",
                color: "#fff", cursor: "pointer", fontWeight: 800,
                padding: ".55rem 1rem", width: "100%",
              }}
              type="button"
            >
              Đã hiểu
            </button>
          </div>
        </div>
      ) : null}

      {/* Combo Purchase Modal */}
      {isOpen && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(7, 23, 57, 0.65)",
            backdropFilter: "blur(4px)",
            zIndex: 9999,
            display: "flex",
            // A landscape phone is shorter than this panel is tall. Centring it
            // with align-items and no scroll cut off the top and bottom and put
            // the buy button out of reach. The overlay scrolls, and the panel
            // centres itself with `margin: auto` below - auto margins collapse
            // to zero once the panel outgrows the screen, so nothing is clipped.
            overflowY: "auto",
            padding: "1rem",
          }}
        >
          <div
            style={{
              background: "var(--surface-card)",
              border: "3px solid #071739",
              borderRadius: "12px",
              boxShadow: "6px 6px 0px #071739",
              maxWidth: "28rem",
              width: "100%",
              margin: "auto",
              padding: "1.5rem",
              position: "relative",
            }}
          >
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              style={{
                position: "absolute",
                top: "0.85rem",
                right: "0.85rem",
                background: "var(--surface-sunken)",
                border: "1.5px solid var(--border-strong)",
                borderRadius: "50%",
                width: "2rem",
                height: "2rem",
                fontSize: "1.1rem",
                fontWeight: 800,
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              ✕
            </button>

            {successNotice ? (
              <div style={{ textAlign: "center", padding: "1rem 0" }}>
                <div
                  style={{
                    width: "4rem",
                    height: "4rem",
                    borderRadius: "50%",
                    background: "#dcfce7",
                    color: "#16a34a",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    margin: "0 auto 1rem",
                  }}
                >
                  <CheckCircle2 style={{ width: "2.5rem", height: "2.5rem" }} />
                </div>
                <h3 style={{ fontSize: "1.3rem", margin: "0 0 0.5rem", color: "var(--text-primary)", fontWeight: 850 }}>
                  Mua Combo Thành Công!
                </h3>
                <p style={{ fontSize: "0.85rem", color: "var(--text-secondary)", margin: "0 0 1.25rem", lineHeight: 1.5 }}>
                  Bạn đã sở hữu trọn bộ <strong>{storyTitle}</strong>. Tất cả các chương đã được mở khóa vĩnh viễn!
                </p>
                <button
                  type="button"
                  onClick={() => setIsOpen(false)}
                  style={{
                    background: "var(--accent)",
                    color: "var(--text-on-accent, #fff)",
                    border: "2px solid #071739",
                    borderRadius: "8px",
                    boxShadow: "3px 3px 0px #071739",
                    padding: "0.65rem 1.5rem",
                    fontWeight: 850,
                    cursor: "pointer",
                    fontSize: "0.9rem",
                  }}
                >
                  Bắt đầu đọc ngay
                </button>
              </div>
            ) : (
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.5rem" }}>
                  <span
                    style={{
                      background: "#fef3c7",
                      color: "#d97706",
                      fontSize: "0.7rem",
                      fontWeight: 800,
                      padding: "0.2rem 0.5rem",
                      borderRadius: "4px",
                      border: "1px solid #fcd34d",
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "0.25rem",
                    }}
                  >
                    <Tag style={{ width: "0.75rem", height: "0.75rem" }} />
                    ƯU ĐÃI TRỌN BỘ FULL
                  </span>
                </div>

                <h3 style={{ fontSize: "1.2rem", margin: "0 0 0.5rem", color: "var(--text-primary)", fontWeight: 850 }}>
                  Mua Combo Truyện
                </h3>
                <p style={{ fontSize: "0.82rem", color: "var(--text-muted)", margin: "0 0 1rem" }}>
                  Tác phẩm: <strong>{storyTitle}</strong>
                </p>

                {/* Price Breakdown Box */}
                <div
                  style={{
                    background: "var(--surface-sunken)",
                    border: "1.5px solid var(--border-subtle)",
                    borderRadius: "8px",
                    padding: "1rem",
                    margin: "0 0 1.25rem",
                    display: "flex",
                    flexDirection: "column",
                    gap: "0.6rem",
                  }}
                >
                  {/* The struck-through list price only means something when the
                      combo is cheaper than it. Without a configured total the two
                      are equal, so showing it would imply a saving of zero. */}
                  {hasDiscount ? (
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.82rem", color: "var(--text-muted)" }}>
                      <span>Tổng mua lẻ từng chương:</span>
                      <span style={{ textDecoration: "line-through" }}>{totalRetailXu} Xu</span>
                    </div>
                  ) : (
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.82rem", color: "var(--text-muted)" }}>
                      <span>Tổng giá các chương:</span>
                      <span>{totalRetailXu} Xu</span>
                    </div>
                  )}

                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                    <span style={{ fontSize: "0.9rem", fontWeight: 800, color: "var(--text-primary)" }}>Giá Mua Combo:</span>
                    <strong style={{ fontSize: "1.4rem", fontWeight: 900, color: "#7c3aed" }}>
                      {actualComboXu} Xu
                    </strong>
                  </div>

                  {hasDiscount ? (
                    <div
                      style={{
                        background: "#ecfdf5",
                        color: "#047857",
                        fontSize: "0.78rem",
                        fontWeight: 800,
                        padding: "0.4rem 0.65rem",
                        borderRadius: "6px",
                        border: "1px solid #a7f3d0",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                      }}
                    >
                      <span>Tiết kiệm so với mua lẻ:</span>
                      <strong>{savingsPercent}% ({savingsXu} Xu)</strong>
                    </div>
                  ) : (
                    <p style={{ color: "var(--text-muted)", fontSize: "0.78rem", margin: 0 }}>
                      Mua combo để mở toàn bộ chương trong một lần, giá bằng tổng các chương.
                    </p>
                  )}
                </div>

                <div
                  style={{
                    fontSize: "0.75rem",
                    color: "var(--text-muted)",
                    marginBottom: "1.25rem",
                    display: "flex",
                    alignItems: "center",
                    gap: "0.4rem",
                  }}
                >
                  <ShieldCheck style={{ width: "1rem", height: "1rem", color: "#10b981" }} />
                  <span>Quyền lợi: Mở khóa toàn bộ chương trả phí vĩnh viễn, đọc không giới hạn.</span>
                </div>

                <div style={{ display: "flex", gap: "0.75rem" }}>
                  <button
                    type="button"
                    onClick={() => setIsOpen(false)}
                    style={{
                      flex: 1,
                      background: "var(--surface-sunken)",
                      color: "var(--text-secondary)",
                      border: "1.5px solid var(--border-strong)",
                      borderRadius: "8px",
                      padding: "0.65rem",
                      fontWeight: 750,
                      cursor: "pointer",
                      fontSize: "0.85rem",
                    }}
                  >
                    Hủy
                  </button>

                  <button
                    type="button"
                    disabled={busy}
                    onClick={handleBuyCombo}
                    style={{
                      flex: 2,
                      background: "linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%)",
                      color: "var(--text-on-accent, #fff)",
                      border: "2px solid #071739",
                      borderRadius: "8px",
                      boxShadow: "3px 3px 0px #071739",
                      padding: "0.65rem",
                      fontWeight: 850,
                      cursor: busy ? "wait" : "pointer",
                      fontSize: "0.88rem",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: "0.4rem",
                    }}
                  >
                    <ShoppingBag style={{ width: "1rem", height: "1rem" }} />
                    {busy ? "Đang xử lý..." : `Xác nhận mua (${actualComboXu} Xu)`}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
