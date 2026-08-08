"use client";

import {
  BookOpen,
  Home,
  List,
  MessageSquareQuote,
  Trophy,
  UsersRound,
  Volume2,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams } from "react-router-dom";
import type { ComponentType, SVGProps } from "react";

type NavIcon = ComponentType<SVGProps<SVGSVGElement>>;

const navItems: Array<{
  href: string;
  label: string;
  icon: NavIcon;
  match: (pathname: string) => boolean;
}> = [
  { href: "/" as string, icon: Home, label: "Trang chủ", match: (pathname) => pathname === "/" },
  {
    href: "/stories" as string,
    icon: BookOpen,
    label: "Truyện",
    match: (pathname) =>
      pathname === "/stories" ||
      pathname.startsWith("/stories/") ||
      pathname.startsWith("/truyen/") ||
      pathname.startsWith("/read/"),
  },
  {
    href: "/categories" as string,
    icon: List,
    label: "Thể loại",
    match: (pathname) =>
      pathname === "/categories" || pathname.startsWith("/categories/"),
  },
  {
    href: "/zhihu" as string,
    icon: MessageSquareQuote,
    label: "Truyện Zhihu",
    match: (pathname) => pathname === "/zhihu" || pathname.startsWith("/zhihu/"),
  },
  {
    href: "/rankings" as string,
    icon: Trophy,
    label: "Bảng xếp hạng",
    match: (pathname) => pathname === "/rankings",
  },
  {
    href: "/audio" as string,
    icon: Volume2,
    label: "Nghe audio",
    match: (pathname) => pathname === "/audio" || pathname.startsWith("/audio/"),
  },
  {
    href: "/teams" as string,
    icon: UsersRound,
    label: "Nhóm xuất bản",
    match: (pathname) => pathname === "/teams" || pathname.startsWith("/teams/"),
  },
];

export function MainNav() {
  const location = useLocation(); const pathname = location.pathname;

  return (
    <nav aria-label="Điều hướng chính" className="mainNav">
      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <Link
            aria-current={item.match(pathname) ? "page" : undefined}
            to={item.href}
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
