"use client";

import {
  createBrowserTeamClient,
  StoryApiError,
  type TeamAnalyticsReport,
} from "@gioitruyen/api-client";
import Link from "next/link";
import type { Route } from "next";
import { useEffect, useMemo, useState } from "react";

import styles from "./team-analytics.module.css";

type Period = TeamAnalyticsReport["period"];

const periods: readonly Period[] = ["7D", "30D", "90D"];
const reasonLabels: Readonly<Record<string, string>> = {
  BOT_SIGNAL: "Dấu hiệu bot",
  DUPLICATE: "Lượt đọc trùng",
  INSUFFICIENT_ACTIVE_TIME: "Thời gian đọc quá ngắn",
  INVALID_SEQUENCE: "Nhịp đọc không hợp lệ",
  SELF_VIEW: "Lượt đọc nội bộ",
};

const number = new Intl.NumberFormat("vi-VN");
const day = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  timeZone: "UTC",
});

function percent(value: number) {
  return `${Math.round(value * 1000) / 10}%`;
}

function reasonName(code: string) {
  return reasonLabels[code] ?? code.toLocaleLowerCase("vi").replaceAll("_", " ");
}

export function TeamAnalytics({ teamId }: Readonly<{ teamId: string }>) {
  const client = useMemo(() => createBrowserTeamClient(), []);
  const [period, setPeriod] = useState<Period>("30D");
  const [report, setReport] = useState<TeamAnalyticsReport | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "forbidden" | "error">(
    "loading",
  );

  useEffect(() => {
    let active = true;
    client.analytics(teamId, period).then(
      (value) => {
        if (!active) return;
        setReport(value);
        setState("ready");
      },
      (error: unknown) => {
        if (!active) return;
        setReport(null);
        setState(
          error instanceof StoryApiError && error.problem.status === 403
            ? "forbidden"
            : "error",
        );
      },
    );
    return () => {
      active = false;
    };
  }, [client, period, teamId]);

  if (state === "forbidden") {
    return (
      <main className={styles.messagePage}>
        <p>Phân quyền nhóm</p>
        <h1>Bạn chưa được xem số liệu.</h1>
        <span>
          Chủ nhóm có thể cấp quyền “Xem phân tích” trong sổ thành viên.
        </span>
        <Link href={`/teams/${teamId}` as Route}>Trở về hồ sơ nhóm</Link>
      </main>
    );
  }

  if (state === "error") {
    return (
      <main className={styles.messagePage}>
        <p>Không tải được dữ liệu</p>
        <h1>Bản tổng hợp đang gián đoạn.</h1>
        <span>Thử tải lại trang sau ít phút.</span>
      </main>
    );
  }

  const maximum = Math.max(
    1,
    ...(report?.series.map((bucket) => bucket.validViews + bucket.invalidViews) ??
      []),
  );
  const reasonsTotal =
    report?.reasons.reduce((sum, reason) => sum + reason.count, 0) ?? 0;

  return (
    <main className={styles.analytics}>
      <header className={styles.header}>
        <div>
          <Link href={`/teams/${teamId}` as Route}>← Không gian nhóm</Link>
          <p>Sổ kiểm lượt đọc</p>
          <h1>Nhịp đọc thật, quyết định rõ.</h1>
        </div>
        <fieldset className={styles.periods}>
          <legend>Khoảng thời gian</legend>
          {periods.map((value) => (
            <button
              aria-pressed={period === value}
              disabled={state === "loading"}
              key={value}
              onClick={() => {
                setState("loading");
                setPeriod(value);
              }}
              type="button"
            >
              {value === "7D" ? "7 ngày" : value === "30D" ? "30 ngày" : "90 ngày"}
            </button>
          ))}
        </fieldset>
      </header>

      {state === "loading" || !report ? (
        <section aria-live="polite" className={styles.loading}>
          Đang đối chiếu các lượt đọc…
        </section>
      ) : (
        <>
          <section aria-label="Tổng quan lượt đọc" className={styles.totals}>
            <article className={styles.quality}>
              <span>Chất lượng lưu lượng</span>
              <strong>{percent(report.totals.qualityRate)}</strong>
              <p>Tỷ lệ lượt hợp lệ trên toàn bộ lượt đã phân loại.</p>
            </article>
            <article>
              <span>Sự kiện thô</span>
              <strong>{number.format(report.totals.rawEvents)}</strong>
            </article>
            <article>
              <span>Lượt hợp lệ</span>
              <strong>{number.format(report.totals.validViews)}</strong>
            </article>
            <article>
              <span>Lượt bị loại</span>
              <strong>{number.format(report.totals.invalidViews)}</strong>
            </article>
          </section>

          <section className={styles.chartSection}>
            <div className={styles.sectionHeading}>
              <div>
                <p>Mỗi cột là một ngày UTC</p>
                <h2>Dải nhịp đọc</h2>
              </div>
              <div className={styles.legend} aria-label="Chú giải">
                <span data-tone="valid">Hợp lệ</span>
                <span data-tone="invalid">Bị loại</span>
              </div>
            </div>
            {report.series.length === 0 ? (
              <p className={styles.empty}>
                Chưa có lượt đọc được tổng hợp trong khoảng này.
              </p>
            ) : (
              <ol
                aria-label="Biểu đồ lượt đọc hợp lệ và bị loại theo ngày"
                className={styles.chart}
              >
                {report.series.map((bucket) => {
                  const validHeight = (bucket.validViews / maximum) * 100;
                  const invalidHeight = (bucket.invalidViews / maximum) * 100;
                  return (
                    <li key={bucket.start}>
                      <div className={styles.bar}>
                        <span
                          className={styles.invalidBar}
                          style={{ height: `${invalidHeight}%` }}
                        />
                        <span
                          className={styles.validBar}
                          style={{ height: `${validHeight}%` }}
                        />
                      </div>
                      <time dateTime={bucket.start}>
                        {day.format(new Date(bucket.start))}
                      </time>
                      <span className={styles.srOnly}>
                        {number.format(bucket.validViews)} hợp lệ,{" "}
                        {number.format(bucket.invalidViews)} bị loại
                      </span>
                    </li>
                  );
                })}
              </ol>
            )}
          </section>

          <section className={styles.reasons}>
            <div className={styles.sectionHeading}>
              <div>
                <p>Vì sao lượt đọc không được tính</p>
                <h2>Dấu vết chất lượng</h2>
              </div>
              <strong>{number.format(reasonsTotal)} tín hiệu</strong>
            </div>
            {report.reasons.length === 0 ? (
              <p className={styles.empty}>Không có lý do loại trong kỳ này.</p>
            ) : (
              <ol>
                {report.reasons.map((reason) => (
                  <li key={reason.code}>
                    <span>{reasonName(reason.code)}</span>
                    <div>
                      <i
                        style={{
                          width: `${Math.max(4, (reason.count / reasonsTotal) * 100)}%`,
                        }}
                      />
                    </div>
                    <strong>{number.format(reason.count)}</strong>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <footer className={styles.note}>
            <span>Đến {new Date(report.to).toLocaleString("vi-VN")}</span>
            <p>
              Số liệu lấy từ bản tổng hợp đã phân loại; sự kiện thô không được
              dùng làm lượt xem hợp lệ.
            </p>
          </footer>
        </>
      )}
    </main>
  );
}
