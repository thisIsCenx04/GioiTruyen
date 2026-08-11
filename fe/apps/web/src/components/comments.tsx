"use client";

import {
  createBrowserCommentClient,
  createBrowserReactionClient,
  createBrowserReportClient,
  StoryApiError,
  type CommunityComment,
  type CommentTargetType,
  type ReportReason,
} from "@gioitruyen/api-client";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";

import { loginHref } from "@/lib/auth";
import styles from "./comments.module.css";
import { API_BASE_URL, apiFetch } from "@/lib/api-base";

type Props = Readonly<{
  targetId: string;
  targetType: CommentTargetType;
}>;

function ReactionButton({ commentId }: Readonly<{ commentId: string }>) {
  const api = useMemo(() => createBrowserReactionClient({ baseUrl: API_BASE_URL, fetchImplementation: apiFetch }), []);
  const [reaction, setReaction] = useState<{
    active: boolean;
    count: number;
  } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    api.status("COMMENT", commentId)
      .then((value) => {
        if (active) setReaction(value);
      })
      .catch(() => undefined);
    return () => { active = false; };
  }, [api, commentId]);

  async function toggle() {
    if (!reaction || busy) return;
    setBusy(true);
    try {
      setReaction(reaction.active
        ? await api.remove("COMMENT", commentId)
        : await api.add("COMMENT", commentId));
    } finally {
      setBusy(false);
    }
  }

  if (!reaction) return null;
  return (
    <button
      aria-label={`${reaction.active ? "Bỏ thích" : "Thích"} · ${reaction.count}`}
      aria-pressed={reaction.active}
      disabled={busy}
      onClick={() => void toggle()}
      type="button"
    >
      ♥ {reaction.count}
    </button>
  );
}

export function Comments({ targetId, targetType }: Props) {
  // This client builds its base URL internally and takes no baseUrl option, so
  // apiFetch is what redirects it onto /api/v1.
  const api = useMemo(() => createBrowserCommentClient({ fetchImplementation: apiFetch }), []);
  const reports = useMemo(() => createBrowserReportClient({ baseUrl: API_BASE_URL, fetchImplementation: apiFetch }), []);
  const [items, setItems] = useState<readonly CommunityComment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [owned, setOwned] = useState<ReadonlySet<string>>(new Set());
  const [body, setBody] = useState("");
  const [replyTo, setReplyTo] = useState<CommunityComment | null>(null);
  const [editing, setEditing] = useState<CommunityComment | null>(null);
  const [editBody, setEditBody] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [reportTarget, setReportTarget] =
    useState<CommunityComment | null>(null);
  const [reportReason, setReportReason] =
    useState<ReportReason>("spam");
  const [reportDetail, setReportDetail] = useState("");

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

  async function submitReport() {
    if (!reportTarget || busy) return;
    setBusy(true);
    try {
      const report = await reports.create({
        ...(reportDetail.trim() ? { detail: reportDetail } : {}),
        reasonCode: reportReason,
        targetId: reportTarget.id,
        targetType: "comment",
      });
      setNotice(report.duplicate
        ? "Báo cáo này đã được tiếp nhận trước đó."
        : "Đã gửi báo cáo đến đội kiểm duyệt.");
      setReportTarget(null);
      setReportDetail("");
    } catch (error) {
      setNotice(error instanceof StoryApiError && error.problem.status === 401
        ? "LOGIN_REQUIRED"
        : error instanceof StoryApiError
          ? error.problem.detail ?? "Chưa thể gửi báo cáo."
          : "Chưa thể gửi báo cáo.");
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
          <Link to={loginHref()}>Đăng nhập</Link> để tham gia thảo luận.
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
                  <ReactionButton commentId={comment.id} />
                  {comment.depth < 2 && (
                    <button onClick={() => setReplyTo(comment)} type="button">
                      Trả lời
                    </button>
                  )}
                  <button
                    onClick={() => setReportTarget(comment)}
                    type="button"
                  >
                    Báo cáo
                  </button>
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
      {reportTarget && (
        <div aria-labelledby="report-title" aria-modal="true" className={styles.report} role="dialog">
          <div>
            <h3 id="report-title">Báo cáo bình luận</h3>
            <p>
              Chỉ gửi báo cáo khi nội dung vi phạm tiêu chuẩn cộng đồng.
            </p>
            <label htmlFor={`reason-${reportTarget.id}`}>Lý do</label>
            <select
              id={`reason-${reportTarget.id}`}
              onChange={(event) =>
                setReportReason(event.target.value as ReportReason)}
              value={reportReason}
            >
              <option value="spam">Spam hoặc quảng cáo</option>
              <option value="harassment">Quấy rối</option>
              <option value="impersonation">Mạo danh</option>
              <option value="sexual_content">Nội dung tình dục</option>
              <option value="illegal_content">Nội dung bất hợp pháp</option>
              <option value="copyright">Vi phạm bản quyền</option>
              <option value="other">Lý do khác</option>
            </select>
            <label htmlFor={`report-detail-${reportTarget.id}`}>
              Chi tiết (không bắt buộc)
            </label>
            <textarea
              id={`report-detail-${reportTarget.id}`}
              maxLength={5000}
              onChange={(event) => setReportDetail(event.target.value)}
              rows={4}
              value={reportDetail}
            />
            <footer>
              <button disabled={busy} onClick={() => void submitReport()} type="button">
                Gửi báo cáo
              </button>
              <button onClick={() => setReportTarget(null)} type="button">
                Hủy
              </button>
            </footer>
          </div>
        </div>
      )}
      {cursor && (
        <button className={styles.more} disabled={busy} onClick={() => void loadMore()} type="button">
          Xem thêm bình luận
        </button>
      )}
    </section>
  );
}
