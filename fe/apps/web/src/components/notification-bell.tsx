"use client";

import { Bell, Check, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { isLoggedIn } from "@/lib/auth";

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

function timeAgo(value: string) {
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return "";
  const seconds = Math.floor((Date.now() - then) / 1000);
  if (seconds < 60) return "vừa xong";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} phút trước`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} giờ trước`;
  if (seconds < 604800) return `${Math.floor(seconds / 86400)} ngày trước`;
  return new Date(value).toLocaleDateString("vi-VN");
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
                items.map((item) => (
                  <button
                    className={item.readAt ? "notifyItem" : "notifyItem isUnread"}
                    key={item.id}
                    onClick={() => void openItem(item)}
                    type="button"
                  >
                    <span className="notifyDot" aria-hidden="true" />
                    <span className="notifyItemBody">
                      <strong>{item.title}</strong>
                      <span>{item.body}</span>
                      <small>{timeAgo(item.createdAt)}</small>
                    </span>
                  </button>
                ))
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
