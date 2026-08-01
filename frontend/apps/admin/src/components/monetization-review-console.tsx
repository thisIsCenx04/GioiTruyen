"use client";

import {
  createBrowserAdminMonetizationClient,
  createBrowserAuthClient,
  StoryApiError,
  type MonetizationKillSwitch,
} from "@gioitruyen/api-client";
import { BrandMark } from "@gioitruyen/ui";
import Link from "next/link";
import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";

import styles from "./monetization-review-console.module.css";

type ReviewKind = "TOPUP" | "WITHDRAWAL";
type Decision = "APPROVE" | "REJECT";

function failure(error: unknown) {
  if (error instanceof StoryApiError) {
    const known: Readonly<Record<number, string>> = {
      401: "Phiên vận hành đã hết hạn. Đăng nhập lại để tiếp tục.",
      403: "Tài khoản không có capability FINANCE_REVIEW.",
      404: "Không tìm thấy hồ sơ hoặc hồ sơ không còn chờ duyệt.",
      409: "Hồ sơ đã đổi trạng thái hoặc grant đã được sử dụng.",
      429: "Quá nhiều lần xác minh. Hãy chờ trước khi thử lại.",
      503: "Thao tác tài chính đang tạm khóa; bằng chứng vẫn được tiếp nhận.",
    };
    return known[error.problem.status] ??
      error.problem.detail ??
      "Không thể hoàn tất quyết định.";
  }
  return "Không thể kết nối máy chủ tài chính.";
}

function operationLabel(operation: MonetizationKillSwitch["operation"]) {
  return {
    TOPUP_CREDIT: "Ghi có top-up",
    WITHDRAWAL_PAYOUT: "Chi trả withdrawal",
    WITHDRAWAL_REQUEST: "Tạo withdrawal",
  }[operation];
}

function safeUUID(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function MonetizationReviewConsole() {
  const api = useMemo(() => createBrowserAdminMonetizationClient(), []);
  const auth = useMemo(() => createBrowserAuthClient(), []);
  const [kind, setKind] = useState<ReviewKind>("TOPUP");
  const [decision, setDecision] = useState<Decision>("APPROVE");
  const [switches, setSwitches] = useState<MonetizationKillSwitch[]>([]);
  const [switchAccess, setSwitchAccess] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<Record<string, unknown> | null>(null);
  const retryKey = useRef<string | null>(null);

  useEffect(() => {
    void api.killSwitches()
      .then((values) => setSwitches([...values]))
      .catch((requestError) => {
        if (
          requestError instanceof StoryApiError &&
          requestError.problem.status === 403
        ) {
          setSwitchAccess(false);
        }
      });
  }, [api]);

  function changeReview(nextKind: ReviewKind, nextDecision = decision) {
    setKind(nextKind);
    setDecision(nextDecision);
    setError("");
    setReceipt(null);
    retryKey.current = null;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const targetId = String(values.get("targetId")).trim();
    const reason = String(values.get("reason")).trim();
    const evidenceReference = String(values.get("evidenceReference")).trim();
    const password = String(values.get("password"));
    const mfaCode = String(values.get("mfaCode")).trim();
    setWorking(true);
    setError("");
    setReceipt(null);
    try {
      if (kind === "TOPUP" && decision === "REJECT") {
        const result = await api.rejectTopup(targetId, {
          evidenceReference,
          reason,
          reasonCode: String(values.get("reasonCode")) as
            | "AMOUNT_MISMATCH"
            | "REFERENCE_UNVERIFIABLE"
            | "DUPLICATE_PAYMENT"
            | "FRAUD_SUSPECTED"
            | "OTHER",
        });
        setReceipt(result);
      } else {
        const grant = await auth.reauthenticate({
          ...(mfaCode ? { mfaCode } : {}),
          password,
          scope: kind === "TOPUP"
            ? "TOPUP_MANUAL_APPROVAL"
            : "WITHDRAWAL_APPROVAL",
          targetId,
          targetType: kind === "TOPUP" ? "topup" : "withdrawal",
        });
        if (kind === "TOPUP") {
          setReceipt(await api.approveTopup(
            targetId,
            { evidenceReference, reason },
            grant.grantToken,
          ));
        } else {
          retryKey.current ??= safeUUID();
          setReceipt(await api.reviewWithdrawal(
            targetId,
            decision === "APPROVE" ? "approve" : "reject",
            reason,
            grant.grantToken,
            retryKey.current,
          ));
        }
      }
      retryKey.current = null;
    } catch (requestError) {
      setError(failure(requestError));
    } finally {
      setWorking(false);
    }
  }

  const requiresReauth = kind === "WITHDRAWAL" || decision === "APPROVE";

  return (
    <main className={styles.shell}>
      <aside className={styles.rail}>
        <Link aria-label="Về bàn kiểm duyệt" href="/">
          <BrandMark inverse />
        </Link>
        <div className={styles.railTitle}>
          <span>FINANCE_REVIEW</span>
          <strong>Kiểm soát giao dịch</strong>
        </div>
        <nav aria-label="Loại hồ sơ tài chính">
          <button
            aria-current={kind === "TOPUP" ? "page" : undefined}
            onClick={() => changeReview("TOPUP")}
            type="button"
          >
            Top-up cần đối soát
          </button>
          <button
            aria-current={kind === "WITHDRAWAL" ? "page" : undefined}
            onClick={() => changeReview("WITHDRAWAL")}
            type="button"
          >
            Withdrawal chờ duyệt
          </button>
        </nav>
        <div className={styles.safety}>
          <span>Trạng thái chốt an toàn</span>
          {!switchAccess && <small>Chỉ SYSTEM_CONFIGURE được xem.</small>}
          {switches.map((item) => (
            <div key={item.operation}>
              <i data-engaged={item.engaged} />
              <strong>{operationLabel(item.operation)}</strong>
              <small>{item.engaged ? "Đang khóa" : "Đang mở"}</small>
            </div>
          ))}
        </div>
      </aside>

      <section className={styles.desk}>
        <header>
          <div>
            <p>Đối soát bằng chứng · phân tách nhiệm vụ</p>
            <h1>{kind === "TOPUP" ? "Review top-up." : "Review withdrawal."}</h1>
          </div>
          <Link href="/">Bàn kiểm duyệt nội dung</Link>
        </header>

        <div className={styles.layout}>
          <section className={styles.evidence}>
            <span className={styles.kicker}>Nguyên tắc bằng chứng</span>
            <h2>Chỉ dùng tham chiếu đã làm sạch.</h2>
            <p>
              Mở hồ sơ từ cảnh báo đối soát và nhập ID nội bộ. Console không
              tải raw provider payload, số tài khoản đầy đủ hay khóa bí mật.
            </p>
            <ol>
              <li><span>01</span><strong>So khớp số tiền và trạng thái provider</strong></li>
              <li><span>02</span><strong>Kiểm tra ledger transaction hoặc reserve</strong></li>
              <li><span>03</span><strong>Ghi lý do và tham chiếu bằng chứng bất biến</strong></li>
            </ol>
            <div className={styles.warning}>
              Người yêu cầu withdrawal không được tự duyệt. API xác minh
              capability và tiêu thụ grant một lần ở thời điểm quyết định.
            </div>
          </section>

          <form className={styles.reviewForm} onSubmit={(event) => void submit(event)}>
            <fieldset className={styles.segmented}>
              <legend>Quyết định</legend>
              <label>
                <input
                  checked={decision === "APPROVE"}
                  name="decision"
                  onChange={() => changeReview(kind, "APPROVE")}
                  type="radio"
                />
                <span>Duyệt</span>
              </label>
              <label>
                <input
                  checked={decision === "REJECT"}
                  name="decision"
                  onChange={() => changeReview(kind, "REJECT")}
                  type="radio"
                />
                <span>Từ chối</span>
              </label>
            </fieldset>
            <label>
              ID hồ sơ nội bộ
              <input
                name="targetId"
                pattern="[0-9a-fA-F-]{36}"
                placeholder="UUID top-up hoặc withdrawal"
                required
              />
            </label>
            <label>
              Tham chiếu bằng chứng đối soát
              <input
                name="evidenceReference"
                pattern="[A-Za-z0-9][A-Za-z0-9._:/-]{2,511}"
                placeholder="reconciliation/case-…"
                required={kind === "TOPUP"}
              />
            </label>
            {kind === "TOPUP" && decision === "REJECT" && (
              <label>
                Mã lý do
                <select defaultValue="AMOUNT_MISMATCH" name="reasonCode">
                  <option value="AMOUNT_MISMATCH">Sai số tiền</option>
                  <option value="REFERENCE_UNVERIFIABLE">Không xác minh được</option>
                  <option value="DUPLICATE_PAYMENT">Thanh toán trùng</option>
                  <option value="FRAUD_SUSPECTED">Nghi ngờ gian lận</option>
                  <option value="OTHER">Khác</option>
                </select>
              </label>
            )}
            <label>
              Lý do quyết định
              <textarea
                maxLength={500}
                minLength={10}
                name="reason"
                placeholder="Nêu bằng chứng đã kiểm tra và kết luận…"
                required
                rows={4}
              />
            </label>
            {requiresReauth && (
              <div className={styles.reauth}>
                <span>Xác minh lại · grant một lần</span>
                <label>
                  Mật khẩu hiện tại
                  <input
                    autoComplete="current-password"
                    maxLength={128}
                    name="password"
                    required
                    type="password"
                  />
                </label>
                <label>
                  Mã MFA (nếu áp dụng)
                  <input
                    autoComplete="one-time-code"
                    maxLength={32}
                    name="mfaCode"
                  />
                </label>
              </div>
            )}
            {error && <div className={styles.error} role="alert">{error}</div>}
            {receipt && (
              <div className={styles.receipt} role="status">
                <span>Audit đã ghi</span>
                <strong>
                  {String(receipt.status ?? receipt.state ?? "COMPLETED")}
                </strong>
                <code>
                  {String(
                    receipt.topupId ??
                    receipt.withdrawalId ??
                    "transaction",
                  ).slice(0, 13)}
                </code>
              </div>
            )}
            <button disabled={working} type="submit">
              {working ? "Đang xác minh và ghi sổ…" : "Ghi quyết định có kiểm soát"}
            </button>
          </form>
        </div>
      </section>
    </main>
  );
}
