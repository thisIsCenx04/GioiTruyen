import { AdminShell } from "../../admin-shell";
import { loadAdminStories } from "../../admin-data";

export const dynamic = "force-dynamic";

export default async function AdminStoriesPage() {
  const stories = await loadAdminStories();

  return (
    <AdminShell activeTab="stories">
      <section className="adminDashboard">
        <header className="adminTopbar">
          <div>
            <p>CRUD</p>
            <h1>Quản lý truyện</h1>
          </div>
          <button type="button">Tạo truyện</button>
        </header>
        <section className="adminCrudPanel">
          {stories.length === 0 ? (
            <p className="adminEmptyState">Chưa có truyện trong database.</p>
          ) : (
            stories.map((story) => (
              <article key={story.id}>
                <div className="adminCrudDetails">
                  <strong>{story.title}</strong>
                  <small>
                    {story.teamName} - {story.authorName}
                  </small>
                </div>
                <span>{story.workflowStatus}</span>
                <div className="adminCrudActions">
                  <button type="button">Sửa</button>
                  <button type="button">Ẩn</button>
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
