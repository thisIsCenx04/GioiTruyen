"use client";

import { Search } from "lucide-react";
import { useNavigate, useLocation, useParams } from "react-router-dom";
import { useTransition } from "react";

export function HeaderSearch() {
  const navigate = useNavigate();
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
        startTransition(() => navigate(href as string));
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
