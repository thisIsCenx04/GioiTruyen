import type { ChartPoint } from "../app/admin-data";

const width = 700;
const height = 220;
const paddingX = 24;
const paddingY = 26;

function coordinates(series: ChartPoint[]) {
  const maximum = Math.max(...series.map((point) => point.value), 1);
  const usableWidth = width - (paddingX * 2);
  const usableHeight = height - (paddingY * 2);
  return series.map((point, index) => ({
    ...point,
    x: paddingX + (series.length === 1 ? usableWidth / 2 : (index / (series.length - 1)) * usableWidth),
    y: paddingY + usableHeight - ((point.value / maximum) * usableHeight),
  }));
}

function smoothPath(points: ReturnType<typeof coordinates>) {
  if (points.length === 0) return "";
  return points.slice(1).reduce((path, point, index) => {
    const previous = points[index];
    if (!previous) return path;
    const middle = (previous.x + point.x) / 2;
    return `${path} C ${middle} ${previous.y}, ${middle} ${point.y}, ${point.x} ${point.y}`;
  }, `M ${points[0]?.x ?? 0} ${points[0]?.y ?? 0}`);
}

export function PointLineChart({ series }: Readonly<{ series: ChartPoint[] }>) {
  const points = coordinates(series);
  return (
    <div className="svgChart">
      {series.length === 0 ? <p>Chưa có dữ liệu trong khoảng thời gian này.</p> : (
        <svg aria-label="Lượt truy cập theo ngày" preserveAspectRatio="none" role="img" viewBox={`0 0 ${width} ${height}`}>
          <g className="chartGrid"><line x1={paddingX} x2={width - paddingX} y1={height / 2} y2={height / 2} /><line x1={paddingX} x2={width - paddingX} y1={height - paddingY} y2={height - paddingY} /></g>
          <polyline className="trafficLine" points={points.map((point) => `${point.x},${point.y}`).join(" ")} />
          {points.map((point) => <circle className="trafficPoint" cx={point.x} cy={point.y} key={point.label} r="5"><title>{point.label}: {point.value.toLocaleString("vi-VN")}</title></circle>)}
        </svg>
      )}
      <div className="chartLabels">{series.map((point) => <span key={point.label}>{point.label}</span>)}</div>
    </div>
  );
}

export function SmoothLineChart({ series }: Readonly<{ series: ChartPoint[] }>) {
  const points = coordinates(series);
  const path = smoothPath(points);
  return (
    <div className="svgChart smoothChart">
      {series.length === 0 ? <p>Chưa có dữ liệu trong khoảng thời gian này.</p> : (
        <svg aria-label="Số reader hoạt động theo ngày" preserveAspectRatio="none" role="img" viewBox={`0 0 ${width} ${height}`}>
          <defs><linearGradient id="readerArea" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#10b7a5" stopOpacity=".3" /><stop offset="1" stopColor="#10b7a5" stopOpacity="0" /></linearGradient></defs>
          <g className="chartGrid"><line x1={paddingX} x2={width - paddingX} y1={height / 2} y2={height / 2} /><line x1={paddingX} x2={width - paddingX} y1={height - paddingY} y2={height - paddingY} /></g>
          <path className="readerArea" d={`${path} L ${points.at(-1)?.x ?? 0} ${height - paddingY} L ${points[0]?.x ?? 0} ${height - paddingY} Z`} />
          <path className="readerLine" d={path} />
          {points.map((point) => <circle className="readerPoint" cx={point.x} cy={point.y} key={point.label} r="4"><title>{point.label}: {point.value.toLocaleString("vi-VN")}</title></circle>)}
        </svg>
      )}
      <div className="chartLabels">{series.map((point) => <span key={point.label}>{point.label}</span>)}</div>
    </div>
  );
}
