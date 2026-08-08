
import { LegalContentPage } from "@/components/legal-content-page";
import { PublicShell } from "@/components/site-chrome";
import { affiliateLinksContent } from "@/lib/legal-content";

/* metadata removed */

export default function AffiliateLinksPage() {
  return (
    <PublicShell>
      <LegalContentPage content={affiliateLinksContent} />
    </PublicShell>
  );
}
