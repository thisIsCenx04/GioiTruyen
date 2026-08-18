import type { HomeStorySummary } from "@gioitruyen/api-client";
import { ArrowRight, Clock3 } from "lucide-react";
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
        <div className="zhihuContentLayout" id="zhihu-list">
          <header className="zhihuPageHeading">
            <h1>TRUYỆN NGẮN ZHIHU</h1>
            <span>{allStories.length} bộ truyện</span>
          </header>

          {allStories.length === 0 ? (
            <p className="emptyCatalog">Chưa có truyện ngắn nào được đăng.</p>
          ) : (
            sections
              .filter((section) => section.stories.length > 0)
              .map((section) => (
                <section className="zhihuShelf" key={section.id}>
                  <header>
                    <h2>{section.title}</h2>
                    <span>{section.stories.length} truyện</span>
                  </header>
                  <div className="catalogGrid catalogGridVertical zhihuStoryGrid">
                    {section.stories.map((story, index) => (
                      <CatalogStoryCard index={index} key={story.id} story={story} />
                    ))}
                  </div>
                </section>
              ))
          )}

          {/* Browsing aids sit under the list: on this page the stories are the
              point, and a right rail left the grid boxed into a third of the
              viewport while the rail itself ran mostly empty. */}
          {allStories.length > 0 ? (
            <div className="zhihuBottomRail">
              <section className="zhihuLatestPanel">
                <header>
                  <h2>
                    <Clock3 aria-hidden="true" size={16} />
                    Mới cập nhật
                  </h2>
                  <Link to="/stories">
                    Xem tất cả
                    <ArrowRight aria-hidden="true" size={14} />
                  </Link>
                </header>
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
            </div>
          ) : null}

          <CommunityChat channel="zhihu" title="Cộng đồng truyện ngắn" />
        </div>
      </main>
    </PublicShell>
  );
}
