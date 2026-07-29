import type { Metadata } from "next";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Tủ truyện cá nhân và các truyện được reader lưu nhiều.",
  title: "Tủ truyện",
};

export default async function LibraryPage() {
  const home = await loadHome();
  const stories = [
    ...new Map(
      home.storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ].sort((left, right) => (right.saveCount ?? 0) - (left.saveCount ?? 0));

  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Tủ truyện</p>
          <h1>Truyện được reader lưu nhiều</h1>
        </header>
        <div className="catalogGrid catalogGridLarge catalogGridVertical">
          {stories.slice(0, 16).map((story, index) => (
            <CatalogStoryCard index={index} key={story.id} story={story} />
          ))}
        </div>
      </section>
    </PublicShell>
  );
}
