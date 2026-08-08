import { loadAdminOverview } from "./admin-data";
import { OverviewWorkspace } from "./components/admin-overview-workspace";

export const dynamic = "force-dynamic";

export default async function AdminDashboardPage() {
  const overview = await loadAdminOverview();

  return <OverviewWorkspace overview={overview} />;
}
