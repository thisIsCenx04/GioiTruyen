"use client";

import { useMemo } from "react";

/**
 * A house banner shown inside a chapter and again on each move to the next one.
 *
 * The image is picked per chapter rather than per render, so scrolling does not
 * shuffle it under the reader; opening a different chapter picks another.
 */

/**
 * House banner artwork already shipped with the site. Replace these paths with
 * real creatives (or an ad-network slot) when there are campaigns to run.
 */
const BANNERS = [
  "/stories_hero_banner.jpg",
  "/categories_hero_banner.jpg",
  "/audio_hero_banner.jpg",
  "/hero_teams_wallpaper.jpg",
] as const;

/** Stable per-key choice: the same chapter always shows the same banner. */
function pick(key: string) {
  let hash = 0;
  for (let index = 0; index < key.length; index++) {
    hash = (hash * 31 + key.charCodeAt(index)) | 0;
  }
  return BANNERS[Math.abs(hash) % BANNERS.length]!;
}

export function ReaderAdBanner({
  seed,
  placement = "inline",
}: Readonly<{
  /** Usually the chapter id, so the banner changes when the chapter does. */
  seed: string;
  placement?: "inline" | "between";
}>) {
  const src = useMemo(() => pick(`${seed}:${placement}`), [seed, placement]);

  return (
    <aside
      aria-label="Quảng cáo"
      className={placement === "between" ? "readerAd isBetween" : "readerAd"}
    >
      <span className="readerAdLabel">Quảng cáo</span>
      <img alt="" loading="lazy" src={src} />
    </aside>
  );
}
