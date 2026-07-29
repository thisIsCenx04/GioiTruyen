import { BrandMark } from "@gioitruyen/ui";
import { Bell, BookMarked, Moon, UserRound, WalletCards } from "lucide-react";
import type { Route } from "next";
import Link from "next/link";
import type { ReactNode } from "react";

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
            <span>0</span>
          </Link>
          <details className="profileMenu">
            <summary>
              <span className="profileAvatar">
                <UserRound aria-hidden="true" />
              </span>
              <span>
                <strong>pocket</strong>
                <small>pocket</small>
              </span>
            </summary>
            <div>
              <Link href={"/library" as Route}>
                <BookMarked aria-hidden="true" />
                Tủ truyện
              </Link>
              <Link href={"/wallet" as Route}>
                <WalletCards aria-hidden="true" />
                Ví của bạn
              </Link>
              <Link href={"/account/sessions" as Route}>
                <UserRound aria-hidden="true" />
                Hồ sơ
              </Link>
            </div>
          </details>
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
      <BrandMark />
      <p>Đọc có nhịp. Viết có người đồng hành.</p>
      <Link href="/teams">Đăng truyện cùng Giới Truyện</Link>
    </footer>
  );
}

export function PublicShell({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <main>
      <SiteHeader />
      {children}
      <SiteFooter />
    </main>
  );
}
