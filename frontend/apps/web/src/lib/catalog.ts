import {
  createPublicCatalogClient,
} from "@gioitruyen/api-client";

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

export const catalog = createPublicCatalogClient({ baseUrl: apiBaseUrl });

export async function loadHome() {
  const [home, taxonomy, promotions, storySections, rankingBoards] = await Promise.all([
    catalog.home(),
    catalog.categories(),
    catalog.promotedHome().catch(() => []),
    catalog.storySections().catch(() => []),
    catalog.rankingBoards().catch(() => []),
  ]);
  return { ...home, promotions, rankingBoards, storySections, taxonomy };
}

export async function loadStorySections() {
  return catalog.storySections().catch(() => []);
}

export async function loadRankingBoards() {
  return catalog.rankingBoards().catch(() => []);
}
