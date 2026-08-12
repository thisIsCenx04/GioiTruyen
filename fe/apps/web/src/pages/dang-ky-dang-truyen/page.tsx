"use client";

import { AuthorApplicationForm } from "@/components/author-application-form";
import { PublicShell } from "@/components/site-chrome";

export default function AuthorApplicationPage() {
  return (
    <PublicShell>
      <main className="authorApplyPage">
        <AuthorApplicationForm />
      </main>
    </PublicShell>
  );
}
