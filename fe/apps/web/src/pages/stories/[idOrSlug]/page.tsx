import { StoryApiError } from "@gioitruyen/api-client";
import {
  BookOpen,
  List,
  MessageSquare,
  Star,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams } from "react-router-dom";
const cache = <T extends (...args: any[]) => any>(fn: T) => fn;

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { Comments } from "@/components/comments";
import { DonationJourney } from "@/components/donation-journey";
import { PublicShell } from "@/components/site-chrome";
import { StoryRelations } from "@/components/story-relations";
import { StoryReportButton } from "@/components/story-report-button";
import { loadStoryDetail } from "@/lib/catalog";

type StoryPageProps = Readonly<{
  params: Promise<{ idOrSlug: string }>;
  searchParams?: Promise<{ page?: string }>;
}>;

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

const chaptersPerPage = 20;

function pageWindow(currentPage: number, totalPages: number) {
  const pages = new Set([1, totalPages, currentPage - 1, currentPage, currentPage + 1]);
  return [...pages]
    .filter((page) => page >= 1 && page <= totalPages)
    .sort((left, right) => left - right);
}

/* generateMetadata removed */

export default async function StoryPage({ params, searchParams }: StoryPageProps) {
  const { idOrSlug } = await params;
  const query = await searchParams;
  const result = await loadStory(idOrSlug);
  if (result.state === "missing" || (result.state === "ready" && !result.story)) throw new Error("Not Found");
  if (result.state === "unavailable") {
    return (
      <PublicShell>
        <section className="notFound" role="status">
          <p>Thư viện đang tạm ngắt kết nối</p>
          <h1>Chưa thể mở truyện.</h1>
          <Link to="/">Trở về trang chủ</Link>
        </section>
      </PublicShell>
    );
  }

  const story = result.story;
  if (!story) throw new Error("Not Found");
  const categories = result.categories ?? [];
  const relatedStories = result.relatedStories ?? [];
  const chapterItems = result.chapters?.items ?? [];
  const orderedChapters = [...chapterItems].sort((left, right) => left.number - right.number);
  const totalPages = Math.max(1, Math.ceil(orderedChapters.length / chaptersPerPage));
  const requestedPage = Number(query?.page ?? "1");
  const currentPage = Number.isFinite(requestedPage)
    ? Math.min(Math.max(Math.trunc(requestedPage), 1), totalPages)
    : 1;
  const pagedChapters = orderedChapters.slice(
    (currentPage - 1) * chaptersPerPage,
    currentPage * chaptersPerPage,
  );
  const visiblePages = pageWindow(currentPage, totalPages);
  const summary = result.summary;
  const team = result.team;
  const firstChapter = orderedChapters[0];
  const latestChapter = orderedChapters.at(-1);
  const publishedAt = new Date(story.publishedAt).toLocaleDateString("vi-VN");
  const updatedAt = new Date(story.updatedAt).toLocaleDateString("vi-VN");

  return (
    <PublicShell>
      <main className="storyDetail storyDetailPage">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link to="/">Trang chủ</Link><span>›</span><Link to="/stories">Truyện</Link><span>›</span>
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
                    ? categories.map((category: any) => <Link to={`/categories/${category.slug}` as string} key={category.id}>{category.name}</Link>)
                    : <span>Đang cập nhật</span>}
                </dd>
              </div>
              <div><dt>Nhóm đăng</dt><dd><Link className="teamBadge" to={`/teams/${story.teamId}` as string}>{team?.name ?? "Nhóm Giới Truyện"}</Link></dd></div>
              <div><dt>Lượt xem</dt><dd>{numberFormatter.format(summary?.viewCount ?? 0)}</dd></div>
              <div><dt>Đã lưu</dt><dd>{numberFormatter.format(summary?.saveCount ?? 0)}</dd></div>
              <div><dt>Trạng thái</dt><dd>{statusLabel(story.completionStatus)}</dd></div>
              <div><dt>Ngày đăng</dt><dd>{publishedAt}</dd></div>
            </dl>

            <div className="storyActionBar" aria-label="Thao tác với truyện">
              <StoryRelations storyId={story.id} />
              <DonationJourney storyTitle={story.title} teamId={story.teamId} variant="action" />
              {firstChapter && <Link className="storyAction storyActionStart" to={`/truyen/${story.slug}/chuong-${firstChapter.number}` as string}><BookOpen /> Đọc từ đầu</Link>}
              {latestChapter && <Link className="storyAction storyActionLatest" to={`/truyen/${story.slug}/chuong-${latestChapter.number}` as string}><Star /> Đọc tập mới</Link>}
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
                  <span>{chapterItems.length} chương · {chaptersPerPage} chương/trang</span>
                </header>
                {orderedChapters.length > 0 ? (
                  <>
                    <ol>
                      {pagedChapters.map((chapter) => (
                        <li key={chapter.id}>
                          <Link to={`/truyen/${story.slug}/chuong-${chapter.number}` as string}>
                            <span>Chương {chapter.number}</span>
                            <strong>{chapter.title}</strong>
                            <time dateTime={chapter.publishedAt}>{new Date(chapter.publishedAt).toLocaleDateString("vi-VN")}</time>
                          </Link>
                        </li>
                      ))}
                    </ol>
                    {totalPages > 1 && (
                      <nav className="chapterPagination" aria-label="Phân trang chương">
                        {currentPage > 1 && (
                          <Link to={`/truyen/${story.slug}?page=${currentPage - 1}` as string}>Trước</Link>
                        )}
                        {visiblePages.map((page, index) => (
                          <span className="chapterPageGroup" key={page}>
                            {index > 0 && page - (visiblePages[index - 1] ?? page) > 1 && <em>...</em>}
                            <Link
                              aria-current={page === currentPage ? "page" : undefined}
                              to={`/truyen/${story.slug}?page=${page}` as string}
                            >
                              {page}
                            </Link>
                          </span>
                        ))}
                        {currentPage < totalPages && (
                          <Link to={`/truyen/${story.slug}?page=${currentPage + 1}` as string}>Sau</Link>
                        )}
                      </nav>
                    )}
                  </>
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
              {relatedStories.map((relatedStory: any, index: number) => (
                <CatalogStoryCard index={index} key={relatedStory.id} story={relatedStory} />
              ))}
            </div>
          </aside>
        </div>
      </main>
    </PublicShell>
  );
}
