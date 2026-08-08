
import { TeamAnalytics } from "../../../../components/team-analytics";

/* metadata removed */

export default async function TeamAnalyticsPage({
  params,
}: Readonly<{ params: Promise<{ teamId: string }> }>) {
  const { teamId } = await params;
  return <TeamAnalytics teamId={teamId} />;
}
