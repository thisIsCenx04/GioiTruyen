import type { Metadata } from "next";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Danh sách truyện đã hoàn thành trên Giới Truyện.",
  title: "Truyện Full",
};

export default async function FullStoriesPage() {
  const home = await loadHome();
  const section = home.storySections.find((item) => item.tag === "COMPLETED")
    ?? home.storySections[0];
  const stories = section?.stories ?? [];

  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Truyện Full</p>
          <h1>Đọc liền mạch đến chương cuối</h1>
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
