import {
  createBrowserTeamClient,
  createPublicCatalogClient,
  type HomeStorySummary,
  type PublicChapter,
  type RankingStory,
} from "@gioitruyen/api-client";
import { apiFetch } from "@/lib/api-base";
import { getAccessToken } from "@/lib/auth";

const apiBaseUrl = (
  (typeof process !== "undefined" && process.env?.API_INTERNAL_URL) || "/api/v1"
).replace(/\/+$/u, "");

export const catalog = createPublicCatalogClient({ baseUrl: apiBaseUrl });

/**
 * The same catalog, but identifying the reader when a token is present.
 *
 * Chapter listings report whether each paid chapter is unlocked, which the
 * server can only answer if it knows who is asking. Anonymous calls report
 * every paid chapter as locked, so a reader who had already bought one still
 * saw the padlock.
 */
const identifiedFetch: typeof fetch = (input, init) => {
  const token = getAccessToken();
  if (!token) return apiFetch(input, init);
  const headers = new Headers(init?.headers);
  headers.set("Authorization", `Bearer ${token}`);
  return apiFetch(input, { ...init, headers });
};

const identifiedCatalog = createPublicCatalogClient({
  baseUrl: apiBaseUrl,
  fetchImplementation: identifiedFetch,
});

const publicTeams = createBrowserTeamClient({ baseUrl: apiBaseUrl, fetchImplementation: apiFetch });

/**
 * Records whether any request behind the current page load failed. Callers
 * check this to tell "the library is genuinely empty" apart from "we could not
 * reach the API" - previously both rendered as an empty page, which made a
 * transient 429 or timeout look like the site had lost all its data.
 */
export type LoadOutcome = { degraded: boolean };

function withTimeout<T>(
  promise: Promise<T>,
  ms = 10000,
  fallback: T,
  outcome?: LoadOutcome,
): Promise<T> {
  let timeoutId: ReturnType<typeof setTimeout>;
  const fail = () => {
    if (outcome) outcome.degraded = true;
    return fallback;
  };
  const timeoutPromise = new Promise<T>((resolve) => {
    timeoutId = setTimeout(() => resolve(fail()), ms);
  });
  return Promise.race([
    promise.then((res) => {
      clearTimeout(timeoutId);
      return res;
    }).catch(() => {
      clearTimeout(timeoutId);
      return fail();
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
/** Chapters shown per page of the story list; the pager is sized from this. */
export const CHAPTERS_PER_PAGE = 20;

/** Stands in when the chapter request fails, so the page renders an empty list. */
const defaultChapters = {
  items: [] as PublicChapter[],
  page: 1,
  size: CHAPTERS_PER_PAGE,
  total: 0,
  totalPages: 0,
};

export async function loadHome() {
  const outcome: LoadOutcome = { degraded: false };
  const [home, taxonomy, promotions, storySections, rankingBoards] = await Promise.all([
    withTimeout(catalog.home(), 10000, defaultHomeData, outcome),
    withTimeout(catalog.categories(), 10000, defaultTaxonomy, outcome),
    withTimeout(catalog.promotedHome(), 10000, [], outcome),
    withTimeout(catalog.storySections(), 10000, [], outcome),
    withTimeout(catalog.rankingBoards(), 10000, [], outcome),
  ]);
  return {
    ...home,
    degraded: outcome.degraded,
    promotions,
    rankingBoards,
    storySections,
    taxonomy,
  };
}

export async function loadStorySections() {
  return withTimeout(catalog.storySections(), 10000, []);
}

/**
 * The Zhihu page runs on its own shelves and ranking boards so a one-page short
 * story never competes with novels for a slot on the main catalog.
 */
export async function loadZhihu() {
  const outcome: LoadOutcome = { degraded: false };
  const [sections, rankingBoards, taxonomy] = await Promise.all([
    withTimeout(fetchJson<TaggedSection[]>("/zhihu/sections"), 10000, [], outcome),
    withTimeout(fetchJson<RankingBoardLike[]>("/zhihu/rankings"), 10000, [], outcome),
    withTimeout(catalog.categories(), 10000, defaultTaxonomy, outcome),
  ]);
  return { degraded: outcome.degraded, rankingBoards, sections, taxonomy };
}

type TaggedSection = {
  id: string;
  tag: string;
  title: string;
  stories: HomeStorySummary[];
};

type RankingBoardLike = {
  id: string;
  title: string;
  subtitle: string;
  unit: string;
  stories: RankingStory[];
};

/** The generated client has no Zhihu methods, so these two routes are called directly. */
async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    cache: "no-store",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function loadRankingBoards() {
  return withTimeout(catalog.rankingBoards(), 10000, []);
}

/** Stories carrying a tag. Tags are editorial labels, separate from genres. */
export async function loadTagStories(slug: string) {
  return withTimeout(
    fetchJson<Array<Record<string, unknown>>>(`/tags/${encodeURIComponent(slug)}/stories`),
    10000,
    [],
  );
}

export async function loadStoryDetail(identifier: string, page = 1) {
  const [rawStory, chapters, taxonomy, sections, rankingBoards] = await Promise.all([
    withTimeout(catalog.story(identifier), 10000, null),
    // Identified: the padlocks depend on which chapters this reader owns.
    withTimeout(
      identifiedCatalog.chapters(identifier, page, CHAPTERS_PER_PAGE),
      10000,
      defaultChapters,
    ),
    withTimeout(catalog.categories(), 10000, defaultTaxonomy),
    withTimeout(catalog.storySections(), 10000, []),
    withTimeout(catalog.rankingBoards(), 10000, []),
  ]);
  if (!rawStory) {
    return null;
  }
  const story = rawStory as typeof rawStory & {
    storyFormat?: "ONESHOT" | "SERIAL";
  };
  const team = await withTimeout(publicTeams.getTeam(story.teamId), 10000, null);
  const summaries = [
    ...sections.flatMap((section) => section.stories),
    ...rankingBoards.flatMap((board) => board.stories.map((row) => row.story)),
  ];
  const summary = summaries.find((item) => item.id === story.id) ?? null;
  const allCandidates = [
    ...new Map(
      sections
        .flatMap((section) => section.stories)
        .concat(rankingBoards.flatMap((board) => board.stories.map((row) => row.story)))
        .filter((item) => item.id !== story.id)
        .map((item) => [item.id, item] as const),
    ).values(),
  ];
  const sameCategory = allCandidates.filter((item: any) =>
    item.categoryIds?.some((catId: string) => story.categoryIds?.includes(catId))
    || item.categoryId === (story as any).categoryId
    || item.storyType === story.storyType
  );
  const otherStories = allCandidates.filter((item) => !sameCategory.includes(item));
  const relatedStories = [...sameCategory, ...otherStories].slice(0, 6);
  const categories = taxonomy.groups
    .flatMap((group) => group.categories)
    .filter((category) => story.categoryIds.includes(category.id));

  // A one-page story is read on this very page, so its single chapter's body is
  // fetched here rather than making the reader click through to a chapter route.
  const oneshotChapterId = (story as { storyFormat?: string }).storyFormat === "ONESHOT"
    ? chapters.items[0]?.id
    : undefined;
  const oneshotChapter = oneshotChapterId
    ? await withTimeout(catalog.chapter(oneshotChapterId), 10000, null)
    : null;

  // "Đọc từ đầu" and "Đọc tập mới" point at the ends of the whole story, not of
  // the page being viewed, so they are fetched as single rows rather than by
  // pulling every chapter back just to look at the first and last.
  const [firstPage, lastPage] = await Promise.all([
    withTimeout(identifiedCatalog.chapters(identifier, 1, 1), 10000, defaultChapters),
    chapters.total > 1
      ? withTimeout(
          identifiedCatalog.chapters(identifier, chapters.total, 1),
          10000,
          defaultChapters,
        )
      : Promise.resolve(defaultChapters),
  ]);
  const firstChapter = firstPage.items[0] ?? null;
  const latestChapter = lastPage.items[0] ?? firstChapter;

  return {
    categories,
    chapters,
    firstChapter,
    latestChapter,
    oneshotChapter,
    relatedStories,
    story,
    summary,
    team,
  };
}
