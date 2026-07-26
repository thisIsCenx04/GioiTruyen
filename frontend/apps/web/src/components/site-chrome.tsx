import { BrandMark } from "@gioitruyen/ui";
import { Bell, Search, UserRound } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

export function SiteHeader() {
  return (
    <header className="siteHeader">
      <Link aria-label="Về trang chủ Giới Truyện" className="brandLink" href="/">
        <BrandMark />
      </Link>
      <nav aria-label="Điều hướng chính">
        <Link href="/">Trang chủ</Link>
        <Link href="/search">Truyện</Link>
        <Link href="/search?q=thể+loại">Thể loại</Link>
        <Link href="/search?q=xếp+hạng">Bảng xếp hạng</Link>
        <Link href="/teams">Cộng đồng</Link>
        <Link href="/teams">Nhóm dịch</Link>
      </nav>
      <form action="/search" className="headerSearch">
        <Search aria-hidden="true" />
        <input aria-label="Tìm truyện, tác giả" name="q" placeholder="Tìm truyện, tác giả..." />
      </form>
      <Link aria-label="Thông báo" className="headerIcon" href="/notifications">
        <Bell aria-hidden="true" />
      </Link>
      <Link className="loginAction" href="/auth/login">
        <UserRound aria-hidden="true" />
        <span>Đăng nhập</span>
      </Link>
      <Link className="quietAction" href="/auth/register">Đăng ký</Link>
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
