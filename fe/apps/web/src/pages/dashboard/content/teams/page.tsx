import { loadAdminTeams, loadAdminUsers } from "../../admin-data";
import { TeamCrudWorkspace } from "@/pages/dashboard/components/admin-crud-workspaces";

export const dynamic = "force-dynamic";

export default async function AdminTeamsPage() {
  const [teams, users] = await Promise.all([loadAdminTeams(), loadAdminUsers()]);

  return (
    <section className="adminDashboard">
      <TeamCrudWorkspace teams={teams} users={users} />
    </section>
  );
}
