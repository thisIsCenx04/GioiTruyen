import type { Metadata } from "next";

import { TeamAnalytics } from "../../../../components/team-analytics";

export const metadata: Metadata = {
  description:
    "Theo dõi lượt đọc hợp lệ, chất lượng lưu lượng và nguyên nhân bị loại của truyện trong nhóm.",
  title: "Chất lượng lượt đọc | Giới Truyện",
};

export default async function TeamAnalyticsPage({
  params,
}: Readonly<{ params: Promise<{ teamId: string }> }>) {
  const { teamId } = await params;
  return <TeamAnalytics teamId={teamId} />;
}
