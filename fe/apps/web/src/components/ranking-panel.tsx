"use client";

import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Eye } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { coverThumbUrl, coverUrl, onCoverError, StoryCoverPlaceholder } from "./story-cover";

/**
 * Orderings, not time windows.
 *
 * This panel is handed a plain list of stories - never a time-scoped one - so it
 * can only re-sort what it already has. The tabs used to read Ngày / Tuần /
 * Tháng / Năm, which promised windows the component never applied, and the
 * "Năm" sort keyed on a field the API does not return (`ratingCount`), so it
 * silently did nothing. Real per-period boards are computed server-side and
 * live on /rankings.
 */
type Ordering = "newest" | "views" | "saves";

const ORDERINGS: ReadonlyArray<{ id: Ordering; label: string }> = [
  { id: "newest", label: "Mới" },
  { id: "views", label: "Xem nhiều" },
  { id: "saves", label: "Lưu nhiều" },
];

const ROW_LIMIT = 8;

const compactNumber = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 1,
  notation: "compact",
});

function sortFor(ordering: Ordering, stories: readonly HomeStorySummary[]) {
  const rows = [...stories];
  if (ordering === "views") return rows.sort((a, b) => (b.viewCount ?? 0) - (a.viewCount ?? 0));
  if (ordering === "saves") return rows.sort((a, b) => (b.saveCount ?? 0) - (a.saveCount ?? 0));
  return rows.sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt));
}

export function RankingPanel({
  stories,
  title = "Bảng xếp hạng",
}: Readonly<{ stories: readonly HomeStorySummary[]; title?: string }>) {
  const [ordering, setOrdering] = useState<Ordering>("newest");
  const rows = sortFor(ordering, stories).slice(0, ROW_LIMIT);

  return (
    <aside className="rankingPanel" aria-labelledby="ranking-panel-title">
      <h2 id="ranking-panel-title">{title}</h2>
      <div className="rankingTabs" role="tablist" aria-label="Sắp xếp bảng xếp hạng">
        {ORDERINGS.map((entry) => (
          <button
            aria-selected={ordering === entry.id}
            className={ordering === entry.id ? "isActive" : ""}
            key={entry.id}
            onClick={() => setOrdering(entry.id)}
            role="tab"
            type="button"
          >
            {entry.label}
          </button>
        ))}
      </div>
      {rows.length === 0 ? (
        <p className="rankingEmpty">Chưa có dữ liệu xếp hạng.</p>
      ) : (
        <ol>
          {rows.map((story, index) => {
            const cover = coverUrl(story.coverAssetId);
            const href = `/truyen/${story.slug}`;
            return (
              <li key={story.id}>
                <b>{index + 1}</b>
                {/* The real cover. This used to render the title's first letter on
                    a tinted block, which read as a placeholder for every story. */}
                <Link aria-label={`Mở ${story.title}`} className="rankingThumb" to={href}>
                  {cover ? (
                    <img
                      alt=""
                      aria-hidden="true"
                      decoding="async"
                      loading="lazy"
                      onError={onCoverError(cover)}
                      src={coverThumbUrl(story.coverAssetId)}
                    />
                  ) : (
                    <StoryCoverPlaceholder />
                  )}
                </Link>
                <div>
                  <Link to={href}>{story.title}</Link>
                  {/* Eye icon means views, so it carries the view count - it used to
                      sit next to a publish date, which the icon contradicted. */}
                  <em>
                    <Eye aria-hidden="true" />
                    {compactNumber.format(story.viewCount ?? 0)}
                  </em>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </aside>
  );
}
