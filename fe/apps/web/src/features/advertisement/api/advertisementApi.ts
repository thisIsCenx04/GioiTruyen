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
