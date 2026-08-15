import type { HomeStorySummary, PromotedHomeStory, RankingBoard } from "@gioitruyen/api-client";
import { BookOpen, Flame, MessageSquare, Sparkles, Trophy, ArrowRight, Zap, Gift } from "lucide-react";
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
        badgeText: "ĐỀ CỬ",
        badgeType: "DE_CU",
        bookingId: story.id,
        createdAt: "",
        id: story.id,
        slotPosition: index + 1,
        story,
        tagLabel: "ĐỀ CỬ",
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
        {/* 1. HERO SECTION: Promoted / Featured Stories */}
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
        <section aria-labelledby="exclusive-section-title" className="storySection storySectionLarge storyListBoard">
          <header>
            <div>
              <h2 id="exclusive-section-title" style={{ display: "flex", alignItems: "center", gap: "0.45rem" }}>
                <Sparkles aria-hidden="true" style={{ color: "#f59e0b" }} />
                Truyện Độc Quyền
              </h2>
            </div>
            <Link to="/stories">Xem tất cả</Link>
          </header>
          <div className="catalogGrid catalogGridLarge catalogGridVertical">
            {displayExclusiveStories.map((story, index) => (
              <CatalogStoryCard index={index} key={`exclusive-${story.id}`} story={story} />
            ))}
          </div>
        </section>

        {/* 3. CÁC SECTION TRUYỆN THEO DANH MỤC (Mỗi section 8 card & nút Xem Tất Cả) */}
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

        {/* 4. BANNER NGANG QUẢNG CÁO SỰ KIỆN & ƯU ĐÃI (Thay cho các khối chữ cũ) */}
        <section className="eventBannersSection" aria-label="Sự kiện & Quảng cáo">
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
              gap: "1.25rem",
              margin: "1.5rem 0",
            }}
          >
            {/* Banner 1: Cuộc thi sáng tác */}
            <div
              style={{
                background: "linear-gradient(135deg, #1e1b4b 0%, #312e81 100%)",
                borderRadius: "1rem",
                padding: "1.5rem",
                color: "#ffffff",
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
                minHeight: "140px",
                position: "relative",
                overflow: "hidden",
                boxShadow: "0 10px 25px -5px rgba(49, 46, 129, 0.4)",
              }}
            >
              <div>
                <span
                  style={{
                    background: "rgba(255, 255, 255, 0.18)",
                    backdropFilter: "blur(8px)",
                    color: "#a5b4fc",
                    fontSize: "0.75rem",
                    fontWeight: 800,
                    padding: "0.25rem 0.65rem",
                    borderRadius: "20px",
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                  }}
                >
                  🔥 SỰ KIỆN HOT 2026
                </span>
                <h3 style={{ fontSize: "1.2rem", fontWeight: 850, margin: "0.6rem 0 0.3rem", color: "#ffffff" }}>
                  Cuộc Thi Sáng Tác Truyện Mới
                </h3>
                <p style={{ fontSize: "0.85rem", color: "#c7d2fe", margin: 0 }}>
                  Giải thưởng tổng nhuận bút lên đến 50.000.000 VNĐ cùng đặc quyền Xuất Bản Độc Quyền!
                </p>
              </div>
              <div style={{ marginTop: "1rem" }}>
                <Link
                  to="/teams"
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "0.4rem",
                    background: "#6366f1",
                    color: "#ffffff",
                    fontWeight: 800,
                    fontSize: "0.85rem",
                    padding: "0.55rem 1.1rem",
                    borderRadius: "0.6rem",
                    textDecoration: "none",
                  }}
                >
                  Tham gia ngay <ArrowRight size={14} />
                </Link>
              </div>
            </div>

            {/* Banner 2: Chương trình nạp xu */}
            <div
              style={{
                background: "linear-gradient(135deg, #065f46 0%, #047857 100%)",
                borderRadius: "1rem",
                padding: "1.5rem",
                color: "#ffffff",
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
                minHeight: "140px",
                position: "relative",
                overflow: "hidden",
                boxShadow: "0 10px 25px -5px rgba(4, 120, 87, 0.4)",
              }}
            >
              <div>
                <span
                  style={{
                    background: "rgba(255, 255, 255, 0.18)",
                    backdropFilter: "blur(8px)",
                    color: "#6ee7b7",
                    fontSize: "0.75rem",
                    fontWeight: 800,
                    padding: "0.25rem 0.65rem",
                    borderRadius: "20px",
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                  }}
                >
                  🎁 ƯU ĐÃI NẠP XU
                </span>
                <h3 style={{ fontSize: "1.2rem", fontWeight: 850, margin: "0.6rem 0 0.3rem", color: "#ffffff" }}>
                  Tặng Ngay 20% Ngọc Khi Nạp Xu
                </h3>
                <p style={{ fontSize: "0.85rem", color: "#a7f3d0", margin: 0 }}>
                  Áp dụng cho mọi gói nạp ví cá nhân. Tích ngọc đề cử truyện hay đẩy Top liền tay!
                </p>
              </div>
              <div style={{ marginTop: "1rem" }}>
                <Link
                  to="/wallet"
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "0.4rem",
                    background: "#10b981",
                    color: "#ffffff",
                    fontWeight: 800,
                    fontSize: "0.85rem",
                    padding: "0.55rem 1.1rem",
                    borderRadius: "0.6rem",
                    textDecoration: "none",
                  }}
                >
                  Nạp xu ngay <Gift size={14} />
                </Link>
              </div>
            </div>
          </div>
        </section>

        {/* 5. KHU THẢO LUẬN MỚI DÀNH TOÀN BỘ KHÔNG GIAN (Bỏ các BXH thô sơ) */}
        <section className="homeCommunitySection" aria-label="Khu thảo luận cộng đồng" style={{ margin: "1.5rem 0" }}>
          <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
            <div>
              <p className="detailEyebrow" style={{ color: "#2563eb", fontWeight: 700, margin: 0, fontSize: "0.85rem" }}>Cộng đồng đang chú ý</p>
              <h2 style={{ fontSize: "1.35rem", fontWeight: 850, color: "#0f172a", margin: "0.2rem 0 0" }}>
                💬 Khu Thảo Luận & Bình Luận Mới
              </h2>
            </div>
            <Link to="/community" style={{ color: "#2563eb", fontWeight: 700, fontSize: "0.9rem", textDecoration: "none" }}>
              Vào cộng đồng →
            </Link>
          </header>

          <div style={{ background: "#ffffff", border: "1px solid #e2e8f0", borderRadius: "1.25rem", padding: "1.25rem", boxShadow: "0 10px 30px -10px rgba(15, 23, 42, 0.08)" }}>
            <CommunityChat compact={false} />
          </div>
        </section>

        {/* 6. THỂ LOẠI TRUYỆN PHỔ BIẾN */}
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
      </div>
    </PublicShell>
  );
}
