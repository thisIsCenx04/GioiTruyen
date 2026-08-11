import type { HomeStorySummary, RankingBoard } from "@gioitruyen/api-client";
import { BookOpen, Flame, MessageSquare, Sparkles, Trophy } from "lucide-react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import {
  PromotedEmptySlot,
  PromotedStoryCard,
} from "@/components/promoted-story-card";
import { CommunityChat } from "@/components/community-chat";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

const promotedSlots = Array.from({ length: 12 }, (_, index) => index + 1);

function formatMetric(value: number, unit: string) {
  return `${value.toLocaleString("vi-VN")}${unit ? ` ${unit}` : ""}`;
}

function getBoardRows(
  board: RankingBoard | undefined,
  stories: readonly HomeStorySummary[],
  fallbackUnit: string,
) {
  if (board && board.stories.length > 0) {
    return {
      title: board.title,
      unit: board.unit,
      rows: board.stories.slice(0, 5).map((row) => ({
        metric: row.metricValue,
        rank: row.rank,
        story: row.story,
      })),
    };
  }

  return {
    title: "",
    unit: fallbackUnit,
    rows: stories.slice(0, 5).map((story, index) => ({
      metric: story.viewCount ?? story.saveCount ?? (5 - index) * 1000,
      rank: index + 1,
      story,
    })),
  };
}

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

  // An outage and an empty library render identically, so a failed load must
  // say so rather than quietly showing a library with nothing in it.
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
  const promotedStories = home.promotions.map((booking) => booking.story);
  const visiblePromotions = home.promotions.slice(0, 11);
  const bookingsBySlot = new Map(
    visiblePromotions.map((booking) => [booking.slotPosition, booking] as const),
  );
  const rankingBoardsById = new Map(
    home.rankingBoards.map((board) => [board.id, board] as const),
  );
  const revenueRows = getBoardRows(rankingBoardsById.get("gold"), stories, "xu");
  const recommendationRows = getBoardRows(
    rankingBoardsById.get("recommendations"),
    stories,
    "đề cử",
  );
  const discussionStories = stories.slice(0, 3);

  return (
    <PublicShell>
      <div className="homeLayout homeLayoutFull">
        <section className="bookingBoard" aria-labelledby="booking-title">
          <header>
            <div>
              <p>Top truyện</p>
              <h1 id="booking-title">Đang nổi bật trên Giới Truyện</h1>
            </div>
            <Link to="/teams#ads-booking">
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
                  <Link to="/stories">Xem tất cả</Link>
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
                  <p className="emptyCatalog">Danh mục này đang chờ những chương truyện đầu tiên.</p>
                )}
              </section>
            ))}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
            <RankingPanel stories={promotedStories.length > 0 ? promotedStories : stories} />
            <CommunityChat compact={true} />
          </div>
        </div>

        <section className="homeSpotlightSection" aria-labelledby="home-spotlight-title">
          <header>
            <div>
              <p className="detailEyebrow">Cộng đồng đang chú ý</p>
              <h2 id="home-spotlight-title">Doanh thu, đề cử và thảo luận mới</h2>
            </div>
            <Link to={"/rankings" as string}>Xem bảng đầy đủ</Link>
          </header>
          <div className="homeSpotlightGrid">
            <article className="homeMiniRanking" aria-labelledby="home-revenue-title">
              <h3 id="home-revenue-title">
                <Trophy aria-hidden="true" />
                Bảng doanh thu
              </h3>
              <ol>
                {revenueRows.rows.map((row) => (
                  <li key={`revenue-${row.story.id}`}>
                    <b>{row.rank}</b>
                    <Link to={`/truyen/${row.story.slug}` as string}>{row.story.title}</Link>
                    <span>{formatMetric(row.metric, revenueRows.unit)}</span>
                  </li>
                ))}
              </ol>
            </article>
            <article className="homeMiniRanking" aria-labelledby="home-recommend-title">
              <h3 id="home-recommend-title">
                <Sparkles aria-hidden="true" />
                Bảng đề cử
              </h3>
              <ol>
                {recommendationRows.rows.map((row) => (
                  <li key={`recommend-${row.story.id}`}>
                    <b>{row.rank}</b>
                    <Link to={`/truyen/${row.story.slug}` as string}>{row.story.title}</Link>
                    <span>{formatMetric(row.metric, recommendationRows.unit)}</span>
                  </li>
                ))}
              </ol>
            </article>
            <article className="homeDiscussionPanel" aria-labelledby="home-discussion-title">
              <h3 id="home-discussion-title">
                <MessageSquare aria-hidden="true" />
                Khu thảo luận
              </h3>
              <div>
                {discussionStories.map((story) => (
                  <Link to={`/truyen/${story.slug}` as string} key={story.id}>
                    <strong>{story.title}</strong>
                    <span>Đọc chương mới và tham gia bình luận cùng cộng đồng.</span>
                  </Link>
                ))}
              </div>
              <Link className="homeDiscussionAction" to={"/community" as string}>
                Vào cộng đồng
              </Link>
            </article>
          </div>
        </section>

        <section className="categorySection categorySectionLarge">
          <header>
            <h2>Thể loại truyện phổ biến</h2>
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

        <section className="aboutBand" aria-labelledby="about-title">
          <div>
            <p className="detailEyebrow">Về Giới Truyện</p>
            <h2 id="about-title">Một thư viện mở cho người đọc, tác giả và các nhóm chuyển ngữ.</h2>
          </div>
          <p>
            Giới Truyện giúp bạn đọc liền mạch trên nhiều thiết bị, lưu truyện yêu thích,
            theo dõi chương mới và ủng hộ trực tiếp người làm nội dung bằng XU. Mỗi tác phẩm
            đều có thông tin tác giả, trạng thái xuất bản và lịch sử cập nhật rõ ràng.
          </p>
        </section>

        <section className="publishingRules" aria-labelledby="publishing-rules-title">
          <div>
            <p className="detailEyebrow">Quy định trước khi đăng truyện</p>
            <h2 id="publishing-rules-title">Tôn trọng bản quyền, người đọc và cộng đồng.</h2>
          </div>
          <ol>
            <li>Truyện phải do bạn sở hữu quyền đăng hoặc có giấy phép chuyển ngữ, xuất bản.</li>
            <li>Không đăng nội dung vi phạm pháp luật, kích động thù ghét, lộ thông tin cá nhân.</li>
            <li>Chương mới cần có tiêu đề, nội dung hoàn chỉnh và gửi kiểm duyệt trước khi xuất bản.</li>
            <li>Nhóm mới đăng ký phải hoàn tất hồ sơ và chờ quản trị viên duyệt quyền xuất bản.</li>
          </ol>
        </section>

        <section className="creatorBanner creatorBannerWide">
          <BookOpen aria-hidden="true" />
          <div>
            <strong>Bạn có một câu chuyện muốn được tìm thấy?</strong>
            <p>Đăng ký nhóm xuất bản, chuẩn bị bản quyền và bắt đầu xây dựng tủ truyện của riêng bạn.</p>
          </div>
          <Link to={"/publishing-rules" as string}>Xem quy định</Link>
          <Link className="primaryAction" to="/teams">Đăng ký ngay</Link>
        </section>
      </div>
    </PublicShell>
  );
}
