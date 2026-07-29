import type { Metadata } from "next";

import { TeamDirectory } from "../../components/team-workspace";

export const metadata: Metadata = {
  description:
    "Đăng ký team xuất bản và theo dõi hồ sơ chờ admin duyệt trên Giới Truyện.",
  title: "Đăng ký team | Giới Truyện",
};

export default function TeamsPage() {
  return <TeamDirectory />;
}
