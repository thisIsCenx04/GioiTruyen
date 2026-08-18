import React, { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Layers, ArrowRight } from "lucide-react";

import { CatalogStoryCard } from "./catalog-story-card";
import { catalog } from "@/lib/catalog";
import styles from "./tabbed-story-layout.module.css";

export interface CategoryItem {
  id: string;
  name: string;
  slug: string;
  /** Published stories in this genre. Absent on payloads from an older API. */
  storyCount?: number;
}

interface CategoryTabbedLayoutProps {
  categories: CategoryItem[];
  fallbackStories: HomeStorySummary[];
}

/** Genres prefetched immediately; the rest follow once these have landed. */
const FIRST_WAVE = 6;
/** How many go out together in each later wave, well under the burst ceiling. */
const WAVE_SIZE = 8;
/** Breathing room between waves so browsing stays ahead of the prefetch. */
const WAVE_DELAY_MS = 1200;

export function CategoryTabbedLayout({
  categories,
  fallbackStories,
}: Readonly<CategoryTabbedLayoutProps>) {
  if (categories.length === 0) return null;

  /**
   * Busiest genre first.
   *
   * The API returns genres alphabetically, which put "Bách Hợp" - empty - at
   * the front and buried the ones actually worth opening. Ties fall back to
   * name so the order is stable rather than arbitrary.
   */
  const ordered = React.useMemo(
    () => [...categories].sort((left, right) => {
      const byCount = (right.storyCount ?? 0) - (left.storyCount ?? 0);
      return byCount !== 0 ? byCount : left.name.localeCompare(right.name, "vi");
    }),
    [categories],
  );
  /** Genres with nothing in them have nothing to fetch, ever. */
  const populated = React.useMemo(
    () => ordered.filter((cat) => (cat.storyCount ?? 0) > 0),
    [ordered],
  );

  const [activeCatId, setActiveCatId] = useState<string>(ordered[0].id);
  const activeCat = ordered.find((c) => c.id === activeCatId) ?? ordered[0];
  /**
   * Stories for the genres fetched so far.
   *
   * The page used to fetch every genre up front - 84 parallel requests against
   * a 60-per-second ceiling, so the tail came back 429 and those genres looked
   * empty. Fetching is now staged: the busiest few first, the rest in small
   * waves afterwards, and empty genres not at all.
   */
  const [storiesByCat, setStoriesByCat] = useState<Record<string, HomeStorySummary[]>>({});
  const [loading, setLoading] = useState(false);
  const requested = useRef<Set<string>>(new Set());

  const fetchCategory = React.useCallback((cat: CategoryItem) => {
    if (requested.current.has(cat.id)) return Promise.resolve();
    requested.current.add(cat.id);
    return catalog.categoryStories(cat.slug)
      .then((fetched) => {
        setStoriesByCat((current) => ({ ...current, [cat.id]: fetched ?? [] }));
      })
      .catch(() => {
        // Let it retry later rather than caching a failure as "genre is empty".
        requested.current.delete(cat.id);
      });
  }, []);

  // Opening a tab always takes priority over whatever the prefetch is doing.
  useEffect(() => {
    if (requested.current.has(activeCat.id)) return;
    setLoading(true);
    void fetchCategory(activeCat).finally(() => setLoading(false));
  }, [activeCat, fetchCategory]);

  // The staged prefetch: busiest genres first, then the quieter ones in waves,
  // so the tail of the list is warm by the time anyone scrolls to it without
  // ever putting 84 requests on the wire at once.
  useEffect(() => {
    let cancelled = false;
    const timers: number[] = [];

    void Promise.all(populated.slice(0, FIRST_WAVE).map(fetchCategory)).then(() => {
      if (cancelled) return;
      const rest = populated.slice(FIRST_WAVE);
      for (let start = 0; start < rest.length; start += WAVE_SIZE) {
        const wave = rest.slice(start, start + WAVE_SIZE);
        const delay = (start / WAVE_SIZE + 1) * WAVE_DELAY_MS;
        timers.push(window.setTimeout(() => {
          if (!cancelled) wave.forEach((cat) => void fetchCategory(cat));
        }, delay));
      }
    });

    return () => {
      cancelled = true;
      timers.forEach((timer) => window.clearTimeout(timer));
    };
  }, [populated, fetchCategory]);

  const activeStories = storiesByCat[activeCat.id];
  const displayedStories = activeStories && activeStories.length > 0
    ? activeStories.slice(0, 12)
    : fallbackStories.slice(0, 12);

  return (
    <div className={styles.sectionBox} style={{ marginTop: "1rem" }}>
      {/* Section Header */}
      <header className={styles.sectionHeader}>
        <div className={styles.titleGroup}>
          <span className={styles.titleIcon}>
            <Layers size={22} />
          </span>
          <h2 className={styles.titleText}>Thể loại</h2>
        </div>
        <Link
          className={styles.filterBtn}
          to={`/categories/${activeCat.slug}` as string}
        >
          <span>Xem tất cả {activeCat.name}</span>
          <ArrowRight size={14} />
        </Link>
      </header>

      {/* Main Body (Left Sidebar Tabs + Right Story Cards Grid) */}
      <div className={styles.sectionBody}>
        {/* Left Side Category Nav */}
        {/* The width belongs to the stylesheet, not here: an inline style wins
            over any media query without !important, so min/max-width set here
            clamped the nav to 220px even at the ≤768px breakpoint where it is
            supposed to become a full-width scrolling strip of tabs. */}
        <nav
          className={styles.sideNav}
          aria-label="Danh sách thể loại"
        >
          {ordered.map((cat) => {
            const isActive = activeCat.id === cat.id;
            // The API's own count. Deriving it from the length of a fetched
            // list under-reported any genre past the list's LIMIT, excluded
            // Zhihu stories, and required fetching all 84 genres to draw the
            // badges at all - which is what tripped the rate limiter.
            const count = cat.storyCount ?? 0;

            return (
              <button
                className={`${styles.tabItem} ${isActive ? styles.tabItemActive : ""}`}
                key={cat.id}
                onClick={() => setActiveCatId(cat.id)}
                type="button"
                style={{
                  justifyContent: "space-between",
                  padding: "0.55rem 0.8rem",
                  fontWeight: isActive ? 700 : 500,
                }}
              >
                <span>{cat.name}</span>
                {count > 0 ? (
                  <span
                    style={{
                      fontSize: "0.7rem",
                      padding: "0.15rem 0.45rem",
                      borderRadius: "10px",
                      background: isActive ? "#2563eb" : "#e2e8f0",
                      color: isActive ? "#ffffff" : "#475569",
                    }}
                  >
                    {count}
                  </span>
                ) : null}
              </button>
            );
          })}
        </nav>

        {/* Right Content Panel (Story Grid) */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: "0.85rem",
            }}
          >
            <h3 style={{ fontSize: "1.05rem", fontWeight: 700, margin: 0 }}>
              {activeCat.name}
            </h3>
            <span style={{ fontSize: "0.78rem", color: "#64748b" }}>
              {loading && !activeStories
                ? "Đang tải…"
                : `Hiển thị ${displayedStories.length} bộ truyện`}
            </span>
          </div>

          <div className={styles.cardGrid}>
            {displayedStories.map((story, idx) => (
              <CatalogStoryCard
                hrefBase="/stories"
                index={idx}
                key={`${activeCat.id}-${story.id}-${idx}`}
                story={story}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
