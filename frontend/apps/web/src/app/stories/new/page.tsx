import type { Metadata } from "next";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Truyện mới ra lò trên Giới Truyện.",
  title: "Truyện mới",
};

export default async function NewStoriesPage() {
  const home = await loadHome();
  const section = home.storySections.find((item) => item.tag === "NEW_RELEASE")
    ?? home.storySections[0];
  const stories = section?.stories ?? [];

  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Truyện mới</p>
          <h1>Truyện vừa lên kệ</h1>
        </header>
        <div className="catalogGrid catalogGridLarge catalogGridVertical">
          {stories.map((story, index) => (
            <CatalogStoryCard index={index} key={story.id} story={story} />
          ))}
        </div>
      </section>
    </PublicShell>
  );
}
