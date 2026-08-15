"use client";

import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Eye } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

type Period = "day" | "week" | "month" | "year";

export function RankingPanel({
  stories,
  title = "Bảng xếp hạng",
}: Readonly<{ stories: readonly HomeStorySummary[]; title?: string }>) {
  const [period, setPeriod] = useState<Period>("day");

  // React State sorting/filtering without page reload
  const sortedStories = [...stories].sort((a, b) => {
    if (period === "week") return (b.saveCount ?? 0) - (a.saveCount ?? 0);
    if (period === "month") return (b.viewCount ?? 0) - (a.viewCount ?? 0);
    if (period === "year") return ((((b as Record<string, unknown>).ratingCount as number) ?? 0) - (((a as Record<string, unknown>).ratingCount as number) ?? 0));
    return new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime();
  });

  return (
    <aside className="rankingPanel" aria-labelledby="ranking-panel-title">
      <h2 id="ranking-panel-title">{title}</h2>
      <div className="rankingTabs" aria-label="Khoảng thời gian xếp hạng">
        <button
          type="button"
          className={period === "day" ? "activeTab" : ""}
          onClick={() => setPeriod("day")}
          style={{ background: period === "day" ? "var(--accent)" : "transparent", color: period === "day" ? "#fff" : "inherit", border: 0, borderRadius: "0.3rem", padding: "0.3rem 0.6rem", fontWeight: 700, cursor: "pointer" }}
        >
          Ngày
        </button>
        <button
          type="button"
          className={period === "week" ? "activeTab" : ""}
          onClick={() => setPeriod("week")}
          style={{ background: period === "week" ? "var(--accent)" : "transparent", color: period === "week" ? "#fff" : "inherit", border: 0, borderRadius: "0.3rem", padding: "0.3rem 0.6rem", fontWeight: 700, cursor: "pointer" }}
        >
          Tuần
        </button>
        <button
          type="button"
          className={period === "month" ? "activeTab" : ""}
          onClick={() => setPeriod("month")}
          style={{ background: period === "month" ? "var(--accent)" : "transparent", color: period === "month" ? "#fff" : "inherit", border: 0, borderRadius: "0.3rem", padding: "0.3rem 0.6rem", fontWeight: 700, cursor: "pointer" }}
        >
          Tháng
        </button>
        <button
          type="button"
          className={period === "year" ? "activeTab" : ""}
          onClick={() => setPeriod("year")}
          style={{ background: period === "year" ? "var(--accent)" : "transparent", color: period === "year" ? "#fff" : "inherit", border: 0, borderRadius: "0.3rem", padding: "0.3rem 0.6rem", fontWeight: 700, cursor: "pointer" }}
        >
          Năm
        </button>
      </div>
      <ol>
        {sortedStories.slice(0, 8).map((story, index) => (
          <li key={story.id}>
            <b>{index + 1}</b>
            <Link
              aria-label={`Mở ${story.title}`}
              className="rankingThumb"
              data-tone={index % 4}
              to={`/truyen/${story.slug}` as string}
            >
              <span>{story.title.slice(0, 1)}</span>
            </Link>
            <div>
              <Link to={`/truyen/${story.slug}` as string}>{story.title}</Link>
              <small>Chương mới</small>
            </div>
            <em>
              <Eye aria-hidden="true" />
              {new Date(story.publishedAt).toLocaleDateString("vi-VN")}
            </em>
          </li>
        ))}
      </ol>
    </aside>
  );
}
