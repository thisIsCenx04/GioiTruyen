import { BookOpen, ChevronRight, Flame, Shapes } from "lucide-react";
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

  const stories = home.sections.flatMap((section) => section.stories);
  const lead = stories[0];
  const latest = home.sections.find((section) => section.type === "LATEST")?.stories ?? [];
  const completed = home.sections.find((section) => section.type === "COMPLETED")?.stories ?? [];
  const original = home.sections.find((section) => section.type === "ORIGINAL")?.stories ?? [];
  const categories = home.taxonomy.groups.flatMap((group) => group.categories);

  return (
    <PublicShell>
      <div className="homeLayout">
        <div className="homeMain">
          <section className="catalogHero" aria-labelledby="hero-title">
            <div className="heroManifesto">
              <span className="heroBadge">TIỂU THUYẾT HOT</span>
              <h1 id="hero-title">{lead?.title ?? "Thư viện đang cập nhật"}</h1>
              <div className="heroMeta">
                <span>{home.sections[0]?.title}</span>
                {lead && <span>Cập nhật {new Date(lead.publishedAt).toLocaleDateString("vi-VN")}</span>}
              </div>
              <p>
                Khám phá truyện vừa xuất bản, lưu tiến độ đọc và theo dõi
                chương mới từ các tác giả, nhóm dịch trên Giới Truyện.
              </p>
              <div className="heroActions">
                {lead && <Link className="primaryAction" href={`/stories/${lead.slug}`}>
                  <BookOpen aria-hidden="true" /> Đọc ngay
                </Link>}
                <Link className="secondaryAction" href="/search">Xem chi tiết</Link>
              </div>
            </div>
            <div aria-hidden="true" className="heroDots"><i /><i /><i /><i /></div>
            <ChevronRight aria-hidden="true" className="heroArrow" />
          </section>

          {home.sections.map((section, sectionIndex) => (
            <section
              aria-labelledby={`section-${section.id}`}
              className="storySection"
              key={section.id}
            >
              <header>
                <div>
                  <h2 id={`section-${section.id}`}>
                    {sectionIndex === 0 && <Flame aria-hidden="true" />}
                    {section.title}
                  </h2>
                </div>
                <Link href="/search">Xem tất cả</Link>
              </header>
              {section.stories.length > 0 ? (
                <div className="catalogGrid">
                  {section.stories.slice(0, 6).map((story, index) => (
                    <CatalogStoryCard index={index + sectionIndex} key={story.id} story={story} />
                  ))}
                </div>
              ) : <p className="emptyCatalog">Kệ truyện đang được cập nhật.</p>}
            </section>
          ))}

          <section className="categorySection">
            <header><h2>Thể loại truyện phổ biến</h2></header>
            <nav className="categoryDock" aria-label="Thể loại nổi bật">
              {categories.slice(0, 6).map((category) => (
                <Link href={`/search?category=${encodeURIComponent(category.slug)}`} key={category.id}>
                  <Shapes aria-hidden="true" /><strong>{category.name}</strong><small>Khám phá thể loại</small>
                </Link>
              ))}
            </nav>
          </section>

          <section className="creatorBanner">
            <BookOpen aria-hidden="true" />
            <div><strong>Bạn muốn chia sẻ câu chuyện của mình?</strong>
              <p>Gia nhập cộng đồng tác giả và nhóm dịch tại Giới Truyện.</p></div>
            <Link href="/teams">Tìm hiểu thêm</Link>
            <Link className="primaryAction" href="/teams">Đăng ký ngay</Link>
          </section>
        </div>

        <aside className="homeAside">
          <section className="sideCard rankingCard">
            <header><h2>Truyện mới cập nhật</h2><Link href="/search">Xem tất cả</Link></header>
            <ol>
              {latest.slice(0, 5).map((story, index) => (
                <li key={story.id}>
                  <b>{index + 1}</b>
                  <span className="rankCover" data-rank={index} />
                  <div><Link href={`/stories/${story.slug}`}>{story.title}</Link>
                    <small>{new Date(story.publishedAt).toLocaleDateString("vi-VN")}</small></div>
                </li>
              ))}
            </ol>
          </section>
          <section className="sideCard continueCard">
            <header><h2>Truyện đã hoàn thành</h2><Link href="/search">Xem tất cả</Link></header>
            {completed.slice(0, 3).map((story, index) => (
              <Link className="continueItem" href={`/stories/${story.slug}`} key={story.id}>
                <span className="rankCover" data-rank={index} />
                <div><strong>{story.title}</strong>
                  <small>Hoàn thành {new Date(story.publishedAt).toLocaleDateString("vi-VN")}</small></div>
              </Link>
            ))}
          </section>
          <section className="sideCard communityCard">
            <header><h2>Sáng tác nguyên bản</h2><Link href="/search">Xem tất cả</Link></header>
            {original.slice(0, 3).map((story, index) => (
              <div key={story.id}><span className="rankCover" data-rank={index} />
                <p><strong>{story.title}</strong><small>{new Date(story.publishedAt).toLocaleDateString("vi-VN")}</small></p>
                <Link href={`/stories/${story.slug}`}>Đọc</Link></div>
            ))}
          </section>
        </aside>
      </div>
    </PublicShell>
  );
}
