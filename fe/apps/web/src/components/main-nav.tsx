"use client";

import {
  BookOpen,
  Home,
  List,
  Menu,
  MessageSquareQuote,
  Trophy,
  UsersRound,
  Volume2,
  X,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useLocation } from "react-router-dom";
import { useEffect, useState, type ComponentType, type SVGProps } from "react";

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
  const location = useLocation();
  const pathname = location.pathname;
  const [open, setOpen] = useState(false);

  // Closing on navigation matters because the links stay mounted: without this
  // the panel would still cover the page the reader just opened.
  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  // A panel that covers the viewport must not scroll the page behind it.
  useEffect(() => {
    if (!open) return undefined;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = previous; };
  }, [open]);

  return (
    <>
      <button
        aria-controls="main-nav"
        aria-expanded={open}
        aria-label={open ? "Đóng menu" : "Mở menu"}
        className="navToggle"
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        {open ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
      </button>

      {open ? (
        <button aria-label="Đóng menu" className="navScrim" onClick={() => setOpen(false)} type="button" />
      ) : null}

      <nav
        aria-label="Điều hướng chính"
        className={open ? "mainNav isOpen" : "mainNav"}
        id="main-nav"
      >
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
    </>
  );
}
