import { BrandMark } from "@gioitruyen/ui";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";

import { BackToTop } from "@/components/back-to-top";
import { HeaderAuthNav } from "@/components/header-auth-nav";
import { HeaderSearch } from "@/components/header-search";
import { MainNav } from "@/components/main-nav";
import { NotificationBell } from "@/components/notification-bell";
import { QuestTimeTracker } from "@/components/quest-time-tracker";
import { SocialLinks } from "@/components/social-links";
import { ThemeToggle } from "@/components/theme-toggle";

export function SiteHeader() {
  return (
    <header className="siteHeader">
      <div className="siteHeaderTop">
        <Link aria-label="Về trang chủ Giới Truyện" className="brandLink" to="/">
          <BrandMark />
        </Link>
        <HeaderSearch />
        <div className="headerActions">
          <SocialLinks compact />
          <ThemeToggle />
          <NotificationBell />
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
  const year = new Date().getFullYear();

  return (
    <footer className="siteFooter">
      <div className="siteFooterInner">
        <div className="footerBrand">
          <BrandMark />
          <p>Đọc có nhịp. Viết có người đồng hành.</p>
          <SocialLinks />
        </div>

        <nav aria-label="Khám phá">
          <h3>Khám phá</h3>
          <Link to={"/stories" as string}>Kho truyện</Link>
          <Link to={"/categories" as string}>Thể loại</Link>
          <Link to={"/rankings" as string}>Bảng xếp hạng</Link>
          <Link to={"/zhihu" as string}>Truyện Zhihu</Link>
          <Link to={"/missions" as string}>Nhiệm vụ</Link>
        </nav>

        <nav aria-label="Dành cho tác giả">
          <h3>Dành cho tác giả</h3>
          <Link to={"/dang-ky-dang-truyen" as string}>Đăng ký đăng truyện</Link>
          <Link to={"/teams" as string}>Nhóm xuất bản</Link>
          <Link to={"/publishing-rules" as string}>Quy định đăng truyện</Link>
          <Link to={"/affiliate-links" as string}>Gắn link</Link>
        </nav>

        <nav aria-label="Hỗ trợ và pháp lý">
          <h3>Hỗ trợ &amp; pháp lý</h3>
          <Link to={"/about" as string}>Về chúng tôi</Link>
          <Link to={"/wallet" as string}>Nạp xu</Link>
          <Link to={"/terms" as string}>Điều khoản sử dụng</Link>
          <Link to={"/privacy-policy" as string}>Chính sách bảo mật</Link>
        </nav>
      </div>

      <div className="siteFooterBase">
        <small>© {year} Giới Truyện. Mọi tác phẩm thuộc bản quyền của tác giả và nhóm dịch.</small>
        <span className="siteFooterBaseLinks">
          <Link to={"/terms" as string}>Điều khoản</Link>
          <Link to={"/privacy-policy" as string}>Bảo mật</Link>
        </span>
      </div>
    </footer>
  );
}

export function PublicShell({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <div className="publicShell">
      {/* Mounted once here so time on any page counts toward the online quest. */}
      <QuestTimeTracker questType="ONLINE_MINUTES" />
      <SiteHeader />
      {children}
      <SiteFooter />
      <BackToTop />
    </div>
  );
}
