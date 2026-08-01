"use client";

import { useState } from "react";
import {
  Calendar,
  Clock,
  Crown,
  Eye,
  Link as LinkIcon,
  Moon,
  Sparkles,
  ThumbsUp,
  ChevronRight,
} from "lucide-react";
import Link from "next/link";
import type { Route } from "next";

import { PublicShell } from "@/components/site-chrome";

// Exact mock dataset matching the user's target design screenshot
const storyListMock = [
  { rank: 1, title: "Người Giữ Đèn Bên Sông", initial: "N", goldMetric: "120,3 N", recMetric: "981", viewMetric: "82,3 N", tone: "indigo" },
  { rank: 2, title: "Hồ Sơ Căn Phòng Số Bảy", initial: "H", goldMetric: "118,1 N", recMetric: "960", viewMetric: "80,9 N", tone: "vermilion" },
  { rank: 3, title: "Ga Tàu Mùa Hạ", initial: "G", goldMetric: "115,3 N", recMetric: "924", viewMetric: "80 N", tone: "amber" },
  { rank: 4, title: "Mộc Bản Triều Nguyên", initial: "M", goldMetric: "113,2 N", recMetric: "903", viewMetric: "78,6 N", tone: "indigo" },
  { rank: 5, title: "Trạm Quan Sát Số Không", initial: "T", goldMetric: "111 N", recMetric: "882", viewMetric: "77,2 N", tone: "vermilion" },
  { rank: 6, title: "Thư Gửi Từ Đà Lạt", initial: "T", goldMetric: "108,9 N", recMetric: "861", viewMetric: "75,8 N", tone: "amber" },
  { rank: 7, title: "Bản Đồ Mười Hai Cửa Biển", initial: "B", goldMetric: "106,7 N", recMetric: "840", viewMetric: "74,4 N", tone: "indigo" },
  { rank: 8, title: "Tiếng Gõ Cửa Lúc Ba Giờ", initial: "T", goldMetric: "104,6 N", recMetric: "819", viewMetric: "73,1 N", tone: "vermilion" },
  { rank: 9, title: "Người Vẽ Bóng Trên Tường", initial: "N", goldMetric: "102,4 N", recMetric: "798", viewMetric: "71,7 N", tone: "amber" },
  { rank: 10, title: "Mật Mã Phố Cổ", initial: "M", goldMetric: "100,3 N", recMetric: "777", viewMetric: "70,3 N", tone: "indigo" },
];

export default function RankingsPage() {
  const [activeFilter, setActiveFilter] = useState<"all" | "week" | "month" | "total">("all");

  return (
    <PublicShell>
      <div className="rankingsRedesignShell">
        {/* ── Top Hero Banner ── */}
        <section className="rankingHeroBanner">
          <div className="rankingHeroContainer">
            <div className="rankingHeroContent">
              <h1>Bảng xếp hạng</h1>
              <p>Khám phá những bộ truyện được yêu thích nhất theo cộng đồng đọc giả.</p>

              <div className="rankingFilterTabs">
                <button
                  className={activeFilter === "all" ? "active" : ""}
                  onClick={() => setActiveFilter("all")}
                  type="button"
                >
                  <Crown size={15} /> Tất cả
                </button>
                <button
                  className={activeFilter === "week" ? "active" : ""}
                  onClick={() => setActiveFilter("week")}
                  type="button"
                >
                  <Calendar size={15} /> Tuần này
                </button>
                <button
                  className={activeFilter === "month" ? "active" : ""}
                  onClick={() => setActiveFilter("month")}
                  type="button"
                >
                  <Moon size={15} /> Tháng này
                </button>
                <button
                  className={activeFilter === "total" ? "active" : ""}
                  onClick={() => setActiveFilter("total")}
                  type="button"
                >
                  <Clock size={15} /> Tổng hợp
                </button>
              </div>
            </div>

            <div className="rankingHeroGraphic">
              <img src="/trophy_leaderboard.png" alt="3D Trophy Leaderboard" className="trophyImg" />
            </div>
          </div>
        </section>

        {/* ── 3-Column Leaderboard Grid ── */}
        <div className="rankingGridContainer">
          {/* Column 1: Thánh Bảng */}
          <div className="rankingColumnCard">
            <header className="columnHeader">
              <div className="colIconCircle colIconBlue">
                <Crown size={20} />
              </div>
              <div className="colHeaderText">
                <h2>Thánh Bảng</h2>
                <p>Top truyện mạnh nhất</p>
              </div>
            </header>

            <ul className="rankingList">
              {storyListMock.map((item) => (
                <li key={`thanhbang-${item.rank}`} className="rankingListItem">
                  <div className={`rankBadge ${item.rank <= 3 ? `rankTop${item.rank}` : "rankNormal"}`}>
                    <span>{item.rank}</span>
                  </div>

                  <Link href={`/truyen/${item.rank}` as Route} className={`storyThumbCover thumbTone-${item.tone}`}>
                    <span className="thumbInitial">{item.initial}</span>
                  </Link>

                  <div className="storyInfo">
                    <Link href={`/truyen/${item.rank}` as Route} className="storyTitleLink">
                      {item.title}
                    </Link>
                    <span className="storyMetric metricBlue">
                      <LinkIcon size={12} /> {item.goldMetric}
                    </span>
                  </div>
                </li>
              ))}
            </ul>

            <div className="columnFooter">
              <Link href={"/rankings/gold" as Route} className="viewMoreLink linkBlue">
                Xem toàn bộ <ChevronRight size={14} />
              </Link>
            </div>
          </div>

          {/* Column 2: Top đề cử */}
          <div className="rankingColumnCard">
            <header className="columnHeader">
              <div className="colIconCircle colIconOrange">
                <ThumbsUp size={20} />
              </div>
              <div className="colHeaderText">
                <h2>Top đề cử</h2>
                <p>Truyện được đề cử nhiều nhất</p>
              </div>
            </header>

            <ul className="rankingList">
              {storyListMock.map((item) => (
                <li key={`decu-${item.rank}`} className="rankingListItem">
                  <div className={`rankBadge ${item.rank <= 3 ? `rankTop${item.rank}` : "rankNormal"}`}>
                    <span>{item.rank}</span>
                  </div>

                  <Link href={`/truyen/${item.rank}` as Route} className={`storyThumbCover thumbTone-${item.tone}`}>
                    <span className="thumbInitial">{item.initial}</span>
                  </Link>

                  <div className="storyInfo">
                    <Link href={`/truyen/${item.rank}` as Route} className="storyTitleLink">
                      {item.title}
                    </Link>
                    <span className="storyMetric metricOrange">
                      <ThumbsUp size={12} /> {item.recMetric}
                    </span>
                  </div>
                </li>
              ))}
            </ul>

            <div className="columnFooter">
              <Link href={"/rankings/recommendations" as Route} className="viewMoreLink linkOrange">
                Xem toàn bộ <ChevronRight size={14} />
              </Link>
            </div>
          </div>

          {/* Column 3: Top lượt xem */}
          <div className="rankingColumnCard">
            <header className="columnHeader">
              <div className="colIconCircle colIconGreen">
                <Eye size={20} />
              </div>
              <div className="colHeaderText">
                <h2>Top lượt xem</h2>
                <p>Truyện có lượt xem cao nhất</p>
              </div>
            </header>

            <ul className="rankingList">
              {storyListMock.map((item) => (
                <li key={`luotxem-${item.rank}`} className="rankingListItem">
                  <div className={`rankBadge ${item.rank <= 3 ? `rankTop${item.rank}` : "rankNormal"}`}>
                    <span>{item.rank}</span>
                  </div>

                  <Link href={`/truyen/${item.rank}` as Route} className={`storyThumbCover thumbTone-${item.tone}`}>
                    <span className="thumbInitial">{item.initial}</span>
                  </Link>

                  <div className="storyInfo">
                    <Link href={`/truyen/${item.rank}` as Route} className="storyTitleLink">
                      {item.title}
                    </Link>
                    <span className="storyMetric metricGreen">
                      <Eye size={12} /> {item.viewMetric}
                    </span>
                  </div>
                </li>
              ))}
            </ul>

            <div className="columnFooter">
              <Link href={"/rankings/views" as Route} className="viewMoreLink linkGreen">
                Xem toàn bộ <ChevronRight size={14} />
              </Link>
            </div>
          </div>
        </div>

        {/* ── Bottom Callout CTA Card ── */}
        <div className="rankingCtaCard">
          <div className="ctaLeft">
            <div className="ctaIconBadge">
              <Crown size={22} />
            </div>
            <div className="ctaText">
              <h3>Bạn muốn truyện của mình xuất hiện ở đây?</h3>
              <p>Hãy tiếp tục sáng tác và nhận được nhiều sự ủng hộ từ độc giả.</p>
            </div>
          </div>

          <Link href={"/publishing-rules" as Route} className="ctaBtn">
            Tìm hiểu thêm <ChevronRight size={16} />
          </Link>
        </div>
      </div>
    </PublicShell>
  );
}
