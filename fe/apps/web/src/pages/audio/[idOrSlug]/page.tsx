import { StoryApiError } from "@gioitruyen/api-client";
import {
  Bookmark,
  CalendarDays,
  ListMusic,
  Volume2,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams } from "react-router-dom";
const cache = <T extends (...args: any[]) => any>(fn: T) => fn;

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

/* generateMetadata removed */

export default async function AudioDetailPage({ params }: AudioDetailProps) {
  const { idOrSlug } = await params;
  const result = await loadAudioStory(idOrSlug);
  if (result.state === "missing") {
    throw new Error("Not Found");
  }
  if (result.state === "unavailable") {
    return (
      <PublicShell>
        <section className="notFound" role="status">
          <p>Audio đang tạm ngắt kết nối</p>
          <h1>Chưa thể mở bản nghe.</h1>
          <Link to={"/audio" as string}>Về trang audio</Link>
        </section>
      </PublicShell>
    );
  }

  const { chapters, rankingStories, story } = result;
  return (
    <PublicShell>
      <article className="catalogDetailPage audioDetailPage">
        <nav className="breadcrumbs" aria-label="Đường dẫn">
          <Link to={"/" as string}>Trang chủ</Link>
          <span>›</span>
          <Link to={"/audio" as string}>Nghe Audio</Link>
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
