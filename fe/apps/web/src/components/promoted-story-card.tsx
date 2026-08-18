import type { PromotedHomeStory } from "@gioitruyen/api-client";
import { Bookmark, Eye, Megaphone, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";

import { coverThumbUrl, coverUrl, onCoverError, StoryCoverPlaceholder } from "./story-cover";

const tones = ["indigo", "vermilion", "teal", "amber"] as const;
const numberFormatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 1,
  notation: "compact",
});

export function PromotedStoryCard({
  booking,
  index,
}: Readonly<{ booking: PromotedHomeStory; index: number }>) {
  const story = booking.story;
  const cover = coverUrl(story.coverAssetId);
  const thumb = coverThumbUrl(story.coverAssetId);
  const isFull = story.progressStatus === "COMPLETED";

  return (
    <article className="promotedCard">
      <Link
        aria-label={`Đọc ${story.title}`}
        className="promotedCover"
        data-tone={tones[index % tones.length]}
        to={`/truyen/${story.slug}` as string}
      >
        {cover ? (
          <img
            alt={`Bìa ${story.title}`}
            className="promotedCoverImage"
            decoding="async"
            /* This shelf sits at the top of the home page, so its covers are
               fetched straight away rather than on scroll. */
            fetchPriority={index < 6 ? "high" : "auto"}
            loading={index < 6 ? "eager" : "lazy"}
            onError={onCoverError(cover)}
            src={thumb}
          />
        ) : (
          <StoryCoverPlaceholder />
        )}
        <div className="promotedCoverOverlay" />
        {/* The "Nổi bật" tag said only that the card was on the promoted shelf,
            which the shelf's own heading already says. The two marks that carry
            information about the story travel with it instead. */}
        {story.storyType === "EXCLUSIVE" ? (
          <span className="promotedExclusiveTag">ĐỘC QUYỀN</span>
        ) : null}
        {isFull ? <span className="fullRibbon"><span>FULL</span></span> : null}
        <span className="slotBadge">#{booking.slotPosition}</span>
        <span className="promotedCoverTitle">{story.title}</span>
        <span className="promotedMetric">
          <span title="Lượt xem">
            <Eye aria-hidden="true" /> {numberFormatter.format(story.viewCount ?? 0)}
          </span>
          <span title="Reader lưu vào tủ truyện">
            <Bookmark aria-hidden="true" /> {numberFormatter.format(story.saveCount ?? 0)}
          </span>
        </span>
      </Link>
      <div className="promotedBody">
        <h3>
          <Link to={`/truyen/${story.slug}` as string}>{story.title}</Link>
        </h3>
        <p className="promotedAuthor">
          <UsersRound aria-hidden="true" />
          {story.teamName ?? "Nhóm Giới Truyện"}
        </p>
      </div>
    </article>
  );
}

export function PromotedEmptySlot({
  slot,
}: Readonly<{ slot: number }>) {
  return (
    <article className="promotedCard promotedEmptyCard">
      <Link
        aria-label={`Đăng ký Bố cáo slot ${slot}`}
        className="promotedEmptyCover"
        to={"/bo-cao" as string}
      >
        <span className="slotBadge">#{slot}</span>
        <Megaphone aria-hidden="true" />
        <strong>Chờ bạn lên top</strong>
        <small>Đăng ký Bố cáo để đưa tác phẩm vào danh sách đề cử nổi bật nhất.</small>
      </Link>
      <div className="promotedBody">
        <h3>
          <Link to={"/bo-cao" as string}>Đăng ký Bố cáo</Link>
        </h3>
        <p className="promotedAuthor">
          <UsersRound aria-hidden="true" />
          Dành cho nhóm xuất bản
        </p>
      </div>
    </article>
  );
}
