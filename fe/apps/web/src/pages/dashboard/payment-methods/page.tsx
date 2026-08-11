"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";

import { getAccessToken, refreshAccessToken } from "@/lib/auth";

type PaymentMethod = {
  id: string;
  name: string;
  type: "BANK_TRANSFER" | "OTHER" | "PAYPAL" | "QR";
  config: Record<string, string>;
  instructions: string | null;
  active: boolean;
  sortOrder: number;
};

const TYPE_LABELS: Record<PaymentMethod["type"], string> = {
  BANK_TRANSFER: "Chuyển khoản ngân hàng",
  OTHER: "Khác",
  PAYPAL: "PayPal",
  QR: "Mã QR",
};

/** Fields each type needs; the backend rejects a save that omits them. */
const TYPE_FIELDS: Record<PaymentMethod["type"], Array<{ key: string; label: string; placeholder: string }>> = {
  BANK_TRANSFER: [
    { key: "bank", label: "Tên ngân hàng", placeholder: "Vietcombank" },
    { key: "accountNumber", label: "Số tài khoản", placeholder: "0123456789" },
    { key: "accountName", label: "Tên chủ tài khoản", placeholder: "NGUYEN VAN A" },
    { key: "branch", label: "Chi nhánh (không bắt buộc)", placeholder: "Hà Nội" },
  ],
  OTHER: [
    { key: "note", label: "Ghi chú", placeholder: "Thông tin thanh toán" },
  ],
  PAYPAL: [
    { key: "paypalEmail", label: "Email PayPal", placeholder: "shop@example.com" },
    { key: "paypalMeLink", label: "Link PayPal.me (không bắt buộc)", placeholder: "https://paypal.me/..." },
  ],
  QR: [
    { key: "qrImageUrl", label: "Ảnh mã QR (URL)", placeholder: "https://.../qr.png" },
    { key: "provider", label: "Nhà cung cấp (không bắt buộc)", placeholder: "VietQR" },
  ],
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

export default function AdminPaymentMethodsPage() {
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [editing, setEditing] = useState<PaymentMethod | null>(null);
  const [creating, setCreating] = useState(false);
  const [formType, setFormType] = useState<PaymentMethod["type"]>("BANK_TRANSFER");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    const response = await adminFetch("/admin/payment-methods");
    if (response.ok) setMethods((await response.json()) as PaymentMethod[]);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const drawerOpen = editing !== null || creating;

  function openCreate() {
    setEditing(null);
    setFormType("BANK_TRANSFER");
    setCreating(true);
  }

  function openEdit(method: PaymentMethod) {
    setCreating(false);
    setFormType(method.type);
    setEditing(method);
  }

  function close() {
    setEditing(null);
    setCreating(false);
    setError("");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");

    const form = new FormData(event.currentTarget);
    const config: Record<string, string> = {};
    for (const field of TYPE_FIELDS[formType]) {
      const value = String(form.get(`config.${field.key}`) ?? "").trim();
      if (value) config[field.key] = value;
    }

    const payload = {
      active: form.get("active") === "on",
      config,
      instructions: String(form.get("instructions") ?? "").trim() || null,
      name: String(form.get("name") ?? "").trim(),
      sortOrder: Number(form.get("sortOrder") ?? 0),
      type: formType,
    };

    try {
      const response = await adminFetch(
        editing ? `/admin/payment-methods/${editing.id}` : "/admin/payment-methods",
        { body: JSON.stringify(payload), method: editing ? "PUT" : "POST" },
      );
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
        throw new Error(problem?.detail ?? "Không lưu được phương thức thanh toán.");
      }
      setNotice(editing ? "Đã cập nhật phương thức." : "Đã thêm phương thức mới.");
      close();
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không lưu được.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(method: PaymentMethod) {
    if (!window.confirm(`Xóa phương thức "${method.name}"?`)) return;
    setError("");
    const response = await adminFetch(`/admin/payment-methods/${method.id}`, { method: "DELETE" });
    if (!response.ok) {
      setError("Không xóa được phương thức này.");
      return;
    }
    setNotice(`Đã xóa "${method.name}".`);
    await refresh();
  }

  return (
    <>
      <header className="adminTopbar">
        <div>
          <p>Thanh toán và nạp xu</p>
          <h1>Phương thức thanh toán</h1>
        </div>
        <button onClick={openCreate} type="button">Thêm phương thức</button>
      </header>

      {notice ? <p className="questNotice">{notice}</p> : null}
      {error ? <p className="questError">{error}</p> : null}

      <section className="adminCrudPanel">
        {methods.length === 0
          ? <p className="adminEmptyState">Chưa có phương thức thanh toán nào.</p>
          : methods.map((method) => (
            <article key={method.id}>
              <div className="adminCrudDetails">
                <strong>{method.name}</strong>
                <small>
                  {TYPE_LABELS[method.type]}
                  {Object.entries(method.config).length > 0
                    ? ` · ${Object.entries(method.config).map(([key, value]) => `${key}: ${value}`).join(" · ")}`
                    : ""}
                </small>
              </div>
              <span>{method.active ? "Đang bật" : "Đã tắt"}</span>
              <div className="adminCrudActions">
                <button onClick={() => openEdit(method)} type="button">Chỉnh sửa</button>
                <button onClick={() => void remove(method)} type="button">Xóa</button>
              </div>
            </article>
          ))}
      </section>

      {drawerOpen ? (
        <div className="crudDrawerLayer">
          <button aria-label="Đóng" className="crudDrawerBackdrop" onClick={close} type="button" />
          <div className="crudDrawer">
            <header>
              <div>
                <p>Thanh toán</p>
                <h2>{editing ? "Chỉnh sửa phương thức" : "Thêm phương thức"}</h2>
              </div>
            </header>

            <form className="drawerForm" onSubmit={submit}>
              <label className="drawerField">
                <span>Loại thanh toán</span>
                <select
                  onChange={(event) => setFormType(event.currentTarget.value as PaymentMethod["type"])}
                  value={formType}
                >
                  {Object.entries(TYPE_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </label>

              <label className="drawerField">
                <span>Tên hiển thị cho người dùng</span>
                <input
                  defaultValue={editing?.name}
                  maxLength={150}
                  name="name"
                  placeholder="Chuyển khoản Vietcombank"
                  required
                />
              </label>

              <div className="drawerFieldGrid">
                {TYPE_FIELDS[formType].map((field) => (
                  <label className="drawerField" key={field.key}>
                    <span>{field.label}</span>
                    <input
                      defaultValue={editing?.type === formType ? editing.config[field.key] ?? "" : ""}
                      name={`config.${field.key}`}
                      placeholder={field.placeholder}
                    />
                  </label>
                ))}
              </div>

              <label className="drawerField">
                <span>Hướng dẫn cho người nạp</span>
                <textarea
                  defaultValue={editing?.instructions ?? ""}
                  name="instructions"
                  placeholder="Chuyển khoản theo đúng nội dung hiển thị trên màn hình nạp xu."
                  rows={3}
                />
              </label>

              <div className="drawerFieldGrid">
                <label className="drawerField">
                  <span>Thứ tự hiển thị</span>
                  <input
                    defaultValue={editing?.sortOrder ?? methods.length + 1}
                    min={0}
                    name="sortOrder"
                    type="number"
                  />
                </label>
              </div>

              <label className="drawerCheck">
                <input defaultChecked={editing?.active ?? true} name="active" type="checkbox" />
                <span>Cho người dùng chọn phương thức này</span>
              </label>

              <footer className="drawerActions">
                <button className="secondaryButton" onClick={close} type="button">Hủy</button>
                <button disabled={busy} type="submit">
                  {busy ? "Đang lưu..." : "Lưu phương thức"}
                </button>
              </footer>
            </form>
          </div>
        </div>
      ) : null}
    </>
  );
}
