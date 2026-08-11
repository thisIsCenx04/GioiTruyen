"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";

import { getAccessToken, refreshAccessToken } from "@/lib/auth";

type AdminQuest = {
  id: string;
  questType: string;
  title: string;
  description: string | null;
  targetValue: number;
  rewardCoin: number;
  rewardGem: number;
  sortOrder: number;
  active: boolean;
};

type QuestTypeOption = {
  value: string;
  label: string;
  unit: string;
  measuredBy: string;
};

const API = "/api/v1";

async function adminFetch(path: string, init?: RequestInit) {
  const send = (token: string | null) => {
    const headers = new Headers(init?.headers);
    headers.set("Accept", "application/json");
    if (init?.body) headers.set("Content-Type", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${API}${path}`, { ...init, headers });
  };
  let response = await send(getAccessToken());
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) response = await send(renewed);
  }
  return response;
}

export default function AdminQuestsPage() {
  const [quests, setQuests] = useState<AdminQuest[]>([]);
  const [types, setTypes] = useState<QuestTypeOption[]>([]);
  const [editing, setEditing] = useState<AdminQuest | null>(null);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(async () => {
    const [questRes, typeRes] = await Promise.all([
      adminFetch("/admin/quests"),
      adminFetch("/admin/quests/types"),
    ]);
    if (questRes.ok) setQuests((await questRes.json()) as AdminQuest[]);
    if (typeRes.ok) setTypes((await typeRes.json()) as QuestTypeOption[]);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const open = editing ?? (creating ? null : undefined);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    const form = new FormData(event.currentTarget);
    const payload = {
      active: form.get("active") === "on",
      description: String(form.get("description") ?? "").trim() || null,
      questType: String(form.get("questType") ?? ""),
      rewardCoin: Number(form.get("rewardCoin") ?? 0),
      rewardGem: Number(form.get("rewardGem") ?? 0),
      sortOrder: Number(form.get("sortOrder") ?? 0),
      targetValue: Number(form.get("targetValue") ?? 1),
      title: String(form.get("title") ?? "").trim(),
    };

    try {
      const response = await adminFetch(
        editing ? `/admin/quests/${editing.id}` : "/admin/quests",
        { body: JSON.stringify(payload), method: editing ? "PUT" : "POST" },
      );
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
        throw new Error(problem?.detail ?? "Không lưu được nhiệm vụ.");
      }
      setNotice(editing ? "Đã cập nhật nhiệm vụ." : "Đã tạo nhiệm vụ mới.");
      setEditing(null);
      setCreating(false);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không lưu được nhiệm vụ.");
    } finally {
      setBusy(false);
    }
  }

  async function deactivate(quest: AdminQuest) {
    if (!window.confirm(`Ẩn nhiệm vụ "${quest.title}"?`)) return;
    setError("");
    const response = await adminFetch(`/admin/quests/${quest.id}`, { method: "DELETE" });
    if (!response.ok) {
      setError("Không ẩn được nhiệm vụ.");
      return;
    }
    setNotice(`Đã ẩn "${quest.title}".`);
    await refresh();
  }

  const selectedType = (value: string) => types.find((type) => type.value === value);

  return (
    <>
      <header className="adminTopbar">
        <div>
          <p>Vận hành người dùng</p>
          <h1>Quản lý nhiệm vụ</h1>
        </div>
        <button onClick={() => { setCreating(true); setEditing(null); }} type="button">
          Tạo nhiệm vụ
        </button>
      </header>

      {notice ? <p className="questNotice">{notice}</p> : null}
      {error ? <p className="questError">{error}</p> : null}

      <section className="adminCrudPanel">
        {quests.length === 0
          ? <p className="adminEmptyState">Chưa có nhiệm vụ nào.</p>
          : quests.map((quest) => (
            <article key={quest.id}>
              <div className="adminCrudDetails">
                <strong>{quest.title}</strong>
                <small>
                  {selectedType(quest.questType)?.label ?? quest.questType}
                  {" · chỉ tiêu "}{quest.targetValue}{" "}
                  {selectedType(quest.questType)?.unit ?? ""}
                  {" · thưởng "}{quest.rewardCoin} xu
                  {quest.rewardGem > 0 ? ` + ${quest.rewardGem} ngọc` : ""}
                </small>
              </div>
              <span>{quest.active ? "Đang bật" : "Đã ẩn"}</span>
              <div className="adminCrudActions">
                <button onClick={() => { setEditing(quest); setCreating(false); }} type="button">
                  Chỉnh sửa
                </button>
                {quest.active ? (
                  <button onClick={() => void deactivate(quest)} type="button">Ẩn</button>
                ) : null}
              </div>
            </article>
          ))}
      </section>

      {open !== undefined ? (
        <div className="crudDrawerLayer">
          <button
            aria-label="Đóng"
            className="crudDrawerBackdrop"
            onClick={() => { setEditing(null); setCreating(false); }}
            type="button"
          />
          <div className="crudDrawer">
            <header>
              <div>
                <p>Nhiệm vụ hằng ngày</p>
                <h2>{editing ? "Chỉnh sửa nhiệm vụ" : "Tạo nhiệm vụ"}</h2>
              </div>
            </header>

            <form className="drawerForm" onSubmit={submit}>
              <label className="drawerField">
                <span>Loại nhiệm vụ</span>
                <select defaultValue={editing?.questType ?? types[0]?.value} name="questType" required>
                  {types.map((type) => (
                    <option key={type.value} value={type.value}>{type.label}</option>
                  ))}
                </select>
                <small className="drawerFieldHint">
                  Cách đo tiến độ đã được lập trình sẵn cho từng loại.
                </small>
              </label>

              <label className="drawerField">
                <span>Tên nhiệm vụ</span>
                <input defaultValue={editing?.title} maxLength={160} name="title" required />
              </label>

              <label className="drawerField">
                <span>Mô tả</span>
                <textarea defaultValue={editing?.description ?? ""} maxLength={500} name="description" rows={3} />
              </label>

              <div className="drawerFieldGrid">
                <label className="drawerField">
                  <span>Chỉ tiêu</span>
                  <input defaultValue={editing?.targetValue ?? 1} min={1} name="targetValue" required type="number" />
                </label>
                <label className="drawerField">
                  <span>Thứ tự hiển thị</span>
                  <input defaultValue={editing?.sortOrder ?? quests.length + 1} min={0} name="sortOrder" type="number" />
                </label>
                <label className="drawerField">
                  <span>Thưởng xu</span>
                  <input defaultValue={editing?.rewardCoin ?? 0} min={0} name="rewardCoin" type="number" />
                </label>
                <label className="drawerField">
                  <span>Thưởng ngọc</span>
                  <input defaultValue={editing?.rewardGem ?? 0} min={0} name="rewardGem" type="number" />
                </label>
              </div>

              <label className="drawerCheck">
                <input defaultChecked={editing?.active ?? true} name="active" type="checkbox" />
                <span>Bật nhiệm vụ này cho người đọc</span>
              </label>

              <footer className="drawerActions">
                <button
                  className="secondaryButton"
                  onClick={() => { setEditing(null); setCreating(false); }}
                  type="button"
                >
                  Hủy
                </button>
                <button disabled={busy} type="submit">
                  {busy ? "Đang lưu..." : "Lưu nhiệm vụ"}
                </button>
              </footer>
            </form>
          </div>
        </div>
      ) : null}
    </>
  );
}
