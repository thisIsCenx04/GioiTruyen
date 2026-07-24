"use client";

import {
  createBrowserNotificationClient,
  StoryApiError,
  type NotificationItem,
} from "@gioitruyen/api-client";
import { BrandMark } from "@gioitruyen/ui";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import styles from "./notification-inbox.module.css";

function time(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function message(error: unknown) {
  if (error instanceof StoryApiError) {
    if (error.problem.status === 401) {
      return "Đăng nhập để xem thông báo của bạn.";
    }
    return error.problem.detail ?? "Không thể tải hộp thư thông báo.";
  }
  return "Không thể kết nối máy chủ. Hãy thử lại.";
}

export function NotificationInbox() {
  const api = useMemo(() => createBrowserNotificationClient(), []);
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
        <Link aria-label="Về trang chủ" href="/">
          <BrandMark />
        </Link>
        <div>
          <span className={styles.eyebrow}>Dấu trang · Hộp thư</span>
          <h1>Những điều<br /><em>vừa xảy ra.</em></h1>
        </div>
        <Link className={styles.back} href="/">Tiếp tục đọc</Link>
      </header>

      <section className={styles.ledger} aria-busy={loading}>
        <div className={styles.summary}>
          <p>
            Mỗi thông báo là một dấu ghi bên lề hành trình đọc và xuất bản
            của bạn.
          </p>
          <strong aria-live="polite">{unread.toString().padStart(2, "0")}</strong>
          <span>chưa đọc</span>
          <button
            disabled={!unread || working}
            onClick={() => void markAllRead()}
            type="button"
          >
            Đánh dấu tất cả đã đọc
          </button>
        </div>

        <div className={styles.stream}>
          {error && <div className={styles.error} role="alert">{error}</div>}
          {loading && <p className={styles.empty}>Đang mở hộp thư…</p>}
          {!loading && !error && items.length === 0 && (
            <div className={styles.empty}>
              <strong>Chưa có dấu ghi mới.</strong>
              <span>Khi truyện bạn theo dõi cập nhật, thông báo sẽ xuất hiện ở đây.</span>
            </div>
          )}
          <ol className={styles.list}>
            {items.map((item) => (
              <li className={item.readAt ? styles.read : styles.unread} key={item.id}>
                <button
                  aria-label={item.readAt ? item.title : `Đánh dấu đã đọc: ${item.title}`}
                  disabled={Boolean(item.readAt) || working}
                  onClick={() => void markRead(item)}
                  type="button"
                >
                  <span className={styles.marker} aria-hidden="true" />
                  <span className={styles.copy}>
                    <small>{item.type.replaceAll("_", " ")} · {time(item.createdAt)}</small>
                    <strong>{item.title}</strong>
                    <span>{item.body}</span>
                  </span>
                  <span className={styles.state}>
                    {item.readAt ? "Đã đọc" : "Mới"}
                  </span>
                </button>
              </li>
            ))}
          </ol>
          {cursor && (
            <button
              className={styles.more}
              disabled={working}
              onClick={() => void loadMore()}
              type="button"
            >
              {working ? "Đang tải…" : "Mở trang cũ hơn"}
            </button>
          )}
        </div>
      </section>
    </main>
  );
}
