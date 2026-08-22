"use client";

import { useCallback, useEffect, useState } from "react";

import { API_BASE_URL, authedFetch } from "@/lib/api-base";

type WalletTransactionRow = {
  id: string;
  currency: string;
  type: string;
  amount: number;
  balanceAfter: number;
  referenceType: string | null;
  referenceId: string | null;
  description: string | null;
  createdAt: string;
};

const typeLabels: Readonly<Record<string, string>> = {
  ADMIN_ADJUSTMENT: "Điều chỉnh admin",
  DAILY_REWARD: "Thưởng ngày",
  DEPOSIT: "Nạp tiền vào ví",
  DONATION: "Donate",
  EARNING: "Doanh thu team",
  PURCHASE: "Mua truyện",
  RECOMMENDATION: "Đề cử truyện",
  REFERRAL_REWARD: "Thưởng giới thiệu",
  REFUND: "Hoàn lại",
  WITHDRAWAL: "Rút tiền",
};

/**
 * Đơn vị của một dòng giao dịch.
 *
 * <p>Ví có hai loại tiền và bảng wallet_transactions ghi rõ loại nào ở cột
 * currency - nhưng sổ này ghi "xu" cho mọi dòng. Một lần đề cử trừ 1.250 ngọc
 * hiện ra thành "-1.250 xu" kèm "Số dư: 104.700 xu", mà 104.700 lại chính là
 * số dư ngọc. Người đọc soi sổ thấy số xu tụt xuống một cách vô lý và không
 * cách nào đối chiếu được.
 */
function unitOf(currency: string): string {
  return currency?.toUpperCase() === "GEM" ? "ngọc" : "xu";
}

const number = new Intl.NumberFormat("vi-VN");
const date = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  month: "2-digit",
  year: "numeric",
});

function time(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "" : date.format(parsed);
}

async function readProblem(response: Response, fallback: string) {
  const problem = await response.json().catch(() => ({})) as { detail?: string };
  return problem.detail ?? fallback;
}

export function WalletTransactionHistory() {
  const [items, setItems] = useState<WalletTransactionRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [allowed, setAllowed] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const response = await authedFetch(`${API_BASE_URL}/wallets/me/transactions`);
      if (response.status === 403) {
        setAllowed(false);
        return;
      }
      if (!response.ok) {
        throw new Error(await readProblem(response, "Không tải được lịch sử thu chi."));
      }
      setAllowed(true);
      setItems((await response.json()) as WalletTransactionRow[]);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Không tải được lịch sử thu chi.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (!allowed) {
    return (
      <section className="topupHistory">
        <h2>Bạn không có quyền thao tác này.</h2>
        <p className="topupHint">Chỉ chủ team và admin được xem lịch sử thu chi.</p>
      </section>
    );
  }

  return (
    <section className="topupHistory">
      <header className="walletSectionHeader">
        <div>
          <p className="detailEyebrow">Sổ ví</p>
          <h2>Lịch sử thu chi</h2>
        </div>
        <button disabled={loading} onClick={() => void load()} type="button">
          Làm mới
        </button>
      </header>
      {error ? <p className="questError" role="alert">{error}</p> : null}
      {loading ? (
        <p className="topupHint">Đang tải lịch sử ví...</p>
      ) : items.length === 0 ? (
        <p className="topupHint">Chưa có giao dịch ví.</p>
      ) : (
        <ul className="walletLedgerList">
          {items.map((item) => {
            const positive = item.amount >= 0;
            return (
              <li key={item.id}>
                <span className="topupHistoryDate">{time(item.createdAt)}</span>
                <strong>{typeLabels[item.type] ?? item.type}</strong>
                <span className={positive ? "walletAmountIn" : "walletAmountOut"}>
                  {positive ? "+" : ""}
                  {number.format(item.amount)} {unitOf(item.currency)}
                </span>
                <span>Số dư: {number.format(item.balanceAfter)} {unitOf(item.currency)}</span>
                <span>{item.description ?? item.referenceType ?? ""}</span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
