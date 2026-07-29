import { AdminShell } from "../../admin-shell";
import { loadAdminUsers } from "../../admin-data";

export const dynamic = "force-dynamic";

export default async function AdminUsersPage() {
  const users = await loadAdminUsers();

  return (
    <AdminShell activeTab="users">
      <section className="adminDashboard">
        <header className="adminTopbar">
          <div>
            <p>CRUD</p>
            <h1>Quản lý user</h1>
          </div>
          <button type="button">Tạo user</button>
        </header>
        <section className="adminCrudPanel">
          {users.length === 0 ? (
            <p className="adminEmptyState">Chưa có user trong database.</p>
          ) : (
            users.map((user) => (
              <article key={user.id}>
                <div className="adminCrudDetails">
                  <strong>{user.displayName || user.email}</strong>
                  <small>
                    {user.email} - {user.roles}
                  </small>
                </div>
                <span>{user.state}</span>
                <div className="adminCrudActions">
                  <button type="button">Sửa quyền</button>
                  <button type="button">Khóa</button>
                  <button type="button">Xem ví</button>
                </div>
              </article>
            ))
          )}
        </section>
      </section>
    </AdminShell>
  );
}
