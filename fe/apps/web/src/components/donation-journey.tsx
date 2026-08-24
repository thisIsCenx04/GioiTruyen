"use client";

import {
  createBrowserWalletClient,
  StoryApiError,
  type DonationReceipt,
} from "@gioitruyen/api-client";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useRef, useState } from "react";
import { XuIcon } from "./currency-icons";

import styles from "./donation-journey.module.css";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

const options = [100, 500, 1_000, 5_000] as const;

/** Matches the ceiling the wallet enforces server-side. */
const MAX_AMOUNT = 1_000_000_000;

function xu(value: number) {
  return new Intl.NumberFormat("vi-VN").format(value);
}

function messageFor(error: unknown) {
  if (error instanceof StoryApiError) {
    if (error.problem.status === 401) {
      return "Đăng nhập để gửi XU cho đội ngũ sáng tác.";
    }
    // The wallet answers 409 when the balance will not cover the gift; 422 is
    // kept because the validation layer uses it for an out-of-range amount.
    if (error.problem.status === 409 || error.problem.status === 422) {
      return "Số dư khả dụng chưa đủ cho món quà này.";
    }
    return error.problem.detail ?? "Chưa thể gửi XU lúc này.";
  }
  return "Không thể kết nối máy chủ. Hãy thử lại.";
}

type DonationJourneyProps = Readonly<{
  storyTitle: string;
  teamId: string;
  variant?: "default" | "action";
  /**
   * "story" khi ủng hộ từ trang truyện, "team" khi ủng hộ thẳng cho nhóm.
   *
   * <p>Tiền đi cùng một đường trong cả hai trường hợp: lệnh gọi chỉ mang
   * teamId, không mang storyId, nên khoản ủng hộ không bao giờ tính vào thứ hạng
   * của truyện nào. Chỉ câu chữ trên biên nhận là khác.
   */
  context?: "story" | "team";
}>;

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

export function DonationJourney({
  storyTitle,
  teamId,
  variant = "default",
  context = "story",
}: DonationJourneyProps) {
  // Donating moves coins between accounts, so the call has to carry the token.
  const api = useMemo(
    () => createBrowserWalletClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }),
    [],
  );
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLElement>(null);
  const [confirming, setConfirming] = useState(false);
  const [amount, setAmount] = useState(500);
  /** Held as text so the box can legitimately be empty while being typed in. */
  const [customAmount, setCustomAmount] = useState("");
  const [note, setNote] = useState("");
  const [balance, setBalance] = useState<number | null>(null);
  const [receipt, setReceipt] = useState<DonationReceipt | null>(null);
  const [error, setError] = useState("");
  const [working, setWorking] = useState(false);
  const retryKey = useRef<string | null>(null);

  useEffect(() => {
    if (!open || balance !== null) return;
    void api.balance()
      .then((wallet) => setBalance(wallet.coinBalance))
      .catch((requestError) => setError(messageFor(requestError)));
  }, [api, balance, open]);

  function changeAmount(value: number) {
    setAmount(value);
    setConfirming(false);
    retryKey.current = null;
  }

  /**
   * The custom box holds text, not a number.
   *
   * Binding a number straight to the input made it impossible to clear: an
   * empty field parses as 0, React wrote the 0 back, and the reader could
   * never delete the leading digit to type a different figure. The typed text
   * is kept as-is and only interpreted when it is a real number.
   */
  function changeCustomAmount(raw: string) {
    const digitsOnly = raw.replace(/[^\d]/gu, "");
    setCustomAmount(digitsOnly);
    setConfirming(false);
    retryKey.current = null;
    setAmount(digitsOnly === "" ? 0 : Number(digitsOnly));
  }

  // Why the reader cannot continue, in their own terms. A button that is
  // merely disabled says nothing, and "donate is broken" is the reasonable
  // conclusion to draw from one.
  const blockedReason = amount < 1
    ? "Hãy nhập số XU muốn gửi."
    : amount > MAX_AMOUNT
      ? `Mỗi lần gửi tối đa ${xu(MAX_AMOUNT)} XU.`
      : balance !== null && amount > balance
        ? `Số dư của bạn là ${xu(balance)} XU, chưa đủ để gửi ${xu(amount)} XU.`
        : "";

  async function submit() {
    if (working || blockedReason !== "") return;
    setWorking(true);
    retryKey.current ??= safeUUID();
    try {
      const nextReceipt = await api.donate(
        teamId,
        { coinAmount: amount, message: note.trim() },
        retryKey.current,
      );
      setReceipt(nextReceipt);
      // The reply already carries the balance the ledger settled on, so it is
      // read back rather than recomputed from the amount that was asked for.
      setBalance(nextReceipt.coinBalance);
      setError("");
      retryKey.current = null;
    } catch (requestError) {
      setError(messageFor(requestError));
    } finally {
      setWorking(false);
    }
  }

  // Puts the caret in the panel once it opens, so the reader continues inside
  // the form rather than from the button behind it, and holds the page still
  // underneath the floating variant.
  useEffect(() => {
    if (!open || variant !== "action") return undefined;
    panelRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = previousOverflow; };
  }, [open, variant]);

  if (!open) {
    return (
      <button
        className={`${styles.trigger} ${variant === "action" ? styles.actionTrigger : ""}`}
        onClick={() => setOpen(true)}
        type="button"
      >
        <XuIcon size={18} />
        Donate
      </button>
    );
  }

  return (
    <>
      {/* Only the floating variant is a dialog; the inline one sits in the page. */}
      {variant === "action" ? (
        <div className={styles.actionBackdrop} onClick={() => setOpen(false)} role="presentation" />
      ) : null}
      <section className={`${styles.panel} ${variant === "action" ? styles.actionPanel : ""}`} aria-label="Ủng hộ đội ngũ sáng tác" ref={panelRef} tabIndex={-1}>
      <div className={styles.heading}>
        <div>
          <span>Gửi một lời cảm ơn</span>
          <h2>Tiếp sức cho<br /><em>trang viết kế tiếp.</em></h2>
        </div>
        <button
          aria-label="Đóng phần ủng hộ"
          className={styles.close}
          onClick={() => setOpen(false)}
          type="button"
        >
          ×
        </button>
      </div>

      {receipt ? (
        <div className={styles.success} role="status">
          <span className={styles.seal} aria-hidden="true">GT</span>
          <div>
            <strong>{xu(receipt.grossCoin)} XU đã được gửi.</strong>
            <p>
              {context === "team"
                ? `Món quà cho nhóm “${storyTitle}” đã ghi vào sổ giao dịch.`
                : `Món quà cho đội ngũ của “${storyTitle}” đã ghi vào sổ giao dịch.`}
            </p>
            <small>Mã biên nhận · {receipt.donationId.slice(0, 8)}</small>
          </div>
        </div>
      ) : (
        <>
          <div className={styles.balanceLine}>
            <span>Số dư khả dụng</span>
            <strong>{balance === null ? "Đang kiểm tra…" : `${xu(balance)} XU`}</strong>
          </div>

          <fieldset className={styles.amounts}>
            <legend>Chọn số XU</legend>
            {options.map((option) => (
              <button
                aria-pressed={amount === option && customAmount === ""}
                key={option}
                onClick={() => {
                  setCustomAmount("");
                  changeAmount(option);
                }}
                type="button"
              >
                {xu(option)}
              </button>
            ))}
            <label className={styles.customAmount}>
              <span>Hoặc nhập số XU bất kỳ</span>
              <input
                aria-label="Số XU tùy chọn"
                inputMode="numeric"
                onChange={(event) => changeCustomAmount(event.target.value)}
                placeholder="Ví dụ: 250"
                type="text"
                value={customAmount}
              />
              <em>XU</em>
            </label>
          </fieldset>

          {blockedReason && !error ? (
            <p className={styles.blocked} role="status">
              {blockedReason}
              {balance !== null && amount > balance ? <Link to="/wallet">Nạp thêm XU</Link> : null}
            </p>
          ) : null}

          <label className={styles.note}>
            <span>Lời nhắn cho đội ngũ · không bắt buộc</span>
            <textarea
              maxLength={500}
              onChange={(event) => {
                setNote(event.target.value);
                setConfirming(false);
                retryKey.current = null;
              }}
              placeholder="Cảm ơn vì câu chuyện này…"
              rows={3}
              value={note}
            />
            <small>{note.length}/500</small>
          </label>

          {error && (
            <div className={styles.error} role="alert">
              <span>{error}</span>
              {error.includes("chưa đủ") && <Link to="/wallet">Nạp thêm XU</Link>}
            </div>
          )}

          {confirming ? (
            <div className={styles.confirm}>
              <p>
                Xác nhận gửi <strong>{xu(amount)} XU</strong>. Giao dịch đã ghi
                sổ sẽ không thể hoàn tác.
              </p>
              <div>
                <button onClick={() => setConfirming(false)} type="button">
                  Xem lại
                </button>
                <button
                  disabled={working || blockedReason !== ""}
                  onClick={() => void submit()}
                  type="button"
                >
                  {working ? "Đang gửi…" : `Xác nhận ${xu(amount)} XU`}
                </button>
              </div>
            </div>
          ) : (
            <button
              className={styles.continue}
              disabled={blockedReason !== ""}
              onClick={() => {
                setError("");
                setConfirming(true);
              }}
              type="button"
            >
              Tiếp tục
            </button>
          )}
        </>
      )}
      </section>
    </>
  );
}
