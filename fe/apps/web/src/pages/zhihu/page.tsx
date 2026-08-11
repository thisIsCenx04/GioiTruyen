import type { HomeStorySummary } from "@gioitruyen/api-client";
import { ArrowRight, BookOpenText, Clock3, MessageSquareQuote } from "lucide-react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { CommunityChat } from "@/components/community-chat";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { loadZhihu } from "@/lib/catalog";

export const revalidate = 120;

export default async function ZhihuPage() {
  const data = await loadZhihu().catch(() => null);

  const sections = (data?.sections ?? []) as Array<{
    id: string;
    tag: string;
    title: string;
    stories: HomeStorySummary[];
  }>;
  const rankingBoards = data?.rankingBoards ?? [];

  // Every one-page story the page knows about, de-duplicated across shelves.
  const allStories = [
    ...new Map(
      sections.flatMap((section) => section.stories).map((story) => [story.id, story] as const),
    ).values(),
  ];

  // An outage and an empty shelf look identical once the fallbacks kick in, so a
  // failed load says so rather than claiming there are no stories.
  if (!data || (data.degraded && allStories.length === 0)) {
    return (
      <PublicShell>
        <section className="notFound" role="status">
          <p>Góc truyện ngắn đang tạm ngắt kết nối</p>
          <h1>Chưa thể tải truyện Zhihu.</h1>
          <p>Kết nối tới máy chủ đang gián đoạn. Vui lòng thử lại sau giây lát.</p>
          <Link to="/zhihu">Thử tải lại</Link>
        </section>
      </PublicShell>
    );
  }

  const newest = sections.find((section) => section.id === "zhihu-new")?.stories ?? [];
  const featuredStory = newest[0] ?? allStories[0] ?? null;
  const latestStories = [...allStories]
    .sort((left, right) => Date.parse(right.publishedAt) - Date.parse(left.publishedAt))
    .slice(0, 8);
  // Prefer stories the backend actually ranked; fall back to the shelves when no
  // snapshot has been computed for one-page stories yet.
  const rankedStories = [
    ...new Map(
      rankingBoards
        .flatMap((board) => board.stories.map((row) => row.story))
        .map((story) => [story.id, story] as const),
    ).values(),
  ];

  return (
    <PublicShell>
      <main className="zhihuPageShell">
        <section className="zhihuHero">
          <div className="zhihuHeroBackdrop" />
          <div className="zhihuHeroInner">
            <div className="zhihuHeroCopy">
              <span className="zhihuEyebrow">
                <MessageSquareQuote aria-hidden="true" />
                Truyện ngắn một trang
              </span>
              <h1>Truyện Zhihu đọc nhanh, cuốn gọn, nhiều cảm xúc.</h1>
              <p>
                Mỗi truyện đọc trọn trong một trang, không chia chương, không phải
                chờ tập mới. Hợp để mở ra khi bạn chỉ có vài phút rảnh.
              </p>
              <div className="zhihuHeroActions">
                <Link to="#zhihu-list">
                  Đọc danh sách
                  <ArrowRight aria-hidden="true" />
                </Link>
                <Link to="/categories">Xem thể loại khác</Link>
              </div>
            </div>

            <aside className="zhihuFeatureCard">
              <span>Gợi ý đầu tiên</span>
              {featuredStory ? (
                <>
                  <h2>{featuredStory.title}</h2>
                  <p>{new Date(featuredStory.publishedAt).toLocaleDateString("vi-VN")}</p>
                  <Link to={`/truyen/${featuredStory.slug}`}>Đọc ngay</Link>
                </>
              ) : (
                <>
                  <h2>Chưa có truyện ngắn</h2>
                  <p>Khi admin đăng truyện dạng một trang, danh sách sẽ hiện ở đây.</p>
                </>
              )}
            </aside>
          </div>
        </section>

        <section className="zhihuQuickStats" aria-label="Điểm nổi bật">
          <article>
            <BookOpenText aria-hidden="true" />
            <strong>{allStories.length}</strong>
            <span>truyện ngắn</span>
          </article>
          <article>
            <Clock3 aria-hidden="true" />
            <strong>một trang</strong>
            <span>đọc hết trong một lần</span>
          </article>
          <article>
            <MessageSquareQuote aria-hidden="true" />
            <strong>đời thường</strong>
            <span>hợp các mẩu chuyện cảm xúc</span>
          </article>
        </section>

        <section className="zhihuContentLayout" id="zhihu-list">
          <div className="zhihuMainList">
            {allStories.length === 0 ? (
              <>
                <header>
                  <span>Danh sách truyện</span>
                  <h2>Truyện ngắn Zhihu</h2>
                </header>
                <p className="emptyCatalog">Chưa có truyện ngắn nào được đăng.</p>
              </>
            ) : (
              sections
                .filter((section) => section.stories.length > 0)
                .map((section) => (
                  <section className="zhihuShelf" key={section.id}>
                    <header>
                      <span>Danh sách truyện</span>
                      <h2>{section.title}</h2>
                    </header>
                    <div className="catalogGrid catalogGridLarge catalogGridVertical">
                      {section.stories.map((story, index) => (
                        <CatalogStoryCard index={index} key={story.id} story={story} />
                      ))}
                    </div>
                  </section>
                ))
            )}

            <CommunityChat channel="zhihu" title="Cộng đồng truyện ngắn" />
          </div>

          <aside className="zhihuSideRail">
            <section>
              <h2>Mới cập nhật</h2>
              <ol>
                {latestStories.map((story, index) => (
                  <li key={story.id}>
                    <b>{index + 1}</b>
                    <Link to={`/truyen/${story.slug}`}>{story.title}</Link>
                    <span>{new Date(story.publishedAt).toLocaleDateString("vi-VN")}</span>
                  </li>
                ))}
              </ol>
            </section>
            <RankingPanel
              stories={rankedStories.length > 0 ? rankedStories : allStories}
              title="Bảng xếp hạng truyện ngắn"
            />
          </aside>
        </section>
      </main>
    </PublicShell>
  );
}
