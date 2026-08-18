"use client";

import {
  createBrowserNotificationClient,
  StoryApiError,
  type NotificationItem,
} from "@gioitruyen/api-client";
import { BrandMark } from "@gioitruyen/ui";
import { Link } from "react-router-dom";
import { useCallback, useEffect, useMemo, useState } from "react";

import styles from "./notification-inbox.module.css";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { timeAgoFormatted } from "./notification-bell";

function message(error: unknown) {
  if (error instanceof StoryApiError) {
    if (error.problem.status === 401) {
      return "Vui lòng đăng nhập để xem danh sách thông báo của bạn.";
    }
    return error.problem.detail ?? "Không thể tải danh sách thông báo.";
  }
  return "Không thể kết nối máy chủ. Vui lòng thử lại sau.";
}

/** How often an open inbox refetches its first page. */
const REFRESH_INTERVAL_MS = 30_000;

export function NotificationInbox() {
  const api = useMemo(
    () => createBrowserNotificationClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }),
    [],
  );
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const page = await api.list();
      setItems([...page.items]);
      setCursor(page.nextCursor);
      setUnread(page.unreadCount);
      setError("");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    const task = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(task);
  }, [load]);

  useEffect(() => {
    const tick = () => {
      if (document.visibilityState !== "visible") return;
      void load();
    };
    const timer = window.setInterval(tick, REFRESH_INTERVAL_MS);
    document.addEventListener("visibilitychange", tick);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", tick);
    };
  }, [load]);

  async function loadMore() {
    if (!cursor || working) return;
    setWorking(true);
    try {
      const page = await api.list(cursor);
      setItems((current) => [...current, ...page.items]);
      setCursor(page.nextCursor);
      setUnread(page.unreadCount);
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setWorking(false);
    }
  }

  async function markRead(item: NotificationItem) {
    if (item.readAt || working) return;
    setWorking(true);
    try {
      const updated = await api.markRead(item.id);
      setItems((current) =>
        current.map((entry) => (entry.id === updated.id ? updated : entry)),
      );
      setUnread((current) => Math.max(0, current - 1));
      setError("");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setWorking(false);
    }
  }

  async function markAllRead() {
    if (!unread || working) return;
    setWorking(true);
    try {
      const watermark = await api.markAllRead();
      setItems((current) =>
        current.map((item) => ({
          ...item,
          readAt: item.readAt ?? watermark.readBefore,
        })),
      );
      setUnread(watermark.unreadCount);
      setError("");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setWorking(false);
    }
  }

  return (
    <main className={styles.inbox}>
      <header className={styles.header}>
        <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
          <Link aria-label="Về trang chủ" to="/">
            <BrandMark />
          </Link>
          <h1 style={{ margin: 0, fontSize: "1.35rem", fontWeight: 850, color: "#0f172a" }}>
            Thông báo hệ thống
          </h1>
        </div>
        <Link className={styles.back} to="/">
          ← Về trang chủ
        </Link>
      </header>

      <section className={styles.ledger} aria-busy={loading}>
        <div className={styles.stream}>
          <div className={styles.toolbar}>
            <span className={styles.unreadBadge}>
              {unread > 0 ? `${unread} thông báo chưa đọc` : "Đã đọc tất cả thông báo"}
            </span>

            <button
              className={styles.markAllBtn}
              disabled={!unread || working}
              onClick={() => void markAllRead()}
              type="button"
            >
              {working ? "Đang xử lý…" : "Đánh dấu tất cả đã đọc"}
            </button>
          </div>

          {error && <div className={styles.error} role="alert">{error}</div>}

          {loading && <p className={styles.empty}>Đang tải hộp thư thông báo…</p>}

          {!loading && !error && items.length === 0 && (
            <div className={styles.empty}>
              <strong>Chưa có thông báo nào.</strong>
              <p style={{ margin: "0.5rem 0 0", color: "#64748b" }}>
                Khi có hoạt động nạp xu, mở khóa hay cập nhật truyện, thông báo sẽ hiển thị tại đây.
              </p>
            </div>
          )}

          <ul className={styles.list}>
            {items.map((item) => {
              const { relative, exact } = timeAgoFormatted(item.createdAt);
              const isUnread = !item.readAt;

              return (
                <li className={isUnread ? styles.unread : styles.read} key={item.id}>
                  <button
                    aria-label={item.readAt ? item.title : `Đánh dấu đã đọc: ${item.title}`}
                    disabled={Boolean(item.readAt) || working}
                    onClick={() => void markRead(item)}
                    type="button"
                  >
                    <span className={styles.copy}>
                      <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.25rem" }}>
                        <span style={{ fontSize: "0.78rem", fontWeight: 700, color: "#1d4ed8", background: "#eff6ff", padding: "0.15rem 0.55rem", borderRadius: "6px" }}>
                          🕒 {exact}
                        </span>
                        <span style={{ fontSize: "0.78rem", fontWeight: 600, color: "#64748b" }}>
                          ({relative})
                        </span>
                      </div>

                      <strong style={{ color: "#0f172a", fontSize: "1.05rem", fontWeight: 800 }}>
                        {item.title}
                      </strong>

                      <span style={{ color: "#334155", fontSize: "0.92rem", fontWeight: 500, lineHeight: 1.5 }}>
                        {item.body}
                      </span>
                    </span>

                    <span className={styles.state}>
                      {isUnread ? "Mới" : "Đã đọc"}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>

          {cursor && (
            <button
              className={styles.more}
              disabled={working}
              onClick={() => void loadMore()}
              type="button"
            >
              {working ? "Đang tải…" : "Xem thêm thông báo cũ hơn"}
            </button>
          )}
        </div>
      </section>
    </main>
  );
}
