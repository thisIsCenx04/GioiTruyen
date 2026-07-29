"use client";

import type { SuggestionResponse } from "@gioitruyen/api-client";
import Link from "next/link";
import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState, useTransition } from "react";

export function CatalogSearch({
  initialQuery = "",
}: Readonly<{ initialQuery?: string }>) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [query, setQuery] = useState(initialQuery);
  const [suggesting, setSuggesting] = useState(false);
  const [items, setItems] = useState<SuggestionResponse["items"]>([]);
  const listId = useId();

  useEffect(() => {
    const normalized = query.trim();
    if (normalized.length < 2) {
      return undefined;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setSuggesting(true);
      void fetch(
        `/api/catalog/search/suggestions?q=${encodeURIComponent(normalized)}&limit=6`,
        { signal: controller.signal },
      )
        .then(async (response) => {
          if (!response.ok) return { items: [] };
          return (await response.json()) as SuggestionResponse;
        })
        .then((result) => setItems(result.items))
        .catch((error: unknown) => {
          if (!(error instanceof DOMException) || error.name !== "AbortError") {
            setItems([]);
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setSuggesting(false);
        });
    }, 180);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  return (
    <form
      action="/search"
      aria-busy={pending || suggesting}
      className="catalogSearch"
      onSubmit={(event) => {
        event.preventDefault();
        const normalized = query.trim();
        setItems([]);
        startTransition(() => {
          const href = normalized.length > 0
            ? `/search?q=${encodeURIComponent(normalized)}`
            : "/search";
          router.push(href as Route);
        });
      }}
      role="search"
    >
      <label htmlFor="catalog-query">Tìm theo tên truyện</label>
      <div className="searchControl">
        <input
          aria-autocomplete="list"
          aria-controls={listId}
          autoComplete="off"
          id="catalog-query"
          maxLength={80}
          name="q"
          onChange={(event) => {
            const value = event.target.value;
            setQuery(value);
            if (value.trim().length < 2) {
              setItems([]);
              setSuggesting(false);
            }
          }}
          placeholder='Thử "kiếm hiệp", "hiện đại"...'
          type="search"
          value={query}
        />
        <button disabled={pending} type="submit">
          {pending ? "Đang tìm" : "Tìm truyện"}
        </button>
      </div>
      {suggesting && items.length === 0 && (
        <div className="suggestionState" role="status">
          Đang tải gợi ý...
        </div>
      )}
      {items.length > 0 && (
        <ul className="suggestionList" id={listId}>
          {items.map((item) => (
            <li key={item.id}>
              <Link href={`/stories/${item.slug}`} onClick={() => setItems([])}>
                <span>{item.title}</span>
                <small>Mở truyện</small>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}
