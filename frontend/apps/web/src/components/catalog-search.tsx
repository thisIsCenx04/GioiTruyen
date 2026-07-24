"use client";

import type { SuggestionResponse } from "@gioitruyen/api-client";
import { useEffect, useId, useState } from "react";

export function CatalogSearch({
  initialQuery = "",
}: Readonly<{ initialQuery?: string }>) {
  const [query, setQuery] = useState(initialQuery);
  const [items, setItems] =
    useState<SuggestionResponse["items"]>([]);
  const listId = useId();

  useEffect(() => {
    const normalized = query.trim();
    if (normalized.length < 2) {
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
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
          if (!(error instanceof DOMException)
              || error.name !== "AbortError") {
            setItems([]);
          }
        });
    }, 180);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  return (
    <form action="/search" className="catalogSearch" role="search">
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
            if (value.trim().length < 2) setItems([]);
          }}
          placeholder="Thử “kiếm hiệp”, “thành phố”…"
          type="search"
          value={query}
        />
        <button type="submit">Tìm truyện</button>
      </div>
      {items.length > 0 && (
        <ul className="suggestionList" id={listId}>
          {items.map((item) => (
            <li key={item.id}>
              <a href={`/stories/${item.slug}`}>
                <span>{item.title}</span>
                <small>Mở truyện</small>
              </a>
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}
