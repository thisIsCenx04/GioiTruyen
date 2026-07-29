import type { Metadata } from "next";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Nghe audio truyện nổi bật trên Giới Truyện.",
  title: "Nghe Audio",
};

export default async function AudioStoriesPage() {
  const home = await loadHome();
  const stories = [
    ...new Map(
      home.storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];

  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Nghe Audio</p>
          <h1>Truyện có bản nghe nổi bật</h1>
        </header>
        <div className="catalogGrid catalogGridLarge catalogGridVertical">
          {stories.slice(0, 16).map((story, index) => (
            <CatalogStoryCard
              hrefBase="/audio"
              index={index}
              key={story.id}
              metricIcon="audio"
              story={story}
            />
          ))}
        </div>
      </section>
    </PublicShell>
  );
}
