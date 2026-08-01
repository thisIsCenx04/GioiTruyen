import { StoryApiError } from "@gioitruyen/api-client";
import {
  BookOpen,
  List,
  MessageSquare,
  Star,
} from "lucide-react";
import type { Metadata, Route } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { Comments } from "@/components/comments";
import { DonationJourney } from "@/components/donation-journey";
import { PublicShell } from "@/components/site-chrome";
import { StoryRelations } from "@/components/story-relations";
import { StoryReportButton } from "@/components/story-report-button";
import { loadStoryDetail } from "@/lib/catalog";

type StoryPageProps = Readonly<{ params: Promise<{ idOrSlug: string }> }>;

const numberFormatter = new Intl.NumberFormat("vi-VN");

const loadStory = cache(async (identifier: string) => {
  try {
    return { ...(await loadStoryDetail(identifier)), state: "ready" as const };
  } catch (error) {
    return error instanceof StoryApiError && error.problem.status === 404
      ? { state: "missing" as const }
      : { state: "unavailable" as const };
  }
});

function statusLabel(status: "ONGOING" | "COMPLETED" | "HIATUS") {
  if (status === "COMPLETED") return "Đã hoàn thành";
  if (status === "HIATUS") return "Tạm ngưng";
  return "Đang cập nhật";
}

export async function generateMetadata({ params }: StoryPageProps): Promise<Metadata> {
  const { idOrSlug } = await params;
  const result = await loadStory(idOrSlug);
  return result.state === "ready" && result.story
    ? { description: result.story.synopsis.slice(0, 160), title: result.story.title }
    : { title: "Không tìm thấy truyện" };
}

export default async function StoryPage({ params }: StoryPageProps) {
  const { idOrSlug } = await params;
  const result = await loadStory(idOrSlug);
  if (result.state === "missing" || (result.state === "ready" && !result.story)) notFound();
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

  const story = result.story;
  if (!story) notFound();
  const categories = result.categories ?? [];
  const relatedStories = result.relatedStories ?? [];
  const chapterItems = result.chapters?.items ?? [];
  const orderedChapters = [...chapterItems].sort((left, right) => right.number - left.number);
  const summary = result.summary;
  const team = result.team;
  const firstChapter = orderedChapters.at(-1);
  const latestChapter = orderedChapters[0];
  const publishedAt = new Date(story.publishedAt).toLocaleDateString("vi-VN");
  const updatedAt = new Date(story.updatedAt).toLocaleDateString("vi-VN");

  return (
    <PublicShell>
      <main className="storyDetail storyDetailPage">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link href="/">Trang chủ</Link><span>›</span><Link href="/stories">Truyện</Link><span>›</span>
          <strong>{story.title}</strong>
        </nav>

        <article className="storyDetailCard">
          <div className="detailCover storyDetailCover" data-tone={story.origin === "ORIGINAL" ? "teal" : "indigo"}>
            <span>{story.title.slice(0, 1)}</span>
            <small>{story.completionStatus === "COMPLETED" ? "FULL" : "MỚI"}</small>
          </div>

          <div className="storyDetailMain">
            <h1>{story.title}</h1>
            <dl className="storyMetadata">
              <div><dt>Cập nhật</dt><dd>{updatedAt}</dd></div>
              <div><dt>Loại</dt><dd><span className="metadataBadge">{story.origin === "ORIGINAL" ? "Truyện sáng tác" : "Truyện chuyển ngữ"}</span></dd></div>
              <div>
                <dt>Thể loại</dt>
                <dd className="metadataCategories">
                  {categories.length > 0
                    ? categories.map((category) => <Link href={`/categories/${category.slug}` as Route} key={category.id}>{category.name}</Link>)
                    : <span>Đang cập nhật</span>}
                </dd>
              </div>
              <div><dt>Nhóm đăng</dt><dd><Link className="teamBadge" href={`/teams/${story.teamId}` as Route}>{team?.name ?? "Nhóm Giới Truyện"}</Link></dd></div>
              <div><dt>Lượt xem</dt><dd>{numberFormatter.format(summary?.viewCount ?? 0)}</dd></div>
              <div><dt>Đã lưu</dt><dd>{numberFormatter.format(summary?.saveCount ?? 0)}</dd></div>
              <div><dt>Trạng thái</dt><dd>{statusLabel(story.completionStatus)}</dd></div>
              <div><dt>Ngày đăng</dt><dd>{publishedAt}</dd></div>
            </dl>

            <div className="storyActionBar" aria-label="Thao tác với truyện">
              <StoryRelations storyId={story.id} />
              <DonationJourney storyTitle={story.title} teamId={story.teamId} variant="action" />
              {firstChapter && <Link className="storyAction storyActionStart" href={`/truyen/${story.slug}/chuong-${firstChapter.number}` as Route}><BookOpen /> Đọc từ đầu</Link>}
              {latestChapter && <Link className="storyAction storyActionLatest" href={`/truyen/${story.slug}/chuong-${latestChapter.number}` as Route}><Star /> Đọc tập mới</Link>}
              <StoryReportButton targetId={story.id} />
            </div>

            <p className="storyDescription">{story.synopsis}</p>
          </div>
        </article>

        <div className="storyDetailContentGrid">
          <div className="storyDetailPrimary">
            <section className="storyChapterPanel" aria-labelledby="chapters-title">
              <div className="storyPanelTabs">
                <a aria-current="page" href="#chapter-list"><List /> Danh sách chương</a>
                <a href="#story-comments"><MessageSquare /> Bình luận</a>
              </div>
              <div id="chapter-list">
                <header>
                  <h2 id="chapters-title">Danh sách chương</h2>
                  <span>{chapterItems.length} chương</span>
                </header>
                {orderedChapters.length > 0 ? (
                  <ol>
                    {orderedChapters.map((chapter) => (
                      <li key={chapter.id}>
                        <Link href={`/truyen/${story.slug}/chuong-${chapter.number}` as Route}>
                          <span>Chương {chapter.number}</span>
                          <strong>{chapter.title}</strong>
                          <time dateTime={chapter.publishedAt}>{new Date(chapter.publishedAt).toLocaleDateString("vi-VN")}</time>
                        </Link>
                      </li>
                    ))}
                  </ol>
                ) : <p className="emptyCatalog">Truyện chưa có chương công khai.</p>}
              </div>
            </section>

            <div id="story-comments"><Comments targetId={story.id} targetType="STORY" /></div>
          </div>

          <aside className="storyRecommendations" aria-labelledby="recommendations-title">
            <header>
              <div>
                <p className="detailEyebrow">Dành cho bạn</p>
                <h2 id="recommendations-title">Truyện tương tự</h2>
              </div>
            </header>
            <div className="storyRecommendationGrid">
              {relatedStories.map((relatedStory, index) => (
                <CatalogStoryCard index={index} key={relatedStory.id} story={relatedStory} />
              ))}
            </div>
          </aside>
        </div>
      </main>
    </PublicShell>
  );
}
