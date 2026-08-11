import { Headphones, Sparkles, Volume2 } from "lucide-react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

/* metadata removed */

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
      <div className="audioPageRedesignShell">
        {/* ── TOP HERO BANNER (FULL WIDTH, HEIGHT 400PX) ── */}
        <section className="audioHeroBanner">
          <div className="audioHeroBg" />
          <div className="audioHeroContainer">
            <div className="audioHeroContent">
              <h1>Truyện audio</h1>
              <p>
                Những truyện đã có bản đọc thành tiếng, nghe được khi bạn không
                tiện nhìn màn hình.
              </p>

              {/* Counted from what the catalog returns; the old row showed
                  invented figures (1M+ giờ nghe / 3K+ tập / 100%). */}
              <div className="audioStatCardsRow">
                <div className="audioStatGlassCard">
                  <div className="statIconCircle">
                    <Headphones size={20} />
                  </div>
                  <div className="statText">
                    <strong>{stories.length}</strong>
                    <span>truyện audio</span>
                  </div>
                </div>

                <div className="audioStatGlassCard">
                  <div className="statIconCircle">
                    <Volume2 size={20} />
                  </div>
                  <div className="statText">
                    <strong>{everything.length}</strong>
                    <span>truyện trên nền tảng</span>
                  </div>
                </div>

                <div className="audioStatGlassCard">
                  <div className="statIconCircle">
                    <Sparkles size={20} />
                  </div>
                  <div className="statText">
                    <strong>{new Set(stories.map((story) => story.teamId)).size}</strong>
                    <span>nhóm thực hiện</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <div className="audioMainBodyContainer">
          {stories.length > 0 ? (
            <div className="catalogGrid catalogGridLarge catalogGridVertical">
              {stories.slice(0, 16).map((story, index) => (
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
            <p className="emptyCatalog">
              Chưa có truyện audio nào. <Link to="/stories">Xem truyện chữ</Link> trong lúc chờ nhé.
            </p>
          )}
        </div>
      </div>
    </PublicShell>
  );
}
