import { BrandMark } from "@gioitruyen/ui";
import { Bell, BookMarked, Moon, UserRound, WalletCards } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";
import type { ReactNode } from "react";

import { HeaderAuthNav } from "@/components/header-auth-nav";
import { HeaderSearch } from "@/components/header-search";
import { MainNav } from "@/components/main-nav";

export function SiteHeader() {
  return (
    <header className="siteHeader">
      <div className="siteHeaderTop">
        <Link aria-label="Về trang chủ Giới Truyện" className="brandLink" href="/">
          <BrandMark />
        </Link>
        <HeaderSearch />
        <div className="headerActions">
          <button aria-label="Đổi giao diện sáng tối" className="headerIcon" type="button">
            <Moon aria-hidden="true" />
          </button>
          <Link
            aria-label="Thông báo"
            className="headerIcon notifyIcon"
            href={"/notifications" as Route}
          >
            <Bell aria-hidden="true" />
          </Link>
          <HeaderAuthNav />
        </div>
      </div>
      <div className="siteHeaderBottom">
        <MainNav />
      </div>
    </header>
  );
}

export function SiteFooter() {
  return (
    <footer className="siteFooter">
      <div>
        <BrandMark />
        <p>Đọc có nhịp. Viết có người đồng hành.</p>
      </div>
      <nav aria-label="Thông tin Giới Truyện">
        <Link href={"/about" as Route}>Về chúng tôi</Link>
        <Link href={"/publishing-rules" as Route}>Quy định đăng truyện</Link>
        <Link href="/teams">Đăng ký nhóm xuất bản</Link>
      </nav>
    </footer>
  );
}

export function PublicShell({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <div className="publicShell">
      <SiteHeader />
      {children}
      <SiteFooter />
    </div>
  );
}
