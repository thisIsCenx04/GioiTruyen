import { createPublicCatalogClient, StoryApiError } from "@gioitruyen/api-client";
import { useNavigate, useLocation, useParams } from "react-router-dom";
const cache = <T extends (...args: any[]) => any>(fn: T) => fn;

import { ChapterReader } from "@/components/chapter-reader";
import { ChapterUnlock, type ChapterAccessView } from "@/components/chapter-unlock";
import { PublicShell } from "@/components/site-chrome";

type Props = Readonly<{ params: Promise<{ chapterId: string }> }>;

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "/api/v1"
).replace(/\/+$/u, "");

const load = cache(async (id: string) => {
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
  try {
    const chapter = await catalog.chapter(id);
    const story = await catalog.story(chapter.storyId);
    return { chapter, story, access: null };
  } catch (error) {
    if (error instanceof StoryApiError && error.problem.status === 404) {
      throw new Error("Not Found");
    }
    try {
      const response = await authorizedFetch(`${apiBaseUrl}/chapters/${encodeURIComponent(id)}/access`);
      if (response.ok) {
        const access = await response.json() as ChapterAccessView;
        const story = await catalog.story(access.storyId).catch(() => null);
        return { chapter: null, story, access };
      }
    } catch {
      // ignore
    }
    const fallbackAccess: ChapterAccessView = {
      authenticated: Boolean(accessToken),
      availableXu: 0,
      chapterId: id,
      chapterTitle: "Chương đã khóa",
      priceXu: 10,
      storyId: "",
      unlocked: false,
    };
    return { chapter: null, story: null, access: fallbackAccess };
  }
});

/* generateMetadata removed */

export default async function ReaderPage({ params }: Props) {
  const result = await load((await params).chapterId);
  return result.chapter
    ? (
      <PublicShell>
        <ChapterReader
          chapter={result.chapter}
          storySlug={result.story!.slug}
          storyTitle={result.story!.title}
        />
      </PublicShell>
    )
    : (
      <PublicShell>
        <ChapterUnlock access={result.access!} />
      </PublicShell>
    );
}
