
import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

/* metadata removed */

export default async function OriginalStoriesPage() {
  const home = await loadHome();
  const section = home.storySections.find((item) => item.tag === "ORIGINAL")
    ?? home.storySections[0];
  const stories = section?.stories ?? [];

  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Truyện Sáng Tác</p>
          <h1>Tác phẩm nguyên bản từ cộng đồng</h1>
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
