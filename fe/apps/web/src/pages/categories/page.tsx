import { Link } from "react-router-dom";
import { Flame, Layers, Sparkles } from "lucide-react";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { CategoryTabbedLayout } from "@/components/category-tabbed-layout";
import { PublicShell } from "@/components/site-chrome";
import { catalog, loadHome } from "@/lib/catalog";

export const revalidate = 120;

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

  // No per-category prefetch here.
  //
  // This used to fan out one /categories/{slug}/stories call per category, all
  // at once. With 84 active genres that is 84 simultaneous requests against a
  // 60-per-second burst limit, so everything past the sixtieth came back 429 -
  // and because the fetch swallows its own errors, those genres simply looked
  // empty. Only the open tab's stories are actually rendered, so the layout
  // loads that one on demand instead; the tab badges read their counts from
  // /categories, which already carries them.
  const categoriesWithStories = categories.filter((cat) => (cat.storyCount ?? 0) > 0).length;

  return (
    <PublicShell>
      <div className="categoriesPageRedesignShell">
        {/* ── Category Tabbed Layout Section ── */}
        <div className="categoriesMainBodyContainer">
          <CategoryTabbedLayout
            categories={categories}
            fallbackStories={allStories}
          />
        </div>
      </div>
    </PublicShell>
  );
}
