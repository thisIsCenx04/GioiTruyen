
import { LegalContentPage } from "@/components/legal-content-page";
import { PublicShell } from "@/components/site-chrome";
import { missionsContent } from "@/lib/legal-content";

/* metadata removed */

export default function MissionsPage() {
  return (
    <PublicShell>
      <LegalContentPage content={missionsContent} />
    </PublicShell>
  );
}
