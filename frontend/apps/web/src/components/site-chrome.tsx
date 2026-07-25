import { BrandMark } from "@gioitruyen/ui";
import Link from "next/link";
import type { ReactNode } from "react";

export function SiteHeader() {
  return (
    <header className="siteHeader">
      <Link aria-label="Về trang chủ Giới Truyện" href="/">
        <BrandMark />
      </Link>
      <nav aria-label="Điều hướng chính">
        <Link href="/#catalog">Khám phá</Link>
        <Link href="/search">Tìm truyện</Link>
        <Link href="/teams">Nhóm xuất bản</Link>
        <Link href="/wallet">Ví XU</Link>
      </nav>
      <Link className="quietAction" href="/auth/login">
        Đăng nhập
      </Link>
    </header>
  );
}

export function SiteFooter() {
  return (
    <footer className="siteFooter">
      <BrandMark />
      <p>Đọc có nhịp. Viết có người đồng hành.</p>
      <Link href="/teams">Gửi bản thảo</Link>
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
