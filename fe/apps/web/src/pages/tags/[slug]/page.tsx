import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadTagStories } from "@/lib/catalog";

export const revalidate = 60;

export default async function TagPage({ params }: any) {
  const { slug } = await params;
  const stories = (await loadTagStories(slug)) as HomeStorySummary[];

  // The slug is the only label available here, so it is turned back into words
  // rather than shown as "ngon-tinh".
  const readableTag = String(slug)
    .split("-")
    .filter(Boolean)
    .join(" ");

  return (
    <PublicShell>
      <main className="tagPageShell">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link to="/">Trang chủ</Link><span>›</span>
          <Link to="/stories">Truyện</Link><span>›</span>
          <strong>#{readableTag}</strong>
        </nav>

        <header className="tagPageHeader">
          <p className="detailEyebrow">Tag</p>
          <h1>#{readableTag}</h1>
          <p>
            {stories.length > 0
              ? `${stories.length} truyện mang tag này.`
              : "Chưa có truyện nào mang tag này."}
          </p>
        </header>

        {stories.length > 0 ? (
          <div className="catalogGrid catalogGridLarge catalogGridVertical">
            {stories.map((story, index) => (
              <CatalogStoryCard index={index} key={story.id} story={story} />
            ))}
          </div>
        ) : (
          <p className="emptyCatalog">
            Hãy thử <Link to="/stories">xem toàn bộ kho truyện</Link> hoặc chọn một thể loại khác.
          </p>
        )}
      </main>
    </PublicShell>
  );
}
