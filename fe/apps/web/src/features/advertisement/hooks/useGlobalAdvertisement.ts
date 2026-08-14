import { useEffect } from "react";

import { canOpenAdvertisement, markAdvertisementOpened } from "../services/advertisementStorage";
import type { GlobalAdvertisement } from "../types/advertisement";

const IGNORED_PATH_PREFIXES = [
  "/dashboard",
  "/admin",
  "/login",
  "/register",
  "/auth/forgot-password",
  "/auth/reset-password",
  "/auth/mfa",
  "/wallet",
];

function shouldIgnoreRoute(pathname: string) {
  return IGNORED_PATH_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

function shouldIgnoreTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return true;
  return Boolean(
    target.closest([
      "[data-ad-ignore]",
      "form",
      "input",
      "textarea",
      "select",
      "button",
      "[contenteditable='true']",
      "[role='dialog']",
    ].join(","))
  );
}

export function useGlobalAdvertisement(advertisement: GlobalAdvertisement | null, pathname: string) {
  useEffect(() => {
    if (!advertisement || shouldIgnoreRoute(pathname)) return;

    const handleClick = (event: MouseEvent) => {
      if (event.defaultPrevented || shouldIgnoreTarget(event.target)) return;
      if (!canOpenAdvertisement(advertisement)) return;

      markAdvertisementOpened(advertisement);
      window.open(advertisement.targetUrl, "_blank", "noopener,noreferrer");
    };

    document.addEventListener("click", handleClick);

    return () => {
      document.removeEventListener("click", handleClick);
    };
  }, [advertisement, pathname]);
}
