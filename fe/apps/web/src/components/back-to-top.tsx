"use client";

import { ArrowUp } from "lucide-react";
import { useEffect, useState } from "react";

/** Only worth showing once the header has scrolled well out of reach. */
const SHOW_AFTER_PX = 600;

/**
 * Floating "back to top" control.
 *
 * A reader deep inside a long chapter has no way to reach the navigation
 * without a long scroll, so this brings the header back in one tap. Shown on
 * both mobile and desktop.
 */
export function BackToTop() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > SHOW_AFTER_PX);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  if (!visible) return null;

  return (
    <button
      aria-label="Lên đầu trang"
      className="backToTop"
      onClick={() => window.scrollTo({ behavior: "smooth", top: 0 })}
      title="Lên đầu trang"
      type="button"
    >
      <ArrowUp aria-hidden="true" size={18} />
    </button>
  );
}
