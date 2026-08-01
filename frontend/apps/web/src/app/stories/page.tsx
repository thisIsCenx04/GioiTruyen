import type { Metadata } from "next";
import { BookOpen, Library, Users } from "lucide-react";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Danh sách truyện độc quyền, mới ra lò, mới cập nhật trên Giới Truyện.",
  title: "Truyện | Giới Truyện",
};

export default async function StoriesPage() {
  const home = await loadHome();
  const storySections = home.storySections.length > 0
    ? home.storySections
    : home.sections;
  const stories = [
    ...new Map(
      storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];

  return (
    <PublicShell>
      <div className="storiesPageRedesignShell">
        {/* ── Top Stories Hero Banner ── */}
        <section className="storiesHeroBanner">
          <div className="storiesHeroBg" />
          <div className="storiesHeroContainer">
            <div className="storiesHeroContent">
              <h1>
                Truyện độc quyền, mới ra lò, mới cập nhật, sáng tác và hoàn thành.
              </h1>
              <p>
                Khám phá hàng ngàn bộ truyện đặc sắc, được cập nhật liên tục mỗi ngày chỉ có tại Giới Truyện.
              </p>

              <div className="storiesStatCardsRow">
                <div className="storiesStatGlassCard">
                  <div className="statIconCircle">
                    <BookOpen size={20} />
                  </div>
                  <div className="statText">
                    <strong>1M+</strong>
                    <span>Lượt đọc mỗi tháng</span>
                  </div>
                </div>

                <div className="storiesStatGlassCard">
                  <div className="statIconCircle">
                    <Library size={20} />
                  </div>
                  <div className="statText">
                    <strong>5K+</strong>
                    <span>Tác phẩm độc quyền</span>
                  </div>
                </div>

                <div className="storiesStatGlassCard">
                  <div className="statIconCircle">
                    <Users size={20} />
                  </div>
                  <div className="statText">
                    <strong>200+</strong>
                    <span>Nhóm sáng tác</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── Main Catalog Grid & Ranking Section ── */}
        <div className="storiesMainBodyContainer">
          <div className="storyRankingLayout">
            <div className="homeSectionStack">
              {storySections.map((section) => (
                <section
                  aria-labelledby={`${section.id}-title`}
                  className="storySection storySectionLarge storyListBoard"
                  key={section.id}
                >
                  <header>
                    <h2 id={`${section.id}-title`}>{section.title}</h2>
                  </header>
                  <div className="catalogGrid catalogGridLarge catalogGridVertical">
                    {section.stories.map((story, index) => (
                      <CatalogStoryCard index={index} key={story.id} story={story} />
                    ))}
                  </div>
                </section>
              ))}
            </div>
            <RankingPanel stories={stories} />
          </div>
        </div>
      </div>
    </PublicShell>
  );
}
