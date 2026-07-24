import Link from "next/link";

import { PublicShell } from "@/components/site-chrome";

export default function StoryNotFound() {
  return (
    <PublicShell>
      <section className="notFound">
        <p>Trang này đã rời khỏi mục lục</p>
        <h1>Không tìm thấy truyện.</h1>
        <Link href="/">Trở về thư viện</Link>
      </section>
    </PublicShell>
  );
}
