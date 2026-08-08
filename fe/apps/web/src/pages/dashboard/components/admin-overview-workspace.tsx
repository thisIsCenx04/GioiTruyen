"use client";

import { useState } from "react";
import type { AdminOverview, ChartPoint } from "../admin-data";
import { BarChart, PointLineChart, SmoothLineChart } from "./admin-charts";

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
    sub: "Nhóm xuất bản trên hệ thống",
    fmt: (v: number) => v.toLocaleString("vi-VN"),
    gradient: "linear-gradient(135deg, #a855f7 0%, #9333ea 100%)",
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    ),
  },
  {
    key: "stories" as const,
    label: "Tổng Số Truyện",
    sub: "Truyện trên nền tảng",
    fmt: (v: number) => v.toLocaleString("vi-VN"),
    gradient: "linear-gradient(135deg, #6366f1 0%, #4f46e5 100%)",
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="9" rx="1" /><rect x="14" y="3" width="7" height="5" rx="1" />
        <rect x="14" y="12" width="7" height="9" rx="1" /><rect x="3" y="16" width="7" height="5" rx="1" />
      </svg>
    ),
  },
];

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

export function OverviewWorkspace({ overview }: Readonly<{ overview: AdminOverview }>) {
  const { stats, revenueSeries, trafficSeries, readerSeries } = overview;
  const [range, setRange] = useState<Range>("30d");

  const filteredRevenue = filterByRange(revenueSeries, range);
  const filteredTraffic = filterByRange(trafficSeries, range);
  const filteredReaders = filterByRange(readerSeries, range);

  return (
    <div className="adminDashboard" style={{ display: "flex", flexDirection: "column", gap: "0.85rem" }}>
      {/* Topbar */}
      <header className="adminTopbar">
        <div>
          <p style={{ margin: "0 0 0.15rem", fontSize: "0.65rem", color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            Dữ liệu thời gian thực
          </p>
          <h1 style={{ margin: 0 }}>Bảng Điều Khiển Quản Trị</h1>
        </div>
      </header>

      {/* Stat Cards */}
      <div style={{ display: "grid", gap: "0.7rem", gridTemplateColumns: "repeat(auto-fit, minmax(185px, 1fr))" }}>
        {STAT_CARDS.map((card) => {
          const val = stats[card.key];
          return (
            <div
              key={card.key}
              style={{
                background: card.gradient,
                borderRadius: "12px",
                padding: "1rem 1.1rem",
                color: "#fff",
                boxShadow: "0 4px 16px rgba(0,0,0,0.12)",
                display: "flex",
                flexDirection: "column",
                gap: "0.25rem",
                position: "relative",
                overflow: "hidden",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <span style={{ fontSize: "0.8rem", fontWeight: 600, opacity: 0.9 }}>{card.label}</span>
                <span style={{ opacity: 0.7, flexShrink: 0 }}>{card.icon}</span>
              </div>
              <strong style={{ fontSize: "1.6rem", fontWeight: 800, lineHeight: 1.1, letterSpacing: "-0.02em" }}>
                {card.fmt(val)}
              </strong>
              <small style={{ fontSize: "0.72rem", opacity: 0.8 }}>{card.sub}</small>
              {/* Decorative circle */}
              <div style={{
                position: "absolute", right: "-1rem", bottom: "-1.2rem",
                width: "5rem", height: "5rem", borderRadius: "50%",
                background: "rgba(255,255,255,0.08)", pointerEvents: "none",
              }} />
            </div>
          );
        })}
      </div>

      {/* Range Filter */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: "0.5rem" }}>
        <span style={{ fontSize: "0.8rem", color: "#64748b", fontWeight: 600 }}>Xem theo:</span>
        <RangeSelector value={range} onChange={setRange} />
      </div>

      {/* Revenue Bar Chart */}
      <div style={{ background: "#fff", border: "1px solid #dfeaf6", borderRadius: "12px", padding: "1rem 1.25rem", boxShadow: "0 2px 10px rgba(24,47,100,0.05)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.65rem" }}>
          <div>
            <h3 style={{ fontSize: "0.95rem", fontWeight: 700, margin: 0, color: "#0f172a" }}>Doanh Thu Xu</h3>
            <p style={{ fontSize: "0.75rem", color: "#64748b", margin: "0.1rem 0 0" }}>Theo từng ngày, tính từ gần nhất</p>
          </div>
          <span style={{ fontSize: "0.72rem", background: "#fef3c7", color: "#d97706", padding: "0.25rem 0.65rem", borderRadius: "20px", fontWeight: 700 }}>
            {RANGE_LABELS[range]}
          </span>
        </div>
        <BarChart series={filteredRevenue} />
      </div>

      {/* Line Charts Row */}
      <div style={{ display: "grid", gap: "0.7rem", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))" }}>
        <div style={{ background: "#fff", border: "1px solid #dfeaf6", borderRadius: "12px", padding: "1rem 1.25rem", boxShadow: "0 2px 10px rgba(24,47,100,0.05)" }}>
          <h3 style={{ fontSize: "0.95rem", fontWeight: 700, margin: "0 0 0.6rem", color: "#0f172a" }}>Lượt Truy Cập Theo Ngày</h3>
          <PointLineChart series={filteredTraffic} />
        </div>

        <div style={{ background: "#fff", border: "1px solid #dfeaf6", borderRadius: "12px", padding: "1rem 1.25rem", boxShadow: "0 2px 10px rgba(24,47,100,0.05)" }}>
          <h3 style={{ fontSize: "0.95rem", fontWeight: 700, margin: "0 0 0.6rem", color: "#0f172a" }}>Độc Giả Hoạt Động Theo Ngày</h3>
          <SmoothLineChart series={filteredReaders} />
        </div>
      </div>
    </div>
  );
}
