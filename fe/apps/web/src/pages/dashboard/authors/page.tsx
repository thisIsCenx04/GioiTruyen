"use client";

import { useCallback, useEffect, useState } from "react";

import { getAccessToken, refreshAccessToken } from "@/lib/auth";

type AuthorApplication = {
  id: string;
  userId: string;
  userEmail: string;
  userName: string | null;
  teamName: string;
  penName: string | null;
  introduction: string;
  sampleWork: string | null;
  status: string;
  reviewNote: string | null;
  reviewedAt: string | null;
  createdTeamId: string | null;
  createdAt: string;
  updatedAt: string;
};

const REFRESH_INTERVAL_MS = 30_000;

const STATUS_LABELS: Record<string, string> = {
  APPROVED: "Đã duyệt",
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

export default function AdminAuthorApplicationsPage() {
  const [rows, setRows] = useState<AuthorApplication[]>([]);
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [notes, setNotes] = useState<Record<string, string>>({});

  const refresh = useCallback(async () => {
    const query = filter ? `?status=${filter}` : "";
    const response = await adminFetch(`/admin/author-applications${query}`);
    if (response.ok) setRows((await response.json()) as AuthorApplication[]);
  }, [filter]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const timer = window.setInterval(() => void refresh(), REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  async function act(row: AuthorApplication, action: "approve" | "reject") {
    const note = notes[row.id]?.trim() ?? "";

    if (action === "reject" && !note) {
      setNotice("");
      setError("Nhập lý do từ chối trước khi thực hiện; người gửi sẽ nhận được nội dung này.");
      return;
    }

    const verb = action === "approve" ? "Duyệt" : "Từ chối";
    if (!window.confirm(
      action === "approve"
        ? `Duyệt yêu cầu của ${row.userEmail} và tạo nhóm "${row.teamName}"?`
        : `${verb} yêu cầu của ${row.userEmail}?`,
    )) return;

    setBusy(row.id);
    setError("");
    setNotice("");
    try {
      const response = await adminFetch(`/admin/author-applications/${row.id}/${action}`, {
        body: JSON.stringify({ note }),
        method: "POST",
      });
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
        throw new Error(problem?.detail ?? "Không thực hiện được thao tác.");
      }
      setNotice(action === "approve"
        ? `Đã duyệt và tạo nhóm "${row.teamName}" cho ${row.userEmail}.`
        : `Đã từ chối yêu cầu của ${row.userEmail} và gửi lý do.`);
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
          <p>Tài khoản và phân quyền</p>
          <h1>Duyệt đăng ký đăng truyện</h1>
        </div>
        <select onChange={(event) => setFilter(event.currentTarget.value)} value={filter}>
          <option value="">Tất cả (chờ duyệt trước)</option>
          <option value="PENDING">Chờ duyệt</option>
          <option value="APPROVED">Đã duyệt</option>
          <option value="REJECTED">Đã từ chối</option>
        </select>
      </header>

      {notice ? <p className="questNotice">{notice}</p> : null}
      {error ? <p className="questError">{error}</p> : null}

      <section className="topupReviewList">
        {rows.length === 0
          ? <p className="adminEmptyState">Chưa có yêu cầu nào.</p>
          : rows.map((row) => {
            const pending = row.status === "PENDING";
            return (
              <article
                className={pending ? "topupReviewRow isPending" : "topupReviewRow isSettled"}
                key={row.id}
              >
                <div className="topupReviewHead">
                  <strong>{row.teamName}</strong>
                  <span className="topupReviewStatus">{STATUS_LABELS[row.status] ?? row.status}</span>
                </div>

                <p className="topupReviewMeta">
                  <b>{row.userName ?? "—"}</b> ({row.userEmail})
                  {row.penName ? ` · bút danh ${row.penName}` : ""}
                </p>
                <p className="topupReviewMeta">{row.introduction}</p>
                {row.sampleWork ? (
                  <p className="topupReviewMeta">Tác phẩm mẫu: {row.sampleWork}</p>
                ) : null}
                <p className="topupReviewMeta">
                  Gửi lúc {formatMoment(row.createdAt)}
                  {row.reviewedAt ? ` · Duyệt lúc ${formatMoment(row.reviewedAt)}` : ""}
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
                      {busy === row.id ? "Đang xử lý…" : "Duyệt và tạo nhóm"}
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
