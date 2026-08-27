"use client";

import { Bell, Check, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { isLoggedIn } from "@/lib/auth";
import { parseServerInstant, siteTimeParts } from "@/lib/datetime";

type NotificationItem = {
  id: string;
  type: string;
  title: string;
  body: string;
  data: Record<string, string>;
  readAt: string | null;
  createdAt: string;
};

type NotificationPage = {
  items: NotificationItem[];
  nextCursor: string | null;
  hasMore: boolean;
  unreadCount: number;
};

/** How often the bell asks whether anything new arrived. */
const POLL_INTERVAL_MS = 30_000;

/** How long a toast for a new message stays on screen. */
const TOAST_DURATION_MS = 6_000;

/**
 * Mốc thời gian của một thông báo, đọc theo giờ Việt Nam.
 *
 * <p>Luật đọc/hiển thị nằm ở {@link parseServerInstant} và {@link
 * siteTimeParts}: chuỗi không mang múi giờ là UTC, và mọi thứ hiện ra màn hình
 * theo giờ Việt Nam. Trước đây chỗ này tự gọi `new Date()` rồi `getHours()`,
 * nên một thông báo vừa gửi xong ở Việt Nam hiện thành 01:13 kèm dòng "7 giờ
 * trước".
 */
export function timeAgoFormatted(value: string) {
  const date = parseServerInstant(value);
  if (!date) return { relative: "vừa xong", exact: "" };
  const then = date.getTime();

  const { day, hour, minute, month, year } = siteTimeParts(date);
  const exact = `${hour}:${minute} - ${day}/${month}/${year}`;

  const seconds = Math.max(0, Math.floor((Date.now() - then) / 1000));
  if (seconds < 60) return { relative: "vừa xong", exact };
  if (seconds < 3600) return { relative: `${Math.floor(seconds / 60)} phút trước`, exact };
  if (seconds < 86400) return { relative: `${Math.floor(seconds / 3600)} giờ trước`, exact };

  const days = Math.floor(seconds / 86400);
  return { relative: `${days} ngày trước`, exact };
}

/**
 * The bell in the header: an unread count, a dropdown, and a toast when
 * something arrives while the reader is on the page.
 *
 * <p>Polling rather than a live connection: messages here are approvals and
 * rewards, not chat, so half a minute of delay costs nothing and a websocket
 * would be one more thing to keep running.
 */
export function NotificationBell() {
  const navigate = useNavigate();
  const signedIn = isLoggedIn();

  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState<NotificationItem | null>(null);

  // Ids already seen, so a toast fires only for genuinely new arrivals and
  // not on every poll. Null means "first load" - nothing is new yet.
  const seen = useRef<Set<string> | null>(null);
  const panel = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    if (!signedIn) return;
    try {
      const response = await authedFetch(`${API_BASE_URL}/notifications?limit=10`);
      if (!response.ok) return;
      const page = (await response.json()) as NotificationPage;

      setItems(page.items);
      setUnread(page.unreadCount);

      if (seen.current === null) {
        // First load: remember what is there without announcing any of it.
        seen.current = new Set(page.items.map((item) => item.id));
        return;
      }
      const fresh = page.items.find((item) => !seen.current!.has(item.id) && !item.readAt);
      page.items.forEach((item) => seen.current!.add(item.id));
      if (fresh) setToast(fresh);
    } catch {
      // A dropped poll is not worth telling the reader about; the next one
      // will pick the messages up.
    }
  }, [signedIn]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!signedIn) return undefined;
    const tick = () => {
      if (document.visibilityState !== "visible") return;
      void load();
    };
    const timer = window.setInterval(tick, POLL_INTERVAL_MS);
    document.addEventListener("visibilitychange", tick);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", tick);
    };
  }, [load, signedIn]);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), TOAST_DURATION_MS);
    return () => window.clearTimeout(timer);
  }, [toast]);

  // Clicking anywhere else closes the dropdown, as a menu should.
  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (event: MouseEvent) => {
      if (panel.current && !panel.current.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  async function markAllRead() {
    if (!unread || busy) return;
    setBusy(true);
    try {
      const response = await authedFetch(`${API_BASE_URL}/notifications/read-all`, { method: "POST" });
      if (response.ok) {
        const now = new Date().toISOString();
        setItems((current) => current.map((item) => ({ ...item, readAt: item.readAt ?? now })));
        setUnread(0);
      }
    } catch {
      // Leave the badge alone; the next poll reports the true count.
    } finally {
      setBusy(false);
    }
  }

  async function openItem(item: NotificationItem) {
    setOpen(false);
    if (!item.readAt) {
      setUnread((current) => Math.max(0, current - 1));
      setItems((current) => current.map((entry) =>
        entry.id === item.id ? { ...entry, readAt: new Date().toISOString() } : entry));
      void authedFetch(`${API_BASE_URL}/notifications/${item.id}/read`, { method: "POST" })
        .catch(() => undefined);
    }
    const target = item.data?.targetUrl;
    if (target) navigate(target as string);
  }

  // A guest has no inbox, so the bell simply points at the login page.
  if (!signedIn) {
    return (
      <Link aria-label="Thông báo" className="headerIcon notifyIcon" to={"/login" as string}>
        <Bell aria-hidden="true" />
      </Link>
    );
  }

  return (
    <>
      <div className="notifyWrap" ref={panel}>
        <button
          aria-expanded={open}
          aria-label={unread > 0 ? `Thông báo (${unread} chưa đọc)` : "Thông báo"}
          className="headerIcon notifyIcon"
          onClick={() => setOpen((value) => !value)}
          type="button"
        >
          <Bell aria-hidden="true" />
          {unread > 0 ? (
            <span className="notifyBadge">{unread > 99 ? "99+" : unread}</span>
          ) : null}
        </button>

        {open ? (
          <div className="notifyPanel" role="dialog">
            <header className="notifyPanelHead">
              <strong>Thông báo</strong>
              <label className="notifyMarkAll">
                <input
                  checked={unread === 0}
                  disabled={busy || unread === 0}
                  onChange={() => void markAllRead()}
                  type="checkbox"
                />
                <span>Đã đọc tất cả</span>
              </label>
            </header>

            <div className="notifyList">
              {items.length === 0 ? (
                <p className="notifyEmpty">Chưa có thông báo nào.</p>
              ) : (
                items.map((item) => {
                  const { relative, exact } = timeAgoFormatted(item.createdAt);
                  return (
                    <button
                      className={item.readAt ? "notifyItem" : "notifyItem isUnread"}
                      key={item.id}
                      onClick={() => void openItem(item)}
                      type="button"
                    >
                      <span className="notifyDot" aria-hidden="true" />
                      <span className="notifyItemBody" style={{ display: "flex", flexDirection: "column", gap: "0.2rem", width: "100%" }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%", gap: "0.5rem", marginBottom: "0.1rem" }}>
                          <span style={{ fontSize: "0.75rem", color: "#64748b", fontWeight: 600 }}>
                            {exact}
                          </span>
                          <span style={{ fontSize: "0.72rem", color: "#1d4ed8", background: "#eff6ff", padding: "0.1rem 0.45rem", borderRadius: "4px", fontWeight: 750, flexShrink: 0 }}>
                            {relative}
                          </span>
                        </div>
                        <strong style={{ fontSize: "0.92rem", fontWeight: 800, color: "#0f172a", textAlign: "left" }}>{item.title}</strong>
                        <span style={{ color: "#475569", fontSize: "0.84rem", lineHeight: 1.4, textAlign: "left" }}>{item.body}</span>
                      </span>
                    </button>
                  );
                })
              )}
            </div>

            <footer className="notifyPanelFoot">
              <Link onClick={() => setOpen(false)} to={"/notifications" as string}>
                Xem tất cả thông báo
              </Link>
            </footer>
          </div>
        ) : null}
      </div>

      {toast ? (
        <div className="notifyToast" role="status">
          <span className="notifyToastIcon" aria-hidden="true"><Check size={16} /></span>
          <button
            className="notifyToastBody"
            onClick={() => { const item = toast; setToast(null); void openItem(item); }}
            type="button"
          >
            <strong>{toast.title}</strong>
            <span>{toast.body}</span>
          </button>
          <button aria-label="Đóng" className="notifyToastClose" onClick={() => setToast(null)} type="button">
            <X size={15} />
          </button>
        </div>
      ) : null}
    </>
  );
}
