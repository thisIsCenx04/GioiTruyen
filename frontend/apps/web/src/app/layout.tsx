import type { Metadata } from "next";
import type { ReactNode } from "react";

import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "GiớiTruyện — Đọc tiếp một thế giới",
    template: "%s · GiớiTruyện",
  },
  description:
    "Nền tảng truyện dài kỳ dành cho độc giả, tác giả và nhóm xuất bản Việt Nam.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
