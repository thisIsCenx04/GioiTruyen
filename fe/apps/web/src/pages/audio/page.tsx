import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

/**
 * Audio stories.
 *
 * Deliberately the same shape as the stories and rankings pages: a plain
 * heading with a count, then the grid. The previous version opened with a
 * 400px tinted hero and three glass stat cards with coloured icon circles,
 * which looked like a different site from the one it sits inside.
 */
export default async function AudioStoriesPage() {
  const home = await loadHome();
  const everything = [
    ...new Map(
      home.storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];
  // The page used to list the whole catalog; now that stories carry a type it
  // shows only the ones actually published as audio.
  const stories = everything.filter(
    (story) => (story as { storyType?: string }).storyType === "AUDIO",
  );

  return (
    <PublicShell>
      <main className="plainPageShell">
        <div className="plainPageContainer">
          <header className="plainPageHeading">
            <h1>TRUYỆN AUDIO</h1>
            <span>{stories.length} truyện</span>
          </header>

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
              Chưa có truyện audio nào. <Link to="/stories">Xem truyện chữ</Link> trong lúc chờ nhé.
            </p>
          )}
        </div>
      </main>
    </PublicShell>
  );
}
