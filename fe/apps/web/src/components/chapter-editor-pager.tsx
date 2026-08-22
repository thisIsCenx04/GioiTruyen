"use client";

/**
 * Paging for the chapter list inside an edit form.
 *
 * <p>A finished translation runs to two and a half thousand chapters, and the
 * form rendered every one of them: thousands of textareas mounted at once, a
 * page that scrolls for minutes, and no way to reach chapter 1,800 except by
 * dragging. Only the current page is rendered now.
 *
 * <p>The page is a view over the draft list, never a copy of it. Edits still
 * write to the real index in the full array, so saving submits every chapter -
 * the ones off-screen included. Paging that dropped the unseen chapters would
 * delete them on save, which is the one failure this must not have.
 */

/** Chapters shown at once. Matches the reader's own pager. */
export const CHAPTERS_PER_EDIT_PAGE = 20;

/**
 * The chapter number written in a title, or null when it carries none.
 *
 * <p>Kept here rather than imported so the pager stays free of the import
 * parser; the shape it matches is the one every chapter title is given.
 */
function declaredNumber(title: string): number | null {
  const match = /^\s*(?:chương|chuong|chapter|chap)\s*(\d+(?:\.\d+)?)/iu.exec(title);
  if (!match?.[1]) return null;
  const parsed = Number(match[1]);
  return Number.isFinite(parsed) ? parsed : null;
}

export function chapterPageCount(total: number): number {
  return Math.max(1, Math.ceil(total / CHAPTERS_PER_EDIT_PAGE));
}

/**
 * The slice of chapters shown on a page, with each item's index in the full
 * list so callers edit the right entry.
 */
export function chapterPageSlice<T>(
  chapters: readonly T[],
  page: number,
): Array<{ chapter: T; index: number }> {
  const start = (page - 1) * CHAPTERS_PER_EDIT_PAGE;
  return chapters
    .slice(start, start + CHAPTERS_PER_EDIT_PAGE)
    .map((chapter, offset) => ({ chapter, index: start + offset }));
}

/**
 * Page numbers to offer: the ends, and a window around the current page.
 *
 * <p>A 2,500-chapter story has 125 pages, and listing all of them is the same
 * scrolling problem one level up.
 */
export function chapterPageWindow(current: number, total: number): number[] {
  const wanted = new Set([1, total, current - 1, current, current + 1]);
  return [...wanted]
    .filter((page) => page >= 1 && page <= total)
    .sort((left, right) => left - right);
}

/**
 * What the pager says about the page on screen.
 *
 * <p>Named by the chapters' own numbers, which are not their positions in the
 * list. A file that skips a chapter - one real upload jumped straight from
 * "Chương 86" to "Chương 88" - leaves 385 chapters numbered up to 386, and
 * labelling by position printed "Chương 381–385 / 385" directly above a
 * chapter whose own title read "Chương 386". Both figures were right and the
 * screen still contradicted itself.
 *
 * <p>The count is kept, but stated as a count rather than as the last number.
 */
export function chapterPageLabel(
  titles: readonly string[],
  page: number,
  total: number,
): string {
  const start = (page - 1) * CHAPTERS_PER_EDIT_PAGE + 1;
  const end = Math.min(page * CHAPTERS_PER_EDIT_PAGE, total);

  const numbers = chapterPageSlice(titles, page)
    .map(({ chapter }) => declaredNumber(chapter))
    .filter((value): value is number => value != null);

  const first = numbers[0] ?? start;
  const last = numbers.at(-1) ?? end;
  return `Chương ${first}–${last} · ${total} chương`;
}

export function ChapterEditorPager({
  onChange,
  page,
  titles,
  total,
}: Readonly<{
  onChange: (page: number) => void;
  page: number;
  /**
   * Every chapter title in the draft list, so the label can name the chapters
   * by their own numbers rather than by their place in the array.
   */
  titles?: readonly string[];
  /** Chapters in the whole draft list, not on this page. */
  total: number;
}>) {
  const pages = chapterPageCount(total);
  if (pages <= 1) return null;

  const first = (page - 1) * CHAPTERS_PER_EDIT_PAGE + 1;
  const last = Math.min(page * CHAPTERS_PER_EDIT_PAGE, total);
  const window = chapterPageWindow(page, pages);

  return (
    <nav aria-label="Phân trang chương" className="chapterEditPager">
      <span className="chapterEditPagerCount">
        {chapterPageLabel(titles ?? [], page, total)}
      </span>
      <div className="chapterEditPagerPages">
        <button
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
          type="button"
        >
          Trước
        </button>
        {window.map((entry, position) => (
          <span key={entry}>
            {/* A gap means pages were skipped, so the numbers are not misread
                as consecutive. */}
            {position > 0 && entry - (window[position - 1] ?? entry) > 1 ? <em>…</em> : null}
            <button
              aria-current={entry === page ? "page" : undefined}
              className={entry === page ? "isCurrent" : undefined}
              onClick={() => onChange(entry)}
              type="button"
            >
              {entry}
            </button>
          </span>
        ))}
        <button
          disabled={page >= pages}
          onClick={() => onChange(page + 1)}
          type="button"
        >
          Sau
        </button>
      </div>
      {/* Typing the number beats pressing "Sau" a hundred times. */}
      <label className="chapterEditPagerJump">
        Tới trang
        <input
          max={pages}
          min={1}
          onChange={(event) => {
            const wanted = Number(event.target.value);
            if (Number.isFinite(wanted) && wanted >= 1 && wanted <= pages) onChange(wanted);
          }}
          type="number"
          value={page}
        />
        / {pages}
      </label>
    </nav>
  );
}
