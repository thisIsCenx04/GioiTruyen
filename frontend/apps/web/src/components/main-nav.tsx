"use client";

import {
  BookOpen,
  ChevronDown,
  Headphones,
  Home,
  List,
  Star,
  Trophy,
  UsersRound,
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
  trailing?: NavIcon;
  match: (pathname: string) => boolean;
}> = [
  { href: "/" as Route, icon: Home, label: "Trang chủ", match: (pathname) => pathname === "/" },
  {
    href: "/stories/new" as Route,
    icon: BookOpen,
    label: "Truyện mới",
    match: (pathname) => pathname === "/stories/new",
  },
  {
    href: "/categories" as Route,
    icon: List,
    label: "Thể loại",
    match: (pathname) => pathname === "/categories",
    trailing: ChevronDown,
  },
  {
    href: "/rankings" as Route,
    icon: Trophy,
    label: "Bảng xếp hạng",
    match: (pathname) => pathname === "/rankings",
  },
  {
    href: "/stories/full" as Route,
    icon: BookOpen,
    label: "Truyện Full",
    match: (pathname) => pathname === "/stories/full",
  },
  {
    href: "/stories/original" as Route,
    icon: Star,
    label: "Truyện Sáng Tác",
    match: (pathname) => pathname === "/stories/original",
  },
  {
    href: "/teams" as Route,
    icon: UsersRound,
    label: "Team",
    match: (pathname) => pathname === "/teams" || pathname.startsWith("/teams/"),
  },
  {
    href: "/audio" as Route,
    icon: Headphones,
    label: "Nghe Audio",
    match: (pathname) => pathname === "/audio",
  },
];

export function MainNav() {
  const pathname = usePathname();

  return (
    <nav aria-label="Điều hướng chính" className="mainNav">
      {navItems.map((item) => {
        const Icon = item.icon;
        const Trailing = item.trailing;

        return (
          <Link
            aria-current={item.match(pathname) ? "page" : undefined}
            href={item.href}
            key={item.href}
          >
            <Icon aria-hidden="true" />
            {item.label}
            {Trailing ? <Trailing aria-hidden="true" /> : null}
          </Link>
        );
      })}
    </nav>
  );
}
