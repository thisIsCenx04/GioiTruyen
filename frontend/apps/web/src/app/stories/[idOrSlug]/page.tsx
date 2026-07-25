import { StoryApiError } from "@gioitruyen/api-client";
import type { Metadata } from "next";
import type { Route } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

import { PublicShell } from "@/components/site-chrome";
import { Comments } from "@/components/comments";
import { DonationJourney } from "@/components/donation-journey";
import { StoryRelations } from "@/components/story-relations";
import { catalog } from "@/lib/catalog";

type StoryPageProps = Readonly<{
  params: Promise<{ idOrSlug: string }>;
}>;

const loadStory = cache(async (identifier: string) => {
  try {
    const [story, chapters] = await Promise.all([
      catalog.story(identifier),
      catalog.chapters(identifier),
    ]);
    return { chapters, state: "ready" as const, story };
  } catch (error) {
    return error instanceof StoryApiError && error.problem.status === 404
      ? { state: "missing" as const }
      : { state: "unavailable" as const };
  }
});

export async function generateMetadata({
  params,
}: StoryPageProps): Promise<Metadata> {
  const { idOrSlug } = await params;
  const result = await loadStory(idOrSlug);
  if (result.state !== "ready") {
    return { title: "Không tìm thấy truyện" };
  }
  return {
    description: result.story.synopsis.slice(0, 160),
    title: result.story.title,
  };
}

export default async function StoryPage({ params }: StoryPageProps) {
  const { idOrSlug } = await params;
  const result = await loadStory(idOrSlug);
  if (result.state === "missing") notFound();
  if (result.state === "unavailable") {
    return (
      <PublicShell>
        <section className="notFound" role="status">
          <p>Thư viện đang tạm ngắt kết nối</p>
          <h1>Chưa thể mở truyện.</h1>
          <Link href="/">Trở về trang chủ</Link>
        </section>
      </PublicShell>
    );
  }
  const { chapters, story } = result;

  return (
    <PublicShell>
      <article className="storyDetail">
        <header className="storyMasthead">
          <div className="detailCover" data-tone="indigo">
            <span>{story.title.slice(0, 1)}</span>
            <small>Giới Truyện</small>
          </div>
          <div>
            <p className="detailEyebrow">
              {story.origin === "ORIGINAL"
                ? "Truyện sáng tác"
                : "Truyện chuyển ngữ"}{" "}
              · {story.completionStatus === "COMPLETED"
                ? "Đã hoàn thành"
                : "Đang xuất bản"}
            </p>
            <h1>{story.title}</h1>
            <p className="synopsis">{story.synopsis}</p>
            {chapters.items[0] && (
              <Link
                className="primaryAction"
                href={`#chapter-${chapters.items[0].number}`}
              >
                Đọc từ chương đầu
              </Link>
            )}
            <StoryRelations storyId={story.id} />
            <DonationJourney storyTitle={story.title} teamId={story.teamId} />
          </div>
        </header>

        <section className="chapterList" aria-labelledby="chapters-title">
          <header>
            <div>
              <p>Mục lục đã xuất bản</p>
              <h2 id="chapters-title">{chapters.items.length} chương</h2>
            </div>
            <span>Cập nhật mới nhất ở trên</span>
          </header>
          {chapters.items.length > 0 ? (
            <ol>
              {chapters.items.map((chapter) => (
                <li
                  id={`chapter-${chapter.number}`}
                  key={chapter.id}
                >
                  <Link href={`/read/${chapter.id}` as Route}>
                  <span>{String(chapter.number).padStart(3, "0")}</span>
                  <div>
                    <strong>{chapter.title}</strong>
                    <small>
                      {new Date(chapter.publishedAt).toLocaleDateString(
                        "vi-VN",
                      )}
                    </small>
                  </div>
                  <span aria-hidden="true">→</span>
                  </Link>
                </li>
              ))}
            </ol>
          ) : (
            <p className="emptyCatalog">
              Chưa có chương công khai. Theo dõi truyện để nhận cập nhật.
            </p>
          )}
        </section>
        <Comments targetId={story.id} targetType="STORY" />
      </article>
    </PublicShell>
  );
}
