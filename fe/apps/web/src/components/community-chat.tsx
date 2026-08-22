"use client";

import { Send } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { loginHref } from "@/lib/auth";

/**
 * The community chat panel.
 *
 * <p>This used to be theatre: three invented readers were seeded into
 * localStorage on first visit, shown under a "Trực tiếp" badge, and anything
 * typed was posted to /community/messages - a route that did not exist, whose
 * failure was swallowed. Every visitor saw a private conversation that had
 * never happened, and nothing anyone wrote reached another person.
 *
 * <p>It now reads and writes the real room. An empty room says so.
 */

export interface CommunityMessage {
  id: string;
  userId: string;
  userName: string;
  userAvatarUrl: string | null;
  userRole?: string;
  content: string;
  createdAt: string;
}

const MAX_LENGTH = 500;
/** Cheap polling: the panel is a side attraction, not a messenger. */
const REFRESH_MS = 20_000;

/** "5 phút trước" - relative time, computed rather than stored as a label. */
function relativeTime(iso: string): string {
  const at = Date.parse(iso);
  if (Number.isNaN(at)) return "";
  const seconds = Math.max(0, Math.round((Date.now() - at) / 1000));
  if (seconds < 60) return "Vừa xong";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days} ngày trước`;
  return new Date(at).toLocaleDateString("vi-VN");
}

/** A stable tone per person, so the same reader keeps the same colour. */
const TONES = ["indigo", "cyan", "gold", "blue"] as const;
function tone(userId: string): string {
  let sum = 0;
  for (let index = 0; index < userId.length; index += 1) sum += userId.charCodeAt(index);
  return TONES[sum % TONES.length] ?? "indigo";
}

export function CommunityChat({
  channel = "main",
  compact = false,
  title,
}: Readonly<{ channel?: "main" | "zhihu"; compact?: boolean; title?: string }>) {
  const [messages, setMessages] = useState<CommunityMessage[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const listRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setIsLoggedIn(
      Boolean(localStorage.getItem("access_token") || localStorage.getItem("gioitruyen_token")),
    );
  }, []);

  const load = useCallback(async () => {
    try {
      const response = await authedFetch(`${API_BASE_URL}/community/messages?room=${channel}`);
      if (!response.ok) {
        setState("error");
        return;
      }
      setMessages((await response.json()) as CommunityMessage[]);
      setState("ready");
    } catch {
      setState("error");
    }
  }, [channel]);

  useEffect(() => {
    setState("loading");
    void load();
    const timer = window.setInterval(() => void load(), REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  // Newest message at the bottom, so the panel opens on the latest word.
  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages]);

  async function handleSend(event: FormEvent) {
    event.preventDefault();
    const content = input.trim();
    if (!content || sending) return;

    setSending(true);
    setError(null);
    try {
      const response = await authedFetch(`${API_BASE_URL}/community/messages`, {
        body: JSON.stringify({ content, room: channel }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        const detail = (await response.json().catch(() => null)) as { message?: string } | null;
        setError(detail?.message ?? "Không gửi được tin nhắn. Vui lòng thử lại.");
        return;
      }
      const posted = (await response.json()) as CommunityMessage;
      setMessages((current) => [...current, posted]);
      setInput("");
    } catch {
      setError("Mất kết nối tới máy chủ. Vui lòng thử lại.");
    } finally {
      setSending(false);
    }
  }

  const remaining = MAX_LENGTH - input.length;

  return (
    <section className={`communityChatWidget ${compact ? "compactChat" : "fullChat"}`}>
      <header className="chatHeader">
        <div>
          <h3>{title ?? "Cộng đồng Giới Truyện"}</h3>
        </div>
        {state === "ready" && messages.length > 0 ? (
          <span className="chatCount">{messages.length} tin nhắn gần đây</span>
        ) : null}
      </header>

      <div className="chatMessageList" ref={listRef}>
        {state === "loading" ? <p className="chatNotice">Đang tải thảo luận…</p> : null}

        {state === "error" ? (
          <p className="chatNotice">
            Không tải được khu thảo luận.{" "}
            <button onClick={() => void load()} type="button">
              Thử lại
            </button>
          </p>
        ) : null}

        {state === "ready" && messages.length === 0 ? (
          <p className="chatNotice">
            Chưa có tin nhắn nào. Hãy là người mở lời đầu tiên.
          </p>
        ) : null}

        {messages.map((message) => (
          <div className="chatMessageItem" key={message.id}>
            <div className={`chatAvatar avatarTone-${tone(message.userId)}`}>
              {message.userAvatarUrl ? (
                <img alt="" aria-hidden="true" loading="lazy" src={message.userAvatarUrl} />
              ) : (
                message.userName.slice(0, 1).toUpperCase()
              )}
            </div>
            <div className="chatMessageBody">
              <div className="chatAuthorRow">
                <strong className="chatAuthorName">{message.userName}</strong>
                {message.userRole === "ADMIN" && <span className="roleBadge adminRole">Admin</span>}
                {message.userRole === "TEAM" && <span className="roleBadge teamRole">Team</span>}
                <span className="chatTime">{relativeTime(message.createdAt)}</span>
              </div>
              <p className="chatText">{message.content}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="chatFooter">
        {isLoggedIn ? (
          <form className="chatForm" onSubmit={handleSend}>
            <input
              className="chatInput"
              maxLength={MAX_LENGTH}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Nhập tin nhắn thảo luận…"
              type="text"
              value={input}
            />
            <button
              aria-label="Gửi tin nhắn"
              className="chatSendBtn"
              disabled={sending || input.trim().length === 0}
              type="submit"
            >
              <Send aria-hidden="true" />
            </button>
          </form>
        ) : (
          <div className="chatLoginNotice">
            <span>Đăng nhập để tham gia thảo luận</span>
            <Link className="chatLoginBtn" to={loginHref()}>
              Đăng nhập ngay
            </Link>
          </div>
        )}

        {error ? (
          <p className="chatError" role="alert">
            {error}
          </p>
        ) : null}

        {isLoggedIn && remaining < 80 ? (
          <p className="chatHint">Còn {remaining} ký tự</p>
        ) : null}
      </div>
    </section>
  );
}
