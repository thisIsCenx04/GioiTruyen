"use client";

import { Bookmark, BookOpen, Clock, Heart, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { PublicShell } from "@/components/site-chrome";
import type { HomeStorySummary } from "@gioitruyen/api-client";

type TabKey = "favorites" | "reading" | "history";

export default function LibraryPage() {
  const [activeTab, setActiveTab] = useState<TabKey>("favorites");
  const [stories, setStories] = useState<HomeStorySummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem("access_token") || localStorage.getItem("gioitruyen_token");
    if (token) {
      setIsLoggedIn(true);
    }

    async function fetchLibrary() {
      setLoading(true);
      try {
        const response = await fetch("/api/v1/me/library", {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (response.ok) {
          const data = await response.json() as HomeStorySummary[];
          setStories(data);
        } else {
          setStories([]);
        }
      } catch {
        setStories([]);
      } finally {
        setLoading(false);
      }
    }

    void fetchLibrary();
  }, []);

  return (
    <PublicShell>
      <section className="catalogPage libraryPage" style={{ maxWidth: "1200px", margin: "0 auto", padding: "2rem 1.5rem" }}>
        <header className="pageIntro compactIntro" style={{ marginBottom: "2rem" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", color: "#0f5fff", fontWeight: 800, textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>
            <Bookmark style={{ width: "1rem", height: "1rem" }} />
            Tủ truyện cá nhân
          </div>
          <h1 style={{ fontSize: "2.2rem", fontWeight: 900, color: "#0f172a", margin: "0.4rem 0 0.6rem" }}>
            Nơi lưu giữ các bộ truyện yêu thích của bạn
          </h1>
          <p style={{ color: "#64748b", fontSize: "0.9rem" }}>
            Theo dõi tiến độ đọc, đánh dấu chương mới và đồng bộ tủ truyện của bạn trên mọi thiết bị.
          </p>
        </header>

        {/* Tab Filters */}
        <div className="libraryTabs" style={{ display: "flex", gap: "0.75rem", borderBottom: "1px solid #e2e8f0", paddingBottom: "1rem", marginBottom: "2rem" }}>
          <button
            type="button"
            onClick={() => setActiveTab("favorites")}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.5rem",
              padding: "0.6rem 1.25rem",
              borderRadius: "0.6rem",
              border: activeTab === "favorites" ? "1px solid #0f5fff" : "1px solid #e2e8f0",
              background: activeTab === "favorites" ? "#0f5fff" : "#ffffff",
              color: activeTab === "favorites" ? "#ffffff" : "#475569",
              fontWeight: 700,
              fontSize: "0.85rem",
              cursor: "pointer",
              transition: "all 0.2s ease",
            }}
          >
            <Heart style={{ width: "1rem", height: "1rem" }} />
            Yêu thích
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("reading")}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.5rem",
              padding: "0.6rem 1.25rem",
              borderRadius: "0.6rem",
              border: activeTab === "reading" ? "1px solid #0f5fff" : "1px solid #e2e8f0",
              background: activeTab === "reading" ? "#0f5fff" : "#ffffff",
              color: activeTab === "reading" ? "#ffffff" : "#475569",
              fontWeight: 700,
              fontSize: "0.85rem",
              cursor: "pointer",
              transition: "all 0.2s ease",
            }}
          >
            <BookOpen style={{ width: "1rem", height: "1rem" }} />
            Đang đọc
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("history")}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.5rem",
              padding: "0.6rem 1.25rem",
              borderRadius: "0.6rem",
              border: activeTab === "history" ? "1px solid #0f5fff" : "1px solid #e2e8f0",
              background: activeTab === "history" ? "#0f5fff" : "#ffffff",
              color: activeTab === "history" ? "#ffffff" : "#475569",
              fontWeight: 700,
              fontSize: "0.85rem",
              cursor: "pointer",
              transition: "all 0.2s ease",
            }}
          >
            <Clock style={{ width: "1rem", height: "1rem" }} />
            Lịch sử đọc
          </button>
        </div>

        {/* Content Body */}
        {!isLoggedIn ? (
          <div style={{ background: "#ffffff", border: "1px dashed #cbd5e1", borderRadius: "1rem", padding: "3.5rem 2rem", textAlign: "center" }}>
            <Sparkles style={{ width: "2.5rem", height: "2.5rem", color: "#0f5fff", marginBottom: "1rem" }} />
            <h2 style={{ fontSize: "1.3rem", fontWeight: 800, color: "#0f172a", marginBottom: "0.5rem" }}>
              Đăng nhập để mở tủ truyện của bạn
            </h2>
            <p style={{ color: "#64748b", fontSize: "0.85rem", marginBottom: "1.5rem" }}>
              Đăng nhập giúp tự động lưu danh sách truyện yêu thích, đồng bộ lịch sử đọc trên điện thoại và máy tính.
            </p>
            <Link
              to="/login?returnTo=/library"
              style={{
                display: "inline-block",
                background: "#0f5fff",
                color: "#ffffff",
                padding: "0.75rem 1.75rem",
                borderRadius: "0.6rem",
                fontWeight: 800,
                fontSize: "0.85rem",
                textDecoration: "none",
              }}
            >
              Đăng nhập ngay
            </Link>
          </div>
        ) : loading ? (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: "1.25rem" }}>
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="loadingCard" style={{ height: "240px" }} />
            ))}
          </div>
        ) : stories.length === 0 ? (
          <div style={{ background: "#ffffff", border: "1px dashed #cbd5e1", borderRadius: "1rem", padding: "3.5rem 2rem", textAlign: "center" }}>
            <Bookmark style={{ width: "2.5rem", height: "2.5rem", color: "#94a3b8", marginBottom: "1rem" }} />
            <h2 style={{ fontSize: "1.2rem", fontWeight: 800, color: "#0f172a", marginBottom: "0.5rem" }}>
              Tủ truyện đang trống
            </h2>
            <p style={{ color: "#64748b", fontSize: "0.85rem", marginBottom: "1.5rem" }}>
              Khi đọc một bộ truyện, hãy nhấn nút "Yêu thích" hoặc "Đánh dấu" để lưu vào tủ truyện nhé.
            </p>
            <Link
              to="/stories"
              style={{
                display: "inline-block",
                background: "#0f5fff",
                color: "#ffffff",
                padding: "0.7rem 1.5rem",
                borderRadius: "0.6rem",
                fontWeight: 800,
                fontSize: "0.85rem",
                textDecoration: "none",
              }}
            >
              Khám phá danh sách truyện
            </Link>
          </div>
        ) : (
          <div className="catalogGrid catalogGridLarge catalogGridVertical">
            {stories.map((story, index) => (
              <CatalogStoryCard index={index} key={story.id} story={story} />
            ))}
          </div>
        )}
      </section>
    </PublicShell>
  );
}
