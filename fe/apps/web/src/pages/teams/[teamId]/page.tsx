
import { TeamWorkspace } from "../../../components/team-workspace";

/* metadata removed */

export default async function TeamWorkspacePage({
  params,
}: Readonly<{ params: Promise<{ teamId: string }> }>) {
  const { teamId } = await params;
  return <TeamWorkspace teamId={teamId} />;
}
