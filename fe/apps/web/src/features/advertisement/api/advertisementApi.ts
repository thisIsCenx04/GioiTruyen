import type { GlobalAdvertisement } from "../types/advertisement";

const API_BASE_URL = (import.meta.env.VITE_API_URL || "/api/v1").replace(/\/+$/u, "");

export async function getGlobalAdvertisement(signal?: AbortSignal): Promise<GlobalAdvertisement | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/public/advertisements/active?placement=GLOBAL_CLICK`, {
      cache: "no-store",
      credentials: "same-origin",
      headers: { Accept: "application/json" },
      signal,
    });

    if (response.status === 204 || !response.ok) {
      return null;
    }

    return (await response.json()) as GlobalAdvertisement;
  } catch {
    return null;
  }
}

/**
 * Records that an ad was shown or acted on.
 *
 * <p>The endpoint has existed since the ads feature was built, but nothing ever
 * called it, so ad_events stayed empty and the admin dashboard could only ever
 * report zero impressions and zero clicks - which reads as "the banners are
 * broken" rather than "nobody is counting".
 *
 * <p>A failed report is dropped on purpose: measurement must never interrupt
 * the reader, and a lost event is better than a blocked page.
 */
export async function trackAdEvent(
  advertisementId: string,
  eventType: "IMPRESSION" | "CLICK" | "REDIRECT",
): Promise<void> {
  try {
    await fetch(`${API_BASE_URL}/public/advertisements/${advertisementId}/events`, {
      body: JSON.stringify({ eventType, pageUrl: window.location.pathname.slice(0, 1000) }),
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      // keepalive lets the report survive the navigation that a click starts.
      keepalive: true,
      method: "POST",
    });
  } catch {
    // Deliberately silent - see above.
  }
}
