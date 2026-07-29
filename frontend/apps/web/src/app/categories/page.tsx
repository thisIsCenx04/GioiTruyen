import { Shapes } from "lucide-react";
import type { Metadata, Route } from "next";
import Link from "next/link";

import { PublicShell } from "@/components/site-chrome";
import { catalog } from "@/lib/catalog";

export const revalidate = 300;

export const metadata: Metadata = {
  description: "Khám phá truyện theo thể loại đang mở trên Giới Truyện.",
  title: "Thể loại",
};

export default async function CategoriesPage() {
  const taxonomy = await catalog.categories();
  const categories = taxonomy.groups.flatMap((group) => group.categories);

  return (
    <PublicShell>
      <section className="catalogPage">
        <header className="pageIntro compactIntro">
          <p>Thể loại</p>
          <h1>Chọn màu nội dung phù hợp với nhịp đọc của bạn.</h1>
        </header>
        <nav className="categoryDock categoryDockColor categoryDirectory" aria-label="Danh sách thể loại">
          {categories.map((category, index) => (
            <Link
              data-tone={index % 8}
              href={`/categories/${category.slug}` as Route}
              key={category.id}
            >
              <Shapes aria-hidden="true" />
              <strong>{category.name}</strong>
              <small>Khám phá truyện thuộc nhóm {category.name.toLocaleLowerCase("vi-VN")}</small>
            </Link>
          ))}
        </nav>
      </section>
    </PublicShell>
  );
}
