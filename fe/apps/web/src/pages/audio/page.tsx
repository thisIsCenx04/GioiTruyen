import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

/**
 * Truyện audio.
 *
 * <p>Trang này từng chỉ liệt kê truyện có `storyType === "AUDIO"`, tức là những
 * bộ có người thu âm sẵn - trên thực tế gần như không có bộ nào, nên tab Audio
 * luôn trống. Nhưng bản đọc ở đây do trình duyệt tạo ra từ chính chữ của
 * chương, nên điều kiện để nghe được một bộ truyện chỉ là nó có chương. Vì vậy
 * toàn bộ kho truyện nằm ở đây.
 *
 * <p>Vẫn giữ nguyên hình dạng của trang truyện và trang bảng xếp hạng: một tiêu
 * đề kèm số đếm, rồi tới lưới truyện.
 */
export default async function AudioStoriesPage() {
  const home = await loadHome().catch(() => null);
  const sections = home?.storySections && home.storySections.length > 0
    ? home.storySections
    : home?.sections ?? [];
  const stories = [
    ...new Map(
      sections.flatMap((section) => section.stories).map((story) => [story.id, story] as const),
    ).values(),
  ];

  return (
    <PublicShell>
      <main className="plainPageShell">
        <div className="plainPageContainer">
          <header className="plainPageHeading">
            <h1>TRUYỆN AUDIO</h1>
            <span>{stories.length} truyện</span>
          </header>

          <p className="plainPageLede">
            Mọi truyện trên gioitruyen đều nghe được: trình duyệt đọc thẳng chữ của từng chương,
            chạy liên tục hết chương này sang chương khác, và nhớ giúp bạn chỗ đang nghe dở.
          </p>

          {stories.length > 0 ? (
            <div className="catalogGrid catalogGridVertical plainStoryGrid">
              {stories.map((story, index) => (
                <CatalogStoryCard
                  hrefBase="/audio"
                  index={index}
                  key={story.id}
                  metricIcon="audio"
                  story={story}
                />
              ))}
            </div>
          ) : (
            <p className="plainEmpty">
              Chưa tải được danh sách truyện. <Link to="/stories">Thử trang truyện chữ</Link> xem sao.
            </p>
          )}
        </div>
      </main>
    </PublicShell>
  );
}
