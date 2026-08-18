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

import { catalog } from "@/lib/catalog";

const DEFAULT_CATEGORIES = [
  { id: "1", name: "Chữa Lành", slug: "chua-lanh" },
  { id: "2", name: "Cổ Đại", slug: "co-dai" },
  { id: "3", name: "Đô Thị", slug: "do-thi" },
  { id: "4", name: "Hài Hước", slug: "hai-huoc" },
  { id: "5", name: "Ngôn Tình", slug: "ngon-tinh" },
  { id: "6", name: "Trọng Sinh", slug: "trong-sinh" },
  { id: "7", name: "Vả Mặt", slug: "va-mat" },
  { id: "8", name: "Zhihu", slug: "zhihu" },
];

export function MainNav() {
  const location = useLocation();
  const pathname = location.pathname;
  const [open, setOpen] = useState(false);
  const [catDropdownOpen, setCatDropdownOpen] = useState(false);
  const [categories, setCategories] = useState(DEFAULT_CATEGORIES);

  useEffect(() => {
    let isMounted = true;
    catalog
      .categories()
      .then((res) => {
        if (res?.groups && isMounted) {
          const fetched = res.groups.flatMap((g) => g.categories);
          if (fetched.length > 0) {
            setCategories(fetched);
          }
        }
      })
      .catch(() => {
        // Fallback to default
      });
    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    setOpen(false);
    setCatDropdownOpen(false);
  }, [pathname]);

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
          const isCategoryItem = item.href === "/categories";

          if (isCategoryItem) {
            return (
              <div
                className="mainNavDropdownWrapper"
                key={item.href}
                onMouseEnter={() => setCatDropdownOpen(true)}
                onMouseLeave={() => setCatDropdownOpen(false)}
                style={{ position: "relative" }}
              >
                <Link
                  aria-current={item.match(pathname) ? "page" : undefined}
                  to={item.href}
                  onClick={() => setCatDropdownOpen(false)}
                >
                  <Icon aria-hidden="true" />
                  <span>{item.label}</span>
                  <span style={{ fontSize: "0.65rem", marginLeft: "2px" }}>▼</span>
                </Link>

                {catDropdownOpen ? (
                  <div className="categoryNavDropdownMenu">
                    {categories.map((cat) => (
                      <Link
                        key={cat.slug}
                        to={`/categories/${cat.slug}`}
                        onClick={() => setCatDropdownOpen(false)}
                        className="categoryDropdownItem"
                      >
                        <span className="dropdownChevron">›</span>
                        <span>{cat.name}</span>
                      </Link>
                    ))}
                  </div>
                ) : null}
              </div>
            );
          }

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
