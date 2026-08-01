import type { Metadata } from "next";
import { Headphones, Sparkles, Volume2 } from "lucide-react";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

export const revalidate = 60;

export const metadata: Metadata = {
  description: "Nghe audio truyện nổi bật trên Giới Truyện.",
  title: "Nghe Audio | Giới Truyện",
};

export default async function AudioStoriesPage() {
  const home = await loadHome();
  const stories = [
    ...new Map(
      home.storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];

  return (
    <PublicShell>
      <div className="audioPageRedesignShell">
        {/* ── TOP HERO BANNER (FULL WIDTH, HEIGHT 400PX) ── */}
        <section className="audioHeroBanner">
          <div className="audioHeroBg" />
          <div className="audioHeroContainer">
            <div className="audioHeroContent">
              <h1>Nghe Audio Truyện Đặc Sắc</h1>
              <p>
                Trải nghiệm hàng ngàn chương truyện audio chất lượng cao với giọng đọc truyền cảm và sống động.
              </p>

              <div className="audioStatCardsRow">
                <div className="audioStatGlassCard">
                  <div className="statIconCircle">
                    <Headphones size={20} />
                  </div>
                  <div className="statText">
                    <strong>1M+</strong>
                    <span>Giờ nghe audio</span>
                  </div>
                </div>

                <div className="audioStatGlassCard">
                  <div className="statIconCircle">
                    <Volume2 size={20} />
                  </div>
                  <div className="statText">
                    <strong>3K+</strong>
                    <span>Tập truyện audio</span>
                  </div>
                </div>

                <div className="audioStatGlassCard">
                  <div className="statIconCircle">
                    <Sparkles size={20} />
                  </div>
                  <div className="statText">
                    <strong>100%</strong>
                    <span>Âm thanh chất lượng cao</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <div className="audioMainBodyContainer">
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
        </div>
      </div>
    </PublicShell>
  );
}
