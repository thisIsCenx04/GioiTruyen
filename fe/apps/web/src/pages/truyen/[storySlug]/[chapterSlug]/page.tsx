import { createPublicCatalogClient, StoryApiError } from "@gioitruyen/api-client";
import { useNavigate, useLocation, useParams } from "react-router-dom";
const cache = <T extends (...args: any[]) => any>(fn: T) => fn;

import { ChapterReader } from "@/components/chapter-reader";
import { ChapterUnlock, type ChapterAccessView } from "@/components/chapter-unlock";
import { QuestTimeTracker } from "@/components/quest-time-tracker";
import { PublicShell } from "@/components/site-chrome";

type Props = Readonly<{
  params: Promise<{ storySlug: string; chapterSlug: string }>;
}>;

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "/api/v1"
).replace(/\/+$/u, "");

function chapterNumber(value: string) {
  const match = /^chuong-(\d+)$/u.exec(value);
  return match ? Number(match[1]) : null;
}

const load = cache(async (storySlug: string, chapterSlug: string) => {
  const match = document.cookie.match(/(?:^|;\s*)access_token=([^;]*)/);
  const accessToken = match ? decodeURIComponent(match[1]) : (typeof localStorage !== "undefined" ? localStorage.getItem("access_token") ?? undefined : undefined);
  const authorizedFetch: typeof fetch = (input, init) => {
    const headers = new Headers(init?.headers);
    if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
    return fetch(input, { ...init, cache: "no-store", headers });
  };
  const catalog = createPublicCatalogClient({
    baseUrl: apiBaseUrl,
    fetchImplementation: authorizedFetch,
  });
  const [story, chapters] = await Promise.all([
    catalog.story(storySlug),
    catalog.chapters(storySlug),
  ]);
  const number = chapterNumber(chapterSlug);
  const chapterSummary = chapters.items.find((chapter) =>
    chapter.slug === chapterSlug || chapter.number === number);
  if (!chapterSummary) throw new Error("Not Found");

  /** What the reader can spend, for the "not enough coin" message. */
  const readerCoinBalance = async () => {
    if (!accessToken) return 0;
    try {
      const response = await authorizedFetch(`${apiBaseUrl}/wallets/me`);
      if (!response.ok) return 0;
      const wallet = (await response.json()) as { coinBalance?: number };
      return wallet.coinBalance ?? 0;
    } catch {
      return 0;
    }
  };

  try {
    const chapter = await catalog.chapter(chapterSummary.id);

    // The server withholds the text of a paid chapter, so the locked state is
    // read from the response rather than inferred from a failed request. The
    // old code only showed the paywall when the call threw, which it never did.
    if (chapter.unlocked) {
      return { access: null, chapter, story };
    }
    return {
      access: {
        authenticated: Boolean(accessToken),
        availableXu: await readerCoinBalance(),
        chapterId: chapter.id,
        chapterTitle: chapter.title,
        priceXu: chapter.coinPrice,
        storyId: story.id,
        unlocked: false,
      } satisfies ChapterAccessView,
      chapter: null,
      story,
    };
  } catch (error) {
    if (error instanceof StoryApiError && error.problem.status === 404) throw new Error("Not Found");
    const fallbackAccess: ChapterAccessView = {
      authenticated: Boolean(accessToken),
      availableXu: 0,
      chapterId: chapterSummary.id,
      chapterTitle: chapterSummary.title,
      priceXu: chapterSummary.coinPrice,
      storyId: story.id,
      unlocked: false,
    };
    return {
      access: fallbackAccess,
      chapter: null,
      story,
    };
  }
});

/* generateMetadata removed */

export default async function CleanReaderPage({ params }: Props) {
  const { chapterSlug, storySlug } = await params;
  const result = await load(storySlug, chapterSlug);
  const cleanStoryHref = `/truyen/${result.story.slug}`;
  const returnTo = `${cleanStoryHref}/${chapterSlug}`;

  return (
    <PublicShell>
      {/* Only a chapter page counts toward the reading-time quests. */}
      {result.chapter ? <QuestTimeTracker questType="READ_MINUTES" /> : null}
      {result.chapter ? (
        <ChapterReader
          chapter={result.chapter}
          storySlug={result.story.slug}
          storyTitle={result.story.title}
        />
      ) : (
        <ChapterUnlock
          access={result.access!}
          returnTo={returnTo}
          storyHref={cleanStoryHref}
        />
      )}
    </PublicShell>
  );
}
