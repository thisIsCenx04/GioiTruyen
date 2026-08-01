import {
  createBrowserTeamClient,
  createPublicCatalogClient,
  type PublicChapter,
} from "@gioitruyen/api-client";

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

export const catalog = createPublicCatalogClient({ baseUrl: apiBaseUrl });
const publicTeams = createBrowserTeamClient({ baseUrl: apiBaseUrl });

export async function loadHome() {
  const [home, taxonomy, promotions, storySections, rankingBoards] = await Promise.all([
    catalog.home().catch(() => ({ locale: "vi", version: "1.0", generatedAt: new Date().toISOString(), sections: [] })),
    catalog.categories().catch(() => ({ groups: [] })),
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

export async function loadStoryDetail(identifier: string) {
  const [story, chapters, taxonomy, sections, rankingBoards] = await Promise.all([
    catalog.story(identifier).catch(() => null),
    catalog.chapters(identifier).catch(() => ({ items: [] as PublicChapter[] })),
    catalog.categories().catch(() => ({ groups: [] })),
    catalog.storySections().catch(() => []),
    catalog.rankingBoards().catch(() => []),
  ]);
  if (!story) {
    return null;
  }
  const team = await publicTeams.getTeam(story.teamId).catch(() => null);
  const summaries = [
    ...sections.flatMap((section) => section.stories),
    ...rankingBoards.flatMap((board) => board.stories.map((row) => row.story)),
  ];
  const summary = summaries.find((item) => item.id === story.id) ?? null;
  const relatedStories = [
    ...new Map(
      rankingBoards
        .flatMap((board) => board.stories.map((row) => row.story))
        .filter((item) => item.id !== story.id)
        .map((item) => [item.id, item] as const),
    ).values(),
  ].slice(0, 6);
  const categories = taxonomy.groups
    .flatMap((group) => group.categories)
    .filter((category) => story.categoryIds.includes(category.id));

  return { categories, chapters, relatedStories, story, summary, team };
}
