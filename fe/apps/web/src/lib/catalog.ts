import {
  createBrowserTeamClient,
  createPublicCatalogClient,
  type PublicChapter,
} from "@gioitruyen/api-client";

const apiBaseUrl = (
  (typeof process !== "undefined" && process.env?.API_INTERNAL_URL) || "/api/v1"
).replace(/\/+$/u, "");

export const catalog = createPublicCatalogClient({ baseUrl: apiBaseUrl });
const publicTeams = createBrowserTeamClient({ baseUrl: apiBaseUrl });

function withTimeout<T>(promise: Promise<T>, ms = 10000, fallback: T): Promise<T> {
  let timeoutId: ReturnType<typeof setTimeout>;
  const timeoutPromise = new Promise<T>((resolve) => {
    timeoutId = setTimeout(() => resolve(fallback), ms);
  });
  return Promise.race([
    promise.then((res) => {
      clearTimeout(timeoutId);
      return res;
    }).catch(() => {
      clearTimeout(timeoutId);
      return fallback;
    }),
    timeoutPromise,
  ]);
}

const defaultHomeData = {
  locale: "vi",
  version: "1.0",
  generatedAt: new Date().toISOString(),
  sections: [],
};

const defaultTaxonomy = { version: "1.0", groups: [] };
const defaultChapters = { items: [] as PublicChapter[], nextCursor: null, hasMore: false };

export async function loadHome() {
  const [home, taxonomy, promotions, storySections, rankingBoards] = await Promise.all([
    withTimeout(catalog.home(), 10000, defaultHomeData),
    withTimeout(catalog.categories(), 10000, defaultTaxonomy),
    withTimeout(catalog.promotedHome(), 10000, []),
    withTimeout(catalog.storySections(), 10000, []),
    withTimeout(catalog.rankingBoards(), 10000, []),
  ]);
  return { ...home, promotions, rankingBoards, storySections, taxonomy };
}

export async function loadStorySections() {
  return withTimeout(catalog.storySections(), 10000, []);
}

export async function loadRankingBoards() {
  return withTimeout(catalog.rankingBoards(), 10000, []);
}

export async function loadStoryDetail(identifier: string) {
  const [story, chapters, taxonomy, sections, rankingBoards] = await Promise.all([
    withTimeout(catalog.story(identifier), 10000, null),
    withTimeout(catalog.chapters(identifier), 10000, defaultChapters),
    withTimeout(catalog.categories(), 10000, defaultTaxonomy),
    withTimeout(catalog.storySections(), 10000, []),
    withTimeout(catalog.rankingBoards(), 10000, []),
  ]);
  if (!story) {
    return null;
  }
  const team = await withTimeout(publicTeams.getTeam(story.teamId), 10000, null);
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
