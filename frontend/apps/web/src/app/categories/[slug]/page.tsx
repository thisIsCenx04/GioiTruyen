import { BookOpen, Grid3X3, Headphones, LibraryBig } from "lucide-react";
import type { Metadata, Route } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

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
  const category = categories.find((item) => item.slug === slug);
  if (!category) {
    return null;
  }

  const [results, boards] = await Promise.all([
    catalog.categoryStories(category.slug).catch(() => []),
    loadRankingBoards(),
  ]);
  const stories = results;
  const rankingStories = boards.flatMap((board) => board.stories.map((row) => row.story));

  return { category, stories, taxonomy, rankingStories };
}

export async function generateMetadata({ params }: CategoryDetailProps): Promise<Metadata> {
  const { slug } = await params;
  const result = await loadCategory(slug);
  return result
    ? {
        description: `Danh sách truyện thuộc thể loại ${result.category.name} trên Giới Truyện.`,
        title: result.category.name,
      }
    : { title: "Không tìm thấy thể loại" };
}

export default async function CategoryDetailPage({ params }: CategoryDetailProps) {
  const { slug } = await params;
  const result = await loadCategory(slug);
  if (!result) {
    notFound();
  }

  const relatedCategories = result.taxonomy.groups
    .flatMap((group) => group.categories)
    .filter((item) => item.slug !== result.category.slug)
    .slice(0, 10);

  return (
    <PublicShell>
      <section className="catalogDetailPage">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link href={"/" as Route}>Trang chủ</Link>
          <span>›</span>
          <Link href={"/categories" as Route}>Thể loại</Link>
          <span>›</span>
          <strong>{result.category.name}</strong>
        </nav>

        <header className="monkeyDetailHero categoryDetailHero">
          <div className="detailHeroCover" data-tone="teal">
            <Grid3X3 aria-hidden="true" />
          </div>
          <div>
            <p className="detailEyebrow">Thể loại</p>
            <h1>{result.category.name}</h1>
            <p>
              Những tác phẩm mang màu sắc {result.category.name.toLocaleLowerCase("vi-VN")}, được sắp xếp theo lần cập nhật gần nhất.
            </p>
            <div className="detailHeroStats" aria-label="Thông số thể loại">
              <span title="Số truyện">
                <LibraryBig aria-hidden="true" />
                {numberFormatter.format(result.stories.length)}
              </span>
              <span title="Có thể đọc">
                <BookOpen aria-hidden="true" />
                Online
              </span>
              <span title="Có bản audio">
                <Headphones aria-hidden="true" />
                Audio
              </span>
            </div>
          </div>
        </header>

        <div className="storyRankingLayout detailContentLayout">
          <div className="homeSectionStack">
            <section className="storySection storySectionLarge storyListBoard">
              <header>
                <h2>Truyện thuộc {result.category.name}</h2>
                <Link href="/categories">Đổi thể loại</Link>
              </header>
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

            <section className="categoryDetailMore" aria-labelledby="related-categories">
              <header>
                <h2 id="related-categories">Thể loại liên quan</h2>
              </header>
              <div>
                {relatedCategories.map((category, index) => (
                  <Link
                    data-tone={index % 8}
                    href={`/categories/${category.slug}` as Route}
                    key={category.id}
                  >
                    {category.name}
                  </Link>
                ))}
              </div>
            </section>
          </div>
          <RankingPanel stories={[...result.stories, ...result.rankingStories]} />
        </div>
      </section>
    </PublicShell>
  );
}
