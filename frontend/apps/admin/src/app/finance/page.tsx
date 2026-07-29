import type { Metadata } from "next";

import { AdminShell } from "../admin-shell";
import { loadAdminCashFlow } from "../admin-data";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  description: "Bảng kiểm soát nạp xu, donate và dòng tiền.",
  title: "Cash flow",
};

const numberFormatter = new Intl.NumberFormat("vi-VN");

export default async function FinancePage() {
  const cashFlow = await loadAdminCashFlow();

  return (
    <AdminShell activeTab="cash-flow">
      <section className="adminDashboard">
        <header className="adminTopbar">
          <div>
            <p>CRUD</p>
            <h1>Cash flow</h1>
          </div>
          <button type="button">Tạo giao dịch</button>
        </header>
        <section className="adminCrudPanel">
          {cashFlow.length === 0 ? (
            <p className="adminEmptyState">Chưa có giao dịch trong database.</p>
          ) : (
            cashFlow.map((entry) => (
              <article key={entry.id}>
                <div className="adminCrudDetails">
                  <strong>{entry.description}</strong>
                  <small>
                    {entry.userEmail} - {entry.referenceType}/{entry.referenceId}
                  </small>
                </div>
                <span>
                  {entry.amountXu > 0 ? `+${numberFormatter.format(entry.amountXu)}` : numberFormatter.format(entry.amountXu)} XU
                </span>
                <div className="adminCrudActions">
                  <button type="button">Sửa</button>
                  <button type="button">Đối soát</button>
                  <button type="button">Xóa</button>
                </div>
              </article>
            ))
          )}
        </section>
      </section>
    </AdminShell>
  );
}
