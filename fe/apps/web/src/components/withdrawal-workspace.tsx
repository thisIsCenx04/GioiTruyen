"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";

import styles from "./withdrawal-workspace.module.css";
import { FormDialog, type DialogRequest } from "@/components/form-dialog";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

type WithdrawalState =
  | "PENDING_REVIEW"
  | "APPROVED"
  | "PROCESSING"
  | "PAID"
  | "COMPLETED"
  | "DISPUTED"
  | "REJECTED"
  | "FAILED";

type WithdrawalReceipt = {
  id: string;
  teamId: string | null;
  accountName: string;
  bankName: string;
  destinationMasked: string;
  grossAmountXu: number;
  feeXu: number;
  netAmountXu: number;
  state: WithdrawalState;
  /** Lời của quản trị viên; khi huỷ thì đây là lý do. */
  adminNote: string | null;
  /** Mã giao dịch ngân hàng, để đối chiếu với sao kê. */
  transferReference: string | null;
  /** Lời của chính người rút khi báo chưa nhận được tiền. */
  confirmNote: string | null;
  reviewedAt: string | null;
  paidAt: string | null;
  confirmedAt: string | null;
  createdAt: string;
};

type WithdrawalPage = {
  items: WithdrawalReceipt[];
  nextCursor: string | null;
};

class HttpRequestError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

const stateCopy: Record<WithdrawalState, string> = {
  APPROVED: "Đã duyệt · chờ chuyển tiền",
  COMPLETED: "Hoàn tất",
  DISPUTED: "Bạn đã báo chưa nhận",
  FAILED: "Thanh toán lỗi",
  PAID: "Đã chuyển · chờ bạn xác nhận",
  PENDING_REVIEW: "Chờ duyệt",
  PROCESSING: "Đang thanh toán",
  REJECTED: "Đã huỷ · đã hoàn xu",
};

/** Câu giải thích trạng thái nói người rút đang chờ ai, và chờ điều gì. */
const stateHint: Partial<Record<WithdrawalState, string>> = {
  APPROVED: "Yêu cầu đã được duyệt, đang chờ chuyển tiền.",
  COMPLETED: "Bạn đã xác nhận nhận được tiền. Yêu cầu này khép lại.",
  DISPUTED: "Quản trị viên đang xem lại. Bạn sẽ được liên hệ.",
  PAID: "Kiểm tra tài khoản ngân hàng rồi xác nhận giúp.",
  PENDING_REVIEW: "Đang chờ quản trị viên duyệt.",
  REJECTED: "Số xu của yêu cầu này đã được hoàn lại vào ví.",
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

async function readProblem(response: Response, fallback: string) {
  const problem = await response.json().catch(() => ({})) as { detail?: string };
  return problem.detail ?? fallback;
}

async function loadWithdrawals(cursor?: string | null): Promise<WithdrawalPage> {
  const url = new URL(`${API_BASE_URL}/wallets/me/withdrawals`, window.location.origin);
  if (cursor) url.searchParams.set("cursor", cursor);
  const response = await authedFetch(url.pathname + url.search);
  if (!response.ok) {
    throw new HttpRequestError(response.status, await readProblem(response, "Không tải được lịch sử rút tiền."));
  }
  return response.json() as Promise<WithdrawalPage>;
}

export function WithdrawalWorkspace() {
  const [items, setItems] = useState<WithdrawalReceipt[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [allowed, setAllowed] = useState(true);
  const [working, setWorking] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [accountName, setAccountName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [bankName, setBankName] = useState("");
  const [amount, setAmount] = useState(100_000);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [ask, setAsk] = useState<DialogRequest | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await loadWithdrawals();
      setItems([...page.items]);
      setCursor(page.nextCursor ?? null);
      setAllowed(true);
      setError("");
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : "Không tải được lịch sử rút tiền.";
      if (requestError instanceof HttpRequestError && requestError.status === 403) setAllowed(false);
      else setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const task = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(task);
  }, [load]);

  async function loadMore() {
    if (!cursor || working) return;
    setWorking(true);
    try {
      const page = await loadWithdrawals(cursor);
      setItems((current) => [...current, ...page.items]);
      setCursor(page.nextCursor ?? null);
      setError("");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Không tải được lịch sử rút tiền.");
    } finally {
      setWorking(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!confirming) {
      setConfirming(true);
      setError("");
      setNotice("");
      return;
    }
    setWorking(true);
    try {
      const response = await authedFetch(`${API_BASE_URL}/wallets/me/withdrawals`, {
        body: JSON.stringify({ accountName, accountNumber, bankName, grossAmountXu: amount }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        if (response.status === 403) setAllowed(false);
        throw new Error(await readProblem(response, "Không tạo được yêu cầu rút tiền."));
      }
      const receipt = (await response.json()) as WithdrawalReceipt;
      setItems((current) => [
        receipt,
        ...current.filter((entry) => entry.id !== receipt.id),
      ]);
      setNotice(
        `Đã giữ ${xu(receipt.grossAmountXu)} xu; phí ${xu(receipt.feeXu)} xu, thực nhận ${xu(receipt.netAmountXu)} xu.`,
      );
      setError("");
      setConfirming(false);
      setAccountNumber("");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Không tạo được yêu cầu rút tiền.");
    } finally {
      setWorking(false);
    }
  }

  /** Gửi một quyết định của người rút về máy chủ rồi tải lại sổ. */
  async function respond(item: WithdrawalReceipt, path: string, body: unknown, done: string) {
    setWorking(true);
    setError("");
    try {
      const response = await authedFetch(
        `${API_BASE_URL}/wallets/me/withdrawals/${item.id}/${path}`,
        { body: JSON.stringify(body ?? {}), headers: { "Content-Type": "application/json" }, method: "POST" },
      );
      if (!response.ok) {
        setError(await readProblem(response, "Không gửi được phản hồi."));
        await load();
        return;
      }
      setNotice(done);
      await load();
    } finally {
      setWorking(false);
    }
  }

  function confirmReceived(item: WithdrawalReceipt) {
    setAsk({
      intro: `Xác nhận bạn đã nhận đủ ${xu(item.netAmountXu)} xu vào tài khoản ${item.bankName} · ${item.destinationMasked}.`,
      lines: [
        item.transferReference
          ? `Mã giao dịch quản trị viên ghi: ${item.transferReference}`
          : "Hãy đối chiếu với sao kê ngân hàng trước khi xác nhận.",
        "Xác nhận xong yêu cầu này khép lại, không mở lại được.",
      ],
      onSubmit: () => respond(item, "confirm", {}, "Đã ghi nhận. Cảm ơn bạn đã xác nhận."),
      submitLabel: "Tôi đã nhận được",
      title: "Xác nhận đã nhận tiền",
    });
  }

  function reportNotReceived(item: WithdrawalReceipt) {
    setAsk({
      fields: [{
        hint: "Quản trị viên đọc phần này để tra lại giao dịch, nên càng cụ thể càng nhanh.",
        kind: "textarea",
        label: "Mô tả tình trạng",
        maxLength: 500,
        name: "note",
        placeholder: "Ví dụ: đã kiểm tra sao kê tới 22/8 nhưng chưa thấy khoản nào về.",
        required: true,
        requiredMessage: "Quản trị viên cần biết bạn đã kiểm tra tới đâu mới tra được giao dịch.",
      }],
      intro: "Yêu cầu sẽ quay lại bàn quản trị viên để tra lại.",
      lines: [
        "Chỉ báo sau khi đã kiểm tra sao kê ngân hàng - chuyển khoản liên ngân hàng có thể chậm vài giờ.",
      ],
      onSubmit: (values) => respond(item, "dispute", { note: values.note },
        "Đã gửi. Quản trị viên sẽ tra lại và liên hệ với bạn."),
      submitLabel: "Báo chưa nhận",
      title: "Báo chưa nhận được tiền",
      tone: "danger",
    });
  }

  const reserved = items
    .filter((item) =>
      ["PENDING_REVIEW", "APPROVED", "PROCESSING"].includes(item.state))
    .reduce((total, item) => total + item.grossAmountXu, 0);
  const ready = accountName.trim() && accountNumber.trim() && bankName.trim() && amount >= 100_000;

  if (!allowed) {
    return (
      <section className={styles.restricted} id="withdrawals">
        <span>Rút tiền</span>
        <strong>Bạn không có quyền thao tác này.</strong>
        <p>Chỉ chủ team và admin được xem lịch sử thu chi hoặc tạo yêu cầu rút tiền.</p>
      </section>
    );
  }

  return (
    <section className={styles.workspace} id="withdrawals">
      <FormDialog onClose={() => setAsk(null)} request={ask} />
      <div className={styles.heading}>
        <div>
          <span>Ví chủ team</span>
          <h2>Rút tiền</h2>
          <p>
            Doanh thu team được cộng dồn vào ví chủ sở hữu. Mỗi yêu cầu rút tiền
            ghi rõ tài khoản nhận, phí rút và số xu thực nhận sau khi duyệt.
          </p>
        </div>
        <div className={styles.reserved}>
          <span>Đang chờ xử lý</span>
          <strong>{xu(reserved)} xu</strong>
        </div>
      </div>

      <div className={styles.grid}>
        <form className={styles.form} onSubmit={(event) => void submit(event)}>
          <span className={styles.label}>Yêu cầu mới</span>
          <label>
            Tên trên thẻ
            <input
              maxLength={160}
              onChange={(event) => {
                setAccountName(event.target.value);
                setConfirming(false);
              }}
              required
              value={accountName}
            />
          </label>
          <label>
            Số tài khoản
            <input
              inputMode="numeric"
              maxLength={80}
              onChange={(event) => {
                setAccountNumber(event.target.value);
                setConfirming(false);
              }}
              required
              value={accountNumber}
            />
          </label>
          <label>
            Ngân hàng
            <input
              maxLength={120}
              onChange={(event) => {
                setBankName(event.target.value);
                setConfirming(false);
              }}
              required
              value={bankName}
            />
          </label>
          <label>
            Số xu muốn rút
            <input
              max={1_000_000_000}
              min={100_000}
              onChange={(event) => {
                setAmount(Number(event.target.value));
                setConfirming(false);
              }}
              required
              step={10_000}
              type="number"
              value={amount}
            />
          </label>
          <p className={styles.policy}>
            Phí rút tiền được backend tính theo rule hiện hành và chốt trên biên
            nhận. Thành viên team không xem được tab này.
          </p>
          {error && <div className={styles.error} role="alert">{error}</div>}
          {notice && <div className={styles.notice} role="status">{notice}</div>}
          {confirming && (
            <div className={styles.confirm}>
              Xác nhận giữ <strong>{xu(amount)} xu</strong> để tạo yêu cầu rút về{" "}
              <strong>{bankName}</strong>, tài khoản <strong>{accountNumber}</strong>.
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
            <button disabled={working || !ready} type="submit">
              {working
                ? "Đang ghi sổ..."
                : confirming
                  ? "Xác nhận yêu cầu"
                  : "Kiểm tra yêu cầu"}
            </button>
          </div>
        </form>

        <div className={styles.ledger}>
          <div className={styles.ledgerTitle}>
            <div>
              <span className={styles.label}>Sổ rút tiền</span>
              <h3>Lịch sử rút</h3>
            </div>
            <button disabled={loading} onClick={() => void load()} type="button">
              Làm mới
            </button>
          </div>
          {loading ? (
            <p className={styles.empty}>Đang mở sổ giao dịch...</p>
          ) : items.length === 0 ? (
            <p className={styles.empty}>Chưa có yêu cầu rút tiền.</p>
          ) : (
            <ol>
              {items.map((item) => (
                <li key={item.id}>
                  <div>
                    <span>{time(item.createdAt)}</span>
                    <strong>{xu(item.grossAmountXu)} xu</strong>
                    <small>
                      Phí {xu(item.feeXu)} · nhận {xu(item.netAmountXu)} xu
                    </small>
                  </div>
                  <div>
                    <span className={styles.state} data-state={item.state}>
                      {stateCopy[item.state]}
                    </span>
                    <small>{item.bankName} · {item.destinationMasked}</small>
                    {stateHint[item.state] ? <small>{stateHint[item.state]}</small> : null}
                    {item.transferReference ? (
                      <small>Mã giao dịch: {item.transferReference}</small>
                    ) : null}
                    {/* Lý do huỷ phải đọc được ngay tại dòng đó: đây là câu trả
                        lời cho "vì sao tiền của tôi quay về ví". */}
                    {item.adminNote ? (
                      <small className={styles.note}>Quản trị viên: {item.adminNote}</small>
                    ) : null}
                    {item.confirmNote ? (
                      <small className={styles.note}>Bạn đã báo: {item.confirmNote}</small>
                    ) : null}

                    {item.state === "PAID" ? (
                      <div className={styles.rowActions}>
                        <button
                          disabled={working}
                          onClick={() => confirmReceived(item)}
                          type="button"
                        >
                          Đã nhận được tiền
                        </button>
                        <button
                          className={styles.rowGhost}
                          disabled={working}
                          onClick={() => reportNotReceived(item)}
                          type="button"
                        >
                          Chưa nhận được
                        </button>
                      </div>
                    ) : null}
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
