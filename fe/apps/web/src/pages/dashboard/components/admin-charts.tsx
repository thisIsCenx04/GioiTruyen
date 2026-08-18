import { useCallback, useRef, useState } from "react";
import type { ChartPoint } from "../admin-data";

const W = 700;
const H = 220;
const PX = 28;
const PY = 28;

function coordinates(series: ChartPoint[]) {
  const max = Math.max(...series.map((p) => p.value), 1);
  const uw = W - PX * 2;
  const uh = H - PY * 2;
  return series.map((p, i) => ({
    ...p,
    x: PX + (series.length === 1 ? uw / 2 : (i / (series.length - 1)) * uw),
    y: PY + uh - (p.value / max) * uh,
  }));
}

/**
 * Straight segments between points - no bezier smoothing. A curve invents
 * intermediate values that were never measured, so daily counts are drawn as
 * the polyline they actually are.
 */
function linePath(pts: ReturnType<typeof coordinates>) {
  if (pts.length === 0) return "";
  return pts
    .slice(1)
    .reduce((path, pt) => `${path} L ${pt.x} ${pt.y}`, `M ${pts[0]?.x ?? 0} ${pts[0]?.y ?? 0}`);
}

const EMPTY = (
  <p style={{ color: "#94a3b8", textAlign: "center", padding: "2.5rem 0", fontSize: "0.88rem", margin: 0 }}>
    Chưa có dữ liệu trong khoảng thời gian này.
  </p>
);

const GRID = (
  <g>
    <line x1={PX} x2={W - PX} y1={H / 4} y2={H / 4} stroke="#e2eaf5" strokeWidth="1" />
    <line x1={PX} x2={W - PX} y1={H / 2} y2={H / 2} stroke="#e2eaf5" strokeWidth="1" />
    <line x1={PX} x2={W - PX} y1={(H * 3) / 4} y2={(H * 3) / 4} stroke="#e2eaf5" strokeWidth="1.5" />
    <line x1={PX} x2={W - PX} y1={H - PY} y2={H - PY} stroke="#e2eaf5" strokeWidth="1.5" />
  </g>
);

/**
 * Which point the pointer is nearest, as an index into the series.
 *
 * The svg is drawn with preserveAspectRatio="none", so user units stretch
 * independently on each axis and a viewBox coordinate cannot be compared
 * against a client pixel directly. Everything is worked out as a fraction of
 * the element's own width instead, which survives the stretch.
 */
function useNearestPoint(count: number) {
  const hostRef = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState<number | null>(null);

  const track = useCallback((clientX: number) => {
    const host = hostRef.current;
    if (!host || count === 0) return;
    const box = host.getBoundingClientRect();
    if (box.width === 0) return;

    // The plot area sits inside the same horizontal padding the viewBox uses.
    const padFraction = PX / W;
    const plotStart = box.left + box.width * padFraction;
    const plotWidth = box.width * (1 - padFraction * 2);
    const position = plotWidth === 0 ? 0 : (clientX - plotStart) / plotWidth;
    const index = count === 1 ? 0 : Math.round(position * (count - 1));
    setActive(Math.min(count - 1, Math.max(0, index)));
  }, [count]);

  const clear = useCallback(() => setActive(null), []);

  return { active, clear, hostRef, track };
}

/** The floating readout. Flips to the other side of the point near an edge. */
function Tooltip({
  label,
  leftPercent,
  topPercent,
  value,
}: Readonly<{ label: string; leftPercent: number; topPercent: number; value: string }>) {
  // Past the two thirds mark the card would hang off the card's right edge, so
  // it is anchored by its right side instead of its middle.
  const anchor = leftPercent > 78 ? "translate(-100%, -50%)"
    : leftPercent < 22 ? "translate(0, -50%)"
      : "translate(-50%, -50%)";
  return (
    <div
      className="chartTooltip"
      style={{
        left: `${leftPercent}%`,
        top: `calc(${topPercent}% - 2.6rem)`,
        transform: anchor,
      }}
    >
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export function PointLineChart({ series }: Readonly<{ series: ChartPoint[] }>) {
  const pts = coordinates(series);
  const path = linePath(pts);
  const { active, clear, hostRef, track } = useNearestPoint(series.length);
  const hot = active === null ? null : pts[active];

  return (
    <div className="svgChart" ref={hostRef}>
      {series.length === 0 ? EMPTY : (
        <div className="chartPlot">
          <svg
            aria-label="Lượt truy cập theo ngày"
            onMouseLeave={clear}
            onMouseMove={(event) => track(event.clientX)}
            onTouchEnd={clear}
            onTouchMove={(event) => track(event.touches[0]?.clientX ?? 0)}
            onTouchStart={(event) => track(event.touches[0]?.clientX ?? 0)}
            preserveAspectRatio="none"
            role="img"
            viewBox={`0 0 ${W} ${H}`}
          >
            <defs>
              <linearGradient id="trafficFill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stopColor="#0f6bff" stopOpacity=".22" />
                <stop offset="1" stopColor="#0f6bff" stopOpacity="0" />
              </linearGradient>
            </defs>
            {GRID}
            <path
              d={`${path} L ${pts.at(-1)?.x ?? 0} ${H - PY} L ${pts[0]?.x ?? 0} ${H - PY} Z`}
              fill="url(#trafficFill)"
            />
            <path
              d={path}
              fill="none" stroke="#0f6bff" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round"
            />
            {/* Ties the readout to the day it belongs to, which matters most
                where two days sit close together. */}
            {hot ? (
              <line
                stroke="#0f6bff" strokeDasharray="4 4" strokeOpacity=".45" strokeWidth="1"
                x1={hot.x} x2={hot.x} y1={PY} y2={H - PY}
              />
            ) : null}
            {pts.map((pt, index) => (
              <circle
                cx={pt.x} cy={pt.y} fill="#fff" key={pt.label}
                r={index === active ? 6.5 : 4.5}
                stroke="#0f6bff" strokeWidth={index === active ? 3 : 2}
              />
            ))}
          </svg>
          {hot ? (
            <Tooltip
              label={hot.label}
              leftPercent={(hot.x / W) * 100}
              topPercent={(hot.y / H) * 100}
              value={`${hot.value.toLocaleString("vi-VN")} lượt`}
            />
          ) : null}
        </div>
      )}
      <div className="chartLabels">{series.map((p) => <span key={p.label}>{p.label}</span>)}</div>
    </div>
  );
}

export function BarChart({ series }: Readonly<{ series: ChartPoint[] }>) {
  const max = Math.max(...series.map((p) => p.value), 1);
  const barW = series.length === 0 ? 0 : Math.max(4, (W - PX * 2) / series.length - 4);
  const uw = W - PX * 2;
  const { active, clear, hostRef, track } = useNearestPoint(series.length);

  const geometry = series.map((p, i) => {
    const height = Math.max(2, (p.value / max) * (H - PY * 2));
    return {
      ...p,
      height,
      x: PX + (series.length === 1 ? uw / 2 : (i / (series.length - 1)) * uw) - barW / 2,
      y: H - PY - height,
    };
  });
  const hot = active === null ? null : geometry[active];

  return (
    <div className="svgChart" ref={hostRef}>
      {series.length === 0 ? EMPTY : (
        <div className="chartPlot">
          <svg
            aria-label="Doanh thu theo ngày"
            onMouseLeave={clear}
            onMouseMove={(event) => track(event.clientX)}
            onTouchEnd={clear}
            onTouchMove={(event) => track(event.touches[0]?.clientX ?? 0)}
            onTouchStart={(event) => track(event.touches[0]?.clientX ?? 0)}
            preserveAspectRatio="none"
            role="img"
            viewBox={`0 0 ${W} ${H}`}
          >
            <defs>
              <linearGradient id="barGrad" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stopColor="#d97706" stopOpacity=".9" />
                <stop offset="1" stopColor="#f59e0b" stopOpacity=".6" />
              </linearGradient>
            </defs>
            {GRID}
            {geometry.map((bar, index) => (
              <rect
                fill="url(#barGrad)"
                height={bar.height}
                key={bar.label}
                opacity={active === null || index === active ? 1 : 0.45}
                rx="3"
                width={barW}
                x={bar.x}
                y={bar.y}
              />
            ))}
          </svg>
          {hot ? (
            <Tooltip
              label={hot.label}
              leftPercent={((hot.x + barW / 2) / W) * 100}
              topPercent={(hot.y / H) * 100}
              value={`${hot.value.toLocaleString("vi-VN")} Xu`}
            />
          ) : null}
        </div>
      )}
      <div className="chartLabels">{series.map((p) => <span key={p.label}>{p.label}</span>)}</div>
    </div>
  );
}
