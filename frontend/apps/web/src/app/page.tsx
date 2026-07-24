import { StatusPill } from "@gioitruyen/ui";
import Link from "next/link";

import { CatalogSearch } from "@/components/catalog-search";
import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export default async function HomePage() {
  const home = await loadHome();
  const lead = home.sections[0]?.stories[0];

  return (
    <PublicShell>
      <section className="catalogHero" aria-labelledby="hero-title">
        <div className="heroManifesto">
          <div className="heroEyebrow">
            <StatusPill tone="active">Thư viện đang mở</StatusPill>
            <span>Cập nhật theo từng chương</span>
          </div>
          <h1 id="hero-title">
            Một thế giới hay
            <span>không khép lại ở trang cuối.</span>
          </h1>
          <p>
            Theo dõi truyện dài kỳ, tìm đúng chương vừa ra và trở lại
            chính xác nơi bạn đã dừng.
          </p>
          <CatalogSearch />
        </div>

        <aside className="featuredVolume" aria-label="Truyện nổi bật">
          <span className="volumeIndex">Tập tuyển chọn · 07</span>
          <div className="volumeGlyph" aria-hidden="true">
            {lead?.title.slice(0, 1) ?? "G"}
          </div>
          <p>Đang được đọc</p>
          <h2>{lead?.title ?? "Người Chép Sử Cuối Cùng"}</h2>
          {lead && (
            <Link href={`/stories/${lead.slug}`}>
              Bắt đầu đọc <span aria-hidden="true">↗</span>
            </Link>
          )}
        </aside>
      </section>

      <div className="catalogIndex" aria-hidden="true">
        <span>Đọc mới</span>
        <span>Trọn bộ</span>
        <span>Sáng tác Việt</span>
      </div>

      <section className="catalogSections" id="catalog">
        {home.sections.map((section, sectionIndex) => (
          <section
            aria-labelledby={`section-${section.id}`}
            className="storySection"
            key={section.id}
          >
            <header>
              <div>
                <p>
                  Kệ {String(sectionIndex + 1).padStart(2, "0")} ·{" "}
                  {section.type === "LATEST"
                    ? "Theo nhịp xuất bản"
                    : "Tuyển chọn biên tập"}
                </p>
                <h2 id={`section-${section.id}`}>{section.title}</h2>
              </div>
              <Link href="/search">Xem toàn bộ</Link>
            </header>
            {section.stories.length > 0 ? (
              <div className="catalogGrid">
                {section.stories.map((story, index) => (
                  <CatalogStoryCard
                    index={index + sectionIndex}
                    key={story.id}
                    story={story}
                  />
                ))}
              </div>
            ) : (
              <p className="emptyCatalog">
                Kệ này đang được biên tập. Khám phá các truyện mới nhất
                trong lúc chờ.
              </p>
            )}
          </section>
        ))}
      </section>
    </PublicShell>
  );
}
