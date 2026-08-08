import type { ReactNode } from "react";
import { Suspense } from "react";
import { Outlet } from "react-router-dom";

import {
  NavigationState,
  RouteTransition,
} from "@/components/navigation-state";

// Note: Fonts and global CSS should be handled in index.html or main.tsx
// Noto Sans was loaded via next/font/google, we will need to load it via CSS instead.
// For now we just apply the class if needed, or remove it.

export default function Layout({
  children,
}: Readonly<{ children?: ReactNode }>) {
  return (
    <>
      <Suspense fallback={null}>
        <NavigationState />
        <RouteTransition>{children ?? <Outlet />}</RouteTransition>
      </Suspense>
    </>
  );
}
