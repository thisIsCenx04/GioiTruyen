"use client";

import {
  createBrowserWalletClient,
  StoryApiError,
  type TopupRequest,
  type TopupStatus,
  type WalletBalance,
} from "@gioitruyen/api-client";
import { BrandMark } from "@gioitruyen/ui";
import { Link } from "react-router-dom";
import QRCode from "qrcode";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import styles from "./wallet-journey.module.css";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

const amountOptions = [50_000, 100_000, 200_000, 500_000] as const;

const statusCopy: Record<TopupStatus, { label: string; detail: string }> = {
  AWAITING_PAYMENT: {
    detail: "Đang chờ ngân hàng xác nhận giao dịch.",
    label: "Chờ thanh toán",
  },
  CREDITED: { detail: "XU đã được ghi có vào ví.", label: "Đã ghi có" },
  PENDING_REVIEW: {
    detail: "Giao dịch đang được đối soát thủ công.",
    label: "Đang đối soát",
  },
  REJECTED: {
    detail: "Giao dịch không thể ghi có.",
    label: "Đã từ chối",
  },
};

function formatXu(value: number) {
  return new Intl.NumberFormat("vi-VN").format(value);
}

function formatVnd(value: number) {
  return new Intl.NumberFormat("vi-VN", {
    currency: "VND",
    maximumFractionDigits: 0,
    style: "currency",
  }).format(value);
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function errorMessage(error: unknown) {
  if (error instanceof StoryApiError) {
    if (error.problem.status === 401) {
      return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    }
    return error.problem.detail ?? "Không thể xử lý yêu cầu lúc này.";
  }
  return "Không thể kết nối máy chủ. Hãy thử lại.";
}

function remaining(expiresAt: string, now: number) {
  const seconds = Math.max(
    0,
    Math.floor((new Date(expiresAt).getTime() - now) / 1_000),
  );
  const minutes = Math.floor(seconds / 60);
  return `${minutes.toString().padStart(2, "0")}:${(seconds % 60)
    .toString()
    .padStart(2, "0")}`;
}

function QrCanvas({ payload }: Readonly<{ payload: string }>) {
  const canvas = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!canvas.current) return;
    void QRCode.toCanvas(canvas.current, payload, {
      color: { dark: "#111a3a", light: "#ffffff" },
      errorCorrectionLevel: "M",
      margin: 2,
      width: 232,
    });
  }, [payload]);

  return (
    <canvas
      aria-label="Mã VietQR cho yêu cầu nạp tiền"
      className={styles.qr}
      ref={canvas}
      role="img"
    />
  );
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

export function WalletJourney() {
  const api = useMemo(() => createBrowserWalletClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }), []);
  const [balance, setBalance] = useState<WalletBalance | null>(null);
  const [history, setHistory] = useState<TopupRequest[]>([]);
  const [active, setActive] = useState<TopupRequest | null>(null);
  const [amount, setAmount] = useState(100_000);
  const [now, setNow] = useState(() => Date.now());
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const retryKey = useRef<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [nextBalance, nextHistory] = await Promise.all([
        api.balance(),
        api.topupHistory(),
      ]);
      setBalance(nextBalance);
      setHistory([...nextHistory]);
      setActive((current) => {
        if (!current) {
          return nextHistory.find(
            (entry) =>
              entry.status === "AWAITING_PAYMENT" &&
              new Date(entry.expiresAt).getTime() > Date.now(),
          ) ?? null;
        }
        return nextHistory.find((entry) => entry.id === current.id) ?? current;
      });
      setError("");
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    const task = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(task);
  }, [load]);

  useEffect(() => {
    if (!active || active.status !== "AWAITING_PAYMENT") return;
    const clock = window.setInterval(() => setNow(Date.now()), 1_000);
    const poll = window.setInterval(() => {
      void api.getTopup(active.id).then((updated) => {
        setActive(updated);
        setHistory((entries) =>
          entries.map((entry) => entry.id === updated.id ? updated : entry),
        );
        if (updated.status === "CREDITED") void load();
      }).catch(() => undefined);
    }, 10_000);
    return () => {
      window.clearInterval(clock);
      window.clearInterval(poll);
    };
  }, [active, api, load]);

  async function createTopup() {
    if (working || amount < 10_000 || amount > 1_000_000_000) return;
    setWorking(true);
    setCopied(false);
    retryKey.current ??= safeUUID();
    try {
      const request = await api.createTopup(amount, retryKey.current);
      setActive(request);
      setHistory((entries) => [
        request,
        ...entries.filter((entry) => entry.id !== request.id),
      ]);
      setError("");
      retryKey.current = null;
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setWorking(false);
    }
  }

  async function copyReference() {
    if (!active) return;
    try {
      await navigator.clipboard.writeText(active.transferReference);
      setCopied(true);
    } catch {
      setError("Không thể sao chép tự động. Hãy chọn nội dung chuyển khoản bên dưới.");
    }
  }

  const expired = Boolean(
    active &&
    active.status === "AWAITING_PAYMENT" &&
    new Date(active.expiresAt).getTime() <= now,
  );

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <Link aria-label="Về trang chủ Giới Truyện" to="/">
          <BrandMark />
        </Link>
        <div>
          <span className={styles.eyebrow}>Tài khoản đọc · Ví cá nhân</span>
          <h1>Giữ nhịp đọc<br /><em>bằng XU.</em></h1>
        </div>
        <Link className={styles.back} to="/">Trở lại kệ truyện</Link>
      </header>

      {error && <div className={styles.error} role="alert">{error}</div>}

      <section className={styles.workspace} aria-busy={loading}>
        <aside className={styles.ledger}>
          <span className={styles.sectionLabel}>Số dư khả dụng</span>
          <div className={styles.balance}>
            <strong>{loading ? "—" : formatXu(balance?.availableXu ?? 0)}</strong>
            <span>XU</span>
          </div>
          <p>
            {formatXu(balance?.reservedXu ?? 0)} XU đang được giữ cho các giao
            dịch chưa hoàn tất.
          </p>

          <div className={styles.amountBlock}>
            <label htmlFor="topup-amount">Số tiền muốn nạp</label>
            <div className={styles.amountOptions}>
              {amountOptions.map((option) => (
                <button
                  aria-pressed={amount === option}
                  key={option}
                  onClick={() => {
                    setAmount(option);
                    retryKey.current = null;
                  }}
                  type="button"
                >
                  {formatVnd(option)}
                </button>
              ))}
            </div>
            <div className={styles.customAmount}>
              <input
                id="topup-amount"
                inputMode="numeric"
                max={1_000_000_000}
                min={10_000}
                onChange={(event) => {
                  setAmount(Number(event.target.value));
                  retryKey.current = null;
                }}
                step={10_000}
                type="number"
                value={amount}
              />
              <span>VND</span>
            </div>
            <button
              className={styles.create}
              disabled={working || amount < 10_000 || amount > 1_000_000_000}
              onClick={() => void createTopup()}
              type="button"
            >
              {working ? "Đang tạo biên lai…" : "Tạo mã nạp tiền"}
            </button>
            <small>Mỗi mã chỉ dành cho một lần chuyển khoản.</small>
          </div>
        </aside>

        <div className={styles.receiptColumn}>
          {active ? (
            <article className={styles.receipt}>
              <div className={styles.receiptTop}>
                <div>
                  <span className={styles.sectionLabel}>Biên lai VietQR</span>
                  <h2>{formatVnd(active.amountVnd)}</h2>
                  {active.discountPercent > 0 && (
                    <p>
                      Nhận {formatXu(active.creditedXu)} XU · ưu đãi{" "}
                      {active.discountPercent}%
                    </p>
                  )}
                </div>
                <span
                  className={`${styles.status} ${
                    active.status === "CREDITED" ? styles.success : ""
                  }`}
                >
                  {expired ? "Đã hết hạn" : statusCopy[active.status].label}
                </span>
              </div>

              <div className={styles.payment}>
                <QrCanvas payload={active.qrPayload} />
                <div className={styles.instructions}>
                  <span className={styles.step}>01 · Quét mã bằng ứng dụng ngân hàng</span>
                  <span className={styles.step}>02 · Giữ nguyên số tiền và nội dung</span>
                  <label>Nội dung chuyển khoản</label>
                  <button
                    className={styles.reference}
                    onClick={() => void copyReference()}
                    type="button"
                  >
                    <strong>{active.transferReference}</strong>
                    <span>{copied ? "Đã sao chép" : "Sao chép"}</span>
                  </button>
                  <p>{statusCopy[active.status].detail}</p>
                </div>
              </div>

              <footer className={styles.receiptFooter}>
                <span>Khởi tạo {formatTime(active.createdAt)}</span>
                <strong>
                  {active.status === "AWAITING_PAYMENT"
                    ? expired
                      ? "Tạo mã mới để tiếp tục"
                      : `Còn ${remaining(active.expiresAt, now)}`
                    : statusCopy[active.status].label}
                </strong>
              </footer>
            </article>
          ) : (
            <div className={styles.blankReceipt}>
              <span>Biên lai số 001</span>
              <h2>Một mã nạp.<br />Một giao dịch rõ ràng.</h2>
              <p>
                Chọn số tiền bên trái. Hệ thống sẽ khóa ưu đãi hiện tại và tạo
                mã VietQR có thời hạn dành riêng cho bạn.
              </p>
            </div>
          )}

          <section className={styles.history}>
            <div className={styles.historyHeading}>
              <div>
                <span className={styles.sectionLabel}>Sổ giao dịch</span>
                <h2>Các lần nạp gần đây</h2>
              </div>
              <button disabled={loading} onClick={() => void load()} type="button">
                Làm mới
              </button>
            </div>
            {!loading && history.length === 0 ? (
              <p className={styles.empty}>Chưa có yêu cầu nạp tiền nào.</p>
            ) : (
              <ol>
                {history.map((entry) => {
                  const entryExpired =
                    entry.status === "AWAITING_PAYMENT" &&
                    new Date(entry.expiresAt).getTime() <= now;
                  return (
                    <li key={entry.id}>
                      <button onClick={() => setActive(entry)} type="button">
                        <span>{formatTime(entry.createdAt)}</span>
                        <strong>{formatVnd(entry.amountVnd)}</strong>
                        <span>
                          {entryExpired ? "Đã hết hạn" : statusCopy[entry.status].label}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ol>
            )}
          </section>
        </div>
      </section>
    </main>
  );
}
