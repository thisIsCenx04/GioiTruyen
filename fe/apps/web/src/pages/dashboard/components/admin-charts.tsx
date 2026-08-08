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

function smoothPath(pts: ReturnType<typeof coordinates>) {
  if (pts.length === 0) return "";
  return pts.slice(1).reduce((path, pt, i) => {
    const prev = pts[i]!;
    const mx = (prev.x + pt.x) / 2;
    return `${path} C ${mx} ${prev.y}, ${mx} ${pt.y}, ${pt.x} ${pt.y}`;
  }, `M ${pts[0]?.x ?? 0} ${pts[0]?.y ?? 0}`);
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
    <line x1={PX} x2={W - PX} y1={(H * 3) / 4} y2={(H * 3) / 4} stroke="#e2eaf5" strokeWidth="1" />
    <line x1={PX} x2={W - PX} y1={H - PY} y2={H - PY} stroke="#e2eaf5" strokeWidth="1.5" />
  </g>
);

export function PointLineChart({ series }: Readonly<{ series: ChartPoint[] }>) {
  const pts = coordinates(series);
  return (
    <div className="svgChart">
      {series.length === 0 ? EMPTY : (
        <svg aria-label="Lượt truy cập theo ngày" preserveAspectRatio="none" role="img" viewBox={`0 0 ${W} ${H}`}>
          <defs>
            <linearGradient id="trafficFill" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor="#0f6bff" stopOpacity=".22" />
              <stop offset="1" stopColor="#0f6bff" stopOpacity="0" />
            </linearGradient>
          </defs>
          {GRID}
          <path
            d={`${pts.length > 1 ? pts.slice(1).reduce((path, pt, i) => {
              const prev = pts[i]!;
              const mx = (prev.x + pt.x) / 2;
              return `${path} C ${mx} ${prev.y}, ${mx} ${pt.y}, ${pt.x} ${pt.y}`;
            }, `M ${pts[0]?.x ?? 0} ${pts[0]?.y ?? 0}`) : ""} L ${pts.at(-1)?.x ?? 0} ${H - PY} L ${pts[0]?.x ?? 0} ${H - PY} Z`}
            fill="url(#trafficFill)"
          />
          <path
            d={pts.length > 1 ? pts.slice(1).reduce((path, pt, i) => {
              const prev = pts[i]!;
              const mx = (prev.x + pt.x) / 2;
              return `${path} C ${mx} ${prev.y}, ${mx} ${pt.y}, ${pt.x} ${pt.y}`;
            }, `M ${pts[0]?.x ?? 0} ${pts[0]?.y ?? 0}`) : ""}
            fill="none" stroke="#0f6bff" strokeWidth="2.5" strokeLinecap="round"
          />
          {pts.map((pt) => (
            <circle key={pt.label} cx={pt.x} cy={pt.y} r="4.5" fill="#fff" stroke="#0f6bff" strokeWidth="2">
              <title>{pt.label}: {pt.value.toLocaleString("vi-VN")}</title>
            </circle>
          ))}
        </svg>
      )}
      <div className="chartLabels">{series.map((p) => <span key={p.label}>{p.label}</span>)}</div>
    </div>
  );
}

export function SmoothLineChart({ series }: Readonly<{ series: ChartPoint[] }>) {
  const pts = coordinates(series);
  const path = smoothPath(pts);
  return (
    <div className="svgChart smoothChart">
      {series.length === 0 ? EMPTY : (
        <svg aria-label="Độc giả hoạt động theo ngày" preserveAspectRatio="none" role="img" viewBox={`0 0 ${W} ${H}`}>
          <defs>
            <linearGradient id="readerFill" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor="#10b981" stopOpacity=".2" />
              <stop offset="1" stopColor="#10b981" stopOpacity="0" />
            </linearGradient>
          </defs>
          {GRID}
          <path d={`${path} L ${pts.at(-1)?.x ?? 0} ${H - PY} L ${pts[0]?.x ?? 0} ${H - PY} Z`} fill="url(#readerFill)" />
          <path d={path} fill="none" stroke="#10b981" strokeWidth="2.5" strokeLinecap="round" />
          {pts.map((pt) => (
            <circle key={pt.label} cx={pt.x} cy={pt.y} r="4" fill="#fff" stroke="#10b981" strokeWidth="2">
              <title>{pt.label}: {pt.value.toLocaleString("vi-VN")}</title>
            </circle>
          ))}
        </svg>
      )}
      <div className="chartLabels">{series.map((p) => <span key={p.label}>{p.label}</span>)}</div>
    </div>
  );
}

export function BarChart({ series }: Readonly<{ series: ChartPoint[] }>) {
  const max = Math.max(...series.map((p) => p.value), 1);
  const barW = series.length === 0 ? 0 : Math.max(4, (W - PX * 2) / series.length - 4);
  const uw = W - PX * 2;

  return (
    <div className="svgChart">
      {series.length === 0 ? EMPTY : (
        <svg aria-label="Doanh thu theo ngày" preserveAspectRatio="none" role="img" viewBox={`0 0 ${W} ${H}`}>
          <defs>
            <linearGradient id="barGrad" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor="#d97706" stopOpacity=".9" />
              <stop offset="1" stopColor="#f59e0b" stopOpacity=".6" />
            </linearGradient>
          </defs>
          {GRID}
          {series.map((p, i) => {
            const bh = Math.max(2, (p.value / max) * (H - PY * 2));
            const x = PX + (series.length === 1 ? uw / 2 : (i / (series.length - 1)) * uw) - barW / 2;
            return (
              <rect key={p.label} x={x} y={H - PY - bh} width={barW} height={bh} rx="3" fill="url(#barGrad)">
                <title>{p.label}: {p.value.toLocaleString("vi-VN")} Xu</title>
              </rect>
            );
          })}
        </svg>
      )}
      <div className="chartLabels">{series.map((p) => <span key={p.label}>{p.label}</span>)}</div>
    </div>
  );
}
