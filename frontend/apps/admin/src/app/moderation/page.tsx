import { AdminShell } from "../admin-shell";
import { ModerationConsole } from "../../components/moderation-console";

export default function AdminModerationPage() {
  return (
    <AdminShell activeTab="moderation">
      <section className="adminDashboard adminModerationEmbed">
        <ModerationConsole />
      </section>
    </AdminShell>
  );
}
