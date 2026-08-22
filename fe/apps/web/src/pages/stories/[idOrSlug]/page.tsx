import { StoryApiError } from "@gioitruyen/api-client";
import {
  BookOpen,
  Headphones,
  List,
  MessageSquare,
  Star,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams } from "react-router-dom";
const cache = <T extends (...args: any[]) => any>(fn: T) => fn;

import { AdsenseUnit, ADSENSE_SLOTS } from "@/components/adsense-unit";
import { CatalogStoryCard } from "@/components/catalog-story-card";
import { ChapterListItem } from "@/components/chapter-list-item";
import { Comments } from "@/components/comments";
import { DonationJourney } from "@/components/donation-journey";
import { StoryRecommend } from "@/components/story-recommend";
import { PublicShell } from "@/components/site-chrome";
import { StoryRelations } from "@/components/story-relations";
import { StoryReportButton } from "@/components/story-report-button";
import { StoryShareButton } from "@/components/story-share-button";
import { StoryComboPurchase } from "@/components/story-combo-purchase";
import { coverThumbUrl, coverUrl, onCoverError, StoryCoverPlaceholder } from "@/components/story-cover";
import { loadStoryDetail } from "@/lib/catalog";

type StoryPageProps = Readonly<{
  params: Promise<{ idOrSlug: string }>;
  searchParams?: Promise<{ page?: string }>;
}>;

const numberFormatter = new Intl.NumberFormat("vi-VN");

const loadStory = cache(async (identifier: string, page: number) => {
  try {
    const res = await loadStoryDetail(identifier, page);
    if (!res) return { state: "missing" as const };
    return { ...res, state: "ready" as const };
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

/** What the admin chose when publishing, as the reader sees it. */
const STORY_TYPE_LABELS: Record<string, string> = {
  AUDIO: "Truyện audio",
  EXCLUSIVE: "Truyện độc quyền",
  ORIGINAL: "Truyện sáng tác",
  TEXT: "Truyện chữ",
};

function pageWindow(currentPage: number, totalPages: number) {
  const pages = new Set([1, totalPages, currentPage - 1, currentPage, currentPage + 1]);
  return [...pages]
    .filter((page) => page >= 1 && page <= totalPages)
    .sort((left, right) => left - right);
}

import { ScrollToChapterList } from "@/components/scroll-to-chapter-list";
import { StoryDescription } from "@/components/story-description";

export default async function StoryPage({ params, searchParams }: StoryPageProps) {
  const { idOrSlug } = await params;
  const query = await searchParams;
  const requestedPage = Number(query?.page ?? "1");
  const askedPage = Number.isFinite(requestedPage) ? Math.max(Math.trunc(requestedPage), 1) : 1;
  const result = await loadStory(idOrSlug, askedPage);
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
  // The server pages the list now, so this renders what it sent rather than
  // slicing a full list it no longer receives - a thousand-chapter story used
  // to arrive capped at 100, which put chapter 101 onwards out of reach.
  const chapterItems = result.chapters?.items ?? [];
  const pagedChapters = [...chapterItems].sort((left, right) => left.number - right.number);
  const totalChapters = result.chapters?.total ?? pagedChapters.length;
  const totalPages = Math.max(1, result.chapters?.totalPages ?? 1);
  const currentPage = Math.min(askedPage, totalPages);
  const visiblePages = pageWindow(currentPage, totalPages);
  const summary = result.summary;
  const team = result.team;
  // The ends of the whole story, not of the page being read: "Đọc tập mới" has
  // to reach the newest chapter even when the reader is looking at page one.
  const firstChapter = result.firstChapter ?? pagedChapters[0];
  const latestChapter = result.latestChapter ?? pagedChapters.at(-1);
  // The generated client predates tags, so the field is read off the raw payload.
  const storyTags: Array<{ label: string; slug: string }> =
    (story as { tags?: Array<{ label: string; slug: string }> }).tags ?? [];
  const publishedAt = new Date(story.publishedAt).toLocaleDateString("vi-VN");
  const updatedAt = new Date(story.updatedAt).toLocaleDateString("vi-VN");
  const cover = coverUrl(story.coverAssetId);

  return (
    <PublicShell>
      <main className="storyDetailPage">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link to="/">Trang chủ</Link><span>›</span><Link to="/stories">Truyện</Link><span>›</span>
          <strong>{story.title}</strong>
        </nav>

        <article className="storyDetailCard">
          <div className="detailCover storyDetailCover" data-tone={story.origin === "ORIGINAL" ? "teal" : "indigo"}>
            {/* Above the fold and the largest element on the page, so it loads
                eagerly at high priority. Lazy-loading here would delay the very
                image the reader is waiting for. */}
            {cover
              ? <img alt={`Bìa ${story.title}`} className="coverImage" src={coverThumbUrl(story.coverAssetId)} onError={onCoverError(cover)} decoding="async" fetchPriority="high" loading="eager" />
              : <StoryCoverPlaceholder />}
            {/* Same two marks as the cards, so a story is recognisable from the
                shelf it was clicked on. */}
            {story.storyType === "EXCLUSIVE" ? (
              <span className="coverTagRow"><span className="exclusiveBadge">ĐỘC QUYỀN</span></span>
            ) : null}
            {/* FULL và MỚI cùng nằm ở góc trên bên phải, đối diện nhãn độc
                quyền. Trước đây MỚI là một <small> không có class, bị luật
                chung của .detailCover bắt và thả xuống ngay trên nhãn ĐỘC
                QUYỀN - hai nhãn đè lên nhau, không đọc được nhãn nào. */}
            {story.completionStatus === "COMPLETED"
              ? <span className="fullRibbon"><span>FULL</span></span>
              : <small className="coverNewTag">MỚI</small>}
          </div>

          <div className="storyDetailMain">
            <h1>{story.title}</h1>
            <dl className="storyMetadata">
              <div><dt>Cập nhật</dt><dd>{updatedAt}</dd></div>
              {/* storyType is what the admin picked when publishing. origin is
                  merely "has an original title", which is why an exclusive
                  story used to be labelled as the author's own work. */}
              <div><dt>Loại</dt><dd><span className="metadataBadge">{STORY_TYPE_LABELS[story.storyType] ?? "Truyện chữ"}</span></dd></div>
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
              <div className="storyActionRow">
                <StoryRelations storyId={story.id} />
                <StoryRecommend storyId={story.id} />
                <DonationJourney storyTitle={story.title} teamId={story.teamId} variant="action" />
                <StoryComboPurchase
                  chaptersCount={totalChapters}
                  completionStatus={story.completionStatus}
                  storyId={story.id}
                  storyTitle={story.title}
                />
              </div>
              <div className="storyActionRow">
                {firstChapter && <Link className="storyAction storyActionStart" to={`/truyen/${story.slug}/chuong-${firstChapter.number}` as string}><BookOpen /> Đọc từ đầu</Link>}
                {latestChapter && <Link className="storyAction storyActionLatest" to={`/truyen/${story.slug}/chuong-${latestChapter.number}` as string}><Star /> Đọc tập mới</Link>}
                {/* Bản đọc được dựng từ chính chữ của chương, nên mọi truyện có
                    chương đều nghe được - không riêng truyện gắn nhãn AUDIO.
                    Trước đây điều kiện storyType === "AUDIO" giấu nút này ở gần
                    như toàn bộ kho truyện. */}
                {firstChapter && (
                  <Link className="storyAction storyActionListen" to={`/audio/${story.slug}` as string}>
                    <Headphones /> Nghe truyện
                  </Link>
                )}
                <StoryShareButton storySlug={story.slug} storyTitle={story.title} />
                <StoryReportButton targetId={story.id} />
              </div>
            </div>

            <StoryDescription synopsis={story.synopsis} />

            {/* Below the synopsis, before the chapter list - a natural break in
                the page rather than an interruption of the text. Renders
                nothing until its slot id is set; see ADSENSE_SLOTS. */}
            <AdsenseUnit
              className="storyDetailAd"
              slot={ADSENSE_SLOTS.storyDetail}
              style={{ display: "block", margin: "1rem 0" }}
            />

            {storyTags.length > 0 ? (
              <ul className="storyTagList" aria-label="Tag của truyện">
                {storyTags.map((tag: { label: string; slug: string }) => (
                  <li key={tag.slug}>
                    <Link to={`/tags/${tag.slug}` as string}>#{tag.label}</Link>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </article>

        <div className="storyDetailContentGrid">
          <div className="storyDetailPrimary">
            <section className="storyChapterPanel" aria-labelledby="chapters-title">
              <div className="storyPanelTabs">
                <a aria-current="page" href="#chapter-list"><List /> Danh sách chương</a>
                <a href="#story-comments"><MessageSquare /> Bình luận</a>
              </div>
              <ScrollToChapterList page={currentPage} />
              <div id="chapter-list">
                <header>
                  <h2 id="chapters-title">Danh sách chương</h2>
                  {/* Page size dropped: it described the pager's mechanics, not
                      the story, and the pager below already shows it. */}
                  <span>{totalChapters} chương</span>
                </header>
                {pagedChapters.length > 0 ? (
                  <>
                    {/* Same combo as the button in the story header - one purchase,
                        two places to start it, kept in sync by the component. */}
                    <StoryComboPurchase
                      chaptersCount={totalChapters}
                      completionStatus={story.completionStatus}
                      storyId={story.id}
                      storyTitle={story.title}
                      variant="row"
                    />
                    <ol>
                      {pagedChapters.map((chapter) => (
                        <ChapterListItem chapter={chapter} key={chapter.id} storySlug={story.slug} />
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
                <p className="detailEyebrow">Cùng thể loại</p>
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
