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

  try {
    return {
      access: null,
      chapter: await catalog.chapter(chapterSummary.id),
      story,
    };
  } catch (error) {
    if (error instanceof StoryApiError && error.problem.status === 404) throw new Error("Not Found");
    try {
      const response = await authorizedFetch(`${apiBaseUrl}/chapters/${encodeURIComponent(chapterSummary.id)}/access`);
      if (response.ok) {
        return {
          access: (await response.json()) as ChapterAccessView,
          chapter: null,
          story,
        };
      }
    } catch {
      // ignore
    }
    const fallbackAccess: ChapterAccessView = {
      authenticated: Boolean(accessToken),
      availableXu: 0,
      chapterId: chapterSummary.id,
      chapterTitle: chapterSummary.title,
      priceXu: 10,
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
