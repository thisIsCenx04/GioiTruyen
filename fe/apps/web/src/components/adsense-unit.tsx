"use client";

import { useEffect, useRef } from "react";

/** The publisher account these units bill to. */
export const ADSENSE_CLIENT = "ca-pub-4260233431693229";

declare global {
  interface Window {
    adsbygoogle?: unknown[];
  }
}

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

  useEffect(() => {
    if (pushed.current) return;
    pushed.current = true;
    try {
      (window.adsbygoogle = window.adsbygoogle ?? []).push({});
    } catch {
      // Blocked by an extension, or the script never loaded. The surrounding
      // UI must keep working either way, so this is deliberately silent.
    }
  }, []);

  return (
    <ins
      className={className ? `adsbygoogle ${className}` : "adsbygoogle"}
      data-ad-client={ADSENSE_CLIENT}
      data-ad-format={format}
      data-ad-slot={slot}
      data-full-width-responsive="true"
      style={style ?? { display: "block" }}
    />
  );
}
