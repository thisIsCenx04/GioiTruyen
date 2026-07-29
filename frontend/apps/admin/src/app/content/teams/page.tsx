import { AdminShell } from "../../admin-shell";
import { loadAdminTeams } from "../../admin-data";

export const dynamic = "force-dynamic";

export default async function AdminTeamsPage() {
  const teams = await loadAdminTeams();

  return (
    <AdminShell activeTab="teams">
      <section className="adminDashboard">
        <header className="adminTopbar">
          <div>
            <p>Approval</p>
            <h1>Duyệt team</h1>
          </div>
          <button type="button">Tạo team</button>
        </header>
        <section className="adminCrudPanel">
          {teams.length === 0 ? (
            <p className="adminEmptyState">Chưa có team trong database.</p>
          ) : (
            teams.map((team) => (
              <article key={team.id}>
                <div className="adminCrudDetails">
                  <strong>{team.name}</strong>
                  <small>
                    {team.ownerName} - {team.memberCount} thành viên
                  </small>
                </div>
                <span>{team.state}</span>
                <div className="adminCrudActions">
                  <button type="button">Duyệt</button>
                  <button type="button">Sửa</button>
                  <button type="button">Tạm khóa</button>
                </div>
              </article>
            ))
          )}
        </section>
      </section>
    </AdminShell>
  );
}
