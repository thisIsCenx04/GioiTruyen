import type { HomeStorySummary } from "@gioitruyen/api-client";
import Link from "next/link";

const tones = ["indigo", "vermilion", "teal", "amber"] as const;

export function CatalogStoryCard({
  story,
  index,
}: Readonly<{ story: HomeStorySummary; index: number }>) {
  const initial = story.title.trim().slice(0, 1).toLocaleUpperCase("vi");

  return (
    <article className="catalogCard">
      <Link
        aria-label={`Đọc ${story.title}`}
        className="catalogCover"
        data-tone={tones[index % tones.length]}
        href={`/stories/${story.slug}`}
      >
        <span className="bookSpine" aria-hidden="true">
          {String(index + 1).padStart(2, "0")}
        </span>
        <span className="coverInitial" aria-hidden="true">
          {initial}
        </span>
        <span className="coverLabel">Giới Truyện</span>
      </Link>
      <div className="catalogCardBody">
        <p>{new Date(story.publishedAt).toLocaleDateString("vi-VN")}</p>
        <h3>
          <Link href={`/stories/${story.slug}`}>{story.title}</Link>
        </h3>
        <Link className="readLink" href={`/stories/${story.slug}`}>
          Mở truyện <span aria-hidden="true">→</span>
        </Link>
      </div>
    </article>
  );
}
