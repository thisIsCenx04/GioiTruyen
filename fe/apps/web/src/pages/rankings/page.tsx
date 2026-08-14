"use client";

import { useEffect, useState } from "react";
import type { RankingBoard, Team } from "@gioitruyen/api-client";
import {
  ArrowRight,
  Award,
  BookOpen,
  ChevronRight,
  Crown,
  DollarSign,
  Eye,
  Flame,
  ShieldCheck,
  Sparkles,
  Star,
  ThumbsUp,
  TrendingUp,
  Users,
  Zap,
} from "lucide-react";
import { Link } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";
import { coverUrl } from "@/components/story-cover";
import { loadRankingBoards } from "@/lib/catalog";
import { API_BASE_URL } from "@/lib/api-base";
import styles from "./rankings.module.css";

const boardIcons = {
  gold: Crown,
  recommendations: ThumbsUp,
  views: Eye,
} as const;

function formatViewCount(value: number) {
  if (!value || value <= 0) return "0";
  return value.toLocaleString("vi-VN");
}

const coverGradients = [
  "linear-gradient(135deg, #1e3a8a 0%, #0f172a 100%)",
  "linear-gradient(135deg, #581c87 0%, #2e1065 100%)",
  "linear-gradient(135deg, #065f46 0%, #022c22 100%)",
  "linear-gradient(135deg, #9a3412 0%, #431407 100%)",
  "linear-gradient(135deg, #831843 0%, #4c0519 100%)",
];

function StoryThumb({
  title,
  coverAssetId,
  isTop1,
  toneIndex = 0,
}: {
  title: string;
  coverAssetId?: string | null;
  isTop1?: boolean;
  toneIndex?: number;
}) {
  const [failed, setFailed] = useState(false);
  const src = coverAssetId ? coverUrl(coverAssetId) : "";
  const bg = coverGradients[toneIndex % coverGradients.length];

  return (
    <div className={`${styles.storyCoverThumb} ${isTop1 ? styles.top1CoverThumb : ""}`}>
      {src && !failed ? (
        <img
          src={src}
          alt={title}
          className={styles.storyCoverImg}
          onError={() => setFailed(true)}
          loading="lazy"
        />
      ) : (
        <div className={styles.storyCoverFallback} style={{ background: bg }} title="Chưa có ảnh bìa">
          <BookOpen size={isTop1 ? 20 : 15} style={{ opacity: 0.8 }} />
        </div>
      )}
    </div>
  );
}

type TabType = "all" | "gold" | "recommendations" | "views" | "teams";
type TimeFilterType = "day" | "week" | "month" | "allTime";

export default function RankingsPage() {
  const [boards, setBoards] = useState<RankingBoard[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [activeTab, setActiveTab] = useState<TabType>("all");
  const [activeTime, setActiveTime] = useState<TimeFilterType>("month");

  useEffect(() => {
    let active = true;
    async function init() {
      try {
        const [boardsData, teamsRes] = await Promise.all([
          loadRankingBoards(),
          fetch(`${API_BASE_URL}/teams`).then((res) => (res.ok ? res.json() : [])).catch(() => []),
        ]);
        if (!active) return;
        setBoards(boardsData);
        if (Array.isArray(teamsRes)) {
          setTeams(teamsRes);
        }
      } catch {
        // Fallback
      }
    }
    void init();
    return () => {
      active = false;
    };
  }, []);

  const visibleBoards = boards.filter((b) => {
    if (activeTab === "all") return true;
    return b.id === activeTab;
  });

  return (
    <PublicShell>
      <main className={styles.rankingsPage}>
        {/* ── HERO BANNER ── */}
        <section className={styles.heroSection}>
          <div className={styles.heroGlow1} />
          <div className={styles.heroGlow2} />
          <div className={styles.heroContainer}>
            <div className={styles.heroContent}>
              <div className={styles.heroBadge}>
                <Sparkles size={14} />
                <span>Thế Giới Cổ Tích & Vinh Danh Tác Phẩm</span>
              </div>
              <h1 className={styles.heroTitle}>
                Bảng Xếp Hạng <span className={styles.heroTitleHighlight}>Truyện Hay Nhất</span>
              </h1>
              <p className={styles.heroSubtitle}>
                Khám phá những câu chuyện hấp dẫn nhất, tác giả tài năng và nhóm dịch được độc giả
                yêu thích hàng đầu tại Giới Truyện.
              </p>

              <div className={styles.heroStatsRow}>
                <div className={styles.heroStatItem}>
                  <div className={`${styles.heroStatIcon} ${styles.statGold}`}>
                    <Crown size={18} />
                  </div>
                  <div className={styles.heroStatText}>
                    <strong>Top 1 Thịnh Hành</strong>
                    <span>Cập nhật liên tục</span>
                  </div>
                </div>

                <div className={styles.heroStatItem}>
                  <div className={`${styles.heroStatIcon} ${styles.statBlue}`}>
                    <Flame size={18} />
                  </div>
                  <div className={styles.heroStatText}>
                    <strong>Cộng Đồng Độc Giả</strong>
                    <span>Đọc & Yêu thích</span>
                  </div>
                </div>

                <div className={styles.heroStatItem}>
                  <div className={`${styles.heroStatIcon} ${styles.statGreen}`}>
                    <Award size={18} />
                  </div>
                  <div className={styles.heroStatText}>
                    <strong>Tác Giả Xuất Sắc</strong>
                    <span>Vinh danh hàng tháng</span>
                  </div>
                </div>
              </div>
            </div>

            <div className={styles.heroGraphic}>
              <div className={styles.trophyWrapper}>
                <div className={styles.trophyGlowRing} />
                <img
                  src="/trophy_leaderboard.png"
                  alt="Giới Truyện Leaderboard"
                  className={styles.trophyImg}
                />
              </div>
            </div>
          </div>
        </section>

        {/* ── CONTROLS & MAIN SECTION ── */}
        <div className={styles.mainContainer}>
          <div className={styles.controlsBar}>
            <div className={styles.tabPillsGroup}>
              <button
                type="button"
                className={`${styles.tabBtn} ${activeTab === "all" ? styles.tabBtnActive : ""}`}
                onClick={() => setActiveTab("all")}
              >
                <Flame size={16} />
                <span>Tất cả bảng</span>
              </button>
              <button
                type="button"
                className={`${styles.tabBtn} ${activeTab === "gold" ? styles.tabBtnActive : ""}`}
                onClick={() => setActiveTab("gold")}
              >
                <Crown size={16} />
                <span>Bảng doanh thu</span>
              </button>
              <button
                type="button"
                className={`${styles.tabBtn} ${activeTab === "recommendations" ? styles.tabBtnActive : ""}`}
                onClick={() => setActiveTab("recommendations")}
              >
                <ThumbsUp size={16} />
                <span>Bảng đề cử</span>
              </button>
              <button
                type="button"
                className={`${styles.tabBtn} ${activeTab === "views" ? styles.tabBtnActive : ""}`}
                onClick={() => setActiveTab("views")}
              >
                <Eye size={16} />
                <span>Bảng lượt xem</span>
              </button>
              <button
                type="button"
                className={`${styles.tabBtn} ${activeTab === "teams" ? styles.tabBtnActive : ""}`}
                onClick={() => setActiveTab("teams")}
              >
                <Users size={16} />
                <span>Nhóm xuất bản</span>
              </button>
            </div>

            <div className={styles.timePillsGroup}>
              <button
                type="button"
                className={`${styles.timeBtn} ${activeTime === "day" ? styles.timeBtnActive : ""}`}
                onClick={() => setActiveTime("day")}
              >
                Ngày
              </button>
              <button
                type="button"
                className={`${styles.timeBtn} ${activeTime === "week" ? styles.timeBtnActive : ""}`}
                onClick={() => setActiveTime("week")}
              >
                Tuần
              </button>
              <button
                type="button"
                className={`${styles.timeBtn} ${activeTime === "month" ? styles.timeBtnActive : ""}`}
                onClick={() => setActiveTime("month")}
              >
                Tháng
              </button>
              <button
                type="button"
                className={`${styles.timeBtn} ${activeTime === "allTime" ? styles.timeBtnActive : ""}`}
                onClick={() => setActiveTime("allTime")}
              >
                Toàn thời gian
              </button>
            </div>
          </div>

          {/* ── BOARDS GRID - DỮ LIỆU QUERY TRỰC TIẾP TỪ DB ── */}
          {activeTab === "teams" ? (
            /* Bảng Nhóm Xuất Bản & Tác Giả */
            <div className={styles.singleBoardGrid}>
              <div className={styles.boardColumnCard}>
                <header className={styles.boardHeader}>
                  <div className={`${styles.boardIconCircle} ${styles.iconToneBlue}`}>
                    <Users size={22} />
                  </div>
                  <div className={styles.boardHeaderText}>
                    <h2>Bảng Xếp Hạng Nhóm Xuất Bản & Tác Giả</h2>
                    <p>Các nhóm dịch và tác giả sáng tác tích cực nhất tại Giới Truyện</p>
                  </div>
                </header>

                <div className={styles.boardList}>
                  {teams.length > 0 ? (
                    teams.slice(0, 10).map((team, idx) => (
                      <div key={team.id || team.slug} className={styles.rankRowItem}>
                        <div
                          className={`${styles.rankBadge} ${
                            idx === 0
                              ? styles.badgeTop1
                              : idx === 1
                                ? styles.badgeTop2
                                : idx === 2
                                  ? styles.badgeTop3
                                  : styles.badgeNormal
                          }`}
                        >
                          {idx + 1}
                        </div>
                        <div className={styles.storyCoverThumb} style={{ width: "2.7rem", height: "2.7rem", borderRadius: "0.55rem" }}>
                          <div className={styles.storyCoverFallback} style={{ background: coverGradients[idx % coverGradients.length] }}>
                            <span className={styles.fallbackText}>{team.name ? team.name.slice(0, 1).toUpperCase() : "G"}</span>
                          </div>
                        </div>
                        <div className={styles.storyRowInfo}>
                          <Link to={`/teams/${team.slug}`} className={styles.storyTitleText}>
                            {team.name} <span style={{ color: "#0f5fff", marginLeft: "0.2rem" }}>✔</span>
                          </Link>
                          <div className={styles.storyMetaSub}>
                            <span className={styles.teamNameText}>{team.description || "Nhóm xuất bản chính thức"}</span>
                          </div>
                        </div>
                        <Link to={`/teams/${team.slug}`} className={styles.viewCountPill}>
                          Xem hồ sơ →
                        </Link>
                      </div>
                    ))
                  ) : (
                    <p style={{ textAlign: "center", color: "#64748b", padding: "2rem" }}>Đang tải danh sách nhóm xuất bản...</p>
                  )}
                </div>

                <div className={styles.boardFooterLink}>
                  <Link to="/teams" className={styles.viewMoreBtn}>
                    Khám phá toàn bộ nhóm xuất bản <ChevronRight size={14} />
                  </Link>
                </div>
              </div>
            </div>
          ) : (
            <div className={`${styles.boardsGrid} ${visibleBoards.length === 1 ? styles.singleBoardGrid : ""}`}>
              {visibleBoards.map((board) => {
                const HeaderIcon = boardIcons[board.id] || Crown;
                const top1Item = board.stories[0];
                const otherItems = board.stories.slice(1, 10);

                const iconToneClass =
                  board.id === "gold"
                    ? styles.iconToneGold
                    : board.id === "recommendations"
                      ? styles.iconToneOrange
                      : styles.iconToneGreen;

                return (
                  <div key={board.id} className={styles.boardColumnCard}>
                    <header className={styles.boardHeader}>
                      <div className={`${styles.boardIconCircle} ${iconToneClass}`}>
                        <HeaderIcon size={22} />
                      </div>
                      <div className={styles.boardHeaderText}>
                        <h2>{board.title}</h2>
                        <p>{board.subtitle || "Truyện nổi bật nhất"}</p>
                      </div>
                    </header>

                    {board.stories.length > 0 ? (
                      <div className={styles.boardList}>
                        {/* ── TOP 1 CHAMPION STORY ITEM ── */}
                        {top1Item && (
                          <div className={styles.top1HeroCard}>
                            <div className={styles.top1GlowRibbon}>
                              <Crown size={11} /> TOP 1
                            </div>

                            {/* Ảnh bìa lấy trực tiếp từ coverAssetId (DB Query s.cover_url) */}
                            <StoryThumb
                              title={top1Item.story.title}
                              coverAssetId={top1Item.story.coverAssetId}
                              isTop1
                              toneIndex={0}
                            />

                            <div className={styles.storyRowInfo}>
                              <Link
                                to={`/truyen/${top1Item.story.slug}`}
                                className={`${styles.storyTitleText} ${styles.top1TitleText}`}
                              >
                                {top1Item.story.title}
                              </Link>
                              <div className={styles.storyMetaSub} style={{ marginBottom: "0.3rem" }}>
                                {/* Tên Tác giả lấy trực tiếp từ DB Query s.original_author hoặc teamName */}
                                <span className={styles.authorNameText}>
                                  Tác giả: {top1Item.story.originalAuthor || top1Item.story.teamName || "Tác giả tự do"}
                                </span>
                              </div>
                              {top1Item.story.storyFormat && (
                                <span className={styles.formatBadge}>
                                  {top1Item.story.storyFormat === "SERIAL" ? "Truyện dài" : "Truyện ngắn"}
                                </span>
                              )}
                            </div>

                            {/* Con số Lượt Xem căn bên lề phải - DB Query s.view_count_cache */}
                            <span className={styles.viewCountPill}>
                              <Eye size={12} />
                              {formatViewCount(top1Item.story.viewCount)} lượt
                            </span>
                          </div>
                        )}

                        {/* ── TOP 2 ĐẾN 10 ── */}
                        {otherItems.map((item, itemIdx) => (
                          <div key={`${board.id}-${item.story.id}`} className={styles.rankRowItem}>
                            <div
                              className={`${styles.rankBadge} ${
                                item.rank === 2
                                  ? styles.badgeTop2
                                  : item.rank === 3
                                    ? styles.badgeTop3
                                    : styles.badgeNormal
                              }`}
                            >
                              {item.rank}
                            </div>

                            {/* Ảnh bìa từ DB Query s.cover_url */}
                            <StoryThumb
                              title={item.story.title}
                              coverAssetId={item.story.coverAssetId}
                              toneIndex={itemIdx + 1}
                            />

                            <div className={styles.storyRowInfo}>
                              <Link
                                to={`/truyen/${item.story.slug}`}
                                className={styles.storyTitleText}
                              >
                                {item.story.title}
                              </Link>
                              <div className={styles.storyMetaSub}>
                                {/* Tên Tác giả từ DB Query */}
                                <span className={styles.authorNameText}>
                                  {item.story.originalAuthor || item.story.teamName || "Tác giả tự do"}
                                </span>
                              </div>
                            </div>

                            {/* Con số Lượt Xem căn bên lề phải - DB Query s.view_count_cache */}
                            <span className={styles.viewCountPill}>
                              <Eye size={12} />
                              {formatViewCount(item.story.viewCount)} lượt
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p style={{ textAlign: "center", color: "#94a3b8", padding: "2.5rem 1rem", fontSize: "0.88rem" }}>
                        Bảng này chưa có dữ liệu xếp hạng.
                      </p>
                    )}

                    <div className={styles.boardFooterLink}>
                      <Link to={`/rankings`} className={styles.viewMoreBtn}>
                        Xem chi tiết bảng này <ChevronRight size={14} />
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* ── CREATOR ATTRACTION BANNER ── */}
          <section className={styles.creatorBannerSection}>
            <div className={styles.creatorGlow} />
            <div className={styles.creatorBannerContent}>
              <div>
                <div className={styles.creatorHeadingBadge}>
                  <Zap size={14} />
                  <span>Dành Cho Tác Giả & Nhóm Dịch</span>
                </div>
                <h2>Cùng Giới Truyện Xuất Bản & Đua Top Nhận Nhuận Bút</h2>
                <p>
                  Đăng tải tác phẩm sáng tác hoặc bản dịch chất lượng để tiếp cận hàng triệu độc giả,
                  xây dựng thương hiệu cá nhân và hưởng chính sách chia sẻ doanh thu hấp dẫn nhất.
                </p>

                <div className={styles.perksGrid}>
                  <div className={styles.perkItem}>
                    <div className={styles.perkIcon}>
                      <DollarSign size={16} />
                    </div>
                    <div className={styles.perkText}>
                      <strong>Chia sẻ doanh thu đến 90%</strong>
                      <span>Nhận thu nhập từ bán lẻ chương, bán combo và donate độc giả.</span>
                    </div>
                  </div>

                  <div className={styles.perkItem}>
                    <div className={styles.perkIcon}>
                      <TrendingUp size={16} />
                    </div>
                    <div className={styles.perkText}>
                      <strong>Bảng vàng vinh danh</strong>
                      <span>Top truyện xuất sắc được đẩy mạnh quảng bá trên banner trang chủ.</span>
                    </div>
                  </div>

                  <div className={styles.perkItem}>
                    <div className={styles.perkIcon}>
                      <ShieldCheck size={16} />
                    </div>
                    <div className={styles.perkText}>
                      <strong>Bảo vệ tác quyền</strong>
                      <span>Hợp đồng ký kết độc quyền minh bạch và bảo hộ bản quyền số.</span>
                    </div>
                  </div>

                  <div className={styles.perkItem}>
                    <div className={styles.perkIcon}>
                      <Sparkles size={16} />
                    </div>
                    <div className={styles.perkText}>
                      <strong>Rút tiền 24/7</strong>
                      <span>Thanh toán tự động linh hoạt về tài khoản ngân hàng không giới hạn.</span>
                    </div>
                  </div>
                </div>

                <div className={styles.creatorActionBtns}>
                  <Link to="/teams" className={styles.btnPrimaryCreator}>
                    <Crown size={16} />
                    <span>Đăng Ký Nhóm Xuất Bản Ngay</span>
                    <ArrowRight size={16} />
                  </Link>
                  <Link to="/publishing-rules" className={styles.btnSecondaryCreator}>
                    <BookOpen size={16} />
                    <span>Quy định & Nhuận bút</span>
                  </Link>
                </div>
              </div>

              <div className={styles.creatorCardRight}>
                <h3>
                  <Star size={18} color="#ffd700" />
                  <span>Quy Trình 3 Bước Đơn Giản</span>
                </h3>
                <div className={styles.creatorTimelineList}>
                  <div className={styles.timelineStep}>
                    <div className={styles.stepNumber}>1</div>
                    <div className={styles.stepContent}>
                      <strong>Gửi hồ sơ đăng ký</strong>
                      <p>Điền SĐT và Link Facebook để Admin liên hệ xét duyệt trong 1-3 ngày.</p>
                    </div>
                  </div>
                  <div className={styles.timelineStep}>
                    <div className={styles.stepNumber}>2</div>
                    <div className={styles.stepContent}>
                      <strong>Đăng tải tác phẩm</strong>
                      <p>Hệ thống cấp quyền chủ nhóm, bắt đầu đăng chương và quản lý độc giả.</p>
                    </div>
                  </div>
                  <div className={styles.timelineStep}>
                    <div className={styles.stepNumber}>3</div>
                    <div className={styles.stepContent}>
                      <strong>Đua top & Nhận thu nhập</strong>
                      <p>Truyện lên bảng xếp hạng và nhận tiền nhuận bút rút về tài khoản.</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </div>
      </main>
    </PublicShell>
  );
}
