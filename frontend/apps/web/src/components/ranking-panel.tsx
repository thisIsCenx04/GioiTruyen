import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Eye } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";

export function RankingPanel({
  stories,
  title = "Bảng xếp hạng",
}: Readonly<{ stories: readonly HomeStorySummary[]; title?: string }>) {
  return (
    <aside className="rankingPanel" aria-labelledby="ranking-panel-title">
      <h2 id="ranking-panel-title">{title}</h2>
      <div className="rankingTabs" aria-label="Khoảng thời gian xếp hạng">
        <b>Ngày</b>
        <span>Tuần</span>
        <span>Tháng</span>
        <span>Năm</span>
      </div>
      <ol>
        {stories.slice(0, 8).map((story, index) => (
          <li key={story.id}>
            <b>{index + 1}</b>
            <Link
              aria-label={`Mở ${story.title}`}
              className="rankingThumb"
              data-tone={index % 4}
              href={`/stories/${story.slug}` as Route}
            >
              <span>{story.title.slice(0, 1)}</span>
            </Link>
            <div>
              <Link href={`/stories/${story.slug}` as Route}>{story.title}</Link>
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
