import { loadAdminUsers } from "../../admin-data";
import { UserCrudWorkspace } from "../../../components/admin-crud-workspaces";

export const dynamic = "force-dynamic";

export default async function AdminUsersPage() {
  const users = await loadAdminUsers();

  return (
    <section className="adminDashboard">
      <UserCrudWorkspace users={users} />
    </section>
  );
}
