"use client";

import { Check, Facebook, Link2, Share2, Twitter } from "lucide-react";
import { useState } from "react";

import { isLoggedIn } from "@/lib/auth";
import { reportQuestProgress } from "@/lib/quests";

/**
 * Shares a story to a social network and credits the daily share quest.
 *
 * <p>The quest only counts for signed-in readers, but the share itself works for
 * everyone - a guest should still be able to pass a link on.
 */
export function StoryShareButton({
  storyTitle,
  storySlug,
}: Readonly<{ storyTitle: string; storySlug: string }>) {
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);

  const url = typeof window === "undefined"
    ? `https://gioitruyen.com/truyen/${storySlug}`
    : `${window.location.origin}/truyen/${storySlug}`;
  const text = `Đọc "${storyTitle}" trên Giới Truyện`;

  const creditQuest = () => {
    if (isLoggedIn()) {
      void reportQuestProgress("SHARE_STORY", 1);
    }
  };

  const openShare = (target: string) => {
    creditQuest();
    window.open(target, "_blank", "noopener,noreferrer,width=640,height=560");
    setOpen(false);
  };

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      creditQuest();
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be denied; the share sheet stays open so the
      // reader can still use a network button.
    }
  };

  return (
    <div className="storyShare">
      <button
        aria-expanded={open}
        className="storyAction storyActionShare"
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        <Share2 aria-hidden="true" /> Chia sẻ
      </button>

      {open ? (
        <div className="storyShareSheet" role="menu">
          <button
            onClick={() => openShare(
              `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`,
            )}
            type="button"
          >
            <Facebook aria-hidden="true" size={15} /> Facebook
          </button>
          <button
            onClick={() => openShare(
              `https://twitter.com/intent/tweet?url=${encodeURIComponent(url)}`
              + `&text=${encodeURIComponent(text)}`,
            )}
            type="button"
          >
            <Twitter aria-hidden="true" size={15} /> X (Twitter)
          </button>
          <button onClick={() => void copyLink()} type="button">
            {copied
              ? <><Check aria-hidden="true" size={15} /> Đã copy</>
              : <><Link2 aria-hidden="true" size={15} /> Copy link</>}
          </button>
        </div>
      ) : null}
    </div>
  );
}
