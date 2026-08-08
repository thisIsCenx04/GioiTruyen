import { Link } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";

export default function StoryNotFound() {
  return (
    <PublicShell>
      <section className="notFound">
        <p>Trang này đã rời khỏi mục lục</p>
        <h1>Không tìm thấy truyện.</h1>
        <Link to="/">Trở về thư viện</Link>
      </section>
    </PublicShell>
  );
}
