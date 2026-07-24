"use client";

import {
  createBrowserCommentClient,
  StoryApiError,
  type CommunityComment,
  type CommentTargetType,
} from "@gioitruyen/api-client";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";

import styles from "./comments.module.css";

type Props = Readonly<{
  targetId: string;
  targetType: CommentTargetType;
}>;

export function Comments({ targetId, targetType }: Props) {
  const api = useMemo(() => createBrowserCommentClient(), []);
  const [items, setItems] = useState<readonly CommunityComment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [owned, setOwned] = useState<ReadonlySet<string>>(new Set());
  const [body, setBody] = useState("");
  const [replyTo, setReplyTo] = useState<CommunityComment | null>(null);
  const [editing, setEditing] = useState<CommunityComment | null>(null);
  const [editBody, setEditBody] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    api.list(targetType, targetId).then((page) => {
      if (active) {
        setItems(page.items);
        setCursor(page.nextCursor);
      }
    }).catch(() => {
      if (active) setNotice("Chưa thể tải bình luận.");
    });
    return () => { active = false; };
  }, [api, targetId, targetType]);

  async function submit() {
    if (!body.trim() || busy) return;
    setBusy(true);
    setNotice("");
    try {
      const comment = await api.create({
        body,
        ...(replyTo ? { parentId: replyTo.id } : {}),
        targetId,
        targetType,
      });
      setItems((current) => [comment, ...current]);
      setOwned((current) => new Set(current).add(comment.id));
      setBody("");
      setReplyTo(null);
    } catch (error) {
      setNotice(error instanceof StoryApiError && error.problem.status === 401
        ? "LOGIN_REQUIRED"
        : error instanceof StoryApiError
          ? error.problem.detail ?? "Bình luận chưa được gửi."
          : "Bình luận chưa được gửi.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(comment: CommunityComment) {
    if (busy) return;
    setBusy(true);
    try {
      const next = await api.remove(comment.id, comment.version);
      setItems((current) => current.map((value) =>
        value.id === next.id ? next : value));
    } catch {
      setNotice("Bình luận đã thay đổi, hãy tải lại trang.");
    } finally {
      setBusy(false);
    }
  }

  async function saveEdit() {
    if (!editing || !editBody.trim() || busy) return;
    setBusy(true);
    try {
      const next = await api.update(
        editing.id,
        editing.version,
        editBody,
      );
      setItems((current) => current.map((value) =>
        value.id === next.id ? next : value));
      setEditing(null);
      setEditBody("");
    } catch {
      setNotice("Bình luận đã thay đổi, hãy tải lại trang.");
    } finally {
      setBusy(false);
    }
  }

  async function loadMore() {
    if (!cursor || busy) return;
    setBusy(true);
    try {
      const page = await api.list(targetType, targetId, cursor);
      setItems((current) => [...current, ...page.items]);
      setCursor(page.nextCursor);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={styles.comments} aria-labelledby="comments-title">
      <header>
        <p>Góc độc giả</p>
        <h2 id="comments-title">Bình luận</h2>
      </header>
      <div className={styles.composer}>
        {replyTo && (
          <p>
            Đang trả lời {replyTo.author.displayName}
            <button onClick={() => setReplyTo(null)} type="button">Hủy</button>
          </p>
        )}
        <label htmlFor={`comment-${targetId}`}>Chia sẻ cảm nhận</label>
        <textarea
          id={`comment-${targetId}`}
          maxLength={5000}
          onChange={(event) => setBody(event.target.value)}
          placeholder="Viết điều tử tế và liên quan đến truyện…"
          rows={4}
          value={body}
        />
        <div>
          <small>{body.length}/5000</small>
          <button disabled={busy || !body.trim()} onClick={() => void submit()} type="button">
            Gửi bình luận
          </button>
        </div>
      </div>
      {notice === "LOGIN_REQUIRED" ? (
        <p className={styles.notice}>
          <Link href="/auth/login">Đăng nhập</Link> để tham gia thảo luận.
        </p>
      ) : notice ? <p className={styles.notice} role="status">{notice}</p> : null}
      <ol className={styles.list}>
        {items.map((comment) => (
          <li
            className={comment.status === "DELETED" ? styles.deleted : ""}
            key={comment.id}
            style={{ "--comment-depth": comment.depth } as CSSProperties}
          >
            <div aria-hidden="true" className={styles.avatar}>
              {comment.author.displayName.slice(0, 1).toUpperCase()}
            </div>
            <article>
              <header>
                <strong>{comment.author.displayName}</strong>
                <time dateTime={comment.createdAt}>
                  {new Date(comment.createdAt).toLocaleDateString("vi-VN")}
                </time>
              </header>
              {editing?.id === comment.id ? (
                <div className={styles.editor}>
                  <label htmlFor={`edit-${comment.id}`}>Sửa bình luận</label>
                  <textarea
                    id={`edit-${comment.id}`}
                    maxLength={5000}
                    onChange={(event) => setEditBody(event.target.value)}
                    rows={3}
                    value={editBody}
                  />
                  <div>
                    <button
                      disabled={busy || !editBody.trim()}
                      onClick={() => void saveEdit()}
                      type="button"
                    >
                      Lưu
                    </button>
                    <button
                      onClick={() => setEditing(null)}
                      type="button"
                    >
                      Hủy
                    </button>
                  </div>
                </div>
              ) : <p>{comment.body}</p>}
              {comment.status !== "DELETED" && (
                <footer>
                  {comment.depth < 2 && (
                    <button onClick={() => setReplyTo(comment)} type="button">
                      Trả lời
                    </button>
                  )}
                  {owned.has(comment.id) && (
                    <>
                      <button onClick={() => {
                        setEditing(comment);
                        setEditBody(comment.body);
                      }} type="button">
                        Sửa
                      </button>
                      <button onClick={() => void remove(comment)} type="button">
                        Xóa
                      </button>
                    </>
                  )}
                </footer>
              )}
            </article>
          </li>
        ))}
      </ol>
      {cursor && (
        <button className={styles.more} disabled={busy} onClick={() => void loadMore()} type="button">
          Xem thêm bình luận
        </button>
      )}
    </section>
  );
}
