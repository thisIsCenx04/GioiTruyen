import { Link } from "react-router-dom";
import { Flame, Layers, Sparkles } from "lucide-react";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { catalog, loadHome } from "@/lib/catalog";

export const revalidate = 120;

/* metadata removed */

export default async function CategoriesPage() {
  const [taxonomy, homeData] = await Promise.all([
    catalog.categories().catch(() => ({ groups: [] })),
    loadHome().catch(() => null),
  ]);

  const categories = taxonomy?.groups
    ? taxonomy.groups.flatMap((group) => group.categories)
    : [];

  // Flatten all stories from home data to provide fallbacks
  const allStories = homeData
    ? [
        ...new Map(
          (homeData.storySections.length > 0 ? homeData.storySections : homeData.sections ?? [])
            .flatMap((sec) => sec.stories)
            .map((st) => [st.id, st] as const)
        ).values(),
      ]
    : [];

  // Fetch stories per category in parallel
  const categoryStoriesMap = new Map<string, typeof allStories>();

  await Promise.all(
    categories.map(async (cat) => {
      try {
        const fetched = await catalog.categoryStories(cat.slug);
        if (fetched && fetched.length > 0) {
          categoryStoriesMap.set(cat.id, fetched);
        }
      } catch {
        // Ignore errors for unpopulated categories
      }
    })
  );

  const categoriesWithStories = categoryStoriesMap.size;

  return (
    <PublicShell>
      <div className="categoriesPageRedesignShell">
        {/* ── Top Categories Hero Banner ── */}
        <section className="categoriesHeroBanner">
          <div className="categoriesHeroBg" />
          <div className="categoriesHeroContainer">
            <div className="categoriesHeroContent">
              <h1>Thể loại truyện</h1>
              <p>
                Chọn chủ đề bạn muốn đọc &mdash; mỗi thể loại dẫn thẳng tới danh
                sách truyện thuộc thể loại đó.
              </p>

              {/* Counted from the taxonomy the API returns; the previous row
                  showed invented figures (25+ / 5K+ / 1M+). */}
              <div className="categoriesStatCardsRow">
                <div className="categoriesStatGlassCard">
                  <div className="statIconCircle catIconCyan">
                    <Layers size={20} />
                  </div>
                  <div className="statText">
                    <strong>{categories.length}</strong>
                    <span>thể loại</span>
                  </div>
                </div>

                <div className="categoriesStatGlassCard">
                  <div className="statIconCircle catIconCyan">
                    <Sparkles size={20} />
                  </div>
                  <div className="statText">
                    <strong>{allStories.length}</strong>
                    <span>truyện đang hiển thị</span>
                  </div>
                </div>

                <div className="categoriesStatGlassCard">
                  <div className="statIconCircle catIconCyan">
                    <Flame size={20} />
                  </div>
                  <div className="statText">
                    <strong>{categoriesWithStories}</strong>
                    <span>thể loại đã có truyện</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── Category Pill Nav Grid ── */}
        <div className="categoriesMainBodyContainer">
          <section className="categoryHeaderSection" aria-label="Danh sách thể loại">
            <div className="categoryIntroHeader">
              <span className="categoryBadgeEyebrow">DANH MỤC THỂ LOẠI</span>
              <h2>Chọn thể loại</h2>
              <p>Nhấp vào thể loại bất kỳ để chuyển nhanh tới danh sách các bộ truyện tương ứng.</p>
            </div>

            <nav className="categoryDock categoryDockColor categoryPillNavGrid" aria-label="Lọc theo thể loại">
              {categories.map((category, index) => (
                <a
                  className="categoryPillButton"
                  data-tone={index % 8}
                  href={`#cat-${category.slug}`}
                  key={category.id}
                >
                  <span className="pillDot" />
                  <strong>{category.name}</strong>
                </a>
              ))}
            </nav>
          </section>

          {/* ── Stories Grid per Category ── */}
          <div className="homeSectionStack categoryStoriesStack">
            {categories.map((category, catIndex) => {
              const storiesForCat = categoryStoriesMap.get(category.id) ?? allStories.slice((catIndex * 3) % (allStories.length || 1), ((catIndex * 3) % (allStories.length || 1)) + 6);
              if (!storiesForCat || storiesForCat.length === 0) return null;

              return (
                <section
                  aria-labelledby={`cat-${category.slug}-title`}
                  className="storySection storySectionLarge storyListBoard categoryBlockCard"
                  id={`cat-${category.slug}`}
                  key={category.id}
                >
                  <header className="categorySectionHeader">
                    <div>
                      <h2 id={`cat-${category.slug}-title`}>
                        {category.name}
                      </h2>
                      <span className="catCountSub">
                        {storiesForCat.length} bộ truyện tiêu biểu
                      </span>
                    </div>
                    <Link className="catViewAllBtn" to={`/categories/${category.slug}` as string}>
                      Xem tất cả {category.name} ↗
                    </Link>
                  </header>

                  <div className="catalogGrid catalogGridLarge catalogGridVertical">
                    {storiesForCat.slice(0, 8).map((story, index) => (
                      <CatalogStoryCard index={index} key={`${category.id}-${story.id}`} story={story} />
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
        </div>
      </div>
    </PublicShell>
  );
}
