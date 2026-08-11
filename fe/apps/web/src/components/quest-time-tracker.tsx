"use client";

import { useEffect, useRef } from "react";

import { isLoggedIn } from "@/lib/auth";
import { reportQuestProgress, type QuestType } from "@/lib/quests";

/** One ping per minute: the smallest cadence the minute-based quests need. */
const PING_INTERVAL_MS = 60_000;

/**
 * Counts a minute only if the reader did something in it. Without this a tab
 * left open overnight would finish every timed quest on its own.
 */
const ACTIVITY_WINDOW_MS = 90_000;

/**
 * Reports time spent so the minute-based quests advance.
 *
 * <p>Deliberately cheap: one request a minute per open tab, skipped entirely
 * while the tab is hidden or the reader is idle, and silent on failure. Mount
 * with `questType="READ_MINUTES"` on the chapter reader and
 * `"ONLINE_MINUTES"` once at the app shell.
 */
export function QuestTimeTracker({ questType }: Readonly<{ questType: QuestType }>) {
  const lastActivity = useRef(Date.now());

  useEffect(() => {
    if (!isLoggedIn()) return undefined;

    const markActive = () => {
      lastActivity.current = Date.now();
    };
    // Passive listeners keep scrolling smooth; these only stamp a timestamp.
    const events: Array<keyof WindowEventMap> = [
      "pointerdown",
      "keydown",
      "scroll",
      "focus",
    ];
    for (const event of events) {
      window.addEventListener(event, markActive, { passive: true });
    }

    const timer = window.setInterval(() => {
      // A hidden tab is not reading, and an idle one is not either.
      if (document.visibilityState !== "visible") return;
      if (Date.now() - lastActivity.current > ACTIVITY_WINDOW_MS) return;
      void reportQuestProgress(questType, 1);
    }, PING_INTERVAL_MS);

    return () => {
      window.clearInterval(timer);
      for (const event of events) {
        window.removeEventListener(event, markActive);
      }
    };
  }, [questType]);

  return null;
}
