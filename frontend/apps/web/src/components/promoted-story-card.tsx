import type { PromotedHomeStory } from "@gioitruyen/api-client";
import { BadgeCheck, Bookmark, Eye, Megaphone, UsersRound } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";

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

  return (
    <article className="promotedCard">
      <Link
        aria-label={`Đọc ${story.title}`}
        className="promotedCover"
        data-tone={tones[index % tones.length]}
        href={`/truyen/${story.slug}` as Route}
      >
        <span className="featuredTag">
          <BadgeCheck aria-hidden="true" />
          {booking.tagLabel}
        </span>
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
          <Link href={`/truyen/${story.slug}` as Route}>{story.title}</Link>
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
        href={"/teams#ads-booking" as Route}
      >
        <span className="slotBadge">#{slot}</span>
        <Megaphone aria-hidden="true" />
        <strong>Chờ bạn lên top</strong>
        <small>Đăng ký Bố cáo để truyện xuất hiện tại vị trí nổi bật.</small>
      </Link>
      <div className="promotedBody">
        <h3>
          <Link href={"/teams#ads-booking" as Route}>Đăng ký Bố cáo</Link>
        </h3>
        <p className="promotedAuthor">
          <UsersRound aria-hidden="true" />
          Dành cho nhóm xuất bản
        </p>
      </div>
    </article>
  );
}
