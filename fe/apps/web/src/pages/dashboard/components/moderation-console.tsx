"use client";

import {
  createBrowserAuthClient,
  createBrowserModerationClient,
  StoryApiError,
  type ModerationCase,
  type ModerationDecision,
  type ModerationReviewDetail,
} from "@gioitruyen/api-client";
import { BrandMark, StatusPill } from "@gioitruyen/ui";
import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { authedFetch } from "@/lib/api-base";

function message(error: unknown) {
  if (error instanceof StoryApiError) {
    const known: Readonly<Record<string, string>> = {
      MODERATION_FORBIDDEN:
        "Tài khoản này không có quyền truy cập hàng kiểm duyệt.",
      MODERATION_REVIEW_NOT_FOUND:
        "Hồ sơ không còn tồn tại. Tải lại hàng đợi để tiếp tục.",
      REVIEW_CLAIM_CONFLICT:
        "Hồ sơ vừa được người khác nhận hoặc đã thay đổi. Hãy tải lại.",
      REVIEW_DECISION_CONFLICT:
        "Quyền giữ hồ sơ đã hết hạn hoặc quyết định đã được ghi nhận.",
    };
    return known[error.problem.code] ?? error.problem.detail ?? error.message;
  }
  return "Không thể kết nối máy chủ kiểm duyệt.";
}

function time(value: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

export function ModerationConsole() {
  const api = useMemo(() => createBrowserModerationClient({ fetchImplementation: authedFetch }), []);
  const auth = useMemo(() => createBrowserAuthClient({ baseUrl: "/api/v1" }), []);
  const [cases, setCases] = useState<ModerationCase[]>([]);
  const [detail, setDetail] = useState<ModerationReviewDetail | null>(null);
  const [chapterIndex, setChapterIndex] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [audit, setAudit] = useState<ModerationDecision | null>(null);
  const [authRequired, setAuthRequired] = useState(false);

  const open = useCallback(
    async (review: ModerationCase) => {
      setError("");
      setAudit(null);
      setChapterIndex(0);
      try {
        setDetail(await api.detail(review.id));
      } catch (requestError) {
        setError(message(requestError));
      }
    },
    [api],
  );

  const load = useCallback(async () => {
    try {
      const page = await api.list();
      setCases(page.items);
      setCursor(page.nextCursor);
      if (page.items[0]) await open(page.items[0]);
    } catch (requestError) {
      if (
        requestError instanceof StoryApiError &&
        requestError.problem.status === 401
      ) {
        setAuthRequired(true);
      }
      setError(message(requestError));
    } finally {
      setLoading(false);
    }
  }, [api, open]);

  useEffect(() => {
    const bootstrap = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(bootstrap);
  }, [load]);

  async function loadMore() {
    if (!cursor) return;
    try {
      const page = await api.list(cursor);
      setCases((current) => [...current, ...page.items]);
      setCursor(page.nextCursor);
    } catch (requestError) {
      setError(message(requestError));
    }
  }

  async function claim() {
    if (!detail) return;
    setWorking(true);
    try {
      const claimed = await api.claim(
        detail.review.id,
        detail.review.version,
      );
      setDetail({ ...detail, review: claimed });
      setCases((current) =>
        current.map((item) => (item.id === claimed.id ? claimed : item)),
      );
      setError("");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setWorking(false);
    }
  }

  async function decide(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!detail) return;
    const values = new FormData(event.currentTarget);
    const decision = String(values.get("decision")) as
      | "APPROVE"
      | "REQUEST_CHANGES"
      | "REJECT";
    const note = String(values.get("note")).trim();
    if (decision !== "APPROVE" && !note) {
      setError("Yêu cầu sửa hoặc từ chối phải có ghi chú cụ thể.");
      return;
    }
    setWorking(true);
    try {
      const result = await api.decide(
        detail.review.id,
        detail.review.version,
        {
          decision,
          evidenceRefs: detail.chapters.map((chapter) => chapter.revisionId),
          note: note || null,
          policyVersion: String(values.get("policyVersion")).trim(),
          reasonCode: String(values.get("reasonCode")).trim().toUpperCase(),
        },
      );
      setAudit(result);
      setCases((current) =>
        current.filter((item) => item.id !== result.reviewId),
      );
      setError("");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setWorking(false);
    }
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setWorking(true);
    try {
      await auth.login(
        String(values.get("email")),
        String(values.get("password")),
        String(values.get("mfaCode")) || undefined,
      );
      setAuthRequired(false);
      setLoading(true);
      setError("");
      await load();
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setWorking(false);
    }
  }

  if (loading) {
    return (
      <main className="consoleLoading">
        <BrandMark />
        <p>Đang mở hồ sơ kiểm duyệt…</p>
      </main>
    );
  }

  if (authRequired) {
    return (
      <main className="adminLogin">
        <section>
          <BrandMark />
          <p>Vùng vận hành có kiểm soát</p>
          <h1>Xác minh người kiểm duyệt</h1>
          {error && <div className="consoleError" role="alert">{error}</div>}
          <form onSubmit={login}>
            <label>
              Email
              <input autoComplete="username" name="email" required type="email" />
            </label>
            <label>
              Mật khẩu
              <input
                autoComplete="current-password"
                name="password"
                required
                type="password"
              />
            </label>
            <label>
              Mã MFA (nếu tài khoản yêu cầu)
              <input
                autoComplete="one-time-code"
                inputMode="numeric"
                name="mfaCode"
              />
            </label>
            <button disabled={working} type="submit">
              Mở bàn kiểm duyệt
            </button>
          </form>
          <small>API sẽ kiểm tra capability Moderator/Admin ở từng thao tác.</small>
        </section>
      </main>
    );
  }

  const chapter = detail?.chapters[chapterIndex] ?? null;

  return (
    <main className="reviewShell">
      <aside className="reviewRail">
        <BrandMark inverse />
        <div className="railTitle">
          <span>Hàng kiểm duyệt</span>
          <strong>{cases.length.toString().padStart(2, "0")} hồ sơ mở</strong>
        </div>
        <nav aria-label="Hồ sơ chờ kiểm duyệt">
          {cases.map((review) => (
            <button
              aria-current={review.id === detail?.review.id ? "page" : undefined}
              key={review.id}
              onClick={() => void open(review)}
              type="button"
            >
              <span className={`priority p${Math.ceil(review.priority / 25)}`}>
                P{review.priority}
              </span>
              <strong>{review.targetId.slice(0, 13)}</strong>
              <small>{time(review.submittedAt)}</small>
            </button>
          ))}
        </nav>
        {cursor && (
          <button className="loadMore" onClick={() => void loadMore()} type="button">
            Tải thêm hồ sơ
          </button>
        )}
        <a className="financeLink" href="/finance">
          Mở bàn kiểm soát tài chính
        </a>
        <div className="operator">
          <span>GT</span>
          <div>
            <strong>Moderator console</strong>
            <small>Quyền được xác minh bởi API</small>
          </div>
        </div>
      </aside>

      <section className="reviewDesk">
        <header className="reviewHeader">
          <div>
            <p>Niêm phong revision · kiểm duyệt xuất bản</p>
            <h1>{detail?.story.title ?? "Không có hồ sơ mở"}</h1>
          </div>
          {detail && (
            <div className="lease">
              <span>Trạng thái</span>
              <StatusPill
                tone={detail.review.state === "CLAIMED" ? "active" : "attention"}
              >
                {detail.review.state === "CLAIMED" ? "Đang giữ" : "Chờ nhận"}
              </StatusPill>
              <small>v{detail.review.version}</small>
            </div>
          )}
        </header>

        {error && (
          <div className="consoleError" role="alert">
            <span>{error}</span>
            <button onClick={() => void load()} type="button">
              Tải lại
            </button>
          </div>
        )}

        {detail ? (
          <div className="evidenceLayout">
            <section className="evidence">
              <div className="sealLine" aria-label="Chuỗi chứng cứ">
                <span>Case {detail.review.id.slice(0, 8)}</span>
                <i />
                <span>Story r{detail.story.revisionNo}</span>
                <i />
                <span>{detail.chapters.length} chapter revision</span>
              </div>
              <article className="storyEvidence">
                <div className="evidenceLabel">
                  <span>Hồ sơ truyện đã đóng băng</span>
                  <code>{detail.story.checksum.slice(0, 12)}</code>
                </div>
                <h2>{detail.story.title}</h2>
                <p>{detail.story.synopsis}</p>
                <dl>
                  <div><dt>Nguồn</dt><dd>{detail.story.origin}</dd></div>
                  <div><dt>Ngôn ngữ</dt><dd>{detail.story.language}</dd></div>
                  <div><dt>Revision</dt><dd>{detail.story.revisionNo}</dd></div>
                  <div><dt>Nhóm</dt><dd>{detail.review.teamId.slice(0, 13)}</dd></div>
                </dl>
              </article>

              <div className="chapterTabs" role="tablist" aria-label="Revision chương">
                {detail.chapters.map((item, index) => (
                  <button
                    aria-selected={index === chapterIndex}
                    key={item.revisionId}
                    onClick={() => setChapterIndex(index)}
                    role="tab"
                    type="button"
                  >
                    Ch. {item.number} <small>r{item.revisionNo}</small>
                  </button>
                ))}
              </div>
              {chapter && (
                <article className="chapterEvidence" role="tabpanel">
                  <div className="evidenceLabel">
                    <span>Revision {chapter.revisionId.slice(0, 13)}</span>
                    <code>{chapter.checksum.slice(0, 12)}</code>
                  </div>
                  <pre>{chapter.plainText}</pre>
                </article>
              )}
            </section>

            <aside className="decisionPanel">
              <section>
                <div className="panelHeading">
                  <span>Precheck</span>
                  <strong>{detail.review.checks.length} kiểm tra</strong>
                </div>
                <ul className="checkList">
                  {detail.review.checks.map((check) => (
                    <li key={`${check.rule}-${check.code}`}>
                      <i className={check.outcome.toLowerCase()} />
                      <span>
                        <strong>{check.rule}</strong>
                        <small>{check.code}</small>
                      </span>
                      <b>{check.outcome}</b>
                    </li>
                  ))}
                </ul>
              </section>

              {detail.review.state === "OPEN" && !audit && (
                <button
                  className="claimButton"
                  disabled={working}
                  onClick={() => void claim()}
                  type="button"
                >
                  Nhận hồ sơ trong 15 phút
                </button>
              )}

              {detail.review.state === "CLAIMED" && !audit && (
                <form className="decisionForm" onSubmit={decide}>
                  <fieldset>
                    <legend>Quyết định</legend>
                    <label><input defaultChecked name="decision" type="radio" value="APPROVE" />Duyệt</label>
                    <label><input name="decision" type="radio" value="REQUEST_CHANGES" />Yêu cầu sửa</label>
                    <label><input name="decision" type="radio" value="REJECT" />Từ chối</label>
                  </fieldset>
                  <label>
                    Mã lý do
                    <input defaultValue="CONTENT_ACCEPTED" name="reasonCode" pattern="[A-Z][A-Z0-9_]{2,63}" required />
                  </label>
                  <label>
                    Phiên bản chính sách
                    <input defaultValue="publishing-policy-v1" name="policyVersion" required />
                  </label>
                  <label>
                    Ghi chú cho nhóm xuất bản
                    <textarea maxLength={2000} name="note" rows={5} />
                  </label>
                  <button disabled={working} type="submit">
                    Ghi quyết định và audit
                  </button>
                </form>
              )}

              {audit && (
                <div className="auditReceipt" role="status">
                  <span>Audit đã ghi bất biến</span>
                  <strong>{audit.decision}</strong>
                  <code>{audit.reviewId.slice(0, 13)} · v{audit.version}</code>
                  <small>{time(audit.decidedAt)}</small>
                </div>
              )}
            </aside>
          </div>
        ) : (
          <div className="emptyReview">
            <strong>Hàng kiểm duyệt đã sạch.</strong>
            <span>Hồ sơ mới sẽ xuất hiện theo thứ tự ưu tiên và SLA.</span>
          </div>
        )}
      </section>
    </main>
  );
}
