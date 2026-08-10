const API_BASE = "/api/v1";

export type PromotionPackage = {
  id: string;
  code: string;
  name: string;
  durationDays: number;
  priceCoin: number;
  pricePerDayCoin: number;
  description: string | null;
};

export type PromotableStory = {
  storyId: string;
  slug: string;
  title: string;
  coverUrl: string | null;
  teamId: string;
  teamName: string;
  promoting: boolean;
  activeUntil: string | null;
  activeDaysRemaining: number;
};

export type PromotionBooking = {
  id: string;
  storyId: string;
  storyTitle: string;
  storySlug: string;
  teamId: string;
  teamName: string;
  durationDays: number;
  coinPaid: number;
  startsAt: string;
  endsAt: string;
  status: string;
  daysRemaining: number;
};

export type PromotionOverview = {
  packages: PromotionPackage[];
  stories: PromotableStory[];
  bookings: PromotionBooking[];
  walletCoinBalance: number;
  maxTotalDays: number;
};

function authToken() {
  if (typeof window === "undefined") return null;
  const fromStorage = localStorage.getItem("access_token");
  if (fromStorage) return fromStorage;
  const cookie = document.cookie.match(/(?:^|; )access_token=([^;]*)/);
  return cookie ? decodeURIComponent(cookie[1]) : null;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = authToken();
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(init.body ? { "Content-Type": "application/json" } : {}),
    ...((init.headers as Record<string, string>) ?? {}),
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: "same-origin",
    headers,
  });

  if (!response.ok) {
    // The API returns RFC 7807 problems; `detail` carries the Vietnamese reason.
    const problem = (await response.json().catch(() => null)) as
      | { detail?: string; title?: string }
      | null;
    throw new Error(problem?.detail ?? problem?.title ?? `Yêu cầu thất bại (${response.status}).`);
  }
  return (await response.json()) as T;
}

export function loadPromotionPackages() {
  return request<PromotionPackage[]>("/promotions/packages");
}

export function loadPromotionOverview() {
  return request<PromotionOverview>("/promotions/me");
}

export function createPromotion(storyId: string, packageId: string) {
  return request<PromotionBooking>("/promotions", {
    method: "POST",
    body: JSON.stringify({ storyId, packageId }),
  });
}

export function extendPromotion(promotionId: string, packageId: string) {
  return request<PromotionBooking>(`/promotions/${promotionId}/extend`, {
    method: "POST",
    body: JSON.stringify({ packageId }),
  });
}
