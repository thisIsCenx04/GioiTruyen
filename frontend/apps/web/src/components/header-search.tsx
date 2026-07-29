"use client";

import { Search } from "lucide-react";
import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useTransition } from "react";

export function HeaderSearch() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  return (
    <form
      action="/search"
      aria-busy={pending}
      className="headerSearch"
      onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const query = String(data.get("q") ?? "").trim();
        const href = query.length > 0
          ? `/search?q=${encodeURIComponent(query)}`
          : "/search";
        startTransition(() => router.push(href as Route));
      }}
      role="search"
    >
      <Search aria-hidden="true" />
      <input
        aria-label="Tìm truyện"
        name="q"
        placeholder="Tìm truyện"
        type="search"
      />
    </form>
  );
}
