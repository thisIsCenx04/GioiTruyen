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
  DEPOSIT: "Nạp xu",
  DONATION: "Donate",
  EARNING: "Doanh thu team",
  PURCHASE: "Mua truyện",
  RECOMMENDATION: "Đề cử ngọc",
  REFERRAL_REWARD: "Thưởng giới thiệu",
  REFUND: "Hoàn xu",
  WITHDRAWAL: "Rút tiền",
};

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
                  {number.format(item.amount)} xu
                </span>
                <span>Số dư: {number.format(item.balanceAfter)} xu</span>
                <span>{item.description ?? item.referenceType ?? ""}</span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
