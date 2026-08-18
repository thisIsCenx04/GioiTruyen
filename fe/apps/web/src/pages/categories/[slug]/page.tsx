import { BookOpen, Grid3X3, Headphones, LibraryBig } from "lucide-react";
import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { catalog, loadRankingBoards } from "@/lib/catalog";

export const revalidate = 120;

type CategoryDetailProps = Readonly<{
  params: Promise<{ slug: string }>;
}>;

const numberFormatter = new Intl.NumberFormat("vi-VN");

async function loadCategory(slug: string) {
  const taxonomy = await catalog.categories().catch(() => ({ groups: [] }));
  const categories = taxonomy?.groups ? taxonomy.groups.flatMap((group) => group.categories) : [];
  let category = categories.find((item) => item.slug === slug);
  if (!category) {
    const formattedName = slug.split("-").map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
    category = { id: `cat-${slug}`, name: formattedName, slug } as any;
  }

  const cat = category!;
  const [results, boards] = await Promise.all([
    catalog.categoryStories(cat.slug).catch(() => []),
    loadRankingBoards(),
  ]);
  const stories = results ?? [];
  const rankingStories = boards.flatMap((board) => board.stories.map((row) => row.story));

  return { category: cat, stories, taxonomy, rankingStories };
}

/* generateMetadata removed */

export default async function CategoryDetailPage({ params }: CategoryDetailProps) {
  const { slug } = await params;
  const result = await loadCategory(slug);
  if (!result) {
    return <div>Not Found</div>;
  }

  const relatedCategories = result.taxonomy.groups
    .flatMap((group) => group.categories)
    .filter((item) => item.slug !== result.category.slug)
    .slice(0, 10);

  const allCategories = result.taxonomy.groups.flatMap((group) => group.categories);

  return (
    <PublicShell>
      <section className="catalogDetailPage">
        {/* Category Header Row matching MonkeyD reference */}
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "1.2rem", paddingBottom: "0.8rem", borderBottom: "1px solid #eef2f6" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
            <h1 style={{ fontSize: "1.6rem", fontWeight: 850, margin: 0, textTransform: "uppercase", letterSpacing: "-0.01em" }}>
              TRUYỆN {result.category.name}
            </h1>
            
            {/* Category Switcher Dropdown */}
            <div className="mainNavDropdownWrapper">
              <Link
                to="/categories"
                className="categoryPillButton"
                style={{
                  background: "#0084ff",
                  color: "#ffffff",
                  padding: "0.4rem 0.9rem",
                  borderRadius: "6px",
                  fontSize: "0.85rem",
                  fontWeight: 700,
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "0.4rem",
                  textDecoration: "none",
                  boxShadow: "0 2px 8px rgba(0,132,255,0.25)"
                }}
              >
                <span>Thể Loại ▼</span>
              </Link>
            </div>
          </div>
          <span style={{ fontSize: "0.85rem", color: "#64748b", fontWeight: 600 }}>
            {result.stories.length} bộ truyện
          </span>
        </div>

        {/* Main 2-Column Content Layout (Story Grid + Ranking Side Panel) */}
        <div className="storyRankingLayout detailContentLayout" style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: "1.5rem" }}>
          <div className="homeSectionStack">
            <section className="storySection storyListBoard" style={{ padding: 0 }}>
              {result.stories.length === 0 ? (
                <p className="emptyCatalog">Thể loại này đang chờ những tác phẩm đầu tiên.</p>
              ) : (
                <div className="catalogGrid catalogGridLarge catalogGridVertical">
                  {result.stories.slice(0, 24).map((story, index) => (
                    <CatalogStoryCard index={index} key={story.id} story={story} />
                  ))}
                </div>
              )}
            </section>
          </div>

          <aside className="sideRailArea">
            <RankingPanel stories={[...result.stories, ...result.rankingStories]} title="BẢNG XẾP HẠNG" />
          </aside>
        </div>
      </section>
    </PublicShell>
  );
}
