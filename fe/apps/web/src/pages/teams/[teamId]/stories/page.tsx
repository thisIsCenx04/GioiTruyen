
import { PublishingWorkspace } from "../../../../components/publishing-workspace";

/* metadata removed */

export default async function PublishingWorkspacePage({
  params,
}: Readonly<{ params: Promise<{ teamId: string }> }>) {
  const { teamId } = await params;
  return <PublishingWorkspace teamId={teamId} />;
}
