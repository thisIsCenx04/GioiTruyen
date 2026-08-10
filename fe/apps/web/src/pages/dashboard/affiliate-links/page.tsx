"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";

import { AdminShell } from "../admin-shell";

type Advertisement = {
  id: string;
  name: string;
  type: string;
  imageUrl: string | null;
  targetUrl: string;
  placement: string;
  cooldownSeconds: number;
  maxClicksPerDay: number;
  priority: number;
  active: boolean;
  createdAt: string | null;
  updatedAt: string | null;
};

const API = "/api/v1/admin/advertisements";

function authToken() {
  if (typeof window === "undefined") return null;
  const fromStorage = localStorage.getItem("access_token");
  if (fromStorage) return fromStorage;
  const cookie = document.cookie.match(/(?:^|; )access_token=([^;]*)/);
  return cookie ? decodeURIComponent(cookie[1]) : null;
}

async function callApi<T>(path: string, init: RequestInit = {}): Promise<T | null> {
  const token = authToken();
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(init.body ? { "Content-Type": "application/json" } : {}),
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${API}${path}`, { ...init, credentials: "same-origin", headers });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as
      | { detail?: string; title?: string }
      | null;
    throw new Error(problem?.detail ?? problem?.title ?? `Thao tác thất bại (${response.status}).`);
  }
  // DELETE answers 200 with an empty body.
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : null;
}

export default function AffiliateLinksAdminPage() {
  const [links, setLinks] = useState<Advertisement[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const all = (await callApi<Advertisement[]>("")) ?? [];
      setLinks(all.filter((ad) => ad.type === "AFFILIATE_REDIRECT"));
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không tải được danh sách link.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function addLink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const targetUrl = String(form.get("targetUrl") ?? "").trim();
    const name = String(form.get("name") ?? "").trim();

    if (!targetUrl) {
      setError("Hãy nhập link affiliate.");
      return;
    }

    setBusy(true);
    setError("");
    setNotice("");
    try {
      await callApi<Advertisement>("", {
        method: "POST",
        body: JSON.stringify({
          name: name || `Affiliate ${links.length + 1}`,
          type: "AFFILIATE_REDIRECT",
          imageUrl: null,
          targetUrl,
          placement: "GLOBAL_CLICK",
          cooldownSeconds: Number(form.get("cooldownSeconds") ?? 600),
          maxClicksPerDay: Number(form.get("maxClicksPerDay") ?? 5),
          priority: Number(form.get("priority") ?? 0),
          startAt: null,
          endAt: null,
          active: true,
        }),
      });
      setNotice("Đã thêm link affiliate.");
      event.currentTarget.reset();
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thêm được link.");
    } finally {
      setBusy(false);
    }
  }

  async function removeLink(link: Advertisement) {
    if (!window.confirm(`Xóa link "${link.name}"?\n${link.targetUrl}`)) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await callApi(`/${link.id}`, { method: "DELETE" });
      setNotice("Đã xóa link affiliate.");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không xóa được link.");
    } finally {
      setBusy(false);
    }
  }

  async function toggleLink(link: Advertisement) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await callApi(`/${link.id}/enabled?enabled=${!link.active}`, { method: "PATCH" });
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không đổi được trạng thái.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AdminShell>
      <header className="adminTopbar">
        <div>
          <p>Quảng cáo &amp; liên kết</p>
          <h1>Quản lý link affiliate</h1>
        </div>
      </header>

      <section className="adminCrudPanel" style={{ display: "block", padding: "1.25rem" }}>
        <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Thêm link mới</h2>
        <p style={{ color: "#94a3b8", fontSize: "0.85rem", marginTop: 0 }}>
          Link được mở khi người đọc bấm vào trang (vị trí <code>GLOBAL_CLICK</code>). Thời gian chờ
          tính bằng giây, giới hạn số lần mở mỗi ngày cho một người đọc.
        </p>
        <form onSubmit={addLink} style={{ display: "grid", gap: "0.75rem", gridTemplateColumns: "2fr 1fr" }}>
          <label style={{ display: "flex", flexDirection: "column", gap: "0.3rem" }}>
            <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>Link affiliate</span>
            <input name="targetUrl" placeholder="https://s.shopee.vn/..." required type="url" />
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: "0.3rem" }}>
            <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>Tên hiển thị</span>
            <input name="name" placeholder="Shopee Affiliate 6" />
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: "0.3rem" }}>
            <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>Thời gian chờ (giây)</span>
            <input defaultValue={600} max={86400} min={0} name="cooldownSeconds" type="number" />
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: "0.3rem" }}>
            <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>Số lần mở tối đa / ngày</span>
            <input defaultValue={5} max={100} min={0} name="maxClicksPerDay" type="number" />
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: "0.3rem" }}>
            <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>Độ ưu tiên</span>
            <input defaultValue={0} min={0} name="priority" type="number" />
          </label>
          <div style={{ alignItems: "end", display: "flex" }}>
            <button disabled={busy} type="submit">
              {busy ? "Đang lưu..." : "Thêm link"}
            </button>
          </div>
        </form>

        {error ? (
          <p className="drawerError" role="alert" style={{ marginTop: "0.75rem" }}>
            {error}
          </p>
        ) : null}
        {notice ? (
          <p role="status" style={{ color: "#22c55e", fontSize: "0.85rem", marginTop: "0.75rem" }}>
            {notice}
          </p>
        ) : null}
      </section>

      <section className="adminCrudPanel">
        {loading ? (
          <p className="adminEmptyState">Đang tải...</p>
        ) : links.length === 0 ? (
          <p className="adminEmptyState">Chưa có link affiliate nào.</p>
        ) : (
          links.map((link) => (
            <article key={link.id}>
              <div className="adminCrudDetails">
                <strong>{link.name}</strong>
                <small>
                  <a href={link.targetUrl} rel="noreferrer noopener" target="_blank">
                    {link.targetUrl}
                  </a>
                </small>
                <small>
                  Chờ {link.cooldownSeconds}s · tối đa {link.maxClicksPerDay} lần/ngày · ưu tiên{" "}
                  {link.priority}
                </small>
              </div>
              <span>{link.active ? "Đang bật" : "Đã tắt"}</span>
              <div className="adminCrudActions">
                <button disabled={busy} onClick={() => toggleLink(link)} type="button">
                  {link.active ? "Tắt" : "Bật"}
                </button>
                <button disabled={busy} onClick={() => removeLink(link)} type="button">
                  Xóa
                </button>
              </div>
            </article>
          ))
        )}
      </section>
    </AdminShell>
  );
}
