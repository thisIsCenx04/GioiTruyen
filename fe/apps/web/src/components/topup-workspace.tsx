"use client";

import { Check, Copy, Loader2, Wallet } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { isLoggedIn } from "@/lib/auth";
import {
  createTopup,
  loadDepositPackages,
  loadPaymentMethods,
  loadTopup,
  loadTopupHistory,
  submitTopup,
  type DepositPackage,
  type PaymentMethodView,
  type TopupHistoryRow,
  type TopupInstruction,
} from "@/lib/topups";

const money = new Intl.NumberFormat("vi-VN");

/**
 * Day, month and year in full rather than a shortened form: a top-up is a
 * payment record, and "13/8/26" is not what someone matches against a bank
 * statement.
 */
const timestamp = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

function formatMoment(value: string | null): string {
  if (!value) return "—";
  const parsed = new Date(value);
  // A timestamp the browser cannot read must not render as "Invalid Date".
  return Number.isNaN(parsed.getTime()) ? "—" : timestamp.format(parsed);
}

const STATUS_LABELS: Record<string, string> = {
  CANCELLED: "Đã hủy",
  DRAFT: "Chưa gửi",
  FAILED: "Thất bại",
  PAID: "Đã cộng xu",
  PENDING: "Chờ xác nhận",
  REFUNDED: "Đã hoàn",
};

/** Waiting on a human to confirm a transfer, so a slow poll is enough. */
const POLL_INTERVAL_MS = 15_000;

export function TopupWorkspace() {
  const [packages, setPackages] = useState<DepositPackage[]>([]);
  const [methods, setMethods] = useState<PaymentMethodView[]>([]);
  const [packageId, setPackageId] = useState("");
  const [methodId, setMethodId] = useState("");
  const [instruction, setInstruction] = useState<TopupInstruction | null>(null);
  const [history, setHistory] = useState<TopupHistoryRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const signedIn = isLoggedIn();

  useEffect(() => {
    void Promise.all([loadDepositPackages(), loadPaymentMethods()]).then(([pkgs, mths]) => {
      setPackages(pkgs);
      setMethods(mths);
      setPackageId((current) => current || pkgs[0]?.id || "");
      setMethodId((current) => current || mths[0]?.id || "");
    });
    if (signedIn) void loadTopupHistory().then(setHistory);
  }, [signedIn]);

  // A pending request is confirmed by an admin, so the screen refreshes itself
  // rather than making the reader reload to find out.
  useEffect(() => {
    if (!instruction || instruction.status !== "PENDING") return undefined;
    const timer = window.setInterval(() => {
      void loadTopup(instruction.paymentId).then((fresh) => {
        if (fresh && fresh.status !== "PENDING") {
          setInstruction(fresh);
          void loadTopupHistory().then(setHistory);
        }
      });
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [instruction]);

  const copy = useCallback(async (value: string, key: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      window.setTimeout(() => setCopied(""), 1800);
    } catch {
      // Clipboard permission can be denied; the value stays selectable.
    }
  }, []);

  async function confirmTransfer() {
    if (!instruction) return;
    setSubmitting(true);
    setError("");
    try {
      setInstruction(await submitTopup(instruction.paymentId));
      void loadTopupHistory().then(setHistory);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không gửi được xác nhận chuyển khoản.");
    } finally {
      setSubmitting(false);
    }
  }

  async function start() {
    setBusy(true);
    setError("");
    try {
      setInstruction(await createTopup(packageId, methodId));
      void loadTopupHistory().then(setHistory);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không tạo được yêu cầu nạp.");
    } finally {
      setBusy(false);
    }
  }

  const selectedMethod = methods.find((method) => method.id === methodId);

  return (
    <section className="topupWorkspace">
      <div className="topupPicker">
        <h2>Chọn gói nạp</h2>
        <div className="topupPackages">
          {packages.map((pack) => (
            <button
              className={pack.id === packageId ? "topupPackage isActive" : "topupPackage"}
              key={pack.id}
              onClick={() => setPackageId(pack.id)}
              type="button"
            >
              <strong>{money.format(pack.priceVnd)}đ</strong>
              <span>{money.format(pack.coinAmount + pack.bonusCoin)} xu</span>
              <span>{money.format(pack.gemAmount + pack.bonusGem)} ngọc</span>
              {pack.bonusCoin > 0 ? <em>+{money.format(pack.bonusCoin)} thưởng</em> : null}
            </button>
          ))}
        </div>

        <h2>Chọn phương thức</h2>
        <div className="topupMethods">
          {methods.map((method) => (
            <button
              className={method.id === methodId ? "topupMethod isActive" : "topupMethod"}
              key={method.id}
              onClick={() => setMethodId(method.id)}
              type="button"
            >
              {method.name}
            </button>
          ))}
        </div>

        {selectedMethod?.instructions ? (
          <p className="topupHint">{selectedMethod.instructions}</p>
        ) : null}

        {error ? <p className="questError">{error}</p> : null}

        {signedIn ? (
          <button className="topupSubmit" disabled={busy || !packageId || !methodId} onClick={() => void start()} type="button">
            {busy ? <><Loader2 aria-hidden="true" size={16} /> Đang tạo…</> : <><Wallet aria-hidden="true" size={16} /> Tạo mã nạp tiền</>}
          </button>
        ) : (
          <p className="topupHint">
            <Link to={`/login?returnTo=${encodeURIComponent("/wallet")}`}>Đăng nhập</Link> để nạp xu.
          </p>
        )}
      </div>

      <div className="topupReceipt">
        {instruction ? (
          <>
            <header>
              <p className="detailEyebrow">Biên lai · {instruction.transactionCode}</p>
              <h2>{money.format(instruction.amountVnd)}đ</h2>
              <p>
                Nhận {money.format(instruction.coinAmount)} xu và{" "}
                {money.format(instruction.gemAmount)} ngọc sau khi xác nhận.
              </p>
              <span className={instruction.status === "PAID" ? "topupBadge isPaid" : "topupBadge"}>
                {STATUS_LABELS[instruction.status] ?? instruction.status}
              </span>
            </header>

            {instruction.qrImageUrl ? (
              <img alt={`Mã QR nạp ${instruction.transactionCode}`} className="topupQr" src={instruction.qrImageUrl} />
            ) : null}

            <dl className="topupDetails">
              {instruction.bankName ? (
                <div><dt>Ngân hàng</dt><dd>{instruction.bankName}</dd></div>
              ) : null}
              {instruction.accountNumber ? (
                <div>
                  <dt>Số tài khoản</dt>
                  <dd>
                    {instruction.accountNumber}
                    <button onClick={() => void copy(instruction.accountNumber, "acc")} type="button">
                      {copied === "acc" ? <Check size={13} /> : <Copy size={13} />}
                    </button>
                  </dd>
                </div>
              ) : null}
              {instruction.accountName ? (
                <div><dt>Chủ tài khoản</dt><dd>{instruction.accountName}</dd></div>
              ) : null}
              <div>
                <dt>Nội dung chuyển khoản</dt>
                <dd>
                  <strong>{instruction.transferNote}</strong>
                  <button onClick={() => void copy(instruction.transferNote, "note")} type="button">
                    {copied === "note" ? <Check size={13} /> : <Copy size={13} />}
                  </button>
                </dd>
              </div>
              {instruction.paypalLink ? (
                <div>
                  <dt>PayPal</dt>
                  <dd><a href={instruction.paypalLink} rel="noreferrer" target="_blank">{instruction.paypalLink}</a></dd>
                </div>
              ) : null}
            </dl>

            <p className="topupWarn">
              Chuyển đúng số tiền và giữ nguyên nội dung <strong>{instruction.transferNote}</strong>.
              Sai nội dung sẽ khiến hệ thống không đối chiếu được giao dịch.
            </p>

            {/* Only this button queues the request for review. Looking at the
                QR code, or reloading the page, costs the admin nothing. */}
            {instruction.status === "DRAFT" ? (
              <div className="topupConfirm">
                <p>Sau khi chuyển khoản xong, bấm nút bên dưới để gửi yêu cầu tới quản trị viên.</p>
                <button disabled={submitting} onClick={() => void confirmTransfer()} type="button">
                  {submitting ? "Đang gửi…" : "Tôi đã chuyển khoản"}
                </button>
              </div>
            ) : null}

            {instruction.status === "PENDING" ? (
              <p className="topupPendingNote">
                Đã gửi yêu cầu. Quản trị viên sẽ đối chiếu và cộng xu cho bạn, thường trong vài phút.
              </p>
            ) : null}
          </>
        ) : (
          <div className="topupEmpty">
            <p className="detailEyebrow">Biên lai</p>
            <h2>Một mã nạp. Một giao dịch rõ ràng.</h2>
            <p>
              Chọn gói và phương thức bên trái. Hệ thống sinh mã QR kèm sẵn số tiền
              và nội dung chuyển khoản dành riêng cho bạn.
            </p>
          </div>
        )}
      </div>

      {history.length > 0 ? (
        <div className="topupHistory">
          <h2>Lịch sử nạp</h2>
          <ul>
            {history.map((row) => (
              <li key={row.id}>
                <span className="topupHistoryCode">{row.transactionCode}</span>
                {/* Paid-at when the money landed, created-at while it has not. */}
                <span className="topupHistoryDate">
                  {formatMoment(row.paidAt ?? row.createdAt)}
                </span>
                <span>{money.format(row.amountVnd)}đ</span>
                <span>{money.format(row.coinReceived)} xu · {money.format(row.gemReceived)} ngọc</span>
                <span>{row.methodName ?? "—"}</span>
                <span className={row.status === "PAID" ? "topupBadge isPaid" : "topupBadge"}>
                  {STATUS_LABELS[row.status] ?? row.status}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
