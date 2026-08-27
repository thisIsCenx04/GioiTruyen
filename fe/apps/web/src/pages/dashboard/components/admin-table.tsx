"use client";

import { ArrowDown, ArrowUp, ArrowUpDown, Search } from "lucide-react";
import type { PointerEvent as ReactPointerEvent, ReactNode } from "react";
import { useCallback, useLayoutEffect, useMemo, useRef, useState } from "react";

import { Pager } from "@/components/pager";
import styles from "./admin-table.module.css";

export type SortOrder = "asc" | "desc";
export type SortState<K extends string> = { key: K; order: SortOrder };

/** One table column: how to head it, and how to read a row for sorting. */
export type Column<T, K extends string> = Readonly<{
  key: K;
  label: string;
  /** Rendered cell. Defaults to the sort value when omitted. */
  render?: (row: T) => ReactNode;
  /** Value the column sorts on; omit to make the column unsortable. */
  sortValue?: (row: T) => string | number | boolean | null | undefined;
  /** Right-aligns numeric columns so digits line up. */
  numeric?: boolean;
  /**
   * Be rong mac dinh, tinh bang pixel.
   *
   * <p>Khai bao san chu khong do tu noi dung. Do tu noi dung nghe hop ly nhung
   * chinh la thu da hong: mot o dai bat thuong keo ca cot theo no. Con so o day
   * la quyet dinh cua nguoi thiet ke bang - "cot nay dang bao nhieu thi doc
   * duoc" - va phan tran ra ngoai bi cat kem dau ba cham. Nguoi dung keo rong
   * ra khi can doc ca dong.
   */
  width?: number;
}>;

export type FilterOption = Readonly<{ value: string; label: string }>;

export type Filter<T> = Readonly<{
  id: string;
  label: string;
  options: readonly FilterOption[];
  /** True when the row belongs in the result for the chosen value. */
  matches: (row: T, value: string) => boolean;
}>;

/** A headline number for the page, e.g. "Hoàn thành 12". */
export type Stat = Readonly<{ label: string; value: number | string }>;

/** Cot hep hon muc nay thi khong con doc duoc gi, nen keo den day la dung. */
const MIN_COLUMN_WIDTH = 72;

/** Be rong cho mot cot khong noi ro no muon rong bao nhieu. */
const DEFAULT_COLUMN_WIDTH = 180;

/** Cot so thi hep hon: chu so ngan va can phai. */
const DEFAULT_NUMERIC_COLUMN_WIDTH = 120;

/** Cot thao tac, vua du cho ba den bon nut bieu tuong. */
const DEFAULT_ACTIONS_WIDTH = 168;

/** Khoa cot thao tac trong bang be rong; khong trung voi key cot nao. */
const ACTIONS_KEY = "__actions__";

const WIDTH_STORAGE_PREFIX = "gioitruyen.adminTable.widths.";

/**
 * Be rong cot admin da keo, doc tu lan truoc.
 *
 * <p>Luu trong trinh duyet chu khong gui len may chu: day la thoi quen doc bang
 * cua tung nguoi tren tung man hinh, khong phai cai dat cua he thong.
 */
function readStoredWidths(key: string): Record<string, number> {
  try {
    const raw = window.localStorage.getItem(WIDTH_STORAGE_PREFIX + key);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const widths: Record<string, number> = {};
    for (const [column, value] of Object.entries(parsed)) {
      if (typeof value === "number" && Number.isFinite(value) && value >= MIN_COLUMN_WIDTH) {
        widths[column] = value;
      }
    }
    return widths;
  } catch {
    // Cua so an danh, hoac trinh duyet chan luu tru. Bang van chay binh thuong,
    // chi la moi lan mo lai phai keo lai.
    return {};
  }
}

function storeWidths(key: string, widths: Record<string, number>) {
  try {
    window.localStorage.setItem(WIDTH_STORAGE_PREFIX + key, JSON.stringify(widths));
  } catch {
    // Xem readStoredWidths: khong luu duoc thi thoi.
  }
}

function compare(left: unknown, right: unknown) {
  if (left === right) return 0;
  if (left === null || left === undefined) return 1;
  if (right === null || right === undefined) return -1;
  if (typeof left === "number" && typeof right === "number") return left - right;
  if (typeof left === "boolean" && typeof right === "boolean") return left ? -1 : 1;
  return String(left).localeCompare(String(right), "vi", { sensitivity: "base" });
}export type AdminTableProps<T, K extends string> = Readonly<{
  rows: readonly T[];
  columns: ReadonlyArray<Column<T, K>>;
  rowKey: (row: T) => string;
  rowClassName?: (row: T) => string | undefined;
  /** Free-text search reads these; omit the bar entirely by leaving it out. */
  searchValues?: (row: T) => Array<string | null | undefined>;
  searchPlaceholder?: string;
  filters?: ReadonlyArray<Filter<T>>;
  /** Trailing cell for edit/lock/delete buttons. */
  actions?: (row: T) => ReactNode;
  emptyMessage?: string;
  pageSize?: number;
  /** Be rong cot thao tac; bo trong thi vua du cho ba den bon nut bieu tuong. */
  actionsWidth?: number;
  /**
   * Ten dung de nho be rong cot da keo. Bo trong thi lay tu danh sach key cua
   * cac cot - du de phan biet cac bang voi nhau, va tu doi khi bang doi cot.
   */
  storageKey?: string;
}>;

/**
 * The list surface every admin page shares: a sortable table with a search box
 * and optional filters above it, paged client-side.
 *
 * <p>Paging, filtering and counting all happen here because the admin endpoints
 * return their whole list in one response - there is no page parameter to pass
 * on. Should a list outgrow that, this is the single place that has to learn to
 * ask the server instead.
 */
export function AdminTable<T, K extends string>({
  rows,
  columns,
  rowKey,
  rowClassName,
  searchValues,
  searchPlaceholder = "Tìm theo tên…",
  filters = [],
  actions,
  emptyMessage = "Chưa có dữ liệu.",
  pageSize = 20,
  actionsWidth,
  storageKey,
}: AdminTableProps<T, K>) {
  const [sort, setSort] = useState<SortState<K> | null>(null);
  const [term, setTerm] = useState("");
  const [choices, setChoices] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);

  const widthKey = storageKey ?? columns.map((column) => column.key).join("|");
  // Rong: {} nghia la chua ai keo cot nao, bang dung be rong mac dinh.
  const [widths, setWidths] = useState<Record<string, number>>(() => readStoredWidths(widthKey));

  // Bang doi bo cot - chuyen sang trang khac dung chung component - thi be rong
  // da keo cua bang cu khong con y nghia gi, nen doc lai bo cua bang moi.
  useLayoutEffect(() => {
    setWidths(readStoredWidths(widthKey));
  }, [widthKey]);

  /**
   * Be rong mot cot dang hien.
   *
   * <p>Uu tien be rong nguoi dung da keo, roi den con so cot tu khai bao,
   * cuoi cung moi la mac dinh chung. Khong co buoc nao do tu noi dung: do tu
   * noi dung la ly do mot dong gioi thieu dai keo cot "Ten team" gian ra om
   * tron no, day cac cot con lai ra khoi vung cuon va bang chi con thay mot
   * cot.
   */
  const columnWidth = (column: Column<T, K>) =>
    widths[column.key]
    ?? column.width
    ?? (column.numeric ? DEFAULT_NUMERIC_COLUMN_WIDTH : DEFAULT_COLUMN_WIDTH);

  const actionsColumnWidth = widths[ACTIONS_KEY] ?? actionsWidth ?? DEFAULT_ACTIONS_WIDTH;

  /**
   * Keo mep phai mot cot de doi be rong cua no.
   *
   * <p>Bat su kien tren window chu khong tren cai tay nam: chuot chay ra ngoai
   * tay nam ngay khi keo nhanh, va neu chi nghe tren tay nam thi cot ket lai
   * giua chung.
   */
  const startResize = useCallback((key: string, event: ReactPointerEvent<HTMLSpanElement>) => {
    event.preventDefault();
    event.stopPropagation();
    const startX = event.clientX;
    // Diem xuat phat lay tu be rong dang hien tren man hinh, nen cot khong nhay
    // mot doan ngay khi vua cham vao tay nam.
    const startWidth = Math.round(
      event.currentTarget.parentElement?.getBoundingClientRect().width ?? MIN_COLUMN_WIDTH,
    );

    const move = (moveEvent: PointerEvent) => {
      const next = Math.max(MIN_COLUMN_WIDTH, startWidth + moveEvent.clientX - startX);
      setWidths((state) => ({ ...state, [key]: next }));
    };
    const stop = () => {
      window.removeEventListener("pointermove", move);
      document.body.style.removeProperty("cursor");
      document.body.style.removeProperty("user-select");
      setWidths((state) => {
        storeWidths(widthKey, state);
        return state;
      });
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", stop, { once: true });
    // Con tro giu nguyen hinh keo tren toan trang, va chu khong bi boi den khi
    // chuot luot qua o khac giua luc keo.
    document.body.style.setProperty("cursor", "col-resize");
    document.body.style.setProperty("user-select", "none");
  }, [widthKey]);

  /** Bam dup vao tay nam de tra cot ve be rong mac dinh. */
  const resetColumn = useCallback((key: string) => {
    setWidths((state) => {
      const next = { ...state };
      delete next[key];
      storeWidths(widthKey, next);
      return next;
    });
  }, [widthKey]);


  const visible = useMemo(() => {
    const needle = term.trim().toLowerCase();
    let result = [...rows];

    if (needle && searchValues) {
      result = result.filter((row) =>
        searchValues(row).some((field) => (field ?? "").toLowerCase().includes(needle)),
      );
    }
    for (const filter of filters) {
      const chosen = choices[filter.id];
      if (chosen) result = result.filter((row) => filter.matches(row, chosen));
    }
    if (sort) {
      const column = columns.find((entry) => entry.key === sort.key);
      if (column?.sortValue) {
        result.sort((left, right) => {
          const value = compare(column.sortValue!(left), column.sortValue!(right));
          return sort.order === "asc" ? value : -value;
        });
      }
    }
    return result;
  }, [rows, term, searchValues, filters, choices, sort, columns]);

  const totalPages = Math.max(1, Math.ceil(visible.length / pageSize));
  // Narrowing the result can strand the reader past the end of it.
  const current = Math.min(page, totalPages);
  const pageRows = visible.slice((current - 1) * pageSize, current * pageSize);

  const toggle = (key: K) => {
    setPage(1);
    setSort((state) => {
      if (!state || state.key !== key) return { key, order: "asc" };
      return state.order === "asc" ? { key, order: "desc" } : null;
    });
  };

  return (
    <div className={styles.wrap}>
      {(searchValues || filters.length > 0) && (
        <div className={styles.controls}>
          {searchValues && (
            <label className={styles.search}>
              <Search aria-hidden="true" />
              <input
                onChange={(event) => { setTerm(event.target.value); setPage(1); }}
                placeholder={searchPlaceholder}
                type="search"
                value={term}
              />
            </label>
          )}
          {filters.map((filter) => (
            <label className={styles.filter} key={filter.id}>
              <span>{filter.label}</span>
              <select
                onChange={(event) => {
                  setChoices((state) => ({ ...state, [filter.id]: event.target.value }));
                  setPage(1);
                }}
                value={choices[filter.id] ?? ""}
              >
                <option value="">Tất cả</option>
                {filter.options.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          ))}
          <span className={styles.resultCount}>{visible.length} kết quả</span>
        </div>
      )}

      <div className={styles.scroll}>
        <table className={`${styles.table} ${styles.tableSized}`}>
          <colgroup>
            {columns.map((column) => (
              <col key={column.key} style={{ width: `${columnWidth(column)}px` }} />
            ))}
            {actions ? <col style={{ width: `${actionsColumnWidth}px` }} /> : null}
          </colgroup>
          <thead>
            <tr>
              {columns.map((column) => {
                const active = sort?.key === column.key;
                return (
                  <th className={column.numeric ? styles.numeric : undefined} key={column.key}>
                    {column.sortValue ? (
                      <button
                        aria-label={`Sắp xếp theo ${column.label}`}
                        onClick={() => toggle(column.key)}
                        type="button"
                      >
                        {column.label}
                        {active
                          ? (sort.order === "asc" ? <ArrowUp aria-hidden="true" /> : <ArrowDown aria-hidden="true" />)
                          : <ArrowUpDown aria-hidden="true" className={styles.idle} />}
                      </button>
                    ) : column.label}
                    <ResizeHandle
                      label={column.label}
                      onReset={() => resetColumn(column.key)}
                      onStart={(event) => startResize(column.key, event)}
                    />
                  </th>
                );
              })}
              {actions ? (
                <th aria-label="Thao tác">
                  Thao tác
                  <ResizeHandle
                    label="Thao tác"
                    onReset={() => resetColumn(ACTIONS_KEY)}
                    onStart={(event) => startResize(ACTIONS_KEY, event)}
                  />
                </th>
              ) : null}
            </tr>
          </thead>
          <tbody>
            {pageRows.length === 0 ? (
              <tr>
                <td className={styles.empty} colSpan={columns.length + (actions ? 1 : 0)}>
                  {emptyMessage}
                </td>
              </tr>
            ) : pageRows.map((row) => (
              <tr key={rowKey(row)} className={rowClassName ? rowClassName(row) : undefined}>
                {columns.map((column) => (
                  <td className={column.numeric ? styles.numeric : undefined} key={column.key}>
                    {column.render ? column.render(row) : String(column.sortValue?.(row) ?? "")}
                  </td>
                ))}
                {actions ? <td className={styles.actions}>{actions(row)}</td> : null}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pager onChange={setPage} page={current} totalPages={totalPages} />
    </div>
  );
}

/**
 * Tay nam keo o mep phai mot tieu de cot.
 *
 * <p>Dat ngoai vong lap render de moi cot khong dung lai mot ban sao cua no, va
 * de cho nay giu duoc phan mo ta vi sao no khong phai mot cai nut: no khong
 * kich hoat gi khi bam, chi keo - nen no khong nam trong luong tab, va nguoi
 * dung ban phim doi be rong cot bang cach khac (bam dup de tra ve mac dinh
 * van con, nhung do la tien ich chu khong phai duong duy nhat den du lieu).
 */
function ResizeHandle({
  label,
  onReset,
  onStart,
}: Readonly<{
  label: string;
  onReset: () => void;
  onStart: (event: ReactPointerEvent<HTMLSpanElement>) => void;
}>) {
  return (
    <span
      aria-hidden="true"
      className={styles.resizer}
      onDoubleClick={onReset}
      onPointerDown={onStart}
      title={`Kéo để đổi độ rộng cột "${label}". Bấm đúp để trả về mặc định.`}
    />
  );
}

/**
 * Headline figures for a management page.
 *
 * <p>Counted from the list already in hand rather than fetched: the admin
 * endpoints send everything, so a second round trip would only restate what
 * the page is holding.
 */
export function StatBar({ stats }: Readonly<{ stats: readonly Stat[] }>) {
  return (
    <dl className={styles.stats}>
      {stats.map((stat) => (
        <div key={stat.label}>
          <dt>{stat.label}</dt>
          <dd>{stat.value}</dd>
        </div>
      ))}
    </dl>
  );
}
