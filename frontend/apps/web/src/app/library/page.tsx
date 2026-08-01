import type { HomeStorySummary } from "@gioitruyen/api-client";
import type { Metadata } from "next";
import { cookies } from "next/headers";
import Link from "next/link";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  description: "Tủ truyện cá nhân và các truyện được độc giả lưu nhiều.",
  title: "Tủ truyện",
};

export default async function LibraryPage() {
  const accessToken = (await cookies()).get("access_token")?.value;
  let stories: HomeStorySummary[] = [];
  if (accessToken) {
    const apiBaseUrl = (
      process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
    ).replace(/\/+$/u, "");
    stories = await fetch(`${apiBaseUrl}/me/library`, {
      cache: "no-store",
      headers: { Accept: "application/json", Authorization: `Bearer ${accessToken}` },
    }).then(async (response) => response.ok ? await response.json() as HomeStorySummary[] : []).catch(() => []);
  }

  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Tủ truyện</p>
          <h1>Những truyện được độc giả lưu nhiều nhất</h1>
        </header>
        {!accessToken ? <div className="searchPrompt"><strong>Đăng nhập để mở tủ truyện của bạn.</strong><span>Truyện đã lưu sẽ được đồng bộ trên các thiết bị.</span><Link href="/auth/login?returnTo=%2Flibrary">Đăng nhập</Link></div> : stories.length === 0 ? <div className="searchPrompt"><strong>Tủ truyện đang trống.</strong><span>Mở một trang truyện và chọn Yêu thích để lưu tại đây.</span><Link href="/stories">Khám phá truyện</Link></div> : <div className="catalogGrid catalogGridLarge catalogGridVertical">{stories.map((story, index) => <CatalogStoryCard index={index} key={story.id} story={story} />)}</div>}
      </section>
    </PublicShell>
  );
}
