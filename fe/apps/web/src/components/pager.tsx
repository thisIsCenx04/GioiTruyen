import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";

import styles from "./pager.module.css";

/**
 * Builds the page numbers to show, with gaps where numbers are skipped.
 *
 * <p>First and last are always present so a reader can reach either end in one
 * press, and a window sits around the current page. The count of rendered items
 * stays constant, so the row does not reflow as the reader moves through it.
 *
 * @returns page numbers, with `null` marking an elided run
 */
/** Longest run of skipped pages still written out rather than elided. */
const MAX_WRITTEN_OUT_GAP = 2;

export function pageWindow(current: number, totalPages: number, radius = 2): (number | null)[] {
  if (totalPages <= 1) return totalPages === 1 ? [1] : [];

  const pages = new Set<number>([1, totalPages]);
  for (let page = current - radius; page <= current + radius; page += 1) {
    if (page >= 1 && page <= totalPages) pages.add(page);
  }

  const ordered = [...pages].sort((left, right) => left - right);
  const withGaps: (number | null)[] = [];
  ordered.forEach((page, index) => {
    const previous = ordered[index - 1];
    if (previous === undefined) {
      withGaps.push(page);
      return;
    }
    const missing = page - previous - 1;
    // A short run is written out: an ellipsis standing in for one or two
    // numbers takes the same room and gives the reader less to aim at.
    if (missing > 0 && missing <= MAX_WRITTEN_OUT_GAP) {
      for (let filler = previous + 1; filler < page; filler += 1) {
        withGaps.push(filler);
      }
    } else if (missing > 0) {
      withGaps.push(null);
    }
    withGaps.push(page);
  });
  return withGaps;
}

export type PagerProps = Readonly<{
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  /** Announced to assistive tech, e.g. "Danh sách chương". */
  label?: string;
}>;

export function Pager({ page, totalPages, onChange, label = "Phân trang" }: PagerProps) {
  if (totalPages <= 1) return null;

  const go = (target: number) => onChange(Math.min(Math.max(target, 1), totalPages));
  const first = page <= 1;
  const last = page >= totalPages;

  return (
    <nav aria-label={label} className={styles.pager}>
      <button aria-label="Trang đầu" disabled={first} onClick={() => go(1)} type="button">
        <ChevronsLeft aria-hidden="true" />
      </button>
      <button aria-label="Trang trước" disabled={first} onClick={() => go(page - 1)} type="button">
        <ChevronLeft aria-hidden="true" />
      </button>

      {pageWindow(page, totalPages).map((entry, index) =>
        entry === null ? (
          <span aria-hidden="true" className={styles.gap} key={`gap-${index}`}>…</span>
        ) : (
          <button
            aria-current={entry === page ? "page" : undefined}
            aria-label={`Trang ${entry}`}
            className={entry === page ? styles.current : undefined}
            key={entry}
            onClick={() => go(entry)}
            type="button"
          >
            {entry}
          </button>
        ),
      )}

      <button aria-label="Trang sau" disabled={last} onClick={() => go(page + 1)} type="button">
        <ChevronRight aria-hidden="true" />
      </button>
      <button aria-label="Trang cuối" disabled={last} onClick={() => go(totalPages)} type="button">
        <ChevronsRight aria-hidden="true" />
      </button>
    </nav>
  );
}
