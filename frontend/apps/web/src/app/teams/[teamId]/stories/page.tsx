import type { Metadata } from "next";

import { PublishingWorkspace } from "../../../../components/publishing-workspace";

export const metadata: Metadata = {
  description:
    "Soạn truyện, quản lý revision, gửi duyệt và lên lịch xuất bản.",
  title: "Bàn bản thảo | Giới Truyện",
};

export default async function PublishingWorkspacePage({
  params,
}: Readonly<{ params: Promise<{ teamId: string }> }>) {
  const { teamId } = await params;
  return <PublishingWorkspace teamId={teamId} />;
}
