import { BookOpen, Library, Users } from "lucide-react";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

/* metadata removed */

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
  const teamCount = new Set(stories.map((story) => story.teamId).filter(Boolean)).size;

  return (
    <PublicShell>
      <div className="storiesPageRedesignShell">
        {/* ── Top Stories Hero Banner ── */}
        <section className="storiesHeroBanner">
          <div className="storiesHeroBg" />
          <div className="storiesHeroContainer">
            <div className="storiesHeroContent">
              <h1>Kho truyện Giới Truyện</h1>
              <p>
                Truyện độc quyền, truyện đề cử, truyện vừa cập nhật và truyện sáng tác
                &mdash; xếp sẵn theo từng kệ để bạn tìm nhanh thứ muốn đọc.
              </p>

              {/* Counted from what the catalog actually returns; the old row showed
                  invented figures (1M+ / 5K+ / 200+) that were never measured. */}
              <div className="storiesStatCardsRow">
                <div className="storiesStatGlassCard">
                  <div className="statIconCircle">
                    <Library size={20} />
                  </div>
                  <div className="statText">
                    <strong>{stories.length}</strong>
                    <span>truyện đang hiển thị</span>
                  </div>
                </div>

                <div className="storiesStatGlassCard">
                  <div className="statIconCircle">
                    <BookOpen size={20} />
                  </div>
                  <div className="statText">
                    <strong>{storySections.length}</strong>
                    <span>kệ truyện</span>
                  </div>
                </div>

                <div className="storiesStatGlassCard">
                  <div className="statIconCircle">
                    <Users size={20} />
                  </div>
                  <div className="statText">
                    <strong>{teamCount}</strong>
                    <span>nhóm đăng truyện</span>
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
