import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Bookmark, Eye, Volume2 } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";

const tones = ["indigo", "vermilion", "teal", "amber"] as const;
const numberFormatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 1,
  notation: "compact",
});

export function CatalogStoryCard({
  story,
  index,
  metricIcon = "view",
  hrefBase = "/stories",
}: Readonly<{
  story: HomeStorySummary;
  index: number;
  metricIcon?: "audio" | "view";
  hrefBase?: "/audio" | "/stories";
}>) {
  const publishedDate = new Date(story.publishedAt).toLocaleDateString("vi-VN");
  const PrimaryMetricIcon = metricIcon === "audio" ? Volume2 : Eye;
  const href = hrefBase === "/stories"
    ? (`/truyen/${story.slug}` as Route)
    : (`${hrefBase}/${story.slug}` as Route);

  return (
    <article className="catalogCard">
      <Link
        aria-label={`Đọc ${story.title}`}
        className="catalogCover"
        data-tone={tones[index % tones.length]}
        href={href}
      >
        <span className="coverMetric">
          <span title={metricIcon === "audio" ? "Lượt nghe" : "Lượt xem"}>
            <PrimaryMetricIcon aria-hidden="true" /> {numberFormatter.format(story.viewCount ?? 0)}
          </span>
          <span title="Độc giả lưu vào tủ truyện">
            <Bookmark aria-hidden="true" /> {numberFormatter.format(story.saveCount ?? 0)}
          </span>
        </span>
        <span className="coverLabel">{story.title}</span>
      </Link>
      <div className="catalogCardBody">
        <h3>
          <Link href={href}>{story.title}</Link>
        </h3>
        <p>Chương mới - {publishedDate}</p>
      </div>
    </article>
  );
}
