"use client";

import { useCallback, useEffect, useState } from "react";

import { getAccessToken, refreshAccessToken } from "@/lib/auth";

type AdminTopup = {
  id: string;
  userEmail: string;
  userName: string | null;
  transactionCode: string;
  amountVnd: number;
  coinReceived: number;
  gemReceived: number;
  methodName: string | null;
  status: string;
  adminNote: string | null;
  createdAt: string;
  paidAt: string | null;
  reviewedAt: string | null;
};

/** New transfers arrive while the admin is looking at the queue. */
const REFRESH_INTERVAL_MS = 20_000;

function formatMoment(value: string | null) {
  if (!value) return "—";
  const parsed = new Date(value.replace(" ", "T"));
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

const money = new Intl.NumberFormat("vi-VN");

const STATUS_LABELS: Record<string, string> = {
  CANCELLED: "Đã hủy",
  FAILED: "Thất bại",
  PAID: "Đã cộng xu",
  PENDING: "Chờ xác nhận",
  REFUNDED: "Đã hoàn",
};

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

export default function AdminTopupsPage() {
  const [rows, setRows] = useState<AdminTopup[]>([]);
  // Defaults to the full history: the queue and what was already settled are
  // read together, newest first.
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [notes, setNotes] = useState<Record<string, string>>({});

  const refresh = useCallback(async () => {
    const query = filter ? `?status=${filter}` : "";
    const response = await adminFetch(`/admin/topups${query}`);
    if (response.ok) setRows((await response.json()) as AdminTopup[]);
  }, [filter]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Transfers land while this page is open, so the queue polls instead of
  // waiting for the admin to reload.
  useEffect(() => {
    const timer = window.setInterval(() => void refresh(), REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  async function act(row: AdminTopup, action: "approve" | "reject") {
    const note = notes[row.id]?.trim() ?? "";

    // Rejecting without a reason leaves the reader with a cancelled top-up and
    // no idea what to fix, so the note is required here but optional on approve.
    if (action === "reject" && !note) {
      setNotice("");
      setError("Nhập ghi chú lý do trước khi hủy, người nạp sẽ nhận được nội dung này.");
      return;
    }

    const verb = action === "approve" ? "Cộng xu cho" : "Hủy";
    if (!window.confirm(`${verb} giao dịch ${row.transactionCode} của ${row.userEmail}?`)) return;

    setBusy(row.id);
    setError("");
    setNotice("");
    try {
      const response = await adminFetch(`/admin/topups/${row.id}/${action}`, {
        body: JSON.stringify({ note }),
        method: "POST",
      });
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
        throw new Error(problem?.detail ?? "Không thực hiện được thao tác.");
      }
      setNotice(action === "approve"
        ? `Đã cộng ${money.format(row.coinReceived)} xu và ${money.format(row.gemReceived)} ngọc cho ${row.userEmail}. Đã gửi thông báo tới tài khoản này.`
        : `Đã hủy giao dịch ${row.transactionCode} và gửi thông báo tới ${row.userEmail}.`);
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
          <p>Thanh toán và nạp xu</p>
          <h1>Duyệt nạp xu</h1>
        </div>
        <select onChange={(event) => setFilter(event.currentTarget.value)} value={filter}>
          <option value="">Tất cả (mới nhất trước)</option>
          <option value="PENDING">Chờ xác nhận</option>
          <option value="PAID">Đã cộng xu</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>
      </header>

      {notice ? <p className="questNotice">{notice}</p> : null}
      {error ? <p className="questError">{error}</p> : null}

      <section className="topupReviewList">
        {rows.length === 0
          ? <p className="adminEmptyState">Không có giao dịch nào.</p>
          : rows.map((row) => {
            const pending = row.status === "PENDING";
            return (
              <article
                className={pending ? "topupReviewRow isPending" : "topupReviewRow isSettled"}
                key={row.id}
              >
                <div className="topupReviewHead">
                  <strong>{row.transactionCode} · {money.format(row.amountVnd)}đ</strong>
                  <span className="topupReviewStatus">{STATUS_LABELS[row.status] ?? row.status}</span>
                </div>

                <p className="topupReviewMeta">
                  <b>{row.userName ?? "—"}</b> ({row.userEmail}) · {row.methodName ?? "—"} ·{" "}
                  {money.format(row.coinReceived)} xu + {money.format(row.gemReceived)} ngọc
                </p>
                <p className="topupReviewMeta">
                  Tạo {formatMoment(row.createdAt)}
                  {row.reviewedAt ? ` · Duyệt ${formatMoment(row.reviewedAt)}` : ""}
                  {row.paidAt ? ` · Cộng xu ${formatMoment(row.paidAt)}` : ""}
                </p>
                {row.adminNote ? <p className="topupReviewNote">Ghi chú: {row.adminNote}</p> : null}

                {pending ? (
                  <div className="topupReviewActions">
                    <input
                      onChange={(event) =>
                        setNotes((current) => ({ ...current, [row.id]: event.currentTarget.value }))}
                      placeholder="Ghi chú gửi tới người nạp (bắt buộc khi hủy)"
                      value={notes[row.id] ?? ""}
                    />
                    <button
                      className="topupApprove"
                      disabled={busy === row.id}
                      onClick={() => void act(row, "approve")}
                      type="button"
                    >
                      {busy === row.id ? "Đang xử lý…" : "Xác nhận"}
                    </button>
                    <button
                      className="topupReject"
                      disabled={busy === row.id}
                      onClick={() => void act(row, "reject")}
                      type="button"
                    >
                      Hủy
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
