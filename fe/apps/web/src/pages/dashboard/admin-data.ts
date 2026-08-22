import { getAccessToken, refreshAccessToken } from "../../lib/auth";

const defaultApiBaseUrl = "/api/v1";

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? defaultApiBaseUrl).replace(
  /\/+$/u,
  "",
);

export type ChartPoint = {
  label: string;
  value: number;
};

/** One placement's own-banner performance, from ad_events. */
export type AdPlacementRow = {
  placement: string;
  impressions: number;
  clicks: number;
  /** Clicks per hundred impressions. */
  ctr: number;
  activeUnits: number;
};

export type AdOverview = {
  impressions: number;
  clicks: number;
  ctr: number;
  activeUnits: number;
  impressionSeries: ChartPoint[];
  clickSeries: ChartPoint[];
  placements: AdPlacementRow[];
};

export type AdminOverview = {
  stats: {
    revenueXu: number;
    visits: number;
    readers: number;
    teams: number;
    stories: number;
  };
  revenueSeries: ChartPoint[];
  trafficSeries: ChartPoint[];
  readerSeries: ChartPoint[];
  tasks: string[];
  ads: AdOverview;
};

const EMPTY_ADS: AdOverview = {
  activeUnits: 0,
  clicks: 0,
  clickSeries: [],
  ctr: 0,
  impressions: 0,
  impressionSeries: [],
  placements: [],
};

export type AdminStoryRow = {
  id: string;
  slug: string;
  title: string;
  authorName: string | null;
  teamName: string | null;
  teamId: string;
  categoryId: string | null;
  categoryName: string | null;
  /** Every genre the story belongs to; a story can carry several. */
  categoryIds?: string[];
  categoryNames?: string[];
  coverUrl?: string;
  synopsis: string | null;
  tags?: string[];
  /** SERIAL or ONESHOT; absent on rows saved before the format was introduced. */
  storyFormat?: string;
  /** TEXT, AUDIO, EXCLUSIVE or ORIGINAL; defaults to TEXT. */
  storyType?: string;
  workflowStatus: string;
  completionStatus: string;
  comboPriceXu?: number;
  chapterCount?: number;
  updatedAt: string | null;
  createdAt: string | null;
};

export type AdminCategoryRow = {
  id: string;
  slug: string;
  name: string;
  description: string;
  active: boolean;
  version: number;
  createdAt: string | null;
  updatedAt: string | null;
  /** Stories carrying this genre, whatever their workflow state. */
  storyCount: number;
  /** The published subset - what a reader can actually reach. */
  publishedStoryCount: number;
};

export type AdminTeamRow = {
  id: string;
  slug: string;
  name: string;
  ownerName: string;
  ownerUserId: string;
  description: string;
  state: string;
  memberCount: number;
  updatedAt: string | null;
  createdAt: string | null;
};

export type AdminUserRow = {
  id: string;
  email: string;
  displayName: string;
  bio: string;
  state: string;
  roles: string;
  availableXu: number;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AdminCashFlowRow = {
  id: string;
  entryType: string;
  amountXu: number;
  referenceType: string;
  referenceId: string;
  description: string;
  userId: string;
  userEmail: string;
  createdAt: string | null;
};

function send(path: `/${string}`, token: string | null, init: RequestInit = {}) {
  const headers: Record<string, string> = { Accept: "application/json", ...(init.headers as Record<string, string>) };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return fetch(`${apiBaseUrl}${path}`, {
    cache: "no-store",
    credentials: "same-origin",
    ...init,
    headers,
    signal: AbortSignal.timeout(8000),
  });
}

async function adminRequest<T>(path: `/${string}`, init: RequestInit = {}): Promise<T> {
  let response = await send(path, getAccessToken(), init);

  // Access tokens expire after 30 minutes; renew once before giving up so an
  // admin mid-session is not silently logged out.
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) {
      response = await send(path, renewed, init);
    }
  }

  if (!response.ok) {
    // The server's own explanation, when it sent one. "HTTP 409" tells an
    // admin nothing about which rule they hit.
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(problem?.detail
      ?? `Admin API request failed: ${response.status} ${response.statusText}`);
  }

  // 200 with no body is a valid answer for a command endpoint.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/**
 * The admin API, for calls that are not one of the named loaders above.
 *
 * <p>Same token handling and the same one-shot refresh; the difference is that
 * it takes a method and a body, so a command can use it too.
 */
export function adminFetch<T>(path: `/${string}`, init: RequestInit = {}): Promise<T> {
  return adminRequest<T>(path, init);
}

export function loadAdminOverview() {
  return adminRequest<AdminOverview>("/admin/dashboard")
    .then((overview) => ({ ...overview, ads: overview.ads ?? EMPTY_ADS }))
    .catch(() => ({
      ads: EMPTY_ADS,
      stats: {
        readers: 0,
        revenueXu: 0,
        stories: 0,
        teams: 0,
        visits: 0,
      },
      revenueSeries: [],
      readerSeries: [],
      tasks: [],
      trafficSeries: [],
    }));
}

export function loadAdminStories() {
  return adminRequest<AdminStoryRow[]>("/admin/content/stories").catch(() => []);
}

export function loadAdminCategories() {
  return adminRequest<AdminCategoryRow[]>("/admin/content/categories").catch(() => []);
}

export function loadAdminTeams() {
  return adminRequest<AdminTeamRow[]>("/admin/content/teams").catch(() => []);
}

export function loadAdminUsers() {
  return adminRequest<AdminUserRow[]>("/admin/content/users").catch(() => []);
}

export function loadAdminCashFlow() {
  return adminRequest<AdminCashFlowRow[]>("/admin/finance/cash-flow").catch(() => []);
}
