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
      // Nạp trang đầu tiên với 20 chương/trang cho danh sách nghe audio.
      catalog.chapters(identifier, 1, 20),
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

        {/* Trình phát lên trước, rồi mới tới thông tin truyện và danh sách
            chương. Người vào trang này để bấm nghe, không phải để đọc lý lịch
            truyện - nên nút phát phải nằm trong tầm mắt ngay khi trang mở. */}
        <BrowserAudioPlayer
          coverUrl={cover ? coverThumbUrl(story.coverAssetId) : null}
          detail={(
            <section className="audioStoryDetail" aria-label="Thông tin truyện">
              <p className="detailEyebrow">Về truyện này</p>
              <h2>{story.title}</h2>
              {story.synopsis ? <p className="audioStorySynopsis">{story.synopsis}</p> : null}
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
            </section>
          )}
          initial={chapters}
          storyId={story.id}
          storyIdOrSlug={idOrSlug}
          storySlug={story.slug}
          storySynopsis={story.synopsis}
          storyTitle={story.title}
        />

        {/* Bảng xếp hạng xuống cuối trang: bố cục mới trải hết bề ngang cho trình
            phát, không còn cột phải để nó đứng. */}
        <RankingPanel stories={rankingStories} />
      </article>
    </PublicShell>
  );
}
