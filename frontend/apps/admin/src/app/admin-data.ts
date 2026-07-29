const defaultApiBaseUrl = "http://127.0.0.1:8080/api/v1";

const apiBaseUrl = (process.env.API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? defaultApiBaseUrl).replace(
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
  tasks: string[];
};

export type AdminStoryRow = {
  id: string;
  slug: string;
  title: string;
  authorName: string;
  teamName: string;
  workflowStatus: string;
  completionStatus: string;
  updatedAt: string | null;
};

export type AdminTeamRow = {
  id: string;
  slug: string;
  name: string;
  ownerName: string;
  state: string;
  memberCount: number;
  updatedAt: string | null;
};

export type AdminUserRow = {
  id: string;
  email: string;
  displayName: string;
  state: string;
  roles: string;
  createdAt: string | null;
};

export type AdminCashFlowRow = {
  id: string;
  entryType: string;
  amountXu: number;
  referenceType: string;
  referenceId: string;
  description: string;
  userEmail: string;
  createdAt: string | null;
};

async function adminRequest<T>(path: `/${string}`): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    cache: "no-store",
    headers: { Accept: "application/json" },
  });

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
    tasks: [
      "Không kết nối được API admin. Kiểm tra backend local/prod trước khi thao tác dữ liệu.",
      "Khi API sẵn sàng, dashboard sẽ tự đọc lại từ database.",
    ],
    trafficSeries: [],
  }));
}

export function loadAdminStories() {
  return adminRequest<AdminStoryRow[]>("/admin/content/stories").catch(() => []);
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
