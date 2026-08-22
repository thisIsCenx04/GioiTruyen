"use client";

import { useState } from "react";
import type { AdminOverview, AdOverview, ChartPoint } from "../admin-data";
import { BarChart, PointLineChart } from "./admin-charts";

type Range = "7d" | "30d" | "90d";

const RANGE_LABELS: Record<Range, string> = {
  "7d": "7 ngày",
  "30d": "30 ngày",
  "90d": "3 tháng",
};

function filterByRange(series: ChartPoint[], range: Range): ChartPoint[] {
  const n = range === "7d" ? 7 : range === "30d" ? 30 : 90;
  return series.slice(-n);
}

const STAT_CARDS = [
  {
    key: "revenueXu" as const,
    label: "Doanh Thu Xu",
    sub: "Tổng lượng xu giao dịch",
    detail: "Doanh thu xu quy đổi từ tất cả giao dịch nạp & mở chương",
    explanation: "Bao gồm Xu từ nạp ngân hàng/Momo/ZaloPay, giao dịch ủng hộ tác giả và mở khóa chương trả phí.",
    fmt: (v: number) => `${v.toLocaleString("vi-VN")} Xu`,
    gradient: "linear-gradient(135deg, #f59e0b 0%, #d97706 100%)",
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" />
      </svg>
    ),
  },
  {
    key: "visits" as const,
    label: "Lượt Truy Cập",
    sub: "Phiên đọc ghi nhận",
    detail: "Tổng số phiên xem truyện & đọc chương từ độc giả",
    explanation: "Được tính khi độc giả tải trang chương truyện hoặc duy trì đọc liên tục trên 15 giây.",
    fmt: (v: number) => v.toLocaleString("vi-VN"),
    gradient: "linear-gradient(135deg, #3b82f6 0%, #0f6bff 100%)",
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" />
      </svg>
    ),
  },
  {
    key: "readers" as const,
    label: "Độc Giả Active",
    sub: "Độc giả duy nhất",
    detail: "Số tài khoản người dùng có hoạt động thực tế",
    explanation: "Bao gồm độc giả có thao tác đọc truyện, lưu tủ sách, bình luận hoặc giao dịch xu trong kỳ.",
    fmt: (v: number) => v.toLocaleString("vi-VN"),
    gradient: "linear-gradient(135deg, #10b981 0%, #059669 100%)",
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" />
      </svg>
    ),
  },
  {
    key: "teams" as const,
    label: "Nhóm Dịch / Studio",
    sub: "Nhóm xuất bản hệ thống",
    detail: "Các nhóm dịch & tác giả đang hoạt động xuất bản",
    explanation: "Tổng số team/studio đã qua kiểm duyệt, có quyền đăng truyện, quản lý chương và nhận xu.",
    fmt: (v: number) => v.toLocaleString("vi-VN"),
    gradient: "linear-gradient(135deg, #a855f7 0%, #9333ea 100%)",
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    ),
  },
  // "Tổng Số Truyện" used to close this row. It is a stock figure, not a signal:
  // it only ever goes up, it says nothing about how the platform is doing today,
  // and the content tab already lists every story with its status.
];

/** Placement codes as stored in advertisements.placement. */
const PLACEMENT_LABELS: Record<string, string> = {
  GLOBAL_CLICK: "Click toàn trang",
  HOME: "Trang chủ",
  READER: "Trang đọc chương",
  SIDEBAR: "Cột bên",
  STORY_DETAIL: "Trang chi tiết truyện",
  STORY_OPEN: "Khi mở truyện",
};

function RangeSelector({ value, onChange }: { value: Range; onChange: (r: Range) => void }) {
  return (
    <div style={{ display: "flex", gap: "0.35rem" }}>
      {(Object.keys(RANGE_LABELS) as Range[]).map((r) => (
        <button
          key={r}
          type="button"
          onClick={() => onChange(r)}
          style={{
            padding: "0.3rem 0.75rem",
            borderRadius: "6px",
            fontSize: "0.78rem",
            fontWeight: r === value ? 700 : 500,
            border: r === value ? "1.5px solid #0f6bff" : "1.5px solid #dbeafe",
            background: r === value ? "#eff6ff" : "#f8fafc",
            color: r === value ? "#0f6bff" : "#64748b",
            cursor: "pointer",
            transition: "all 0.15s",
          }}
        >
          {RANGE_LABELS[r]}
        </button>
      ))}
    </div>
  );
}

const CARD_STYLE: React.CSSProperties = {
  background: "var(--surface-card, #fff)",
  border: "1px solid var(--line, #dfeaf6)",
  borderRadius: "12px",
  boxShadow: "0 2px 10px rgba(24,47,100,0.05)",
  padding: "1rem 1.25rem",
};

/**
 * Advertising performance.
 *
 * <p>Everything here is the platform's own banners and affiliate redirects, the
 * events this database records. AdSense impressions, clicks and earnings are
 * not in it: those live in the AdSense account and can only be read through the
 * AdSense Management API, which needs its own OAuth client. Rather than draw an
 * empty AdSense chart that reads as "nobody saw an ad today", the panel says
 * where those numbers are.
 */
function AdsPanel({ ads, range }: Readonly<{ ads: AdOverview; range: Range }>) {
  const ranked = [...ads.placements].sort((left, right) => right.ctr - left.ctr);
  const best = ranked.find((row) => row.impressions > 0);
  const idle = ads.placements.filter((row) => row.activeUnits === 0);

  return (
    <section style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
      <div style={{ display: "grid", gap: "0.75rem", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))" }}>
        <div style={CARD_STYLE}>
          <h3 style={{ color: "var(--text-primary, #0f172a)", fontSize: "0.95rem", fontWeight: 700, margin: "0 0 0.1rem" }}>
            Lượt hiển thị quảng cáo
          </h3>
          <p style={{ color: "var(--text-secondary, #64748b)", fontSize: "0.75rem", margin: "0 0 0.65rem" }}>
            Banner của hệ thống, theo từng ngày
          </p>
          <PointLineChart series={filterByRange(ads.impressionSeries, range)} />
        </div>

        <div style={CARD_STYLE}>
          <h3 style={{ color: "var(--text-primary, #0f172a)", fontSize: "0.95rem", fontWeight: 700, margin: "0 0 0.1rem" }}>
            Lượt click &amp; chuyển hướng
          </h3>
          <p style={{ color: "var(--text-secondary, #64748b)", fontSize: "0.75rem", margin: "0 0 0.65rem" }}>
            Tính cả link tiếp thị liên kết được bấm
          </p>
          <BarChart series={filterByRange(ads.clickSeries, range)} />
        </div>
      </div>

      <div style={CARD_STYLE}>
        <div style={{ alignItems: "baseline", display: "flex", flexWrap: "wrap", gap: "0.5rem 1.25rem", marginBottom: "0.75rem" }}>
          <h3 style={{ color: "var(--text-primary, #0f172a)", fontSize: "0.95rem", fontWeight: 700, margin: 0 }}>
            Hiệu quả theo vị trí đặt
          </h3>
          <span style={{ color: "var(--text-secondary, #64748b)", fontSize: "0.78rem" }}>
            {ads.impressions.toLocaleString("vi-VN")} hiển thị ·{" "}
            {ads.clicks.toLocaleString("vi-VN")} click · CTR {ads.ctr}% ·{" "}
            {ads.activeUnits} banner đang bật
          </span>
        </div>

        {ads.placements.length === 0 ? (
          <p style={{ color: "var(--text-muted, #94a3b8)", fontSize: "0.82rem", margin: 0 }}>
            Chưa có banner nào được tạo. Vào tab Quảng cáo để thêm vị trí đầu tiên.
          </p>
        ) : (
          <table style={{ borderCollapse: "collapse", fontSize: "0.82rem", width: "100%" }}>
            <thead>
              <tr style={{ color: "var(--text-secondary, #64748b)", textAlign: "left" }}>
                <th style={{ padding: "0.4rem 0.5rem" }}>Vị trí</th>
                <th style={{ padding: "0.4rem 0.5rem", textAlign: "right" }}>Hiển thị</th>
                <th style={{ padding: "0.4rem 0.5rem", textAlign: "right" }}>Click</th>
                <th style={{ padding: "0.4rem 0.5rem", textAlign: "right" }}>CTR</th>
                <th style={{ padding: "0.4rem 0.5rem", textAlign: "right" }}>Đang bật</th>
              </tr>
            </thead>
            <tbody>
              {ads.placements.map((row) => (
                <tr key={row.placement} style={{ borderTop: "1px solid var(--line, #e2e8f0)" }}>
                  <td style={{ color: "var(--text-primary, #0f172a)", fontWeight: 650, padding: "0.45rem 0.5rem" }}>
                    {PLACEMENT_LABELS[row.placement] ?? row.placement}
                  </td>
                  <td style={{ padding: "0.45rem 0.5rem", textAlign: "right" }}>
                    {row.impressions.toLocaleString("vi-VN")}
                  </td>
                  <td style={{ padding: "0.45rem 0.5rem", textAlign: "right" }}>
                    {row.clicks.toLocaleString("vi-VN")}
                  </td>
                  <td style={{ fontWeight: 700, padding: "0.45rem 0.5rem", textAlign: "right" }}>{row.ctr}%</td>
                  <td style={{ padding: "0.45rem 0.5rem", textAlign: "right" }}>{row.activeUnits}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* What the table implies, said plainly - only when there is enough
            data to imply it. A CTR computed from a handful of impressions is
            noise, so the suggestion waits for a hundred. */}
        {best && best.impressions >= 100 ? (
          <p style={{ color: "var(--text-secondary, #475569)", fontSize: "0.8rem", margin: "0.75rem 0 0" }}>
            Vị trí <strong>{PLACEMENT_LABELS[best.placement] ?? best.placement}</strong> đang hiệu quả nhất
            với CTR {best.ctr}%. Cân nhắc dồn banner giá trị cao vào đây.
            {idle.length > 0
              ? ` ${idle.length} vị trí chưa bật banner nào: ${idle
                  .map((row) => PLACEMENT_LABELS[row.placement] ?? row.placement)
                  .join(", ")}.`
              : ""}
          </p>
        ) : null}

        <p style={{ color: "var(--text-muted, #94a3b8)", fontSize: "0.75rem", lineHeight: 1.5, margin: "0.75rem 0 0" }}>
          Số liệu trên là banner &amp; link tiếp thị của hệ thống. Doanh thu, lượt hiển thị và click
          của Google AdSense không nằm trong cơ sở dữ liệu này — xem tại AdSense Dashboard, hoặc cấp
          OAuth cho AdSense Management API để đồng bộ tự động vào đây.
        </p>
      </div>
    </section>
  );
}

export function OverviewWorkspace({ overview }: Readonly<{ overview: AdminOverview }>) {
  const { stats, revenueSeries, trafficSeries } = overview;
  const [range, setRange] = useState<Range>("30d");
  const [activeTooltip, setActiveTooltip] = useState<string | null>(null);

  const filteredRevenue = filterByRange(revenueSeries, range);
  const filteredTraffic = filterByRange(trafficSeries, range);

  return (
    <div className="adminDashboard" style={{ display: "flex", flexDirection: "column", gap: "0.85rem" }}>
      {/* Topbar */}
      <header className="adminTopbar">
        <div>
          <p style={{ margin: "0 0 0.15rem", fontSize: "0.65rem", color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            Dữ liệu thời gian thực · Hover từng mục để xem giải thích chi tiết
          </p>
          <h1 style={{ margin: 0 }}>Bảng Điều Khiển Quản Trị</h1>
        </div>
      </header>

      {/* Stat Cards with Interactive Hover Tooltip */}
      <div style={{ display: "grid", gap: "0.75rem", gridTemplateColumns: "repeat(auto-fit, minmax(195px, 1fr))" }}>
        {STAT_CARDS.map((card) => {
          const val = stats[card.key];
          const isHovered = activeTooltip === card.key;

          return (
            <div
              key={card.key}
              onMouseEnter={() => setActiveTooltip(card.key)}
              onMouseLeave={() => setActiveTooltip(null)}
              style={{
                background: card.gradient,
                borderRadius: "12px",
                padding: "1rem 1.1rem",
                color: "#fff",
                boxShadow: isHovered ? "0 8px 24px rgba(0,0,0,0.25)" : "0 4px 16px rgba(0,0,0,0.12)",
                display: "flex",
                flexDirection: "column",
                gap: "0.25rem",
                position: "relative",
                overflow: "visible",
                cursor: "pointer",
                transform: isHovered ? "translateY(-3px)" : "none",
                transition: "all 0.2s cubic-bezier(0.16, 1, 0.3, 1)",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <span style={{ fontSize: "0.8rem", fontWeight: 700, opacity: 0.95 }}>{card.label}</span>
                <span style={{ opacity: 0.85, flexShrink: 0 }}>{card.icon}</span>
              </div>
              <strong style={{ fontSize: "1.65rem", fontWeight: 850, lineHeight: 1.1, letterSpacing: "-0.02em" }}>
                {card.fmt(val)}
              </strong>
              <small style={{ fontSize: "0.72rem", opacity: 0.85 }}>{card.sub}</small>

              {/* Hover Tooltip Popover */}
              {isHovered && (
                <div
                  style={{
                    position: "absolute",
                    top: "105%",
                    left: "50%",
                    transform: "translateX(-50%)",
                    width: "230px",
                    background: "#071739",
                    color: "#ffffff",
                    padding: "0.75rem 0.85rem",
                    borderRadius: "8px",
                    border: "1.5px solid #38bdf8",
                    boxShadow: "0 10px 30px rgba(0, 0, 0, 0.4)",
                    fontSize: "0.75rem",
                    zIndex: 99,
                    pointerEvents: "none",
                    textAlign: "left",
                    lineHeight: 1.45,
                    animation: "fadeIn 0.15s ease-out",
                  }}
                >
                  <strong style={{ color: "#38bdf8", display: "block", fontSize: "0.8rem", marginBottom: "0.25rem" }}>
                    💡 {card.label}
                  </strong>
                  <p style={{ margin: "0 0 0.35rem", color: "#f1f5f9", fontWeight: 600 }}>
                    {card.detail}
                  </p>
                  <span style={{ color: "#94a3b8", display: "block", fontSize: "0.7rem" }}>
                    {card.explanation}
                  </span>
                  <div
                    style={{
                      position: "absolute",
                      bottom: "100%",
                      left: "50%",
                      marginLeft: "-6px",
                      borderWidth: "6px",
                      borderStyle: "solid",
                      borderColor: "transparent transparent #071739 transparent",
                    }}
                  />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Range Filter */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: "0.5rem", marginTop: "0.4rem" }}>
        <span style={{ fontSize: "0.8rem", color: "#64748b", fontWeight: 600 }}>Xem dữ liệu theo:</span>
        <RangeSelector value={range} onChange={setRange} />
      </div>

      {/* Charts Row: traffic + revenue, side by side */}
      <div style={{ display: "grid", gap: "0.75rem", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))" }}>
        <div style={{ background: "#fff", border: "1px solid #dfeaf6", borderRadius: "12px", padding: "1rem 1.25rem", boxShadow: "0 2px 10px rgba(24,47,100,0.05)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.65rem" }}>
            <div>
              <h3
                title="Thống kê số phiên mở đọc truyện & tải trang chương của người dùng theo ngày"
                style={{ fontSize: "0.95rem", fontWeight: 700, margin: 0, color: "#0f172a", cursor: "help", display: "inline-flex", alignItems: "center", gap: "0.35rem" }}
              >
                Lượt Truy Cập Theo Ngày ℹ️
              </h3>
              <p style={{ fontSize: "0.75rem", color: "#64748b", margin: "0.1rem 0 0" }}>Theo từng ngày, tính từ gần nhất đến hiện tại</p>
            </div>
            <span style={{ fontSize: "0.72rem", background: "#e0efff", color: "#0f6bff", padding: "0.25rem 0.65rem", borderRadius: "20px", fontWeight: 700 }}>
              {RANGE_LABELS[range]}
            </span>
          </div>
          <PointLineChart series={filteredTraffic} />
        </div>

        <div style={{ background: "#fff", border: "1px solid #dfeaf6", borderRadius: "12px", padding: "1rem 1.25rem", boxShadow: "0 2px 10px rgba(24,47,100,0.05)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.65rem" }}>
            <div>
              <h3
                title="Biểu đồ cột thể hiện lượng Xu phát sinh từ nạp tiền & giao dịch chương mỗi ngày"
                style={{ fontSize: "0.95rem", fontWeight: 700, margin: 0, color: "#0f172a", cursor: "help", display: "inline-flex", alignItems: "center", gap: "0.35rem" }}
              >
                Doanh Thu Xu ℹ️
              </h3>
              <p style={{ fontSize: "0.75rem", color: "#64748b", margin: "0.1rem 0 0" }}>Theo từng ngày, tính từ gần nhất đến hiện tại</p>
            </div>
            <span style={{ fontSize: "0.72rem", background: "#fef3c7", color: "#d97706", padding: "0.25rem 0.65rem", borderRadius: "20px", fontWeight: 700 }}>
              {RANGE_LABELS[range]}
            </span>
          </div>
          <BarChart series={filteredRevenue} />
        </div>
      </div>

      <AdsPanel ads={overview.ads} range={range} />
    </div>
  );
}
