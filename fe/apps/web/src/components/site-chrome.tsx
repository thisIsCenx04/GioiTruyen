import { BrandMark } from "@gioitruyen/ui";
import { Bell, Moon } from "lucide-react";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";

import { HeaderAuthNav } from "@/components/header-auth-nav";
import { HeaderSearch } from "@/components/header-search";
import { MainNav } from "@/components/main-nav";

export function SiteHeader() {
  return (
    <header className="siteHeader">
      <div className="siteHeaderTop">
        <Link aria-label="Về trang chủ Giới Truyện" className="brandLink" to="/">
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
            to={"/notifications" as string}
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
        <Link to={"/about" as string}>Về chúng tôi</Link>
        <Link to={"/terms" as string}>Điều khoản sử dụng</Link>
        <Link to={"/publishing-rules" as string}>Quy định đăng truyện</Link>
        <Link to={"/privacy-policy" as string}>Chính sách bảo mật</Link>
        <Link to={"/missions" as string}>Nhiệm vụ</Link>
        <Link to={"/affiliate-links" as string}>Gắn link</Link>
        <Link to="/teams">Đăng ký nhóm xuất bản</Link>
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
