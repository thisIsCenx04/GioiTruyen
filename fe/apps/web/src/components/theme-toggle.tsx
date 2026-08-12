"use client";

import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";

/** Where the reader's choice is remembered between visits. */
const STORAGE_KEY = "site-theme";

type Theme = "dark" | "light";

/**
 * Reads the theme the page is already showing.
 *
 * <p>A small script in index.html sets the attribute before React mounts, so
 * the page never flashes white before turning dark. This only has to agree
 * with whatever that script decided.
 */
function currentTheme(): Theme {
  if (typeof document === "undefined") return "light";
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Private browsing can refuse storage; the toggle still works for
      // this visit, it just will not be remembered.
    }
  }, [theme]);

  // Follow the operating system until the reader picks a side themselves.
  useEffect(() => {
    let stored: string | null = null;
    try {
      stored = localStorage.getItem(STORAGE_KEY);
    } catch {
      stored = null;
    }
    if (stored) return undefined;

    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = (event: MediaQueryListEvent) => setTheme(event.matches ? "dark" : "light");
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  const next = theme === "dark" ? "light" : "dark";

  return (
    <button
      aria-label={theme === "dark" ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"}
      className="headerIcon"
      onClick={() => setTheme(next)}
      title={theme === "dark" ? "Giao diện sáng" : "Giao diện tối"}
      type="button"
    >
      {theme === "dark" ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
    </button>
  );
}
