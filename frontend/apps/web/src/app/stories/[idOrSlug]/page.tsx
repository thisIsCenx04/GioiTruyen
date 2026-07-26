import { StoryApiError } from "@gioitruyen/api-client";
import { BookOpen, CalendarDays, Languages, Layers3 } from "lucide-react";
import type { Metadata, Route } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

import { Comments } from "@/components/comments";
import { DonationJourney } from "@/components/donation-journey";
import { PublicShell } from "@/components/site-chrome";
import { StoryRelations } from "@/components/story-relations";
import { catalog } from "@/lib/catalog";

type StoryPageProps = Readonly<{ params: Promise<{ idOrSlug: string }> }>;

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

export async function generateMetadata({ params }: StoryPageProps): Promise<Metadata> {
  const { idOrSlug } = await params;
  const result = await loadStory(idOrSlug);
  return result.state === "ready"
    ? { description: result.story.synopsis.slice(0, 160), title: result.story.title }
    : { title: "Không tìm thấy truyện" };
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
  const firstChapter = chapters.items.at(-1) ?? chapters.items[0];

  return (
    <PublicShell>
      <article className="storyDetail">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link href="/">Trang chủ</Link><span>›</span><Link href="/search">Truyện</Link><span>›</span>
          <strong>{story.title}</strong>
        </nav>
        <header className="storyMasthead">
          <div className="detailCover" data-tone="indigo">
            <span>{story.title.slice(0, 1)}</span>
            <small>{story.completionStatus === "COMPLETED" ? "HOÀN THÀNH" : "ĐANG RA"}</small>
          </div>
          <div className="storySummary">
            <p className="detailEyebrow">
              {story.origin === "ORIGINAL" ? "Truyện sáng tác" : "Truyện chuyển ngữ"}
            </p>
            <h1>{story.title}</h1>
            <div className="storyTags">
              <span>{story.completionStatus === "COMPLETED" ? "Hoàn thành" : "Đang xuất bản"}</span>
              <span>{story.origin === "ORIGINAL" ? "Nguyên bản" : "Chuyển ngữ"}</span>
              <span>{story.language.toLocaleUpperCase("vi")}</span>
            </div>
            <p className="synopsis">{story.synopsis}</p>
            {firstChapter && (
              <Link className="primaryAction" href={`/read/${firstChapter.id}` as Route}>
                <BookOpen aria-hidden="true" /> Đọc ngay
              </Link>
            )}
            <DonationJourney storyTitle={story.title} teamId={story.teamId} />
          </div>
          <aside className="storyInfo">
            <h2>Thông tin truyện</h2>
            <dl>
              <div><dt><Layers3 /> Trạng thái</dt><dd>{story.completionStatus}</dd></div>
              <div><dt><Languages /> Ngôn ngữ</dt><dd>{story.language.toUpperCase()}</dd></div>
              <div><dt><BookOpen /> Số chương</dt><dd>{chapters.items.length}</dd></div>
              <div><dt><CalendarDays /> Ngày đăng</dt><dd>{new Date(story.publishedAt).toLocaleDateString("vi-VN")}</dd></div>
              <div><dt><CalendarDays /> Cập nhật</dt><dd>{new Date(story.updatedAt).toLocaleDateString("vi-VN")}</dd></div>
            </dl>
          </aside>
        </header>

        <div className="storyContentGrid">
          <section className="chapterList" aria-labelledby="chapters-title">
            <header>
              <div><p className="detailEyebrow">Mục lục đã xuất bản</p>
                <h2 id="chapters-title">Danh sách chương</h2></div>
              <span>{chapters.items.length} chương</span>
            </header>
            {chapters.items.length > 0 ? (
              <ol>{chapters.items.map((chapter) => (
                <li id={`chapter-${chapter.number}`} key={chapter.id}>
                  <Link href={`/read/${chapter.id}` as Route}>
                    <span>{String(chapter.number).padStart(3, "0")}</span>
                    <div><strong>{chapter.title}</strong><small>{new Date(chapter.publishedAt).toLocaleDateString("vi-VN")}</small></div>
                    <span aria-hidden="true">→</span>
                  </Link>
                </li>
              ))}</ol>
            ) : <p className="emptyCatalog">Chưa có chương công khai.</p>}
          </section>
          <StoryRelations storyId={story.id} />
        </div>
        <Comments targetId={story.id} targetType="STORY" />
      </article>
    </PublicShell>
  );
}
