import { useParams } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";
import { TeamProfile } from "@/components/team-profile";

export default function TeamProfilePage() {
  const { teamId } = useParams<{ teamId: string }>();
  return (
    <PublicShell>
      <TeamProfile teamId={teamId ?? ""} />
    </PublicShell>
  );
}
