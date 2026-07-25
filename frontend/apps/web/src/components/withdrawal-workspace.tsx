"use client";

import {
  createBrowserWalletClient,
  StoryApiError,
  type WithdrawalReceipt,
  type WithdrawalState,
} from "@gioitruyen/api-client";
import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";

import styles from "./withdrawal-workspace.module.css";

const stateCopy: Record<WithdrawalState, string> = {
  APPROVED: "Đã duyệt",
  FAILED: "Thanh toán lỗi",
  PAID: "Đã thanh toán",
  PENDING_REVIEW: "Chờ duyệt",
  PROCESSING: "Đang thanh toán",
  REJECTED: "Đã từ chối",
};

function xu(value: number) {
  return new Intl.NumberFormat("vi-VN").format(value);
}

function time(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function failureMessage(error: unknown) {
  if (error instanceof StoryApiError) {
    const messages: Readonly<Record<number, string>> = {
      400: "Kiểm tra lại số XU và mã điểm nhận.",
      403: "Bạn không có quyền finance:request trong nhóm này.",
      409: "Yêu cầu trùng khóa nhưng có nội dung khác. Hãy tải lại.",
      422: "Số dư không đủ hoặc điểm nhận không còn khả dụng.",
      503: "Yêu cầu rút XU đang tạm khóa để bảo vệ giao dịch.",
    };
    return messages[error.problem.status] ??
      error.problem.detail ??
      "Không thể xử lý yêu cầu rút XU.";
  }
  return "Không thể kết nối máy chủ. Hãy thử lại.";
}

export function WithdrawalWorkspace({ teamId }: Readonly<{ teamId: string }>) {
  const api = useMemo(() => createBrowserWalletClient(), []);
  const [items, setItems] = useState<WithdrawalReceipt[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [allowed, setAllowed] = useState(true);
  const [working, setWorking] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [amount, setAmount] = useState(100_000);
  const [destinationId, setDestinationId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const retryKey = useRef<string | null>(null);

  const load = useCallback(async () => {
    try {
      const page = await api.withdrawals(teamId);
      setItems([...page.items]);
      setCursor(page.nextCursor ?? null);
      setAllowed(true);
      setError("");
    } catch (requestError) {
      if (
        requestError instanceof StoryApiError &&
        requestError.problem.status === 403
      ) {
        setAllowed(false);
      } else {
        setError(failureMessage(requestError));
      }
    } finally {
      setLoading(false);
    }
  }, [api, teamId]);

  useEffect(() => {
    const task = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(task);
  }, [load]);

  async function loadMore() {
    if (!cursor || working) return;
    setWorking(true);
    try {
      const page = await api.withdrawals(teamId, cursor);
      setItems((current) => [...current, ...page.items]);
      setCursor(page.nextCursor ?? null);
    } catch (requestError) {
      setError(failureMessage(requestError));
    } finally {
      setWorking(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!confirming) {
      setConfirming(true);
      setError("");
      return;
    }
    setWorking(true);
    retryKey.current ??= crypto.randomUUID();
    try {
      const receipt = await api.createWithdrawal(
        teamId,
        { destinationId, grossAmountXu: amount },
        retryKey.current,
      );
      setItems((current) => [
        receipt,
        ...current.filter((entry) => entry.id !== receipt.id),
      ]);
      setNotice(
        `Đã giữ ${xu(receipt.grossAmountXu)} XU; dự kiến nhận ${xu(receipt.netAmountXu)} XU.`,
      );
      setError("");
      setConfirming(false);
      retryKey.current = null;
    } catch (requestError) {
      setError(failureMessage(requestError));
    } finally {
      setWorking(false);
    }
  }

  const reserved = items
    .filter((item) =>
      ["PENDING_REVIEW", "APPROVED", "PROCESSING"].includes(item.state))
    .reduce((total, item) => total + item.grossAmountXu, 0);

  if (!allowed) {
    return (
      <section className={styles.restricted} id="withdrawals">
        <span>Tài chính nhóm</span>
        <strong>Quyền finance:request là bắt buộc.</strong>
        <p>Lịch sử và điểm nhận tiền chỉ hiển thị cho thành viên được ủy quyền.</p>
      </section>
    );
  }

  return (
    <section className={styles.workspace} id="withdrawals">
      <div className={styles.heading}>
        <div>
          <span>Thanh khoản đội ngũ</span>
          <h2>Rút XU có kiểm soát.</h2>
          <p>
            Mỗi yêu cầu giữ nguyên tổng XU, chụp điểm nhận đã xác minh và chuyển
            qua bước duyệt tài chính trước khi thanh toán.
          </p>
        </div>
        <div className={styles.reserved}>
          <span>Đang giữ theo lịch sử tải về</span>
          <strong>{xu(reserved)} XU</strong>
        </div>
      </div>

      <div className={styles.grid}>
        <form className={styles.form} onSubmit={(event) => void submit(event)}>
          <span className={styles.label}>Yêu cầu mới</span>
          <label>
            Tổng XU cần rút
            <input
              max={1_000_000_000}
              min={100_000}
              onChange={(event) => {
                setAmount(Number(event.target.value));
                setConfirming(false);
                retryKey.current = null;
              }}
              required
              step={10_000}
              type="number"
              value={amount}
            />
          </label>
          <label>
            Mã điểm nhận đã xác minh
            <input
              onChange={(event) => {
                setDestinationId(event.target.value);
                setConfirming(false);
                retryKey.current = null;
              }}
              pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
              placeholder="UUID của tài khoản nhận"
              required
              value={destinationId}
            />
          </label>
          <p className={styles.policy}>
            Phí được backend chốt theo chính sách hiện hành và sẽ xuất hiện
            trên biên nhận. Thông tin ngân hàng đầy đủ không được trả về trình duyệt.
          </p>
          {error && <div className={styles.error} role="alert">{error}</div>}
          {notice && <div className={styles.notice} role="status">{notice}</div>}
          {confirming && (
            <div className={styles.confirm}>
              Xác nhận giữ <strong>{xu(amount)} XU</strong> cho yêu cầu này.
              Thao tác tiếp theo cần bộ phận tài chính duyệt.
            </div>
          )}
          <div className={styles.actions}>
            {confirming && (
              <button
                disabled={working}
                onClick={() => setConfirming(false)}
                type="button"
              >
                Xem lại
              </button>
            )}
            <button
              disabled={
                working ||
                amount < 100_000 ||
                amount > 1_000_000_000 ||
                !destinationId
              }
              type="submit"
            >
              {working
                ? "Đang ghi sổ…"
                : confirming
                  ? "Xác nhận yêu cầu"
                  : "Kiểm tra yêu cầu"}
            </button>
          </div>
        </form>

        <div className={styles.ledger}>
          <div className={styles.ledgerTitle}>
            <div>
              <span className={styles.label}>Sổ rút XU</span>
              <h3>Lịch sử riêng của nhóm</h3>
            </div>
            <button disabled={loading} onClick={() => void load()} type="button">
              Làm mới
            </button>
          </div>
          {loading ? (
            <p className={styles.empty}>Đang mở sổ giao dịch…</p>
          ) : items.length === 0 ? (
            <p className={styles.empty}>Nhóm chưa có yêu cầu rút XU.</p>
          ) : (
            <ol>
              {items.map((item) => (
                <li key={item.id}>
                  <div>
                    <span>{time(item.createdAt)}</span>
                    <strong>{xu(item.grossAmountXu)} XU</strong>
                    <small>
                      Phí {xu(item.feeXu)} · nhận {xu(item.netAmountXu)} XU
                    </small>
                  </div>
                  <div>
                    <span className={styles.state} data-state={item.state}>
                      {stateCopy[item.state]}
                    </span>
                    <small>{item.destinationMasked}</small>
                  </div>
                </li>
              ))}
            </ol>
          )}
          {cursor && (
            <button
              className={styles.more}
              disabled={working}
              onClick={() => void loadMore()}
              type="button"
            >
              Mở giao dịch cũ hơn
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
