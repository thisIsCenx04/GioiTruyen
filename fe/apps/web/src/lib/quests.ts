import { getAccessToken, refreshAccessToken } from "./auth";

const API = "/api/v1";

export type QuestProgress = {
  questId: string;
  questType: string;
  title: string;
  description: string | null;
  targetValue: number;
  progressValue: number;
  rewardCoin: number;
  rewardGem: number;
  completed: boolean;
  claimed: boolean;
};

export type QuestBoard = {
  questDate: string;
  completedCount: number;
  totalCount: number;
  quests: QuestProgress[];
};

export type ClaimResult = {
  questId: string;
  rewardCoin: number;
  rewardGem: number;
  coinBalance: number;
  gemBalance: number;
};

/** Quest types the progress ping accepts; mirrors the backend enum. */
export type QuestType =
  | "LOGIN_DAILY"
  | "READ_MINUTES"
  | "ONLINE_MINUTES"
  | "READ_CHAPTERS"
  | "SHARE_STORY"
  | "COMMENT_STORY";

async function questFetch(path: string, init?: RequestInit): Promise<Response> {
  const send = (token: string | null) => {
    const headers = new Headers(init?.headers);
    headers.set("Accept", "application/json");
    if (init?.body) headers.set("Content-Type", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${API}${path}`, { ...init, headers });
  };

  let response = await send(getAccessToken());
  // Quest pings run for as long as the tab is open, so the 30-minute access
  // token will expire mid-session; renew once rather than dropping progress.
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) response = await send(renewed);
  }
  return response;
}

export async function loadQuestBoard(): Promise<QuestBoard | null> {
  const response = await questFetch("/quests/me");
  if (!response.ok) return null;
  return (await response.json()) as QuestBoard;
}

/** The quest catalogue for a signed-out visitor; progress is all zero. */
export async function loadQuestPreview(): Promise<QuestBoard | null> {
  const response = await fetch(`${API}/quests/preview`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) return null;
  return (await response.json()) as QuestBoard;
}

/**
 * Reports progress. Failures are swallowed: a missed ping only costs a minute of
 * quest credit and must never interrupt reading.
 */
export async function reportQuestProgress(
  questType: QuestType,
  amount = 1,
): Promise<QuestBoard | null> {
  try {
    const response = await questFetch("/quests/progress", {
      body: JSON.stringify({ amount, questType }),
      method: "POST",
    });
    if (!response.ok) return null;
    return (await response.json()) as QuestBoard;
  } catch {
    return null;
  }
}

export async function claimQuestReward(questId: string): Promise<ClaimResult> {
  const response = await questFetch(`/quests/${questId}/claim`, { method: "POST" });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(problem?.detail ?? "Không nhận được thưởng. Vui lòng thử lại.");
  }
  return (await response.json()) as ClaimResult;
}
