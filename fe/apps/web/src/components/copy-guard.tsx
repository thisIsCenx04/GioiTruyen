"use client";

import { useEffect } from "react";

/**
 * Discourages casual copying of chapter text.
 *
 * <p>What this is: selection and the context menu are switched off inside the
 * reader, drag-to-save is blocked on its images, and anything that still gets
 * onto the clipboard has a short attribution line appended.
 *
 * <p>What this is not: protection. The text arrives in the browser, so anyone
 * willing to open devtools or read the network response can take it. Blocking
 * F12 and similar is theatre - it fails against the people it targets while
 * breaking keyboard shortcuts for everyone else. The real defences elsewhere
 * are the paywall (paid chapters never leave the server unpaid) and the DMCA
 * route for sites that republish.
 *
 * <p>Selection is deliberately left alone outside the chapter body, so readers
 * can still copy a title, a URL, or their own comment.
 */
export function CopyGuard({
  attribution,
  selector = ".chapterProse",
}: Readonly<{
  /** Appended to copied text; usually the story URL. */
  attribution: string;
  selector?: string;
}>) {
  useEffect(() => {
    const inside = (target: EventTarget | null) =>
      target instanceof Node
      && Boolean((target instanceof Element ? target : target.parentElement)?.closest(selector));

    const onCopy = (event: ClipboardEvent) => {
      const selection = document.getSelection()?.toString() ?? "";
      if (!selection || !inside(event.target)) return;
      event.preventDefault();
      event.clipboardData?.setData(
        "text/plain",
        `${selection}\n\nNguồn: ${attribution}`,
      );
    };

    const block = (event: Event) => {
      if (inside(event.target)) event.preventDefault();
    };

    document.addEventListener("copy", onCopy);
    document.addEventListener("contextmenu", block);
    document.addEventListener("dragstart", block);
    document.addEventListener("cut", block);
    return () => {
      document.removeEventListener("copy", onCopy);
      document.removeEventListener("contextmenu", block);
      document.removeEventListener("dragstart", block);
      document.removeEventListener("cut", block);
    };
  }, [attribution, selector]);

  return null;
}
