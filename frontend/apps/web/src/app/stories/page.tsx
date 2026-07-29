import type { Metadata } from "next";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Danh sách truyện đang phát hành trên Giới Truyện.",
  title: "Truyện",
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
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Mục lục truyện</p>
          <h1>Truyện độc quyền, mới ra lò, mới cập nhật, sáng tác và hoàn thành.</h1>
        </header>
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
                  {section.stories.slice(0, 12).map((story, index) => (
                    <CatalogStoryCard index={index} key={story.id} story={story} />
                  ))}
                </div>
              </section>
            ))}
          </div>
          <RankingPanel stories={stories} />
        </div>
      </section>
    </PublicShell>
  );
}
