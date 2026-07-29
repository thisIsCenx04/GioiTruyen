import { BookOpen, Flame, Shapes } from "lucide-react";
import Link from "next/link";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import {
  PromotedEmptySlot,
  PromotedStoryCard,
} from "@/components/promoted-story-card";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

const promotedSlots = Array.from({ length: 12 }, (_, index) => index + 1);

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

  const storySections = home.storySections.length > 0
    ? home.storySections
    : home.sections;
  const stories = [
    ...new Map(
      storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];
  const categories = home.taxonomy.groups.flatMap((group) => group.categories);
  const promotedStories = home.promotions.map((booking) => booking.story);
  const visiblePromotions = home.promotions.slice(0, 11);
  const bookingsBySlot = new Map(
    visiblePromotions.map((booking) => [booking.slotPosition, booking] as const),
  );

  return (
    <PublicShell>
      <div className="homeLayout homeLayoutFull">
        <section className="bookingBoard" aria-labelledby="booking-title">
          <header>
            <div>
              <p>Top truyện</p>
              <h1 id="booking-title">Đang nổi bật trên Giới Truyện</h1>
            </div>
            <Link href="/teams#ads-booking">
              <BookOpen aria-hidden="true" />
              Đăng ký Bố cáo
            </Link>
          </header>
          <div className="promotedGrid">
            {promotedSlots.map((slot, index) => {
              const booking = bookingsBySlot.get(slot);
              return booking ? (
                <PromotedStoryCard
                  booking={booking}
                  index={index}
                  key={booking.bookingId}
                />
              ) : (
                <PromotedEmptySlot key={`empty-${slot}`} slot={slot} />
              );
            })}
          </div>
        </section>

        <div className="storyRankingLayout">
          <div className="homeSectionStack">
            {storySections.map((section) => (
              <section
                aria-labelledby={`${section.id}-title`}
                className="storySection storySectionLarge storyListBoard"
                key={section.id}
              >
                <header>
                  <div>
                    <h2 id={`${section.id}-title`}>
                      <Flame aria-hidden="true" />
                      {section.title}
                    </h2>
                  </div>
                  <Link href="/stories">Xem tất cả</Link>
                </header>
                {section.stories.length > 0 ? (
                  <div className="catalogGrid catalogGridLarge catalogGridVertical">
                    {section.stories.slice(0, 8).map((story, index) => (
                      <CatalogStoryCard
                        index={index}
                        key={story.id}
                        story={story}
                      />
                    ))}
                  </div>
                ) : (
                  <p className="emptyCatalog">Section này đang được cập nhật.</p>
                )}
              </section>
            ))}
          </div>
          <RankingPanel stories={promotedStories.length > 0 ? promotedStories : stories} />
        </div>

        <section className="categorySection categorySectionLarge">
          <header>
            <h2>Thể loại truyện phổ biến</h2>
            <Link href="/categories">Xem tất cả</Link>
          </header>
          <nav className="categoryDock categoryDockColor" aria-label="Thể loại nổi bật">
            {categories.slice(0, 12).map((category, index) => (
              <Link
                data-tone={index % 8}
                href={`/search?category=${encodeURIComponent(category.slug)}`}
                key={category.id}
              >
                <Shapes aria-hidden="true" />
                <strong>{category.name}</strong>
                <small>Khám phá thể loại</small>
              </Link>
            ))}
          </nav>
        </section>

        <section className="aboutBand" aria-labelledby="about-title">
          <div>
            <p className="detailEyebrow">Về Giới Truyện</p>
            <h2 id="about-title">Không gian đọc truyện hiện đại cho reader, tác giả và team dịch.</h2>
          </div>
          <p>
            Giới Truyện tập trung vào trải nghiệm đọc nhanh, giao diện sáng, lưu tiến độ đọc,
            theo dõi chương mới, donate XU cho tác giả và khu làm việc riêng cho team xuất bản.
            Kho nội dung được đồng bộ từ database để reader luôn thấy truyện, thể loại và bảng xếp hạng mới nhất.
          </p>
        </section>

        <section className="publishingRules" aria-labelledby="publishing-rules-title">
          <div>
            <p className="detailEyebrow">Quy định trước khi đăng truyện</p>
            <h2 id="publishing-rules-title">Nội dung sạch, quyền rõ, lịch đăng có trách nhiệm.</h2>
          </div>
          <ol>
            <li>Truyện phải do bạn sở hữu quyền đăng hoặc có giấy phép chuyển ngữ, xuất bản.</li>
            <li>Không đăng nội dung vi phạm pháp luật, kích động thù ghét, lộ thông tin cá nhân.</li>
            <li>Chương mới cần có tiêu đề, nội dung hoàn chỉnh và gửi kiểm duyệt trước khi xuất bản.</li>
            <li>Team mới đăng ký sẽ vào danh sách chờ admin duyệt trước khi mở quyền xuất bản.</li>
          </ol>
        </section>

        <section className="creatorBanner creatorBannerWide">
          <BookOpen aria-hidden="true" />
          <div>
            <strong>Bạn muốn chia sẻ câu chuyện của mình?</strong>
            <p>Gia nhập cộng đồng tác giả và nhóm dịch tại Giới Truyện.</p>
          </div>
          <Link href="/teams">Tìm hiểu thêm</Link>
          <Link className="primaryAction" href="/teams">Đăng ký ngay</Link>
        </section>
      </div>
    </PublicShell>
  );
}
