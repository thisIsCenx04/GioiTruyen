"use client";

import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";

/**
 * Tells a reader when the site has been redeployed under them.
 *
 * <p>A single-page app keeps running whatever JavaScript it started with. After
 * a deploy, a tab left open goes on using the old bundle indefinitely - which is
 * how a publisher came to report chapter-splitting bugs that had already been
 * fixed and shipped: the fix was on the server, their tab was not.
 *
 * <p>index.html is served with no-store and names the hashed bundle, so
 * comparing the bundle it points at against the one running is enough to know a
 * deploy has happened. Nothing reloads on its own: a reader mid-chapter, or a
 * publisher with an unsaved form, decides when.
 */

/** How often to look. Long enough to be invisible, short enough to matter. */
const CHECK_INTERVAL_MS = 5 * 60 * 1000;

/** The bundle this tab is running, read from its own script tag. */
function runningBundle(): string | null {
  const scripts = [...document.querySelectorAll<HTMLScriptElement>("script[src]")];
  const entry = scripts.find((script) => /\/assets\/index-[^/]+\.js/u.test(script.src));
  return entry ? new URL(entry.src).pathname : null;
}

/** The bundle the server is currently serving, or null if it cannot be read. */
async function deployedBundle(): Promise<string | null> {
  try {
    // cache: no-store on top of the no-store header, because a service worker or
    // an intermediary proxy is not bound by the header alone.
    const response = await fetch(`/index.html?v=${Date.now()}`, { cache: "no-store" });
    if (!response.ok) return null;
    const html = await response.text();
    return /\/assets\/index-[^"']+\.js/u.exec(html)?.[0] ?? null;
  } catch {
    // Offline, or the check was blocked. Silence is right: this is a courtesy,
    // and a failed check must never interrupt reading.
    return null;
  }
}

export function UpdateAvailable() {
  const [stale, setStale] = useState(false);

  useEffect(() => {
    const running = runningBundle();
    // In dev there is no hashed bundle to compare, so there is nothing to do.
    if (!running) return undefined;

    let cancelled = false;
    const check = async () => {
      const deployed = await deployedBundle();
      if (!cancelled && deployed && deployed !== running) setStale(true);
    };

    const timer = window.setInterval(() => void check(), CHECK_INTERVAL_MS);
    // Coming back to a tab left open overnight is the case that matters most.
    const onVisible = () => { if (document.visibilityState === "visible") void check(); };
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, []);

  if (!stale) return null;

  return (
    <div className="updateBanner" role="status">
      <RefreshCw aria-hidden="true" size={16} />
      <span>Đã có bản cập nhật mới của trang.</span>
      <button
        onClick={() => window.location.reload()}
        type="button"
      >
        Tải lại ngay
      </button>
      <button
        aria-label="Đóng thông báo"
        className="updateBannerDismiss"
        onClick={() => setStale(false)}
        type="button"
      >
        ×
      </button>
    </div>
  );
}
