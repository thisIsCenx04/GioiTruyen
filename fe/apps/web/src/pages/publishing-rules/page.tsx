
import { LegalContentPage } from "@/components/legal-content-page";
import { PublicShell } from "@/components/site-chrome";
import { publishingRulesContent } from "@/lib/legal-content";

/* metadata removed */

export default function PublishingRulesPage() {
  return (
    <PublicShell>
      <LegalContentPage content={publishingRulesContent} />
    </PublicShell>
  );
}
