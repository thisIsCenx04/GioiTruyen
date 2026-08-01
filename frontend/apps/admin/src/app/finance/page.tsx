import type { Metadata } from "next";

import { loadAdminCashFlow, loadAdminUsers } from "../admin-data";
import { CashFlowCrudWorkspace } from "../../components/admin-crud-workspaces";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  description: "Bảng kiểm soát nạp xu, donate và dòng tiền.",
  title: "Cash flow",
};

export default async function FinancePage() {
  const [cashFlow, users] = await Promise.all([loadAdminCashFlow(), loadAdminUsers()]);

  return (
    <section className="adminDashboard">
      <CashFlowCrudWorkspace entries={cashFlow} users={users} />
    </section>
  );
}
