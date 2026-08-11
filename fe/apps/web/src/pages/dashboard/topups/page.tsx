"use client";

import { useCallback, useEffect, useState } from "react";

import { getAccessToken, refreshAccessToken } from "@/lib/auth";

type AdminTopup = {
  id: string;
  userEmail: string;
  transactionCode: string;
  amountVnd: number;
  coinReceived: number;
  gemReceived: number;
  methodName: string | null;
  status: string;
  createdAt: string;
  paidAt: string | null;
};

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
  const [filter, setFilter] = useState("PENDING");
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const refresh = useCallback(async () => {
    const query = filter ? `?status=${filter}` : "";
    const response = await adminFetch(`/admin/topups${query}`);
    if (response.ok) setRows((await response.json()) as AdminTopup[]);
  }, [filter]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function act(row: AdminTopup, action: "approve" | "reject") {
    const verb = action === "approve" ? "Cộng xu cho" : "Hủy";
    if (!window.confirm(`${verb} giao dịch ${row.transactionCode} của ${row.userEmail}?`)) return;

    setBusy(row.id);
    setError("");
    setNotice("");
    try {
      const response = await adminFetch(`/admin/topups/${row.id}/${action}`, { method: "POST" });
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
        throw new Error(problem?.detail ?? "Không thực hiện được thao tác.");
      }
      setNotice(action === "approve"
        ? `Đã cộng ${money.format(row.coinReceived)} xu và ${money.format(row.gemReceived)} ngọc cho ${row.userEmail}.`
        : `Đã hủy giao dịch ${row.transactionCode}.`);
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
          <option value="PENDING">Chờ xác nhận</option>
          <option value="PAID">Đã cộng xu</option>
          <option value="CANCELLED">Đã hủy</option>
          <option value="">Tất cả</option>
        </select>
      </header>

      {notice ? <p className="questNotice">{notice}</p> : null}
      {error ? <p className="questError">{error}</p> : null}

      <section className="adminCrudPanel">
        {rows.length === 0
          ? <p className="adminEmptyState">Không có giao dịch nào.</p>
          : rows.map((row) => (
            <article key={row.id}>
              <div className="adminCrudDetails">
                <strong>{row.transactionCode} · {money.format(row.amountVnd)}đ</strong>
                <small>
                  {row.userEmail} · {row.methodName ?? "—"} ·{" "}
                  {money.format(row.coinReceived)} xu + {money.format(row.gemReceived)} ngọc
                  {" · "}{row.createdAt}
                </small>
              </div>
              <span>{STATUS_LABELS[row.status] ?? row.status}</span>
              <div className="adminCrudActions">
                {row.status === "PENDING" ? (
                  <>
                    <button disabled={busy === row.id} onClick={() => void act(row, "approve")} type="button">
                      {busy === row.id ? "Đang xử lý…" : "Xác nhận đã nhận tiền"}
                    </button>
                    <button disabled={busy === row.id} onClick={() => void act(row, "reject")} type="button">
                      Hủy
                    </button>
                  </>
                ) : null}
              </div>
            </article>
          ))}
      </section>
    </>
  );
}
