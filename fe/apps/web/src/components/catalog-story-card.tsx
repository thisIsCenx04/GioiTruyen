import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Bookmark, Clock, Eye, Volume2 } from "lucide-react";
import { Link } from "react-router-dom";

import { coverThumbUrl, coverUrl, onCoverError, StoryCoverPlaceholder } from "./story-cover";

const tones = ["indigo", "vermilion", "teal", "amber"] as const;
const numberFormatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 1,
  notation: "compact",
});

function formatRelativeTime(dateStr?: string): string {
  if (!dateStr) return "Vừa xong";
  const now = new Date().getTime();
  const past = new Date(dateStr).getTime();
  const diffMinutes = Math.floor((now - past) / (1000 * 60));
  if (diffMinutes < 1) return "Vừa xong";
  if (diffMinutes < 60) return `${diffMinutes} phút trước`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} giờ trước`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 30) return `${diffDays} ngày trước`;
  const diffMonths = Math.floor(diffDays / 30);
  return `${diffMonths} tháng trước`;
}

export function CatalogStoryCard({
  story,
  index,
  metricIcon = "view",
  hrefBase = "/stories",
  rank,
  updatedText,
  isFull,
}: Readonly<{
  story: HomeStorySummary;
  index: number;
  metricIcon?: "audio" | "view";
  hrefBase?: "/audio" | "/stories";
  rank?: number;
  updatedText?: string;
  isFull?: boolean;
}>) {
  const cover = coverUrl(story.coverAssetId);
  const thumb = coverThumbUrl(story.coverAssetId);
  const href = hrefBase === "/stories"
    ? (`/truyen/${story.slug}` as string)
    : (`${hrefBase}/${story.slug}` as string);

  const fullState = isFull || story.progressStatus === "COMPLETED" || (story as any).completionStatus === "COMPLETED" || (story as any).isFull;
  const chapterNum = (story as any).latestChapterNumber || (story as any).chapterCount || (story as any).chaptersCount || 1;
  const displayTime = updatedText || formatRelativeTime(story.publishedAt || (story as any).updatedAt);

  return (
    <article className="catalogCard">
      <Link
        aria-label={`Đọc ${story.title}`}
        className="catalogCover"
        data-tone={tones[index % tones.length]}
        to={href}
      >
        {rank !== undefined ? <span className="rankBadge">{rank}</span> : null}
        {/* Two independent facts that often hold at once, so each has its own
            corner: FULL says the story is finished, ĐỘC QUYỀN where it can be
            read. Sharing a row let a long label push the other one across the
            artwork. */}
        {story.storyType === "EXCLUSIVE" ? (
          <span className="coverTagRow"><span className="exclusiveBadge">ĐỘC QUYỀN</span></span>
        ) : null}
        {fullState ? <span className="fullRibbon"><span>FULL</span></span> : null}
        {cover
          ? (
            <img
              alt={`Bìa ${story.title}`}
              className="coverImage"
              decoding="async"
              /* The first row is what the reader is already looking at, so it
                 does not wait for the lazy-load observer. */
              fetchPriority={index < 6 ? "high" : "auto"}
              loading={index < 6 ? "eager" : "lazy"}
              onError={onCoverError(cover)}
              src={thumb}
            />
          )
          : <StoryCoverPlaceholder />}
        <div className="coverMetricOverlay">
          <span>
            <Eye size={13} aria-hidden="true" />
            {numberFormatter.format(story.viewCount ?? 0)}
          </span>
          <span>
            <Bookmark size={13} aria-hidden="true" />
            {numberFormatter.format(story.saveCount ?? 0)}
          </span>
        </div>
      </Link>
      <div className="catalogCardBody">
        <h3 title={story.title}>
          <Link to={href}>{story.title}</Link>
        </h3>
        <div className="catalogCardMetaRow">
          <span className="metaChapter">Chương {chapterNum}</span>
          <span className="metaTime">{displayTime}</span>
        </div>
      </div>
    </article>
  );
}
