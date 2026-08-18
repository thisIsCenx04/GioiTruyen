"use client";

import { Lock } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { ChapterUnlockDialog } from "./chapter-unlock-dialog";

import { XuIcon } from "./currency-icons";

export type ChapterListEntry = Readonly<{
  id: string;
  number: number;
  title: string;
  publishedAt: string;
  accessType: "FREE" | "PAID";
  coinPrice: number;
  unlocked: boolean;
}>;

/**
 * One row of the table of contents.
 *
 * A locked chapter is not a link: following it would only land the reader on a
 * paywall, so the row opens the unlock dialog in place instead.
 */
export function ChapterListItem({
  chapter,
  storySlug,
}: Readonly<{
  chapter: ChapterListEntry;
  storySlug: string;
}>) {
  const [asking, setAsking] = useState(false);
  const href = `/truyen/${storySlug}/chuong-${chapter.number}`;
  const publishedLabel = new Date(chapter.publishedAt).toLocaleDateString("vi-VN");

  const isUnlocked = Boolean(chapter.unlocked) || chapter.accessType === "FREE" || (chapter.coinPrice ?? 0) === 0;

  if (isUnlocked) {
    return (
      <li>
        <Link to={href as string}>
          <span>Chương {chapter.number}</span>
          <strong>{chapter.title}</strong>
          <time dateTime={chapter.publishedAt}>{publishedLabel}</time>
        </Link>
      </li>
    );
  }

  return (
    <li className="chapterLocked">
      <button onClick={() => setAsking(true)} type="button">
        <span>Chương {chapter.number}</span>
        <strong>
          {chapter.title}
          <em className="chapterLockBadge">
            <Lock aria-hidden="true" size={12} /> {chapter.coinPrice.toLocaleString("vi-VN")} <XuIcon size={14} />
          </em>
        </strong>
        <time dateTime={chapter.publishedAt}>{publishedLabel}</time>
      </button>
      {asking ? (
        <ChapterUnlockDialog
          chapterId={chapter.id}
          chapterTitle={chapter.title}
          coinPrice={chapter.coinPrice}
          onClose={() => setAsking(false)}
          returnTo={href}
        />
      ) : null}
    </li>
  );
}
