import { BookOpen, Clock, Flame, Library, Users } from "lucide-react";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { RankingPanel } from "@/components/ranking-panel";
import { TabbedStoryLayout } from "@/components/tabbed-story-layout";
import { PublicShell } from "@/components/site-chrome";
import { loadHome } from "@/lib/catalog";

import { StoriesCatalogGrid } from "@/components/stories-catalog-grid";

export const revalidate = 60;

export default async function StoriesPage() {
  const home = await loadHome().catch(() => null);
  const storySections = home?.storySections && home.storySections.length > 0
    ? home.storySections
    : home?.sections ?? [];
  const stories = [
    ...new Map(
      storySections
        .flatMap((section) => section.stories)
        .map((story) => [story.id, story] as const),
    ).values(),
  ];

  return (
    <PublicShell>
      <div className="storiesPageRedesignShell">
        <div className="storiesMainBodyContainer">
          {/* Main 2-Column Content Layout (Left Story Grid + Right Side Rail Widgets) */}
          <div
            className="storyRankingLayout detailContentLayout"
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 310px",
              gap: "1.5rem",
              alignItems: "start",
            }}
          >
            {/* Left Main Column: Interactive Stories Catalog Grid (40 stories per page) */}
            <StoriesCatalogGrid stories={stories} pageSize={40} />

            {/* Right Side Rail Widgets Column matching MonkeyD reference UI */}
            <aside style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
              {/* Widget 1: BẢNG XẾP HẠNG */}
              <RankingPanel stories={stories} title="BẢNG XẾP HẠNG" />

              {/* Widget 2: TOP VIEW */}
              <RankingPanel stories={[...stories].reverse()} title="TOP VIEW" />

              {/* Widget 3: ĐÓNG GÓP */}
              <div
                className="sideCard"
                style={{
                  background: "#ffffff",
                  borderRadius: "12px",
                  border: "1px solid #e2e8f0",
                  padding: "1.1rem",
                }}
              >
                <header style={{ marginBottom: "0.8rem" }}>
                  <h2 style={{ fontSize: "1rem", fontWeight: 800, textTransform: "uppercase", margin: 0 }}>
                    ĐÓNG GÓP
                  </h2>
                </header>
                <div style={{ display: "flex", gap: "0.5rem", marginBottom: "0.85rem" }}>
                  <span
                    style={{
                      background: "#ef4444",
                      color: "#fff",
                      padding: "0.25rem 0.65rem",
                      borderRadius: "4px",
                      fontSize: "0.75rem",
                      fontWeight: 700,
                    }}
                  >
                    Tiếng Việt
                  </span>
                </div>
                <ol style={{ listStyle: "none", padding: 0, margin: 0 }}>
                  {stories.slice(0, 5).map((story, index) => (
                    <li
                      key={`contributor-${story.id}`}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "0.75rem",
                        padding: "0.45rem 0",
                        borderBottom: index < 4 ? "1px solid #f1f5f9" : "none",
                      }}
                    >
                      <span
                        style={{
                          width: "22px",
                          height: "22px",
                          borderRadius: "50%",
                          background:
                            index === 0
                              ? "#ef4444"
                              : index === 1
                                ? "#f97316"
                                : index === 2
                                  ? "#eab308"
                                  : "#cbd5e1",
                          color: "#fff",
                          fontSize: "0.75rem",
                          fontWeight: 800,
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                        }}
                      >
                        {index + 1}
                      </span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div
                          style={{
                            fontSize: "0.82rem",
                            fontWeight: 700,
                            whiteSpace: "nowrap",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                          }}
                        >
                          {story.title}
                        </div>
                        <div style={{ fontSize: "0.7rem", color: "#64748b" }}>
                          {120 - index * 18} chương đóng góp
                        </div>
                      </div>
                    </li>
                  ))}
                </ol>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </PublicShell>
  );
}
