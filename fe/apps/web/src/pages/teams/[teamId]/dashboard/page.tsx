import { PublisherDashboard } from "@/components/publisher-dashboard";
import { useParams } from "react-router-dom";

export default function PublisherDashboardPage() {
  const { teamId } = useParams<{ teamId: string }>();
  return <PublisherDashboard teamId={teamId ?? ""} />;
}
