"use client";

import { useEffect } from "react";
import { useLocation } from "react-router-dom";

/**
 * Puts every navigation back at the top of the page.
 *
 * A single-page app keeps the window's scroll offset when the route changes, so
 * opening a story from halfway down a listing dropped the reader into the middle
 * of the new page. Browsers also restore the previous offset on reload, which
 * fights this, so that behaviour is turned off too.
 *
 * The hash is honoured when present: an in-page anchor like "#chapter-list"
 * should still jump to its target rather than the top.
 */
export function ScrollToTop() {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if ("scrollRestoration" in window.history) {
      window.history.scrollRestoration = "manual";
    }
  }, []);

  useEffect(() => {
    if (hash) {
      const target = document.getElementById(hash.slice(1));
      if (target) {
        target.scrollIntoView();
        return;
      }
    }
    // "instant" rather than smooth: a new page should already be at the top,
    // not visibly race there.
    window.scrollTo({ behavior: "instant" as ScrollBehavior, left: 0, top: 0 });
  }, [pathname, hash]);

  return null;
}
