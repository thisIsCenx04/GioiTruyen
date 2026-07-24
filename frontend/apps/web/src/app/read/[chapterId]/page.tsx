import { StoryApiError } from "@gioitruyen/api-client";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { cache } from "react";

import { ChapterReader } from "@/components/chapter-reader";
import { catalog } from "@/lib/catalog";

type Props = Readonly<{ params: Promise<{ chapterId: string }> }>;

const load = cache(async (id: string) => {
  try {
    return await catalog.chapter(id);
  } catch (error) {
    if (error instanceof StoryApiError && error.problem.status === 404) {
      notFound();
    }
    throw error;
  }
});

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const chapter = await load((await params).chapterId);
  return { title: `${chapter.title} · Chương ${chapter.number}` };
}

export default async function ReaderPage({ params }: Props) {
  return <ChapterReader chapter={await load((await params).chapterId)} />;
}
