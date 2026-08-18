"use client";

import { useEffect } from "react";

import { loadSiteBanners } from "@/lib/site-banners";

/**
 * Publishes the admin-uploaded banners as CSS custom properties.
 *
 * Every page hero draws its artwork as `var(--banner-<slot>, url("/default"))`,
 * so a slot with no upload keeps the built-in image and one slot never has to
 * know about the pages that use it. Mounted once in the public shell.
 */
export function SiteBannerStyles() {
  useEffect(() => {
    let active = true;

    void loadSiteBanners().then((banners) => {
      if (!active) return;
      for (const [slot, url] of Object.entries(banners)) {
        // The value goes straight into a CSS declaration, so anything that
        // could close the url() and start another declaration is refused.
        if (!/^\/[\w\-./]*$/u.test(url)) continue;
        document.documentElement.style.setProperty(`--banner-${slot}`, `url("${url}")`);
      }
    });

    return () => {
      active = false;
    };
  }, []);

  return null;
}
