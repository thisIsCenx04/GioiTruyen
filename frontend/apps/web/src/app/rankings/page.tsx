import { Coins, Eye, ThumbsUp } from "lucide-react";
import type { Metadata } from "next";
import type { Route } from "next";
import Link from "next/link";

import { PublicShell } from "@/components/site-chrome";
import { loadRankingBoards } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Bảng xếp hạng truyện theo doanh thu, đề cử và lượt xem trên Giới Truyện.",
  title: "Bảng xếp hạng",
};

const numberFormatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 1,
  notation: "compact",
});
const icons = {
  gold: Coins,
  recommendations: ThumbsUp,
  views: Eye,
} as const;

export default async function RankingsPage() {
  const boards = await loadRankingBoards();

  return (
    <PublicShell>
      <section className="catalogPage rankingPage">
        <header className="pageIntro compactIntro">
          <p>Bảng xếp hạng</p>
          <h1>Top truyện nổi bật hôm nay</h1>
        </header>
        <div className="rankingBoardGrid rankingBoardGridLarge">
          {boards.map((board) => {
            const Icon = icons[board.id];

            return (
              <section
                aria-labelledby={`${board.id}-title`}
                className="rankingBoard rankingBoardLarge"
                key={board.id}
              >
                <header>
                  <Icon aria-hidden="true" />
                  <h2 id={`${board.id}-title`}>{board.title}</h2>
                </header>
                <ol>
                  {board.stories.map((row, index) => (
                    <li key={row.story.id}>
                      <b>{row.rank}</b>
                      <Link
                        aria-label={`Mở ${row.story.title}`}
                        className="rankingThumb"
                        data-tone={index % 4}
                        href={`/stories/${row.story.slug}` as Route}
                      >
                        <span>{row.story.title.slice(0, 1)}</span>
                      </Link>
                      <div>
                        <Link href={`/stories/${row.story.slug}` as Route}>
                          {row.story.title}
                        </Link>
                        <small>
                          <Icon aria-hidden="true" />
                          {numberFormatter.format(row.metricValue)}
                        </small>
                      </div>
                    </li>
                  ))}
                </ol>
              </section>
            );
          })}
        </div>
      </section>
    </PublicShell>
  );
}
