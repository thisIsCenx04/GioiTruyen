"use client";

import { useCallback, useEffect, useState } from "react";

import { getAccessToken, refreshAccessToken } from "@/lib/auth";

type PromotionBooking = {
  id: string;
  storyId: string;
  storyTitle: string;
  storySlug: string;
  teamId: string;
  teamName: string;
  durationDays: number;
  coinPaid: number;
  startsAt: string;
  endsAt: string;
  status: string;
  daysRemaining: number;
  reviewNote: string | null;
  reviewedAt: string | null;
  createdAt: string;
  purchasedByEmail: string;
};

const money = new Intl.NumberFormat("vi-VN");

/** Requests arrive while the admin is on the page. */
const REFRESH_INTERVAL_MS = 30_000;

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Đang chạy",
  CANCELLED: "Đã hủy",
  EXPIRED: "Đã kết thúc",
  PENDING: "Chờ duyệt",
  REJECTED: "Đã từ chối",
};

function formatMoment(value: string | null) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString("vi-VN", {
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
}

async function adminFetch(path: string, init?: RequestInit) {
  const send = (token: string | null) => {
    const headers = new Headers(init?.headers);
    headers.set("Accept", "application/json");
    if (init?.body) headers.set("Content-Type", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`/api/v1${path}`, { ...init, headers });
  };
  let response = await send(getAccessToken());
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) response = await send(renewed);
  }
  return response;
}

export default function AdminPromotionsPage() {
  const [rows, setRows] = useState<PromotionBooking[]>([]);
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [notes, setNotes] = useState<Record<string, string>>({});

  const refresh = useCallback(async () => {
    const query = filter ? `?status=${filter}` : "";
    const response = await adminFetch(`/admin/promotions${query}`);
    if (response.ok) setRows((await response.json()) as PromotionBooking[]);
  }, [filter]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const timer = window.setInterval(() => void refresh(), REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  async function act(row: PromotionBooking, action: "approve" | "reject") {
    const note = notes[row.id]?.trim() ?? "";

    // Rejecting refunds the coins, so the buyer is owed an explanation.
    if (action === "reject" && !note) {
      setNotice("");
      setError("Nhập lý do từ chối trước khi thực hiện; người đăng ký sẽ nhận được nội dung này.");
      return;
    }

    const verb = action === "approve" ? "Duyệt" : "Từ chối";
    if (!window.confirm(`${verb} bố cáo cho truyện "${row.storyTitle}"?`)) return;

    setBusy(row.id);
    setError("");
    setNotice("");
    try {
      const response = await adminFetch(`/admin/promotions/${row.id}/${action}`, {
        body: JSON.stringify({ note }),
        method: "POST",
      });
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
        throw new Error(problem?.detail ?? "Không thực hiện được thao tác.");
      }
      setNotice(action === "approve"
        ? `Đã duyệt bố cáo "${row.storyTitle}" (${row.durationDays} ngày). Đã gửi thông báo tới ${row.purchasedByEmail}.`
        : `Đã từ chối và hoàn ${money.format(row.coinPaid)} xu cho ${row.purchasedByEmail}.`);
      setNotes((current) => ({ ...current, [row.id]: "" }));
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thực hiện được thao tác.");
    } finally {
      setBusy("");
    }
  }

  return (
    <>
      <header className="adminTopbar">
        <div>
          <p>Quảng cáo và bố cáo</p>
          <h1>Duyệt bố cáo</h1>
        </div>
        <select onChange={(event) => setFilter(event.currentTarget.value)} value={filter}>
          <option value="">Tất cả (chờ duyệt trước)</option>
          <option value="PENDING">Chờ duyệt</option>
          <option value="ACTIVE">Đang chạy</option>
          <option value="REJECTED">Đã từ chối</option>
          <option value="EXPIRED">Đã kết thúc</option>
        </select>
      </header>

      {notice ? <p className="questNotice">{notice}</p> : null}
      {error ? <p className="questError">{error}</p> : null}

      <section className="topupReviewList">
        {rows.length === 0
          ? <p className="adminEmptyState">Chưa có yêu cầu bố cáo nào.</p>
          : rows.map((row) => {
            const pending = row.status === "PENDING";
            return (
              <article
                className={pending ? "topupReviewRow isPending" : "topupReviewRow isSettled"}
                key={row.id}
              >
                <div className="topupReviewHead">
                  <strong>{row.storyTitle} · {row.durationDays} ngày · {money.format(row.coinPaid)} xu</strong>
                  <span className="topupReviewStatus">{STATUS_LABELS[row.status] ?? row.status}</span>
                </div>

                <p className="topupReviewMeta">
                  Team <b>{row.teamName}</b> · đăng ký bởi {row.purchasedByEmail}
                </p>
                <p className="topupReviewMeta">
                  Gửi lúc {formatMoment(row.createdAt)}
                  {row.reviewedAt ? ` · Duyệt lúc ${formatMoment(row.reviewedAt)}` : ""}
                  {row.status === "ACTIVE" ? ` · Còn ${row.daysRemaining} ngày` : ""}
                </p>
                {row.reviewNote ? <p className="topupReviewNote">Ghi chú: {row.reviewNote}</p> : null}

                {pending ? (
                  <div className="topupReviewActions">
                    <input
                      onChange={(event) =>
                        setNotes((current) => ({ ...current, [row.id]: event.currentTarget.value }))}
                      placeholder="Ghi chú gửi tới người đăng ký (bắt buộc khi từ chối)"
                      value={notes[row.id] ?? ""}
                    />
                    <button
                      className="topupApprove"
                      disabled={busy === row.id}
                      onClick={() => void act(row, "approve")}
                      type="button"
                    >
                      {busy === row.id ? "Đang xử lý…" : "Duyệt"}
                    </button>
                    <button
                      className="topupReject"
                      disabled={busy === row.id}
                      onClick={() => void act(row, "reject")}
                      type="button"
                    >
                      Từ chối
                    </button>
                  </div>
                ) : null}
              </article>
            );
          })}
      </section>
    </>
  );
}
