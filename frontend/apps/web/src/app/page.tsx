import { StatusPill } from "@gioitruyen/ui";
import {
  BookOpen,
  Compass,
  Flame,
  Heart,
  Landmark,
  Rocket,
  Search,
  Sparkles,
} from "lucide-react";
import Link from "next/link";

import { CatalogSearch } from "@/components/catalog-search";
import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export default async function HomePage() {
  const home = await loadHome().catch(() => null);
  if (!home) {
    return (
      <PublicShell>
        <section className="notFound" role="status">
          <p>Thư viện đang tạm ngắt kết nối</p>
          <h1>Chưa thể tải danh mục truyện.</h1>
          <Link href="/">Thử tải lại</Link>
        </section>
      </PublicShell>
    );
  }
  const lead = home.sections[0]?.stories[0];
  const storyCount = new Set(
    home.sections.flatMap((section) =>
      section.stories.map((story) => story.id),
    ),
  ).size;

  return (
    <PublicShell>
      <section className="catalogHero" aria-labelledby="hero-title">
        <div className="heroManifesto">
          <div className="heroEyebrow">
            <StatusPill tone="active">Thư viện đang mở</StatusPill>
            <span>Cập nhật theo từng chương</span>
          </div>
          <h1 id="hero-title">
            Đọc tiếp một thế giới
            <span>đang mở.</span>
          </h1>
          <p>
            Khám phá truyện dài kỳ, lưu chính xác đoạn đang đọc và theo
            dõi chương mới từ những tác giả bạn yêu thích.
          </p>
          <CatalogSearch />
          <dl className="heroStats" aria-label="Thư viện hôm nay">
            <div>
              <dt>Đang lên kệ</dt>
              <dd>{storyCount || "12"} truyện</dd>
            </div>
            <div>
              <dt>Nhịp cập nhật</dt>
              <dd>Mỗi ngày</dd>
            </div>
            <div>
              <dt>Không gian</dt>
              <dd>Sáng tác Việt</dd>
            </div>
          </dl>
        </div>
        {lead && (
          <Link
            className="heroFeatured"
            href={`/stories/${lead.slug}`}
          >
            <span>Truyện nổi bật hôm nay</span>
            <strong>{lead.title}</strong>
            <small>
              Mở truyện <span aria-hidden="true">→</span>
            </small>
          </Link>
        )}
      </section>

      <nav className="categoryDock" aria-label="Thể loại nổi bật">
        <Link href="/search?q=kỳ+ảo">
          <Sparkles aria-hidden="true" />
          <span>Kỳ ảo Việt</span>
        </Link>
        <Link href="/search?q=trinh+thám">
          <Search aria-hidden="true" />
          <span>Trinh thám</span>
        </Link>
        <Link href="/search?q=lịch+sử">
          <Landmark aria-hidden="true" />
          <span>Lịch sử</span>
        </Link>
        <Link href="/search?q=viễn+tưởng">
          <Rocket aria-hidden="true" />
          <span>Viễn tưởng</span>
        </Link>
        <Link href="/search?q=lãng+mạn">
          <Heart aria-hidden="true" />
          <span>Lãng mạn</span>
        </Link>
        <Link href="/search?q=phiêu+lưu">
          <Compass aria-hidden="true" />
          <span>Phiêu lưu</span>
        </Link>
        <Link href="/search?q=đời+thường">
          <BookOpen aria-hidden="true" />
          <span>Đời thường</span>
        </Link>
      </nav>

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
                  {sectionIndex === 0 && (
                    <Flame aria-hidden="true" />
                  )}
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
