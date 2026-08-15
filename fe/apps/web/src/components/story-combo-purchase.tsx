"use client";

import { useState, useEffect } from "react";
import { Sparkles, ShoppingBag, CheckCircle2, ShieldCheck, Tag } from "lucide-react";

interface StoryComboPurchaseProps {
  storyId: string;
  storyTitle: string;
  completionStatus?: string;
  comboPriceXu?: number;
  chaptersCount?: number;
}

export function StoryComboPurchase({
  storyId,
  storyTitle,
  completionStatus = "COMPLETED",
  comboPriceXu,
  chaptersCount = 20,
}: StoryComboPurchaseProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [isPurchased, setIsPurchased] = useState(false);
  const [userXu, setUserXu] = useState(500); // Default user balance mock/state
  const [busy, setBusy] = useState(false);
  const [successNotice, setSuccessNotice] = useState(false);

  // Auto calculate retail total & discount
  const estPaidChapters = Math.max(1, chaptersCount - 3); // First 3 free
  const retailUnitPrice = 10;
  const totalRetailXu = estPaidChapters * retailUnitPrice;
  
  // Combo price: custom or 30% discount default (must be <= totalRetailXu)
  const actualComboXu = comboPriceXu && comboPriceXu > 0 && comboPriceXu <= totalRetailXu
    ? comboPriceXu
    : Math.round(totalRetailXu * 0.7);

  const savingsXu = Math.max(0, totalRetailXu - actualComboXu);
  const savingsPercent = totalRetailXu > 0 ? Math.round((savingsXu / totalRetailXu) * 100) : 0;

  useEffect(() => {
    // Check if already purchased in local storage
    const purchasedList = JSON.parse(localStorage.getItem("purchased_combos") || "[]");
    if (purchasedList.includes(storyId)) {
      setIsPurchased(true);
    }
  }, [storyId]);

  if (completionStatus !== "COMPLETED") {
    return null; // Combo purchase only applies to COMPLETED stories
  }

  function handleBuyCombo() {
    setBusy(true);
    setTimeout(() => {
      if (userXu < actualComboXu) {
        alert(`Số dư Xu không đủ (${userXu} Xu). Bạn cần thêm ${actualComboXu - userXu} Xu để mua combo full truyện này.`);
        setBusy(false);
        return;
      }
      setUserXu((prev) => prev - actualComboXu);
      setIsPurchased(true);
      setSuccessNotice(true);
      setBusy(false);

      const purchasedList = JSON.parse(localStorage.getItem("purchased_combos") || "[]");
      if (!purchasedList.includes(storyId)) {
        purchasedList.push(storyId);
        localStorage.setItem("purchased_combos", JSON.stringify(purchasedList));
      }
    }, 600);
  }

  return (
    <>
      {isPurchased ? (
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
          Đã Mua Combo Full
        </button>
      ) : (
        <button
          type="button"
          onClick={() => setIsOpen(true)}
          className="storyAction"
          style={{
            background: "linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%)",
            color: "var(--surface-card)",
            borderColor: "#4f46e5",
            fontWeight: 850,
            boxShadow: "0 4px 12px rgba(99, 102, 241, 0.35)",
            position: "relative",
          }}
        >
          <Sparkles style={{ width: "1.1rem", height: "1.1rem", color: "#fef08a" }} />
          Mua Combo Full
        </button>
      )}

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
            alignItems: "center",
            justifyContent: "center",
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
                    color: "var(--surface-card)",
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
                  Mua Combo Full Truyện
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
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.82rem", color: "var(--text-muted)" }}>
                    <span>Tổng mua lẻ {estPaidChapters} chương VIP:</span>
                    <span style={{ textDecoration: "line-through" }}>{totalRetailXu} Xu</span>
                  </div>

                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                    <span style={{ fontSize: "0.9rem", fontWeight: 800, color: "var(--text-primary)" }}>Giá Mua Combo Full:</span>
                    <strong style={{ fontSize: "1.4rem", fontWeight: 900, color: "#7c3aed" }}>
                      {actualComboXu} Xu
                    </strong>
                  </div>

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
                    <span>🔥 Tiết kiệm ngay cho bạn:</span>
                    <strong>{savingsPercent}% ({savingsXu} Xu)</strong>
                  </div>
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
                      color: "var(--surface-card)",
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
