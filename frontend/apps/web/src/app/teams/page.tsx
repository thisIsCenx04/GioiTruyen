import type { Metadata } from "next";

import { PublicShell } from "@/components/site-chrome";
import { TeamDirectory } from "../../components/team-workspace";

export const metadata: Metadata = {
  description:
    "Đăng ký nhóm xuất bản và theo dõi hồ sơ chờ quản trị viên duyệt trên Giới Truyện.",
  title: "Đăng ký nhóm xuất bản | Giới Truyện",
};

export default function TeamsPage() {
  return (
    <PublicShell>
      <TeamDirectory />
    </PublicShell>
  );
}
