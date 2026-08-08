
import { LegalContentPage } from "@/components/legal-content-page";
import { PublicShell } from "@/components/site-chrome";
import { privacyContent } from "@/lib/legal-content";

/* metadata removed */

export default function PrivacyPolicyPage() {
  return (
    <PublicShell>
      <LegalContentPage content={privacyContent} />
    </PublicShell>
  );
}
