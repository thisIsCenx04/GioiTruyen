import type { HomeStorySummary } from "@gioitruyen/api-client";
import { ArrowRight, BookOpenText, Clock3, MessageSquareQuote } from "lucide-react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 120;

/* metadata removed */

const zhihuKeywords = [
  "zhihu",
  "truyện ngắn",
  "truyen ngan",
  "đoản",
  "doan",
  "đoản văn",
  "short",
];

function normalize(value: string) {
  return value
    .toLocaleLowerCase("vi-VN")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/gu, "");
}

function isZhihuStory(story: HomeStorySummary) {
  const searchable = normalize(story.title);

  return zhihuKeywords.some((keyword) => searchable.includes(normalize(keyword)));
}

export default async function ZhihuPage() {
  const home = await loadHome();
  const storySections = home.storySections.length > 0
    ? home.storySections
    : home.sections;
  const allStories = [
    ...new Map(
      storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];
  const matchedStories = allStories.filter(isZhihuStory);
  const zhihuStories = matchedStories.length > 0
    ? matchedStories
    : allStories.slice(0, 18);
  const latestStories = [...zhihuStories]
    .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt))
    .slice(0, 8);
  const featuredStory = zhihuStories[0] ?? null;

  return (
    <PublicShell>
      <main className="zhihuPageShell">
        <section className="zhihuHero">
          <div className="zhihuHeroBackdrop" />
          <div className="zhihuHeroInner">
            <div className="zhihuHeroCopy">
              <span className="zhihuEyebrow">
                <MessageSquareQuote aria-hidden="true" />
                Thể truyện ngắn
              </span>
              <h1>Truyện Zhihu đọc nhanh, cuốn gọn, nhiều cảm xúc.</h1>
              <p>
                Tuyển tập truyện ngắn, đoản văn và những câu chuyện đời thường
                có nhịp đọc nhanh, hợp để mở ra khi bạn chỉ có vài phút rảnh.
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
                  <p>
                    {new Date(featuredStory.publishedAt).toLocaleDateString("vi-VN")}
                  </p>
                  <Link to={`/truyen/${featuredStory.slug}`}>Mở truyện</Link>
                </>
              ) : (
                <>
                  <h2>Chưa có truyện Zhihu</h2>
                  <p>Khi dữ liệu được gắn thể loại Zhihu, danh sách sẽ tự hiện ở đây.</p>
                </>
              )}
            </aside>
          </div>
        </section>

        <section className="zhihuQuickStats" aria-label="Điểm nổi bật">
          <article>
            <BookOpenText aria-hidden="true" />
            <strong>{zhihuStories.length}</strong>
            <span>truyện phù hợp</span>
          </article>
          <article>
            <Clock3 aria-hidden="true" />
            <strong>ngắn gọn</strong>
            <span>ưu tiên nhịp đọc nhanh</span>
          </article>
          <article>
            <MessageSquareQuote aria-hidden="true" />
            <strong>đời thường</strong>
            <span>hợp các mẩu chuyện cảm xúc</span>
          </article>
        </section>

        <section className="zhihuContentLayout" id="zhihu-list">
          <div className="zhihuMainList">
            <header>
              <span>Danh sách truyện</span>
              <h2>Truyện Zhihu mới và dễ đọc</h2>
            </header>
            {zhihuStories.length === 0 ? (
              <p className="emptyCatalog">Chưa có truyện Zhihu để hiển thị.</p>
            ) : (
              <div className="catalogGrid catalogGridLarge catalogGridVertical">
                {zhihuStories.slice(0, 24).map((story, index) => (
                  <CatalogStoryCard index={index} key={story.id} story={story} />
                ))}
              </div>
            )}
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
            <RankingPanel stories={zhihuStories} />
          </aside>
        </section>
      </main>
    </PublicShell>
  );
}
