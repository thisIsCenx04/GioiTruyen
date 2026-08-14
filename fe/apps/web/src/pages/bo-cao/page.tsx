"use client";

import { AlertCircle, BadgeCheck, CalendarClock, Coins, Megaphone, RefreshCw } from "lucide-react";
import { type FormEvent, useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";
import {
  type PromotionBooking,
  type PromotionOverview,
  type PromotionPackage,
  createPromotion,
  extendPromotion,
  loadPromotionOverview,
  loadPromotionPackages,
} from "@/lib/promotions";
import styles from "./page.module.css";

const coinFormatter = new Intl.NumberFormat("vi-VN");

function formatDate(value: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleString("vi-VN", {
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

export default function PromotionPage() {
  const [overview, setOverview] = useState<PromotionOverview | null>(null);
  const [packages, setPackages] = useState<PromotionPackage[]>([]);
  const [signedIn, setSignedIn] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [storyId, setStoryId] = useState("");
  const [packageId, setPackageId] = useState("");

  const refresh = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await loadPromotionOverview();
      setOverview(data);
      setPackages(data.packages);
      setSignedIn(true);
    } catch {
      // Signed-out visitors still get the rules and the price table.
      setSignedIn(false);
      try {
        setPackages(await loadPromotionPackages());
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Không tải được bảng giá.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!storyId || !packageId) {
      setError("Hãy chọn truyện và gói bố cáo.");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const booking = await createPromotion(storyId, packageId);
      setNotice(
        `Đã đăng ký bố cáo cho "${booking.storyTitle}" đến ${formatDate(booking.endsAt)}.`
      );
      setStoryId("");
      setPackageId("");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể đăng ký bố cáo.");
    } finally {
      setBusy(false);
    }
  }

  async function handleExtend(booking: PromotionBooking, extendPackageId: string) {
    if (!extendPackageId) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await extendPromotion(booking.id, extendPackageId);
      setNotice(`Đã gia hạn "${updated.storyTitle}" đến ${formatDate(updated.endsAt)}.`);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể gia hạn.");
    } finally {
      setBusy(false);
    }
  }

  const maxDays = overview?.maxTotalDays ?? 30;

  return (
    <PublicShell>
      <div className={styles.page}>
        <header className={styles.hero}>
          <span className={styles.heroBadge}>
            <Megaphone aria-hidden="true" size={16} /> Bố cáo truyện
          </span>
          <h1>Đưa truyện của nhóm lên khu Bố cáo trang chủ</h1>
          <p>
            Truyện đã xuất bản của nhóm bạn có thể mua suất hiển thị tại khu Bố cáo trên trang
            chủ. Chi phí được trừ trực tiếp bằng xu trong ví.
          </p>
        </header>

        <section className={styles.card}>
          <h2>Quy định đăng ký</h2>
          <ol className={styles.rules}>
            <li>
              <strong>Điều kiện:</strong> truyện phải ở trạng thái <em>Đã xuất bản</em>, và bạn phải
              là thành viên đang hoạt động của nhóm sở hữu truyện đó.
            </li>
            <li>
              <strong>Thanh toán:</strong> trừ xu ngay khi đăng ký. Nếu số dư không đủ, đăng ký sẽ
              bị từ chối và không trừ xu.
            </li>
            <li>
              <strong>Thời hạn tối đa:</strong> tổng thời gian hiển thị không vượt quá{" "}
              <strong>{maxDays} ngày</strong> tính từ thời điểm hiện tại.
            </li>
            <li>
              <strong>Gia hạn:</strong> khi lượt bố cáo sắp hết, dùng nút <em>Gia hạn</em> để cộng
              thêm ngày. Ngày mới cộng nối tiếp vào ngày còn lại, không làm mất thời gian đã mua.
            </li>
            <li>
              <strong>Thứ tự hiển thị:</strong> nhóm đăng ký trước được xếp ở vị trí trước.
            </li>
            <li>
              <strong>Hoàn xu:</strong> không hoàn xu sau khi đăng ký thành công, kể cả khi chủ động
              gỡ truyện khỏi khu bố cáo.
            </li>
          </ol>
        </section>

        <section className={styles.card}>
          <h2>Bảng giá</h2>
          <p className={styles.cardHint}>Gói càng dài, đơn giá mỗi ngày càng rẻ.</p>
          <div className={styles.priceGrid}>
            {packages.map((pkg) => (
              <article className={styles.priceCard} key={pkg.id}>
                <h3>{pkg.name}</h3>
                <p className={styles.price}>
                  <Coins aria-hidden="true" size={18} />
                  {coinFormatter.format(pkg.priceCoin)} xu
                </p>
                <p className={styles.perDay}>
                  {coinFormatter.format(pkg.pricePerDayCoin)} xu / ngày
                </p>
                {pkg.description ? <p className={styles.priceNote}>{pkg.description}</p> : null}
              </article>
            ))}
          </div>
          {packages.length === 0 && !loading ? (
            <p className={styles.empty}>Hiện chưa có gói bố cáo nào đang mở bán.</p>
          ) : null}
        </section>

        {!signedIn ? (
          <section className={styles.card}>
            <p className={styles.signedOut}>
              <AlertCircle aria-hidden="true" size={18} />
              Hãy <Link to="/login?returnTo=/bo-cao">đăng nhập</Link> bằng tài khoản thuộc nhóm
              dịch để đăng ký bố cáo.
            </p>
          </section>
        ) : (
          <>
            <section className={styles.card}>
              <div className={styles.sectionHead}>
                <h2>Đăng ký bố cáo</h2>
                <span className={styles.balance}>
                  <Coins aria-hidden="true" size={16} />
                  Số dư: {coinFormatter.format(overview?.walletCoinBalance ?? 0)} xu
                </span>
              </div>

              {overview && overview.stories.length === 0 ? (
                <p className={styles.empty}>
                  Nhóm của bạn chưa có truyện nào đã xuất bản, nên chưa thể đăng ký bố cáo.
                </p>
              ) : (
                <form className={styles.form} onSubmit={submit}>
                  <label className={styles.field}>
                    <span>Chọn truyện</span>
                    <select
                      onChange={(event) => setStoryId(event.currentTarget.value)}
                      value={storyId}
                    >
                      <option value="">-- Chọn truyện đã xuất bản --</option>
                      {overview?.stories.map((story) => (
                        <option key={story.storyId} value={story.storyId}>
                          {story.title}
                          {story.promoting ? ` (đang bố cáo, còn ${story.activeDaysRemaining} ngày)` : ""}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className={styles.field}>
                    <span>Chọn gói</span>
                    <select
                      onChange={(event) => setPackageId(event.currentTarget.value)}
                      value={packageId}
                    >
                      <option value="">-- Chọn gói --</option>
                      {packages.map((pkg) => (
                        <option key={pkg.id} value={pkg.id}>
                          {pkg.name} — {coinFormatter.format(pkg.priceCoin)} xu
                        </option>
                      ))}
                    </select>
                  </label>

                  <button className={styles.submit} disabled={busy} type="submit">
                    {busy ? "Đang xử lý..." : "Đăng ký và thanh toán"}
                  </button>
                </form>
              )}

              {error ? (
                <p className={styles.error} role="alert">
                  <AlertCircle aria-hidden="true" size={16} /> {error}
                </p>
              ) : null}
              {notice ? (
                <p className={styles.notice} role="status">
                  <BadgeCheck aria-hidden="true" size={16} /> {notice}
                </p>
              ) : null}
            </section>

            <section className={styles.card}>
              <h2>Lượt bố cáo của nhóm</h2>
              {overview && overview.bookings.length === 0 ? (
                <p className={styles.empty}>Chưa có lượt bố cáo nào.</p>
              ) : (
                <div className={styles.bookingList}>
                  {overview?.bookings.map((booking) => (
                    <article className={styles.booking} key={booking.id}>
                      <div className={styles.bookingMain}>
                        <strong>{booking.storyTitle}</strong>
                        <small>
                          <CalendarClock aria-hidden="true" size={14} />
                          {formatDate(booking.startsAt)} → {formatDate(booking.endsAt)}
                        </small>
                        <small>
                          {booking.durationDays} ngày ·{" "}
                          {coinFormatter.format(booking.coinPaid)} xu · {booking.teamName}
                        </small>
                      </div>
                      <div className={styles.bookingSide}>
                        <span
                          className={styles.status}
                          data-status={booking.status}
                        >
                          {booking.status === "ACTIVE"
                            ? `Đang chạy · còn ${booking.daysRemaining} ngày`
                            : booking.status === "EXPIRED"
                              ? "Đã hết hạn"
                              : "Đã hủy"}
                        </span>
                        {booking.status === "ACTIVE" ? (
                          <label className={styles.extend}>
                            <RefreshCw aria-hidden="true" size={14} />
                            <select
                              defaultValue=""
                              disabled={busy}
                              onChange={(event) => {
                                const value = event.currentTarget.value;
                                event.currentTarget.value = "";
                                void handleExtend(booking, value);
                              }}
                            >
                              <option value="">Gia hạn...</option>
                              {packages.map((pkg) => (
                                <option key={pkg.id} value={pkg.id}>
                                  +{pkg.durationDays} ngày ({coinFormatter.format(pkg.priceCoin)} xu)
                                </option>
                              ))}
                            </select>
                          </label>
                        ) : null}
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </PublicShell>
  );
}
