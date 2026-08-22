"use client";

import { BookOpen, Coins, Eye, PenLine, Users } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { PublisherTabs } from "@/components/publisher-tabs";
import { PublicShell } from "@/components/site-chrome";
import { coverUrl, StoryCoverPlaceholder } from "@/components/story-cover";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { PointLineChart, BarChart } from "@/pages/dashboard/components/admin-charts";

type ChartPoint = { label: string; value: number };

type TeamOverview = {
  teamId: string;
  teamName: string;
  memberRole: string;
  stats: {
    revenueXu: number;
    views: number;
    stories: number;
    publishedStories: number;
    chapters: number;
    followers: number;
  };
  revenueSeries: ChartPoint[];
  viewSeries: ChartPoint[];
  tasks: string[];
};

type TeamStoryRow = {
  id: string;
  slug: string;
  title: string;
  coverUrl: string | null;
  status: string;
  progressStatus: string;
  storyFormat: string;
  viewCount: number;
  followCount: number;
  chapterCount: number;
  publishedChapterCount: number;
  favoriteCount: number;
  /** Net xu this story earned the team. */
  revenueXu: number;
  updatedAt: string | null;
};

const number = new Intl.NumberFormat("vi-VN");
const compact = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1, notation: "compact" });

/** Vietnamese labels for the story workflow states the schema stores. */
const STATUS_LABELS: Readonly<Record<string, string>> = {
  DRAFT: "Bản nháp",
  PENDING_REVIEW: "Chờ duyệt",
  PUBLISHED: "Đã đăng",
  REJECTED: "Bị từ chối",
  HIDDEN: "Đã ẩn",
};

const ROLE_LABELS: Readonly<Record<string, string>> = {
  OWNER: "Chủ nhóm",
  MANAGER: "Quản lý",
  EDITOR: "Biên tập",
};

function statusTone(status: string): string {
  if (status === "PUBLISHED") return "#16a34a";
  if (status === "PENDING_REVIEW") return "#d97706";
  if (status === "REJECTED") return "#dc2626";
  return "#64748b";
}

export function PublisherDashboard({ teamId }: Readonly<{ teamId: string }>) {
  const [overview, setOverview] = useState<TeamOverview | null>(null);
  const [stories, setStories] = useState<TeamStoryRow[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "forbidden" | "error">("loading");
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const [overviewRes, storiesRes] = await Promise.all([
          authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/dashboard`),
          authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories`),
        ]);
        if (!active) return;
        // 403/404 both mean "not your team" - the API answers 404 for a
        // non-member on purpose, so treat them the same in the UI.
        if (overviewRes.status === 403 && storiesRes.ok) {
          navigate(`/teams/${encodeURIComponent(teamId)}/stories`, { replace: true });
          return;
        }
        if (overviewRes.status === 403 || overviewRes.status === 404) {
          setState("forbidden");
          return;
        }
        if (!overviewRes.ok) {
          setState("error");
          return;
        }
        setOverview((await overviewRes.json()) as TeamOverview);
        if (storiesRes.ok) setStories((await storiesRes.json()) as TeamStoryRow[]);
        setState("ready");
      } catch {
        if (active) setState("error");
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [navigate, teamId]);

  if (state === "loading") {
    return (
      <PublicShell>
        <main className="publisherShell">
          <p className="publisherNotice">Đang tải bảng điều khiển…</p>
        </main>
      </PublicShell>
    );
  }

  if (state === "forbidden") {
    return (
      <PublicShell>
        <main className="publisherShell">
          <div className="publisherNotice">
            <h1>Bạn chưa có quyền đăng truyện ở nhóm này</h1>
            <p>Chỉ chủ nhóm, quản lý và biên tập mới mở được bảng điều khiển này.</p>
            <Link className="publisherPrimaryBtn" to="/dang-ky-dang-truyen">
              Đăng ký đăng truyện
            </Link>
          </div>
        </main>
      </PublicShell>
    );
  }

  if (state === "error" || !overview) {
    return (
      <PublicShell>
        <main className="publisherShell">
          <div className="publisherNotice">
            <h1>Không tải được bảng điều khiển</h1>
            <p>Kết nối tới máy chủ đang gián đoạn. Vui lòng thử lại sau giây lát.</p>
          </div>
        </main>
      </PublicShell>
    );
  }

  const { stats } = overview;
  const statCards = [
    { icon: Coins, label: "Doanh thu Xu", value: number.format(stats.revenueXu), hint: "Thực nhận sau phí" },
    { icon: Eye, label: "Lượt đọc", value: number.format(stats.views), hint: "Toàn bộ truyện của nhóm" },
    { icon: BookOpen, label: "Truyện", value: `${stats.publishedStories}/${stats.stories}`, hint: "Đã đăng / tổng số" },
    { icon: PenLine, label: "Chương", value: number.format(stats.chapters), hint: "Tổng số chương" },
    { icon: Users, label: "Người theo dõi", value: number.format(stats.followers), hint: "Theo dõi nhóm" },
  ];

  return (
    <PublicShell>
      <main className="publisherShell">
        <div className="publisherContainer">
          <header className="publisherHeading">
            <div>
              {/* The team's own name leads. "Bảng điều khiển đăng truyện" was
                  the same words on every team's page, and the one thing that
                  told them apart sat underneath in small grey text. */}
              <h1>NHÓM XUẤT BẢN · {overview.teamName.toLocaleUpperCase("vi")}</h1>
              <span>
                Bảng điều khiển
                {ROLE_LABELS[overview.memberRole] ? ` · ${ROLE_LABELS[overview.memberRole]}` : ""}
              </span>
            </div>
          </header>

          <PublisherTabs active="dashboard" memberRole={overview.memberRole} teamId={teamId} />

          <div className="publisherStatGrid">
            {statCards.map((card) => (
              <div className="publisherStatCard" key={card.label}>
                <span className="publisherStatLabel">
                  <card.icon aria-hidden="true" size={15} />
                  {card.label}
                </span>
                <strong>{card.value}</strong>
                <small>{card.hint}</small>
              </div>
            ))}
          </div>

          {overview.tasks.length > 0 ? (
            <section className="publisherCard">
              <h2>Cần xử lý</h2>
              <ul className="publisherTaskList">
                {overview.tasks.map((task) => (
                  <li key={task}>{task}</li>
                ))}
              </ul>
            </section>
          ) : null}

          {/* Same two charts as the admin overview, scoped to this team. */}
          <div className="publisherChartRow">
            <section className="publisherCard">
              <h2>Lượt đọc theo ngày</h2>
              <PointLineChart series={overview.viewSeries} />
            </section>
            <section className="publisherCard">
              <h2>Doanh thu Xu theo ngày</h2>
              <BarChart series={overview.revenueSeries} />
            </section>
          </div>

          <section className="publisherCard">
            <header className="publisherCardHeader">
              <h2>Truyện của nhóm</h2>
              <span>{stories.length} truyện</span>
            </header>
            {stories.length === 0 ? (
              <p className="publisherEmpty">
                Nhóm chưa có truyện nào. Bấm “Quản lý truyện” để đăng truyện đầu tiên.
              </p>
            ) : (
              /* A table rather than a list: the point of this section is
                 comparing stories against each other, and figures only compare
                 when they line up in columns. Scrolls sideways on a narrow
                 screen instead of wrapping the numbers out of alignment. */
              <div className="publisherStoryTableWrap">
                <table className="publisherStoryTable">
                  <thead>
                    <tr>
                      <th scope="col">Truyện</th>
                      <th scope="col">Chương</th>
                      <th scope="col">Lượt đọc</th>
                      <th scope="col">Đã lưu</th>
                      <th scope="col">Theo dõi</th>
                      <th scope="col">Doanh thu</th>
                      <th scope="col">Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stories.map((story) => {
                      const cover = coverUrl(story.coverUrl);
                      return (
                        <tr key={story.id}>
                          <th scope="row">
                            <Link className="publisherStoryThumb" to={`/truyen/${story.slug}`}>
                              {cover ? (
                                <img alt="" aria-hidden="true" loading="lazy" src={cover} />
                              ) : (
                                <StoryCoverPlaceholder />
                              )}
                            </Link>
                            <Link to={`/truyen/${story.slug}`}>{story.title}</Link>
                          </th>
                          <td>{story.publishedChapterCount}/{story.chapterCount}</td>
                          <td>{number.format(story.viewCount)}</td>
                          <td>{number.format(story.favoriteCount ?? 0)}</td>
                          <td>{number.format(story.followCount)}</td>
                          <td className="publisherRevenueCell">
                            {number.format(story.revenueXu ?? 0)} xu
                          </td>
                          <td>
                            <span
                              className="publisherStatusPill"
                              style={{ color: statusTone(story.status) }}
                            >
                              {STATUS_LABELS[story.status] ?? story.status}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>
      </main>
    </PublicShell>
  );
}
