import { CategoryCrudWorkspace } from "../../../components/admin-crud-workspaces";
import { loadAdminCategories } from "../../admin-data";

export const dynamic = "force-dynamic";

export default async function AdminCategoriesPage() {
  const categories = await loadAdminCategories();

  return (
    <section className="adminDashboard">
      <CategoryCrudWorkspace categories={categories} />
    </section>
  );
}
