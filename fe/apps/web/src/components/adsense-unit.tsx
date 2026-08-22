"use client";

import { useEffect, useRef } from "react";

/**
 * The publisher account these units bill to.
 *
 * <p>Overridable from the environment so a staging build can point at a
 * different account, but the real account is the default: the id is already
 * baked into index.html, where it doubles as the site-ownership snippet, and
 * the two must not disagree.
 */
export const ADSENSE_CLIENT =
  import.meta.env.VITE_ADSENSE_CLIENT || "ca-pub-4260233431693229";

/**
 * Ad unit ids from the AdSense dashboard, by where they appear.
 *
 * <p>Create the unit under Ads → By ad unit → Display ads, then supply its id
 * (a ten-digit number) as the environment variable named beside it. They are
 * read from the environment rather than written here so that turning a
 * placement on is a deploy setting, not a code change - and so that no
 * placeholder id can ever be mistaken for a real one.
 *
 * <p>A slot left empty renders nothing at all. A manual unit with no
 * `data-ad-slot` never fills, so an empty `<ins>` is only a hole in the page
 * and an unfilled-impression signal against the account. Every placement below
 * is wired up and starts serving the moment its id is set.
 */
export const ADSENSE_SLOTS: Readonly<Record<string, string>> = {
  /** VITE_ADSENSE_SLOT_CHAPTER_GATE - the interstitial between two chapters. */
  chapterGate: import.meta.env.VITE_ADSENSE_SLOT_CHAPTER_GATE || "",
  /** VITE_ADSENSE_SLOT_CHAPTER_FOOTER - below the chapter text. */
  chapterFooter: import.meta.env.VITE_ADSENSE_SLOT_CHAPTER_FOOTER || "",
  /** VITE_ADSENSE_SLOT_CHAPTER_TOP - above the chapter text. */
  chapterTop: import.meta.env.VITE_ADSENSE_SLOT_CHAPTER_TOP || "",
  /** VITE_ADSENSE_SLOT_STORY_DETAIL - under the synopsis on a story page. */
  storyDetail: import.meta.env.VITE_ADSENSE_SLOT_STORY_DETAIL || "",
  /** VITE_ADSENSE_SLOT_HOME_TOP - between the promoted board and the shelves. */
  homeTop: import.meta.env.VITE_ADSENSE_SLOT_HOME_TOP || "",
  /** VITE_ADSENSE_SLOT_HOME_MID - further down the home page. */
  homeMid: import.meta.env.VITE_ADSENSE_SLOT_HOME_MID || "",
  /** VITE_ADSENSE_SLOT_CATALOG - inside a catalogue listing. */
  catalog: import.meta.env.VITE_ADSENSE_SLOT_CATALOG || "",
};

declare global {
  interface Window {
    adsbygoogle?: unknown[];
  }
}

/**
 * Height held for a slot before the ad arrives.
 *
 * <p>Without this the page reflows the moment a creative loads: the paragraph
 * being read jumps down the screen, and the layout shift counts against Core
 * Web Vitals. The values are the shortest common creative for each shape, so
 * the reserved band is filled rather than leaving a gap when a taller ad
 * arrives.
 */
const RESERVED_HEIGHT: Readonly<Record<string, number>> = {
  auto: 280,
  horizontal: 100,
  rectangle: 250,
  vertical: 600,
};

/**
 * One AdSense slot.
 *
 * <p>The unit is pushed once per mount. Pushing the same <ins> twice makes
 * AdSense log "already have ads in them" and leave the slot blank, so a ref
 * guards against React re-running the effect.
 */
export function AdsenseUnit({
  className,
  format = "auto",
  slot,
  style,
}: Readonly<{
  className?: string;
  format?: string;
  /** Ad unit id from the AdSense dashboard. Leave empty for auto ads. */
  slot?: string;
  style?: React.CSSProperties;
}>) {
  const pushed = useRef(false);
  const configured = Boolean(slot);

  useEffect(() => {
    // Nothing is pushed for an unconfigured slot. Pushing one asks AdSense to
    // fill a unit it cannot identify, which reports as an unfilled impression
    // and leaves a blank box on the page.
    if (!configured || pushed.current) return;
    pushed.current = true;
    try {
      (window.adsbygoogle = window.adsbygoogle ?? []).push({});
    } catch {
      // Blocked by an extension, or the script never loaded. The surrounding
      // UI must keep working either way, so this is deliberately silent.
    }
  }, [configured]);

  // No slot id yet: render nothing rather than an empty frame. See ADSENSE_SLOTS.
  if (!configured) return null;

  return (
    <ins
      className={className ? `adsbygoogle ${className}` : "adsbygoogle"}
      data-ad-client={ADSENSE_CLIENT}
      data-ad-format={format}
      data-ad-slot={slot}
      data-full-width-responsive="true"
      style={{
        display: "block",
        // Reserved rather than collapsed, so the arriving creative does not
        // shove the page. An unfilled slot collapses itself, so this costs
        // nothing when Google has nothing to serve.
        minHeight: RESERVED_HEIGHT[format] ?? RESERVED_HEIGHT.auto,
        ...style,
      }}
    />
  );
}
