"use client";

import { AlertTriangle, X } from "lucide-react";
import { useEffect, useState } from "react";

import { missingChapterNumbers } from "@/pages/dashboard/components/story-import";

/**
 * Warns that a story has holes in its chapter numbering.
 *
 * <p>An upload can be accepted, saved and published while still missing
 * chapters from the middle: one real file ran from "Chương 1" to "Chương 386"
 * with no chapter 87 anywhere in it. Nothing about the saved story shows this -
 * the chapter_number column is renumbered by position on save, so it always
 * reads as a gap-free 1…N - and the only person who ever found out was a reader
 * who reached the hole.
 *
 * <p>So it is said where the story is opened, and the list of what is missing
 * is one click away rather than something to work out by scrolling.
 */

/** Runs of three or more are shown as a range; anything shorter is listed. */
function groupRuns(numbers: readonly number[]): string[] {
  const groups: string[] = [];
  let index = 0;
  while (index < numbers.length) {
    let end = index;
    while (end + 1 < numbers.length && numbers[end + 1] === (numbers[end] ?? 0) + 1) end += 1;
    const length = end - index + 1;
    if (length >= 3) {
      groups.push(`${numbers[index]}–${numbers[end]}`);
    } else {
      for (let position = index; position <= end; position += 1) {
        groups.push(String(numbers[position]));
      }
    }
    index = end + 1;
  }
  return groups;
}

export function MissingChaptersNotice({
  titles,
}: Readonly<{
  /** Every chapter title of the story, in order. */
  titles: readonly string[];
}>) {
  const [open, setOpen] = useState(false);
  const missing = missingChapterNumbers(titles);

  // Esc closes the list, the same as every other dialog on the site.
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  if (missing.length === 0) return null;

  const groups = groupRuns(missing);

  return (
    <>
      <div className="missingChapterBanner" role="status">
        <AlertTriangle aria-hidden="true" size={18} />
        <p>
          Truyện đang thiếu <strong>{missing.length.toLocaleString("vi-VN")} chương</strong> ở giữa
          truyện (theo số chương ghi trong tên chương).
        </p>
        <button onClick={() => setOpen(true)} type="button">
          Xem các chương đang thiếu
        </button>
      </div>

      {open ? (
        <div
          aria-labelledby="missing-chapters-title"
          aria-modal="true"
          className="missingChapterDialogBackdrop"
          onClick={(event) => {
            if (event.target === event.currentTarget) setOpen(false);
          }}
          role="dialog"
        >
          <div className="missingChapterDialog">
            <header>
              <div>
                <p>THIẾU CHƯƠNG</p>
                <h2 id="missing-chapters-title">
                  {missing.length.toLocaleString("vi-VN")} chương chưa có nội dung
                </h2>
              </div>
              <button aria-label="Đóng" onClick={() => setOpen(false)} type="button">
                <X aria-hidden="true" size={18} />
              </button>
            </header>

            <div className="missingChapterList">
              {groups.map((group) => (
                <span key={group}>{group}</span>
              ))}
            </div>

            <p className="missingChapterHint">
              Các số chương trên không có trong truyện. Hãy bổ sung file chứa những chương này rồi
              upload lại — chương trùng sẽ được cập nhật, chương mới sẽ được thêm vào.
            </p>

            <footer>
              <button onClick={() => setOpen(false)} type="button">
                Đã hiểu
              </button>
            </footer>
          </div>
        </div>
      ) : null}
    </>
  );
}
