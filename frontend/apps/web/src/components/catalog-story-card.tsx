import type { HomeStorySummary } from "@gioitruyen/api-client";
import Link from "next/link";

const tones = ["indigo", "vermilion", "teal", "amber"] as const;

export function CatalogStoryCard({
  story,
  index,
}: Readonly<{ story: HomeStorySummary; index: number }>) {
  return (
    <article className="catalogCard">
      <Link
        aria-label={`Đọc ${story.title}`}
        className="catalogCover"
        data-tone={tones[index % tones.length]}
        href={`/stories/${story.slug}`}
      >
        <span className="coverLabel">{story.title}</span>
      </Link>
      <div className="catalogCardBody">
        <h3><Link href={`/stories/${story.slug}`}>{story.title}</Link></h3>
        <p>Cập nhật {new Date(story.publishedAt).toLocaleDateString("vi-VN")}</p>
      </div>
    </article>
  );
}
