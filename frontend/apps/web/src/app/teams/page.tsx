import type { Metadata } from "next";

import { TeamDirectory } from "../../components/team-workspace";

export const metadata: Metadata = {
  description:
    "Tạo nhóm xuất bản và mở không gian cộng tác của bạn trên Giới Truyện.",
  title: "Nhóm xuất bản | Giới Truyện",
};

export default function TeamsPage() {
  return <TeamDirectory />;
}
