import Link from "next/link";
import type { CSSProperties } from "react";

import { AdminShell } from "./admin-shell";
import { loadAdminOverview, type ChartPoint } from "./admin-data";

export const dynamic = "force-dynamic";

const numberFormatter = new Intl.NumberFormat("vi-VN");

function formatNumber(value: number) {
  return numberFormatter.format(value);
}

function formatXu(value: number) {
  return `${numberFormatter.format(value)} XU`;
}

function chartHeight(value: number, max: number) {
  if (max <= 0) {
    return "0%";
  }

  return `${Math.max(8, Math.round((value / max) * 100))}%`;
}

function chartStyle(value: number, max: number): CSSProperties {
  return { "--height": chartHeight(value, max) } as CSSProperties;
}

function maxValue(points: ChartPoint[]) {
  return points.reduce((max, point) => Math.max(max, point.value), 0);
}

export default async function AdminPage() {
  const overview = await loadAdminOverview();
  const revenueMax = maxValue(overview.revenueSeries);
  const trafficMax = maxValue(overview.trafficSeries);
  const stats = [
    { label: "Doanh thu", value: formatXu(overview.stats.revenueXu), delta: "Tổng từ ledger_entries" },
    { label: "Lượt truy cập", value: formatNumber(overview.stats.visits), delta: "Tổng reading_sessions" },
    { label: "Reader", value: formatNumber(overview.stats.readers), delta: "Reader duy nhất" },
    { label: "Teams", value: formatNumber(overview.stats.teams), delta: `${formatNumber(overview.stats.stories)} truyện` },
  ];

  return (
    <AdminShell activeTab="dashboard">
      <section className="adminDashboard">
        <header className="adminTopbar">
          <div>
            <p>Dashboard</p>
            <h1>Thống kê vận hành</h1>
          </div>
          <Link href="/finance">Mở cash flow</Link>
        </header>

        <div className="statGrid">
          {stats.map((item) => (
            <article className="statCard" key={item.label}>
              <span>{item.label}</span>
              <strong>{item.value}</strong>
              <small>{item.delta}</small>
            </article>
          ))}
        </div>

        <section className="chartPanel">
          <header>
            <div>
              <p>Revenue</p>
              <h2>Doanh thu theo ngày</h2>
            </div>
            <span>Dữ liệu DB</span>
          </header>
          <div className="barChart" aria-label="Biểu đồ doanh thu từ database">
            {overview.revenueSeries.map((point) => (
              <i key={point.label} style={chartStyle(point.value, revenueMax)}>
                <span>{numberFormatter.format(point.value)}</span>
              </i>
            ))}
          </div>
        </section>

        <div className="dashboardSplit">
          <section className="chartPanel">
            <header>
              <div>
                <p>Traffic</p>
                <h2>Lượt truy cập theo ngày</h2>
              </div>
              <span>Dữ liệu DB</span>
            </header>
            <div className="lineChart" aria-label="Biểu đồ lượt truy cập từ database">
              {overview.trafficSeries.map((point) => (
                <i aria-label={`${point.label}: ${point.value}`} key={point.label} style={chartStyle(point.value, trafficMax)} />
              ))}
            </div>
          </section>
          <section className="adminTasks">
            <header>
              <p>Task processing</p>
              <h2>Việc cần xử lý tiếp</h2>
            </header>
            <ol>
              {overview.tasks.map((task) => (
                <li key={task}>{task}</li>
              ))}
            </ol>
          </section>
        </div>
      </section>
    </AdminShell>
  );
}
