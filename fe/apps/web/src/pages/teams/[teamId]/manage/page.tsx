import { useParams } from "react-router-dom";

import { TeamManage } from "@/components/team-manage";

export default function TeamManagePage() {
  const { teamId } = useParams<{ teamId: string }>();
  return <TeamManage teamId={teamId ?? ""} />;
}
