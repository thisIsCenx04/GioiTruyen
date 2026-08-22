import { Fragment } from "react";
import type { HomeStorySummary, PromotedHomeStory, RankingBoard } from "@gioitruyen/api-client";
import { Link } from "react-router-dom";

import { AdsenseUnit, ADSENSE_SLOTS } from "@/components/adsense-unit";
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

  // A hand-built "Truyện Độc Quyền" shelf used to sit here. The API serves that
  // shelf itself, so the page drew the heading twice; and when fewer than four
  // exclusives existed this one quietly fell back to the newest stories of any
  // kind, putting ordinary titles under a heading that promised exclusives.
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

        {/* Between the promoted board and the shelves: past the first screen,
            below real content, and nowhere near the navigation. Renders nothing
            until its slot id is set; see ADSENSE_SLOTS. */}
        <AdsenseUnit format="horizontal" slot={ADSENSE_SLOTS.homeTop} />

        {/* 2. CÁC SECTION TRUYỆN THEO DANH MỤC (Mỗi section hiển thị 12 card & nút Xem Tất Cả)
               Truyện độc quyền là shelf đầu tiên do API trả về. */}
        {storySections.map((section, sectionIndex) => (
          <Fragment key={section.id}>
            {/* One more unit, deep enough that a reader who scrolls this far is
                genuinely browsing. Two units on a long home page is what
                AdSense's own guidance calls a reasonable density; a third
                between every shelf would be the thing that annoys people. */}
            {sectionIndex === 2 ? (
              <AdsenseUnit format="rectangle" slot={ADSENSE_SLOTS.homeMid} />
            ) : null}
          <section
            aria-labelledby={`${section.id}-title`}
            className="storySection storyListBoard"
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
          </Fragment>
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
        {/* A row of twelve genre chips used to close the page. It showed the
            first twelve genres alphabetically - "1x1", "Bách Hợp", "Báo Thù" -
            which is neither the most read nor the most stocked, so it guided
            nobody anywhere. The header already carries a genre menu, and
            /categories lists all of them ranked by how many stories they hold. */}
      </div>
    </PublicShell>
  );
}
