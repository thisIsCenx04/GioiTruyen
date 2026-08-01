import { createPublicCatalogClient, StoryApiError } from "@gioitruyen/api-client";
import type { Metadata } from "next";
import { cookies } from "next/headers";
import { notFound } from "next/navigation";
import { cache } from "react";

import { ChapterReader } from "@/components/chapter-reader";
import { ChapterUnlock, type ChapterAccessView } from "@/components/chapter-unlock";
import { PublicShell } from "@/components/site-chrome";

type Props = Readonly<{
  params: Promise<{ storySlug: string; chapterSlug: string }>;
}>;

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

function chapterNumber(value: string) {
  const match = /^chuong-(\d+)$/u.exec(value);
  return match ? Number(match[1]) : null;
}

const load = cache(async (storySlug: string, chapterSlug: string) => {
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
  const [story, chapters] = await Promise.all([
    catalog.story(storySlug),
    catalog.chapters(storySlug),
  ]);
  const number = chapterNumber(chapterSlug);
  const chapterSummary = chapters.items.find((chapter) =>
    chapter.slug === chapterSlug || chapter.number === number);
  if (!chapterSummary) notFound();

  try {
    return {
      access: null,
      chapter: await catalog.chapter(chapterSummary.id),
      story,
    };
  } catch (error) {
    if (error instanceof StoryApiError && error.problem.status === 404) notFound();
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

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { chapterSlug, storySlug } = await params;
  const result = await load(storySlug, chapterSlug);
  return {
    title: result.chapter
      ? `${result.chapter.title} · Chương ${result.chapter.number} · ${result.story.title}`
      : `${result.access?.chapterTitle ?? "Chương đã khóa"} · Mở khóa`,
  };
}

export default async function CleanReaderPage({ params }: Props) {
  const { chapterSlug, storySlug } = await params;
  const result = await load(storySlug, chapterSlug);
  const cleanStoryHref = `/truyen/${result.story.slug}`;
  const returnTo = `${cleanStoryHref}/${chapterSlug}`;

  return (
    <PublicShell>
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
