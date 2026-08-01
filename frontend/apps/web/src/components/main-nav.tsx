"use client";

import {
  BookOpen,
  Home,
  List,
  Trophy,
  UsersRound,
  Volume2,
} from "lucide-react";
import type { Route } from "next";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType, SVGProps } from "react";

type NavIcon = ComponentType<SVGProps<SVGSVGElement>>;

const navItems: Array<{
  href: Route;
  label: string;
  icon: NavIcon;
  match: (pathname: string) => boolean;
}> = [
  { href: "/" as Route, icon: Home, label: "Trang chủ", match: (pathname) => pathname === "/" },
  {
    href: "/stories" as Route,
    icon: BookOpen,
    label: "Truyện",
    match: (pathname) =>
      pathname === "/stories" ||
      pathname.startsWith("/stories/") ||
      pathname.startsWith("/truyen/") ||
      pathname.startsWith("/read/"),
  },
  {
    href: "/categories" as Route,
    icon: List,
    label: "Thể loại",
    match: (pathname) =>
      pathname === "/categories" || pathname.startsWith("/categories/"),
  },
  {
    href: "/rankings" as Route,
    icon: Trophy,
    label: "Bảng xếp hạng",
    match: (pathname) => pathname === "/rankings",
  },
  {
    href: "/audio" as Route,
    icon: Volume2,
    label: "Nghe audio",
    match: (pathname) => pathname === "/audio" || pathname.startsWith("/audio/"),
  },
  {
    href: "/teams" as Route,
    icon: UsersRound,
    label: "Nhóm xuất bản",
    match: (pathname) => pathname === "/teams" || pathname.startsWith("/teams/"),
  },
];

export function MainNav() {
  const pathname = usePathname();

  return (
    <nav aria-label="Điều hướng chính" className="mainNav">
      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <Link
            aria-current={item.match(pathname) ? "page" : undefined}
            href={item.href}
            key={item.href}
          >
            <Icon aria-hidden="true" />
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
