import { StoryApiError } from "@gioitruyen/api-client";
import {
  Bookmark,
  CalendarDays,
  ListMusic,
  Volume2,
} from "lucide-react";
import type { Metadata, Route } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

import { BrowserAudioPlayer } from "@/components/browser-audio-player";
import { RankingPanel } from "@/components/ranking-panel";
import { PublicShell } from "@/components/site-chrome";
import { catalog, loadHome } from "@/lib/catalog";

export const revalidate = 60;

type AudioDetailProps = Readonly<{ params: Promise<{ idOrSlug: string }> }>;

const numberFormatter = new Intl.NumberFormat("vi-VN");

const loadAudioStory = cache(async (identifier: string) => {
  try {
    const [story, chapters, home] = await Promise.all([
      catalog.story(identifier),
      catalog.chapters(identifier, 80),
      loadHome(),
    ]);
    const rankingStories = [
      ...new Map(
        home.storySections
          .flatMap((section) => section.stories)
          .map((item) => [item.id, item] as const),
      ).values(),
    ];

    return { chapters, rankingStories, state: "ready" as const, story };
  } catch (error) {
    return error instanceof StoryApiError && error.problem.status === 404
      ? { state: "missing" as const }
      : { state: "unavailable" as const };
  }
});

export async function generateMetadata({ params }: AudioDetailProps): Promise<Metadata> {
  const { idOrSlug } = await params;
  const result = await loadAudioStory(idOrSlug);
  return result.state === "ready"
    ? {
        description: `Nghe audio truyện ${result.story.title} trên Giới Truyện.`,
        title: `Audio ${result.story.title}`,
      }
    : { title: "Không tìm thấy audio" };
}

export default async function AudioDetailPage({ params }: AudioDetailProps) {
  const { idOrSlug } = await params;
  const result = await loadAudioStory(idOrSlug);
  if (result.state === "missing") {
    notFound();
  }
  if (result.state === "unavailable") {
    return (
      <PublicShell>
        <section className="notFound" role="status">
          <p>Audio đang tạm ngắt kết nối</p>
          <h1>Chưa thể mở bản nghe.</h1>
          <Link href={"/audio" as Route}>Về trang audio</Link>
        </section>
      </PublicShell>
    );
  }

  const { chapters, rankingStories, story } = result;
  return (
    <PublicShell>
      <article className="catalogDetailPage audioDetailPage">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link href={"/" as Route}>Trang chủ</Link>
          <span>›</span>
          <Link href={"/audio" as Route}>Nghe Audio</Link>
          <span>›</span>
          <strong>{story.title}</strong>
        </nav>

        <header className="monkeyDetailHero audioDetailHero">
          <div className="detailHeroCover" data-tone="indigo">
            <Volume2 aria-hidden="true" />
            <small>Audio</small>
          </div>
          <div>
            <p className="detailEyebrow">Nghe truyện</p>
            <h1>{story.title}</h1>
            <p>{story.synopsis}</p>
            <div className="detailHeroStats">
              <span title="Số tập nghe">
                <ListMusic aria-hidden="true" />
                {numberFormatter.format(chapters.items.length)}
              </span>
              <span title="Ngày đăng">
                <CalendarDays aria-hidden="true" />
                {new Date(story.publishedAt).toLocaleDateString("vi-VN")}
              </span>
              <span title="Lưu vào tủ">
                <Bookmark aria-hidden="true" />
                Theo dõi
              </span>
            </div>
          </div>
        </header>

        <div className="storyRankingLayout detailContentLayout">
          <div className="homeSectionStack">
            <BrowserAudioPlayer chapters={chapters.items} storyTitle={story.title} />
          </div>
          <RankingPanel stories={rankingStories} />
        </div>
      </article>
    </PublicShell>
  );
}
