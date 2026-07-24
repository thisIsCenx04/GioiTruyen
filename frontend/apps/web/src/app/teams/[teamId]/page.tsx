import type { Metadata } from "next";

import { TeamWorkspace } from "../../../components/team-workspace";

export const metadata: Metadata = {
  description:
    "Quản lý hồ sơ nhóm, thành viên, quyền cộng tác và người theo dõi.",
  title: "Không gian nhóm | Giới Truyện",
};

export default async function TeamWorkspacePage({
  params,
}: Readonly<{ params: Promise<{ teamId: string }> }>) {
  const { teamId } = await params;
  return <TeamWorkspace teamId={teamId} />;
}
