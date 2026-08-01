import { loadAdminCategories, loadAdminStories, loadAdminTeams } from "../../admin-data";
import { StoryCrudWorkspace } from "../../../components/admin-crud-workspaces";

export const dynamic = "force-dynamic";

export default async function AdminStoriesPage() {
  const [categories, stories, teams] = await Promise.all([
    loadAdminCategories(),
    loadAdminStories(),
    loadAdminTeams(),
  ]);

  return (
    <section className="adminDashboard">
      <StoryCrudWorkspace categories={categories} stories={stories} teams={teams} />
    </section>
  );
}
