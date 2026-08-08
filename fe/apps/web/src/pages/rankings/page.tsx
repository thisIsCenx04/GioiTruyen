import type { RankingBoard } from "@gioitruyen/api-client";
import {
  ChevronRight,
  Crown,
  Eye,
  Link as LinkIcon,
  ThumbsUp,
} from "lucide-react";

import { Link } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";
import { loadRankingBoards } from "@/lib/catalog";

const boardIcons = {
  gold: Crown,
  recommendations: ThumbsUp,
  views: Eye,
} as const;

const metricIcons = {
  gold: LinkIcon,
  recommendations: ThumbsUp,
  views: Eye,
} as const;

const linkTones = {
  gold: "linkBlue",
  recommendations: "linkOrange",
  views: "linkGreen",
} as const;

function formatMetric(value: number, unit: string) {
  return `${value.toLocaleString("vi-VN")}${unit ? ` ${unit}` : ""}`;
}

function RankingColumn({ board }: Readonly<{ board: RankingBoard }>) {
  const HeaderIcon = boardIcons[board.id];
  const MetricIcon = metricIcons[board.id];

  return (
    <div className="rankingColumnCard">
      <header className="columnHeader">
        <div className={`colIconCircle colIcon${board.id === "gold" ? "Blue" : board.id === "recommendations" ? "Orange" : "Green"}`}>
          <HeaderIcon size={20} />
        </div>
        <div className="colHeaderText">
          <h2>{board.title}</h2>
          <p>{board.subtitle}</p>
        </div>
      </header>

      {board.stories.length > 0 ? (
        <ul className="rankingList">
          {board.stories.slice(0, 10).map((item) => (
            <li className="rankingListItem" key={`${board.id}-${item.story.id}`}>
              <div className={`rankBadge ${item.rank <= 3 ? `rankTop${item.rank}` : "rankNormal"}`}>
                <span>{item.rank}</span>
              </div>
              <Link
                className="storyThumbCover thumbTone-indigo"
                to={`/truyen/${item.story.slug}` as string}
              />
              <div className="storyInfo">
                <Link className="storyTitleLink" to={`/truyen/${item.story.slug}` as string}>
                  {item.story.title}
                </Link>
                {board.id === "gold" ? (
                  <span className="storyMetric metricBlue">
                    <HeaderIcon size={12} />
                    Top Xếp Hạng
                  </span>
                ) : (
                  <span className={`storyMetric ${board.id === "recommendations" ? "metricOrange" : "metricGreen"}`}>
                    <MetricIcon size={12} />
                    {formatMetric(item.metricValue, board.unit)}
                  </span>
                )}
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="emptyCatalog">Bảng này chưa có dữ liệu.</p>
      )}

      <div className="columnFooter">
        <Link to={"/rankings" as string} className={`viewMoreLink ${linkTones[board.id]}`}>
          Cập nhật từ dữ liệu thật <ChevronRight size={14} />
        </Link>
      </div>
    </div>
  );
}

export default async function RankingsPage() {
  const boards = await loadRankingBoards();

  return (
    <PublicShell>
      <div className="rankingsRedesignShell">
        <section className="rankingHeroBanner">
          <div className="rankingHeroContainer">
            <div className="rankingHeroContent">
              <h1>Bảng xếp hạng</h1>
              <p>Khám phá những bộ truyện được yêu thích nhất theo dữ liệu đọc, đề cử và doanh thu.</p>
            </div>
            <div className="rankingHeroGraphic">
              <img
                alt="3D Trophy Leaderboard"
                className="trophyImg"
                height={260}
               
                src="/trophy_leaderboard.png"
                width={260}
              />
            </div>
          </div>
        </section>

        <div className="rankingGridContainer">
          {boards.length > 0 ? (
            boards.map((board) => <RankingColumn board={board} key={board.id} />)
          ) : (
            <section className="rankingColumnCard">
              <h2>Chưa có bảng xếp hạng</h2>
              <p className="emptyCatalog">Dữ liệu sẽ xuất hiện sau khi có truyện và lượt đọc.</p>
            </section>
          )}
        </div>

        <div className="rankingCtaCard">
          <div className="ctaLeft">
            <div className="ctaIconBadge">
              <Crown size={22} />
            </div>
            <div className="ctaText">
              <h3>Bạn muốn truyện của mình xuất hiện ở đây?</h3>
              <p>Đăng truyện, cập nhật đều và nhận sự ủng hộ từ độc giả.</p>
            </div>
          </div>
          <Link to={"/publishing-rules" as string} className="ctaBtn">
            Tìm hiểu thêm <ChevronRight size={16} />
          </Link>
        </div>
      </div>
    </PublicShell>
  );
}
