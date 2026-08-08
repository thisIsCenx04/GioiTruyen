
import { loadAdminCashFlow, loadAdminUsers } from "../admin-data";
import { CashFlowCrudWorkspace } from "@/pages/dashboard/components/admin-crud-workspaces";

export const dynamic = "force-dynamic";

/* metadata removed */

export default async function FinancePage() {
  const [cashFlow, users] = await Promise.all([loadAdminCashFlow(), loadAdminUsers()]);

  return (
    <section className="adminDashboard">
      <CashFlowCrudWorkspace entries={cashFlow} users={users} />
    </section>
  );
}
