import { StoryApiError } from "@gioitruyen/api-client";
import {
  Bookmark,
  CalendarDays,
  ListMusic,

} from "lucide-react";
import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams } from "react-router-dom";
const cache = <T extends (...args: any[]) => any>(fn: T) => fn;

import { BrowserAudioPlayer } from "@/components/browser-audio-player";
import { RankingPanel } from "@/components/ranking-panel";
import { coverThumbUrl, coverUrl, onCoverError, StoryCoverPlaceholder } from "@/components/story-cover";
import { PublicShell } from "@/components/site-chrome";
import { catalog, loadHome } from "@/lib/catalog";

export const revalidate = 60;

type AudioDetailProps = Readonly<{ params: Promise<{ idOrSlug: string }> }>;

const numberFormatter = new Intl.NumberFormat("vi-VN");

const loadAudioStory = cache(async (identifier: string) => {
  try {
    const [story, chapters, home] = await Promise.all([
      catalog.story(identifier),
      // Trang một, một trăm chương - kích cỡ tối đa máy chủ cho phép. Chỗ này
      // từng viết catalog.chapters(identifier, 80), mà tham số thứ hai là SỐ
      // TRANG chứ không phải cỡ trang: mọi truyện đều bị hỏi trang 80 và trả về
      // rỗng, nên danh sách chương của trang nghe luôn trống.
      catalog.chapters(identifier, 1, 100),
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
  const cover = coverUrl(story.coverAssetId);
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

        {/* Cùng một tấm bìa, cùng một cách bày như trang truyện chữ. Trước đây
            chỗ này là một ô màu với cái loa ở giữa, nên người vừa bấm "Nghe
            truyện" từ trang truyện không nhận ra mình vẫn đang ở đúng bộ đó. */}
        <header className="monkeyDetailHero audioDetailHero">
          <div className="detailCover storyDetailCover" data-tone="indigo">
            {cover
              ? (
                <img
                  alt={`Bìa ${story.title}`}
                  className="coverImage"
                  decoding="async"
                  fetchPriority="high"
                  loading="eager"
                  onError={onCoverError(cover)}
                  src={coverThumbUrl(story.coverAssetId)}
                />
              )
              : <StoryCoverPlaceholder />}
            <span className="coverTagRow"><span className="audioBadge">NGHE</span></span>
          </div>
          <div>
            <p className="detailEyebrow">Nghe truyện</p>
            <h1>{story.title}</h1>
            <p>{story.synopsis}</p>
            <div className="detailHeroStats">
              <span title="Số chương nghe được">
                <ListMusic aria-hidden="true" />
                {numberFormatter.format(chapters.total)} chương
              </span>
              <span title="Ngày đăng">
                <CalendarDays aria-hidden="true" />
                {new Date(story.publishedAt).toLocaleDateString("vi-VN")}
              </span>
              <Link className="audioReadLink" to={`/stories/${story.slug}`}>
                <Bookmark aria-hidden="true" />
                Xem bản chữ
              </Link>
            </div>
          </div>
        </header>

        <div className="storyRankingLayout detailContentLayout">
          <div className="homeSectionStack">
            <BrowserAudioPlayer
              initial={chapters}
              storyId={story.id}
              storyIdOrSlug={idOrSlug}
              storySlug={story.slug}
              storyTitle={story.title}
            />
          </div>
          <RankingPanel stories={rankingStories} />
        </div>
      </article>
    </PublicShell>
  );
}
