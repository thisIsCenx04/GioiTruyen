import type { HomeStorySummary, PromotedHomeStory, RankingBoard } from "@gioitruyen/api-client";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PromotedStoryCard } from "@/components/promoted-story-card";
import { CommunityChat } from "@/components/community-chat";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export default async function HomePage() {
  const home = await loadHome().catch(() => null);

  const storySections = home
    ? (home.storySections.length > 0 ? home.storySections : home.sections)
    : [];
  const stories = [
    ...new Map(
      storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];

  if (!home || (home.degraded && stories.length === 0)) {
    return (
      <PublicShell>
        <section className="notFound" role="status">
          <p>Thư viện đang tạm ngắt kết nối</p>
          <h1>Chưa thể tải danh mục truyện.</h1>
          <p>Kết nối tới máy chủ đang gián đoạn. Vui lòng thử lại sau giây lát.</p>
          <Link to="/">Thử tải lại</Link>
        </section>
      </PublicShell>
    );
  }

  const categories = home.taxonomy.groups.flatMap((group) => group.categories);
  const featuredPromotions: PromotedHomeStory[] = home.promotions.length > 0
    ? home.promotions.slice(0, 12)
    : stories.slice(0, 6).map((story, index) => ({
        badgeText: "ĐỀ CỬ NỔI BẬT",
        badgeType: "DE_CU",
        bookingId: story.id,
        createdAt: "",
        id: story.id,
        slotPosition: index + 1,
        story,
        tagLabel: "ĐỀ CỬ NỔI BẬT",
      }));

  // Exclusive stories filter (or top curated fallback if sample data is small)
  const exclusiveStories = stories.filter(
    (s) => s.storyType === "EXCLUSIVE" || (s as any).story_type === "EXCLUSIVE",
  );
  const displayExclusiveStories = exclusiveStories.length >= 4
    ? exclusiveStories.slice(0, 8)
    : stories.slice(0, 8);

  return (
    <PublicShell>
      <div className="homeLayout homeLayoutFull">
        {/* 1. HERO SECTION: Promoted / Featured Stories (Bố Cáo) */}
        <section className="bookingBoard" aria-labelledby="booking-title">
          <header>
            <div>
              <h1 id="booking-title">Truyện Đề Cử Nổi Bật</h1>
            </div>
            {/* /bo-cao is the booking screen itself. The old target was a team
                directory with an anchor that no longer exists, so the button
                dropped the reader on a list with nothing to book from. */}
            <Link to="/bo-cao">Đăng ký Bố cáo</Link>
          </header>
          <div className="promotedGrid">
            {featuredPromotions.map((booking, index) => (
              <PromotedStoryCard
                booking={booking}
                index={index}
                key={booking.bookingId || booking.story.id}
              />
            ))}
          </div>
        </section>

        {/* 2. SECTION TRUYỆN ĐỘC QUYỀN (Immediately under Hero Section) */}
        <section aria-labelledby="exclusive-section-title" className="storySection storyListBoard">
          <header>
            <div>
              <p className="homeEyebrow">Chỉ có tại Giới Truyện</p>
              <h2 id="exclusive-section-title">Truyện Độc Quyền</h2>
            </div>
            <Link to="/stories">Xem tất cả</Link>
          </header>
          <div className="catalogGrid">
            {displayExclusiveStories.slice(0, 12).map((story, index) => (
              <CatalogStoryCard index={index} key={`exclusive-${story.id}`} story={story} />
            ))}
          </div>
        </section>

        {/* 3. CÁC SECTION TRUYỆN THEO DANH MỤC (Mỗi section hiển thị 12 card & nút Xem Tất Cả) */}
        {storySections.map((section) => (
          <section
            aria-labelledby={`${section.id}-title`}
            className="storySection storyListBoard"
            key={section.id}
          >
            <header>
              <div>
                <h2 id={`${section.id}-title`}>{section.title}</h2>
              </div>
              <Link to="/stories">Xem tất cả</Link>
            </header>
            {section.stories.length > 0 ? (
              <div className="catalogGrid">
                {section.stories.slice(0, 12).map((story, index) => (
                  <CatalogStoryCard
                    index={index}
                    key={story.id}
                    story={story}
                  />
                ))}
              </div>
            ) : (
              <p className="emptyCatalog">Danh mục này đang chờ những chương truyện đầu tiên.</p>
            )}
          </section>
        ))}



        {/* 5. KHU THẢO LUẬN CỘNG ĐỒNG */}
        <section className="homeCommunitySection" aria-labelledby="home-community-title">
          <header>
            <div>
              <p className="homeEyebrow">Cộng đồng đang chú ý</p>
              <h2 id="home-community-title">Khu Thảo Luận &amp; Bình Luận Mới</h2>
            </div>
            <Link to="/community">Vào cộng đồng</Link>
          </header>

          <div className="homeCommunityPanel">
            <CommunityChat compact={false} />
          </div>
        </section>

        {/* 6. THỂ LOẠI TRUYỆN PHỔ BIẾN */}
        <section className="categorySection categorySectionLarge">
          <header>
            <h2>Thể loại</h2>
            <Link to="/categories">Xem tất cả</Link>
          </header>
          <nav className="categoryDock categoryDockColor" aria-label="Thể loại nổi bật">
            {categories.slice(0, 12).map((category, index) => (
              <Link
                data-tone={index % 8}
                to={`/categories/${category.slug}`}
                key={category.id}
              >
                <strong>{category.name}</strong>
              </Link>
            ))}
          </nav>
        </section>
      </div>
    </PublicShell>
  );
}
