"use client";

import type { ReactNode } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useSearchParams } from "next/navigation";

const pendingAttribute = "data-route-pending";

function isModifiedClick(event: MouseEvent) {
  return event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0;
}

function isSamePageHashChange(current: URL, next: URL) {
  return current.pathname === next.pathname
    && current.search === next.search
    && current.hash !== next.hash;
}

function isInternalUrl(url: URL) {
  return url.origin === window.location.origin;
}

export function NavigationState() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const delayTimer = useRef<number | null>(null);
  const routeKey = useMemo(
    () => `${pathname}?${searchParams.toString()}`,
    [pathname, searchParams],
  );
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (delayTimer.current !== null) {
      window.clearTimeout(delayTimer.current);
      delayTimer.current = null;
    }
    const timeout = window.setTimeout(() => {
      setPending(false);
      document.documentElement.removeAttribute(pendingAttribute);
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [routeKey]);

  useEffect(() => {
    if (!pending) return undefined;
    document.documentElement.setAttribute(pendingAttribute, "true");
    const timeout = window.setTimeout(() => {
      setPending(false);
      document.documentElement.removeAttribute(pendingAttribute);
    }, 9000);
    return () => {
      window.clearTimeout(timeout);
    };
  }, [pending]);

  useEffect(() => {
    const startPending = () => {
      if (delayTimer.current !== null) {
        window.clearTimeout(delayTimer.current);
      }
      delayTimer.current = window.setTimeout(() => {
        setPending(true);
        delayTimer.current = null;
      }, 180);
    };

    const handleClick = (event: MouseEvent) => {
      if (event.defaultPrevented || isModifiedClick(event)) return;
      const target = event.target;
      if (!(target instanceof Element)) return;
      const anchor = target.closest<HTMLAnchorElement>("a[href]");
      if (!anchor || anchor.target || anchor.hasAttribute("download")) return;
      const nextUrl = new URL(anchor.href, window.location.href);
      const currentUrl = new URL(window.location.href);
      if (!isInternalUrl(nextUrl) || nextUrl.href === currentUrl.href) return;
      if (isSamePageHashChange(currentUrl, nextUrl)) return;
      startPending();
    };

    const handleSubmit = (event: SubmitEvent) => {
      if (event.defaultPrevented) return;
      const form = event.target;
      if (!(form instanceof HTMLFormElement)) return;
      const method = (form.method || "get").toLowerCase();
      if (method !== "get") return;
      const nextUrl = new URL(form.action || window.location.href, window.location.href);
      if (isInternalUrl(nextUrl)) startPending();
    };

    const handlePageShow = () => {
      if (delayTimer.current !== null) {
        window.clearTimeout(delayTimer.current);
        delayTimer.current = null;
      }
      setPending(false);
      document.documentElement.removeAttribute(pendingAttribute);
    };

    window.addEventListener("pageshow", handlePageShow);
    document.addEventListener("click", handleClick, true);
    document.addEventListener("submit", handleSubmit, true);
    return () => {
      window.removeEventListener("pageshow", handlePageShow);
      document.removeEventListener("click", handleClick, true);
      document.removeEventListener("submit", handleSubmit, true);
      if (delayTimer.current !== null) {
        window.clearTimeout(delayTimer.current);
      }
    };
  }, []);

  return (
    <>
      <div aria-hidden="true" className="routeProgress" data-active={pending ? "true" : "false"} />
      <span aria-live="polite" className="srOnly">
        {pending ? "Đang tải trang mới" : ""}
      </span>
    </>
  );
}

export function RouteTransition({ children }: Readonly<{ children: ReactNode }>) {
  return <div className="routeSurface">{children}</div>;
}
