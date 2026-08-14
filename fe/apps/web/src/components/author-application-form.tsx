"use client";

import { useEffect, useState, type FormEvent } from "react";

import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { isLoggedIn } from "@/lib/auth";

type Application = {
  id: string;
  teamName: string;
  penName: string | null;
  phoneNumber: string | null;
  facebookUrl: string | null;
  introduction: string;
  status: string;
  reviewNote: string | null;
  reviewedAt: string | null;
  createdAt: string;
};

const STATUS_LABELS: Record<string, string> = {
  APPROVED: "Đã được duyệt",
  PENDING: "Đang chờ duyệt",
  REJECTED: "Đã bị từ chối",
};

/**
 * Asking for permission to publish.
 *
 * <p>An approved application creates a team with the applicant as its owner.
 * A team may have one member or many, so this is the same mechanism used by
 * larger groups rather than a separate kind of account.
 */
export function AuthorApplicationForm() {
  const [application, setApplication] = useState<Application | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!isLoggedIn()) {
      setLoading(false);
      return;
    }
    void (async () => {
      try {
        const response = await authedFetch(`${API_BASE_URL}/author-applications/me`);
        if (response.ok) {
          const body = await response.text();
          setApplication(body ? (JSON.parse(body) as Application) : null);
        }
      } catch {
        // A missing application is the normal case; nothing to report.
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      const response = await authedFetch(`${API_BASE_URL}/author-applications`, {
        body: JSON.stringify({
          facebookUrl: String(form.get("facebookUrl") ?? "").trim(),
          introduction: String(form.get("introduction") ?? "").trim(),
          penName: String(form.get("penName") ?? "").trim(),
          phoneNumber: String(form.get("phoneNumber") ?? "").trim(),
          sampleWork: String(form.get("sampleWork") ?? "").trim(),
          teamName: String(form.get("teamName") ?? "").trim(),
        }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
        throw new Error(problem?.detail ?? "Không gửi được yêu cầu. Vui lòng thử lại.");
      }
      setApplication((await response.json()) as Application);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không gửi được yêu cầu.");
    } finally {
      setBusy(false);
    }
  }

  if (!isLoggedIn()) {
    return (
      <section className="authorApply">
        <h2>Đăng truyện lên Giới Truyện</h2>
        <p>Đăng nhập để gửi yêu cầu trở thành người đăng truyện.</p>
      </section>
    );
  }

  if (loading) {
    return <section className="authorApply"><p>Đang tải…</p></section>;
  }

  if (application && application.status !== "REJECTED") {
    return (
      <section className="authorApply">
        <h2>Yêu cầu đăng truyện</h2>
        <p className={application.status === "APPROVED" ? "authorApplyOk" : "authorApplyPending"}>
          {STATUS_LABELS[application.status] ?? application.status}
        </p>
        <dl className="authorApplyFacts">
          <div><dt>Tên nhóm</dt><dd>{application.teamName}</dd></div>
          {application.phoneNumber ? <div><dt>Số điện thoại</dt><dd>{application.phoneNumber}</dd></div> : null}
          {application.facebookUrl ? <div><dt>Facebook / Fanpage</dt><dd><a href={application.facebookUrl} rel="noreferrer" target="_blank">{application.facebookUrl}</a></dd></div> : null}
          {application.penName ? <div><dt>Bút danh</dt><dd>{application.penName}</dd></div> : null}
        </dl>
        {application.status === "APPROVED" ? (
          <p>Nhóm của bạn đã được tạo. Bạn có thể bắt đầu đăng truyện.</p>
        ) : (
          <p>Quản trị viên sẽ xem xét và phản hồi qua thông báo trong tài khoản của bạn.</p>
        )}
        {application.reviewNote ? <p className="authorApplyNote">Ghi chú: {application.reviewNote}</p> : null}
      </section>
    );
  }

  return (
    <section className="authorApply">
      <h2>Đăng ký đăng truyện</h2>
      {application?.status === "REJECTED" ? (
        <p className="authorApplyRejected">
          Yêu cầu trước đã bị từ chối{application.reviewNote ? `: ${application.reviewNote}` : "."}
          {" "}Bạn có thể gửi lại yêu cầu mới.
        </p>
      ) : (
        <p>
          Gửi yêu cầu để được cấp quyền đăng truyện. Sau khi duyệt, hệ thống tạo một nhóm
          do bạn làm chủ; nhóm có thể chỉ một mình bạn hoặc mời thêm thành viên sau.
        </p>
      )}

      <form className="authorApplyForm" onSubmit={submit}>
        <label>
          <span>Tên nhóm đăng truyện *</span>
          <input maxLength={160} name="teamName" placeholder="Ví dụ: Nhà Dịch Ánh Trăng" required />
        </label>
        <label>
          <span>Số điện thoại liên hệ *</span>
          <input maxLength={30} name="phoneNumber" placeholder="Ví dụ: 0912345678" required type="tel" />
        </label>
        <label>
          <span>Link Facebook / Fanpage *</span>
          <input maxLength={500} name="facebookUrl" placeholder="https://facebook.com/your.page" required type="url" />
        </label>
        <label>
          <span>Bút danh (không bắt buộc)</span>
          <input maxLength={120} name="penName" />
        </label>
        <label>
          <span>Giới thiệu về bạn và nội dung dự định đăng *</span>
          <textarea maxLength={2000} name="introduction" required rows={5} />
        </label>
        <label>
          <span>Link tác phẩm mẫu (không bắt buộc)</span>
          <input maxLength={2000} name="sampleWork" />
        </label>
        {error ? <p className="authorApplyError" role="alert">{error}</p> : null}
        <button disabled={busy} type="submit">{busy ? "Đang gửi…" : "Gửi yêu cầu"}</button>
      </form>
    </section>
  );
}
