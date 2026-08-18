"use client";

import { useEffect, useRef } from "react";

/**
 * Puts the reader at the top of the chapter list when they change page.
 *
 * <p>Paging is a route change carrying a new {@code ?page=}, and the browser
 * keeps the scroll position across it - so clicking "2" left the reader looking
 * at the same spot on the page while chapters 21-40 had quietly replaced 1-20
 * above them. They had to scroll up to find where the new range began.
 *
 * <p>Only fires on a change, never on first render: arriving at the story page
 * should show the story, not jump past the cover straight to the list.
 */
export function ScrollToChapterList({ page }: Readonly<{ page: number }>) {
  const previous = useRef<number | null>(null);

  useEffect(() => {
    if (previous.current === null) {
      previous.current = page;
      return;
    }
    if (previous.current === page) return;
    previous.current = page;

    const list = document.getElementById("chapter-list");
    if (!list) return;
    // Behaviour follows the reader's motion preference; "smooth" for everyone
    // else so the jump reads as movement rather than a teleport.
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    list.scrollIntoView({ behavior: reduced ? "auto" : "smooth", block: "start" });
  }, [page]);

  return null;
}
