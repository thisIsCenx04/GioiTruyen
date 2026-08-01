import { createPublicCatalogClient, StoryApiError } from "@gioitruyen/api-client";
import type { Metadata } from "next";
import { cookies } from "next/headers";
import { notFound } from "next/navigation";
import { cache } from "react";

import { ChapterReader } from "@/components/chapter-reader";
import { ChapterUnlock, type ChapterAccessView } from "@/components/chapter-unlock";
import { PublicShell } from "@/components/site-chrome";

type Props = Readonly<{ params: Promise<{ chapterId: string }> }>;

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

const load = cache(async (id: string) => {
  const accessToken = (await cookies()).get("access_token")?.value;
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
      notFound();
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

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const chapter = await load((await params).chapterId);
  return chapter.chapter
    ? { title: `${chapter.chapter.title} · Chương ${chapter.chapter.number}` }
    : { title: `${chapter.access?.chapterTitle ?? "Chương đã khóa"} · Mở khóa` };
}

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
