"use client";

import { useEffect, useState } from "react";
import type { RankingBoard } from "@gioitruyen/api-client";
import { Crown, Eye, ThumbsUp } from "lucide-react";
import { Link } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";
import { coverThumbUrl, coverUrl, onCoverError, StoryCoverPlaceholder } from "@/components/story-cover";
import { loadRankingBoards, type RankingPeriod } from "@/lib/catalog";
import { loadSiteBanners } from "@/lib/site-banners";
import styles from "./rankings.module.css";

const boardIcons = {
  gold: Crown,
  recommendations: ThumbsUp,
  views: Eye,
} as const;

/** Every board is a top 10, so it always draws ten places. */
const BOARD_SLOT_COUNT = 10;

type TabType = "all" | "gold" | "recommendations" | "views";

const BOARD_TABS: ReadonlyArray<{ key: TabType; label: string }> = [
  { key: "all", label: "Tất cả" },
  { key: "gold", label: "Kim bảng" },
  { key: "recommendations", label: "Đề cử" },
  { key: "views", label: "Lượt xem" },
];

/** The windows a board can be read over. */
const TIME_FILTERS = [
  { key: "WEEK", label: "Tuần" },
  { key: "MONTH", label: "Tháng" },
  { key: "ALL", label: "Toàn thời gian" },
] as const satisfies ReadonlyArray<{ key: RankingPeriod; label: string }>;

const compactNumber = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 1,
  notation: "compact",
});

function StoryThumb({ coverAssetId }: Readonly<{ coverAssetId?: string | null }>) {
  const src = coverAssetId ? coverUrl(coverAssetId) : "";
  return (
    <span className={styles.thumb}>
      {src ? (
        <img
          alt=""
          aria-hidden="true"
          decoding="async"
          loading="lazy"
          onError={onCoverError(src)}
          src={coverThumbUrl(coverAssetId)}
        />
      ) : (
        <StoryCoverPlaceholder />
      )}
    </span>
  );
}

export default function RankingsPage() {
  const [boards, setBoards] = useState<RankingBoard[]>([]);
  const [bannerUrl, setBannerUrl] = useState("");
  const [activeTab, setActiveTab] = useState<TabType>("all");
  const [activeTime, setActiveTime] = useState<RankingPeriod>("MONTH");

  useEffect(() => {
    let active = true;
    void loadSiteBanners().then((banners) => {
      if (active) setBannerUrl(banners.rankings ?? "");
    });
    return () => {
      active = false;
    };
  }, []);

  // The boards are scoped server-side, so switching window refetches them.
  useEffect(() => {
    let active = true;
    void loadRankingBoards(activeTime).then((data) => {
      if (active) setBoards(data);
    });
    return () => {
      active = false;
    };
  }, [activeTime]);

  const visibleBoards = boards.filter((board) => activeTab === "all" || board.id === activeTab);

  return (
    <PublicShell>
      <main className={styles.rankingsPage}>
        <div className={styles.container}>
          {/* An admin-uploaded banner still shows, but as a plain image strip
              rather than a tinted hero with text layered over it. */}
          {bannerUrl ? (
            <img alt="" aria-hidden="true" className={styles.banner} src={bannerUrl}  decoding="async" loading="lazy" />
          ) : null}

          <header className={styles.pageHeading}>
            <h1>BẢNG XẾP HẠNG</h1>
            <span>Cập nhật theo {TIME_FILTERS.find((f) => f.key === activeTime)?.label.toLowerCase()}</span>
          </header>

          <div className={styles.controls}>
            <div className={styles.tabs} role="tablist" aria-label="Chọn bảng xếp hạng">
              {BOARD_TABS.map((tab) => (
                <button
                  aria-selected={activeTab === tab.key}
                  className={activeTab === tab.key ? styles.isActive : ""}
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  role="tab"
                  type="button"
                >
                  {tab.label}
                </button>
              ))}
            </div>
            <div className={styles.tabs} role="tablist" aria-label="Khoảng thời gian">
              {TIME_FILTERS.map((filter) => (
                <button
                  aria-selected={activeTime === filter.key}
                  className={activeTime === filter.key ? styles.isActive : ""}
                  key={filter.key}
                  onClick={() => setActiveTime(filter.key)}
                  role="tab"
                  type="button"
                >
                  {filter.label}
                </button>
              ))}
            </div>
          </div>

          {visibleBoards.length === 0 ? (
            <p className={styles.empty}>Chưa có dữ liệu xếp hạng cho khoảng thời gian này.</p>
          ) : (
            <div className={visibleBoards.length === 1 ? styles.boardsGridSingle : styles.boardsGrid}>
              {visibleBoards.map((board) => {
                const HeaderIcon = boardIcons[board.id] ?? Crown;
                // Every board shows ten places whether or not it has ten stories:
                // the empty ranks stay visible so the board reads as a top 10
                // waiting to be filled, not as a short list.
                const slots = Array.from(
                  { length: BOARD_SLOT_COUNT },
                  (_, index) => board.stories[index] ?? null,
                );
                // Only the view board publishes a figure; revenue and gem totals
                // decide the order and stay private.
                const showsMetric = board.id === "views";

                return (
                  <section className={styles.board} key={board.id}>
                    <header className={styles.boardHeader}>
                      <h2>
                        <HeaderIcon aria-hidden="true" size={15} />
                        {board.title}
                      </h2>
                    </header>
                    <ol className={styles.boardList}>
                      {slots.map((item, index) => {
                        const rank = index + 1;
                        if (!item) {
                          return (
                            <li className={styles.emptyRow} key={`${board.id}-empty-${rank}`}>
                              <b>{rank}</b>
                              <span>Hạng còn trống</span>
                            </li>
                          );
                        }
                        return (
                          <li key={`${board.id}-${item.story.id}`}>
                            <b>{rank}</b>
                            <Link
                              aria-label={`Mở ${item.story.title}`}
                              to={`/truyen/${item.story.slug}`}
                            >
                              <StoryThumb coverAssetId={item.story.coverAssetId} />
                            </Link>
                            <div className={styles.rowInfo}>
                              <Link className={styles.rowTitle} to={`/truyen/${item.story.slug}`}>
                                {item.story.title}
                              </Link>
                              <span className={styles.rowMeta}>
                                {item.story.originalAuthor || item.story.teamName || "Tác giả tự do"}
                              </span>
                              {showsMetric ? (
                                <span className={styles.rowMetric}>
                                  <Eye aria-hidden="true" size={12} />
                                  {compactNumber.format(item.metricValue)}
                                </span>
                              ) : null}
                            </div>
                          </li>
                        );
                      })}
                    </ol>
                  </section>
                );
              })}
            </div>
          )}

          {/* Publishing call to action, kept as plain text and links. */}
          <section className={styles.ctaCard}>
            <div>
              <h2>Đăng truyện lên Giới Truyện</h2>
              <p>
                Nhóm dịch và tác giả tự do đều có thể đăng truyện, theo dõi lượt đọc và nhận doanh
                thu theo tháng.
              </p>
            </div>
            <ol className={styles.ctaSteps}>
              <li>Tạo nhóm xuất bản hoặc đăng ký tài khoản tác giả.</li>
              <li>Đăng truyện và chương, chờ kiểm duyệt nội dung.</li>
              <li>Theo dõi thống kê và nhận doanh thu hằng tháng.</li>
            </ol>
            <div className={styles.ctaActions}>
              <Link className={styles.ctaPrimary} to="/teams">
                Nhóm xuất bản
              </Link>
              <Link className={styles.ctaSecondary} to="/publishing-rules">
                Quy định đăng truyện
              </Link>
            </div>
          </section>
        </div>
      </main>
    </PublicShell>
  );
}
