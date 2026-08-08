import type { ReactNode } from "react";

import { AdminShell } from "./admin-shell";
import "./globals.css";

/* metadata removed */

export default function AdminLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="vi">
      <body><AdminShell>{children}</AdminShell></body>
    </html>
  );
}
