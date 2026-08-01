import type { Metadata } from "next";
import type { ReactNode } from "react";

import { AdminShell } from "./admin-shell";
import "./globals.css";

export const metadata: Metadata = {
  title: "Giới Truyện Admin · Control Center",
  description: "Bảng điều khiển quản trị nền tảng truyện tranh Giới Truyện.",
  icons: {
    icon: "/logo-icon.jpg",
    shortcut: "/logo-icon.jpg",
    apple: "/logo-icon.jpg",
  },
};

export default function AdminLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="vi">
      <body><AdminShell>{children}</AdminShell></body>
    </html>
  );
}
