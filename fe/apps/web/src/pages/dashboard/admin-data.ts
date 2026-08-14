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
  updatedAt: string | null;
  createdAt: string | null;
};

export type AdminCategoryRow = {
  id: string;
  slug: string;
  name: string;
  description: string;
  sortOrder: number;
  active: boolean;
  version: number;
  createdAt: string | null;
  updatedAt: string | null;
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

function send(path: `/${string}`, token: string | null) {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return fetch(`${apiBaseUrl}${path}`, {
    cache: "no-store",
    credentials: "same-origin",
    headers,
    signal: AbortSignal.timeout(8000),
  });
}

async function adminRequest<T>(path: `/${string}`): Promise<T> {
  let response = await send(path, getAccessToken());

  // Access tokens expire after 30 minutes; renew once before giving up so an
  // admin mid-session is not silently logged out.
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) {
      response = await send(path, renewed);
    }
  }

  if (!response.ok) {
    throw new Error(`Admin API request failed: ${response.status} ${response.statusText}`);
  }

  return (await response.json()) as T;
}

export function loadAdminOverview() {
  return adminRequest<AdminOverview>("/admin/dashboard").catch(() => ({
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
