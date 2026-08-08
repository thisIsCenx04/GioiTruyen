
import { LegalContentPage } from "@/components/legal-content-page";
import { PublicShell } from "@/components/site-chrome";
import { termsContent } from "@/lib/legal-content";

/* metadata removed */

export default function TermsPage() {
  return (
    <PublicShell>
      <LegalContentPage content={termsContent} />
    </PublicShell>
  );
}
