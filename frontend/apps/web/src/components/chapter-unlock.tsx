"use client";

import Link from "next/link";
import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useState } from "react";

export type ChapterAccessView = Readonly<{
  chapterId: string;
  storyId: string;
  chapterTitle: string;
  priceXu: number;
  unlocked: boolean;
  authenticated: boolean;
  availableXu: number;
}>;

export function ChapterUnlock({
  access,
  returnTo = `/read/${access.chapterId}`,
  storyHref = `/stories/${access.storyId}`,
}: Readonly<{
  access: ChapterAccessView;
  returnTo?: string;
  storyHref?: string;
}>) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function unlock() {
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`/api/workspace/chapters/${access.chapterId}/unlock`, {
        method: "POST",
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => null) as { title?: string } | null;
        throw new Error(problem?.title ?? "Không thể mở khóa chương.");
      }
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể mở khóa chương.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="chapterUnlockPage">
      <section>
        <p>Chương dành cho thành viên</p>
        <h1>{access.chapterTitle}</h1>
        <span>Mở khóa một lần và đọc lại bất cứ lúc nào trên tài khoản của bạn.</span>
        <dl>
          <div><dt>Giá chương</dt><dd>{access.priceXu.toLocaleString("vi-VN")} XU</dd></div>
          {access.authenticated && <div><dt>Số dư hiện tại</dt><dd>{access.availableXu.toLocaleString("vi-VN")} XU</dd></div>}
        </dl>
        {error && <p className="unlockError" role="alert">{error}</p>}
        <div className="unlockActions">
          {access.authenticated ? <button disabled={busy || access.availableXu < access.priceXu} onClick={() => void unlock()} type="button">{busy ? "Đang mở khóa..." : "Mở khóa chương"}</button> : <Link href={`/auth/login?returnTo=${encodeURIComponent(returnTo)}`}>Đăng nhập để mở khóa</Link>}
          <Link href={storyHref as Route}>Về trang truyện</Link>
          {access.authenticated && access.availableXu < access.priceXu && <Link href="/wallet">Nạp thêm XU</Link>}
        </div>
      </section>
    </main>
  );
}
