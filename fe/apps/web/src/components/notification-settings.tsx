"use client";

import {
  createBrowserNotificationClient,
  StoryApiError,
  type NotificationPreference,
} from "@gioitruyen/api-client";
import { BrandMark } from "@gioitruyen/ui";
import { Link } from "react-router-dom";
import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import styles from "./notification-settings.module.css";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

const categories = [
  ["STORY_UPDATES", "Truyện đang theo dõi", "Chương mới, lịch xuất bản và thay đổi trạng thái."],
  ["COMMUNITY", "Cộng đồng", "Phản hồi, lượt nhắc và hoạt động quanh bình luận."],
  ["MODERATION", "Kiểm duyệt", "Quyết định report, appeal và hồ sơ bản quyền."],
  ["ACCOUNT", "Tài khoản", "Cảnh báo an toàn và thay đổi quan trọng."],
] as const;

function message(error: unknown) {
  if (error instanceof StoryApiError) {
    if (error.problem.code === "NOTIFICATION_PREFERENCE_CONFLICT") {
      return "Cài đặt vừa thay đổi ở nơi khác. Tải lại rồi lưu lại lựa chọn.";
    }
    return error.problem.detail ?? "Không thể lưu cài đặt thông báo.";
  }
  return "Không thể kết nối máy chủ. Hãy thử lại.";
}

function base64Url(bytes: ArrayBuffer | null) {
  if (!bytes) throw new Error("Push key is missing");
  const binary = String.fromCharCode(...new Uint8Array(bytes));
  return window.btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/u, "");
}

function applicationKey(value: string) {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/")
    .padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = window.atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

export function NotificationSettings() {
  const api = useMemo(() => createBrowserNotificationClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }), []);
  const [preference, setPreference] = useState<NotificationPreference | null>(null);
  const [email, setEmail] = useState(false);
  const [push, setPush] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [working, setWorking] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const current = await api.preferences();
      setPreference(current);
      setEmail(current.emailEnabled);
      setPush(current.pushEnabled);
      setSelected([...current.categories]);
    } catch (requestError) {
      setError(message(requestError));
    }
  }, [api]);

  useEffect(() => {
    const task = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(task);
  }, [load]);

  function toggleCategory(value: string) {
    setSelected((current) =>
      current.includes(value)
        ? current.filter((entry) => entry !== value)
        : [...current, value],
    );
  }

  async function subscribePush() {
    const publicKey = process.env.NEXT_PUBLIC_WEB_PUSH_PUBLIC_KEY;
    if (!publicKey || !("serviceWorker" in navigator) || !("PushManager" in window)) {
      throw new Error("Trình duyệt hoặc cấu hình hiện tại chưa hỗ trợ push.");
    }
    const registration = await navigator.serviceWorker.register("/push-sw.js");
    const subscription = await registration.pushManager.subscribe({
      applicationServerKey: applicationKey(publicKey),
      userVisibleOnly: true,
    });
    const saved = await api.registerPush({
      auth: base64Url(subscription.getKey("auth")),
      endpoint: subscription.endpoint,
      p256dh: base64Url(subscription.getKey("p256dh")),
    });
    window.localStorage.setItem("gioitruyen.push-subscription-id", saved.id);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!preference) return;
    setWorking(true);
    setError("");
    setNotice("");
    try {
      if (push && !preference.pushEnabled) await subscribePush();
      if (!push && preference.pushEnabled) {
        const id = window.localStorage.getItem("gioitruyen.push-subscription-id");
        if (id) await api.removePush(id);
        const registration = await navigator.serviceWorker.getRegistration("/push-sw.js");
        await (await registration?.pushManager.getSubscription())?.unsubscribe();
        window.localStorage.removeItem("gioitruyen.push-subscription-id");
      }
      const updated = await api.updatePreferences(preference.version, {
        categories: selected,
        consentGranted: email || push,
        emailEnabled: email,
        pushEnabled: push,
      });
      setPreference(updated);
      setNotice("Đã lưu cài đặt thông báo.");
    } catch (requestError) {
      setError(requestError instanceof Error && !(requestError instanceof StoryApiError)
        ? requestError.message
        : message(requestError));
    } finally {
      setWorking(false);
    }
  }

  return (
    <main className={styles.settings}>
      <header>
        <Link aria-label="Về trang chủ" to="/"><BrandMark /></Link>
        <div>
          <span>Hộp thư · Quyền riêng tư</span>
          <h1>Bạn chọn<br /><em>điều được gửi.</em></h1>
        </div>
        <Link to="/notifications">Về thông báo</Link>
      </header>
      <form onSubmit={submit}>
        <section className={styles.intro}>
          <p>Email và push luôn tắt cho đến khi bạn chủ động đồng ý. Thông báo trong website vẫn hoạt động.</p>
          <small>Chính sách consent: {preference?.consentVersion ?? "đang tải…"}</small>
        </section>
        <section className={styles.controls}>
          {error && <div className={styles.error} role="alert">{error}</div>}
          {notice && <div className={styles.notice} role="status">{notice}</div>}
          <fieldset>
            <legend>Kênh nhận</legend>
            <label>
              <input checked={email} onChange={(e) => setEmail(e.target.checked)} type="checkbox" />
              <span><strong>Email</strong><small>Gửi đến email đã xác minh của tài khoản.</small></span>
            </label>
            <label>
              <input checked={push} onChange={(e) => setPush(e.target.checked)} type="checkbox" />
              <span><strong>Thông báo trên thiết bị</strong><small>Trình duyệt sẽ hỏi quyền trước khi đăng ký.</small></span>
            </label>
          </fieldset>
          <fieldset>
            <legend>Điều bạn muốn biết</legend>
            {categories.map(([value, title, detail]) => (
              <label key={value}>
                <input
                  checked={selected.includes(value)}
                  onChange={() => toggleCategory(value)}
                  type="checkbox"
                />
                <span><strong>{title}</strong><small>{detail}</small></span>
              </label>
            ))}
          </fieldset>
          <button disabled={!preference || working} type="submit">
            {working ? "Đang lưu…" : "Lưu lựa chọn"}
          </button>
        </section>
      </form>
    </main>
  );
}
