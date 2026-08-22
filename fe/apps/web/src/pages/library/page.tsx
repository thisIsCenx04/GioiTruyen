"use client";

import type { HomeStorySummary } from "@gioitruyen/api-client";
import { Bookmark, BookOpen, Clock, Heart, PackageCheck } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { isLoggedIn } from "@/lib/auth";

/**
 * Tủ truyện cá nhân.
 *
 * <p>Bốn kệ, mỗi kệ một nguồn dữ liệu riêng. Trước đây ba cái tab chỉ đổi màu
 * nút: lưới bên dưới luôn vẽ đúng một danh sách, nên "Đang đọc" và "Lịch sử
 * đọc" hiện y hệt "Yêu thích" và không nói lên điều gì. Nay mỗi tab gọi đúng kệ
 * của nó.
 */

type Shelf = "favorites" | "reading" | "history" | "combo";

const SHELVES: Array<{
  empty: string;
  icon: typeof Heart;
  key: Shelf;
  label: string;
}> = [
  {
    empty: "Bấm “Yêu thích” ở trang truyện để cất bộ đó vào đây.",
    icon: Heart,
    key: "favorites",
    label: "Yêu thích",
  },
  {
    empty: "Chưa có truyện nào đang đọc dở. Mở một chương bất kỳ là nó xuất hiện ở đây.",
    icon: BookOpen,
    key: "reading",
    label: "Đang đọc",
  },
  {
    empty: "Chưa có lịch sử đọc nào.",
    icon: Clock,
    key: "history",
    label: "Lịch sử đọc",
  },
  {
    empty: "Bạn chưa mua trọn bộ truyện nào. Mua trọn bộ thường rẻ hơn mua lẻ từng chương.",
    icon: PackageCheck,
    key: "combo",
    label: "Đã mua trọn bộ",
  },
];

export default function LibraryPage() {
  const [shelf, setShelf] = useState<Shelf>("favorites");
  const [stories, setStories] = useState<HomeStorySummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const signedIn = isLoggedIn();

  const load = useCallback(async () => {
    if (!signedIn) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setFailed(false);
    const response = await authedFetch(`${API_BASE_URL}/me/library?shelf=${shelf}`).catch(() => null);
    if (response?.ok) {
      setStories((await response.json()) as HomeStorySummary[]);
    } else {
      // Kệ rỗng và kệ không tải được là hai chuyện khác nhau; nói nhầm cái này
      // thành cái kia khiến người đọc tưởng mình mất sạch tủ truyện.
      setStories([]);
      setFailed(true);
    }
    setLoading(false);
  }, [shelf, signedIn]);

  useEffect(() => {
    void load();
  }, [load]);

  const active = SHELVES.find((item) => item.key === shelf) ?? SHELVES[0];

  return (
    <PublicShell>
      <section className="libraryPage">
        <header className="libraryIntro">
          <p className="libraryEyebrow">
            <Bookmark aria-hidden="true" size={15} />
            Tủ truyện cá nhân
          </p>
          <h1>Nơi lưu giữ các bộ truyện yêu thích của bạn</h1>
          <p className="libraryLede">
            Theo dõi tiến độ đọc, đánh dấu chương mới và đồng bộ tủ truyện của bạn trên mọi thiết bị.
          </p>
        </header>

        <div aria-label="Kệ truyện" className="libraryTabs" role="tablist">
          {SHELVES.map((item) => {
            const Icon = item.icon;
            return (
              <button
                aria-selected={shelf === item.key}
                className="libraryTab"
                key={item.key}
                onClick={() => setShelf(item.key)}
                role="tab"
                type="button"
              >
                <Icon aria-hidden="true" size={16} />
                {item.label}
              </button>
            );
          })}
        </div>

        {!signedIn ? (
          <div className="libraryEmpty">
            <Bookmark aria-hidden="true" size={38} />
            <h2>Đăng nhập để mở tủ truyện</h2>
            <p>Tủ truyện đi theo tài khoản, nên bạn mở ở máy nào cũng thấy đúng những gì đã lưu.</p>
            <Link className="libraryCta" to="/login?returnTo=/library">Đăng nhập ngay</Link>
          </div>
        ) : loading ? (
          <div className="catalogGrid catalogGridLarge catalogGridVertical">
            {Array.from({ length: 6 }).map((_, index) => (
              <div className="loadingCard" key={index} />
            ))}
          </div>
        ) : failed ? (
          <div className="libraryEmpty">
            <Bookmark aria-hidden="true" size={38} />
            <h2>Chưa tải được kệ này</h2>
            <p>Kiểm tra kết nối rồi thử lại. Truyện trong tủ vẫn còn nguyên.</p>
            <button className="libraryCta" onClick={() => void load()} type="button">Thử lại</button>
          </div>
        ) : stories.length === 0 ? (
          <div className="libraryEmpty">
            <active.icon aria-hidden="true" size={38} />
            <h2>Kệ “{active.label}” đang trống</h2>
            <p>{active.empty}</p>
            <Link className="libraryCta" to="/stories">Khám phá danh sách truyện</Link>
          </div>
        ) : (
          <>
            <p className="libraryCount">{stories.length} truyện</p>
            <div className="catalogGrid catalogGridLarge catalogGridVertical">
              {stories.map((story, index) => (
                <CatalogStoryCard index={index} key={story.id} story={story} />
              ))}
            </div>
          </>
        )}
      </section>
    </PublicShell>
  );
}
