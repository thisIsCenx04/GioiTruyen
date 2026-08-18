import { API_BASE_URL, apiFetch } from "@/lib/api-base";

/** Banner slot to image URL. A slot with no upload is simply absent. */
export type SiteBanners = Record<string, string>;

/**
 * Banner images an admin uploaded in the dashboard.
 *
 * Never throws: a page asking for its banner falls back to the artwork it draws
 * itself, so a failed call must not take the hero down with it.
 */
export async function loadSiteBanners(): Promise<SiteBanners> {
  try {
    const response = await apiFetch(`${API_BASE_URL}/site/banners`);
    if (!response.ok) return {};
    const payload: unknown = await response.json();
    return payload && typeof payload === "object" ? (payload as SiteBanners) : {};
  } catch {
    return {};
  }
}
