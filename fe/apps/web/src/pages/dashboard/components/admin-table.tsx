"use client";

import { ArrowDown, ArrowUp, ArrowUpDown, Search } from "lucide-react";
import type { ReactNode } from "react";
import { useMemo, useState } from "react";

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
}: AdminTableProps<T, K>) {
  const [sort, setSort] = useState<SortState<K> | null>(null);
  const [term, setTerm] = useState("");
  const [choices, setChoices] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);

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
        <table className={styles.table}>
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
                  </th>
                );
              })}
              {actions ? <th aria-label="Thao tác">Thao tác</th> : null}
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
