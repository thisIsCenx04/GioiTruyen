"use client";

import {
  createBrowserReportClient,
  StoryApiError,
  type ReportReason,
} from "@gioitruyen/api-client";
import { CircleAlert, X } from "lucide-react";
import { Link } from "react-router-dom";
import { useMemo, useState } from "react";

import { loginHref } from "@/lib/auth";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

const reasons: ReadonlyArray<Readonly<{ value: ReportReason; label: string }>> = [
  { value: "broken_content", label: "Nội dung bị lỗi hoặc thiếu" },
  { value: "copyright", label: "Vi phạm bản quyền" },
  { value: "spam", label: "Nội dung rác" },
  { value: "illegal_content", label: "Nội dung không phù hợp" },
  { value: "other", label: "Lý do khác" },
];

export function StoryReportButton({
  targetId,
  targetType = "story",
}: Readonly<{
  targetId: string;
  targetType?: "story" | "chapter";
}>) {
  const api = useMemo(() => createBrowserReportClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }), []);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>("broken_content");
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [requiresLogin, setRequiresLogin] = useState(false);

  async function submit() {
    if (busy) return;
    setBusy(true);
    setMessage("");
    try {
      const report = await api.create({
        ...(detail.trim() ? { detail: detail.trim() } : {}),
        reasonCode: reason,
        targetId,
        targetType,
      });
      setMessage(report.duplicate
        ? "Báo cáo này đã được hệ thống tiếp nhận trước đó."
        : "Báo cáo đã được chuyển đến đội kiểm duyệt.");
    } catch (error) {
      if (error instanceof StoryApiError && error.problem.status === 401) {
        setRequiresLogin(true);
      } else {
        setMessage(error instanceof StoryApiError
          ? error.problem.detail ?? "Chưa thể gửi báo cáo."
          : "Chưa thể kết nối máy chủ.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button className="storyAction storyActionReport" onClick={() => setOpen(true)} type="button">
        <CircleAlert aria-hidden="true" /> Báo lỗi
      </button>
      {open && (
        <div aria-labelledby="story-report-title" aria-modal="true" className="storyReportOverlay" role="dialog">
          <section className="storyReportDialog">
            <header>
              <div>
                <p>Gửi đến đội kiểm duyệt</p>
                <h2 id="story-report-title">Báo lỗi nội dung</h2>
              </div>
              <button aria-label="Đóng" onClick={() => setOpen(false)} type="button"><X /></button>
            </header>
            {requiresLogin ? (
              <div className="storyReportNotice">
                <p>Bạn cần đăng nhập để gửi báo cáo.</p>
                <Link to={loginHref()}>Đăng nhập</Link>
              </div>
            ) : message ? (
              <div className="storyReportNotice" role="status">
                <p>{message}</p>
                <button onClick={() => setOpen(false)} type="button">Đóng</button>
              </div>
            ) : (
              <form onSubmit={(event) => { event.preventDefault(); void submit(); }}>
                <label>
                  <span>Lý do</span>
                  <select onChange={(event) => setReason(event.target.value as ReportReason)} value={reason}>
                    {reasons.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                  </select>
                </label>
                <label>
                  <span>Mô tả thêm</span>
                  <textarea maxLength={1000} onChange={(event) => setDetail(event.target.value)} rows={4} value={detail} />
                </label>
                <button disabled={busy} type="submit">{busy ? "Đang gửi..." : "Gửi báo cáo"}</button>
              </form>
            )}
          </section>
        </div>
      )}
    </>
  );
}
