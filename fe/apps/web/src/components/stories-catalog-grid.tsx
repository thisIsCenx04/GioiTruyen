"use client";

import type { HomeStorySummary } from "@gioitruyen/api-client";
import { useState } from "react";
import { CatalogStoryCard } from "./catalog-story-card";

const PAGE_SIZE = 40;

export function StoriesCatalogGrid({
  stories,
  pageSize = PAGE_SIZE,
}: Readonly<{
  stories: readonly HomeStorySummary[];
  pageSize?: number;
}>) {
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.ceil(stories.length / pageSize);

  const startIndex = (currentPage - 1) * pageSize;
  const pagedStories = stories.slice(startIndex, startIndex + pageSize);

  const handlePageChange = (page: number) => {
    if (page < 1 || page > totalPages) return;
    setCurrentPage(page);
    const element = document.getElementById("stories-catalog-header");
    if (element) {
      element.scrollIntoView({ behavior: "smooth" });
    }
  };

  return (
    <div>
      <header
        id="stories-catalog-header"
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: "1.2rem",
          paddingBottom: "0.8rem",
          borderBottom: "1px solid #eef2f6",
        }}
      >
        <h1
          style={{
            fontSize: "1.5rem",
            fontWeight: 850,
            margin: 0,
            textTransform: "uppercase",
            letterSpacing: "-0.01em",
          }}
        >
          TRUYỆN MỚI CẬP NHẬT
        </h1>
        <span style={{ fontSize: "0.85rem", color: "#64748b", fontWeight: 600 }}>
          {stories.length} bộ truyện
        </span>
      </header>

      {/* 5-Column Grid of Paged Stories */}
      <div className="catalogGrid catalogGridLarge catalogGridVertical">
        {pagedStories.map((story, index) => (
          <CatalogStoryCard index={startIndex + index} key={story.id} story={story} />
        ))}
      </div>

      {/* Interactive Pagination: Only render if total real pages > 1 */}
      {totalPages > 1 ? (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "0.4rem",
            marginTop: "2rem",
            marginBottom: "1rem",
          }}
        >
          <button
            type="button"
            disabled={currentPage === 1}
            onClick={() => handlePageChange(currentPage - 1)}
            style={{
              padding: "0.45rem 0.85rem",
              background: "#f1f5f9",
              border: "1px solid #cbd5e1",
              borderRadius: "6px",
              cursor: currentPage === 1 ? "not-allowed" : "pointer",
              fontSize: "0.85rem",
              fontWeight: 700,
              opacity: currentPage === 1 ? 0.5 : 1,
            }}
          >
            «
          </button>

          {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
            <button
              key={`page-${page}`}
              type="button"
              onClick={() => handlePageChange(page)}
              style={{
                padding: "0.45rem 0.85rem",
                background: page === currentPage ? "#0084ff" : "#ffffff",
                color: page === currentPage ? "#ffffff" : "inherit",
                border: page === currentPage ? "1px solid #0084ff" : "1px solid #e2e8f0",
                borderRadius: "6px",
                cursor: "pointer",
                fontSize: "0.85rem",
                fontWeight: page === currentPage ? 700 : 600,
                boxShadow: page === currentPage ? "0 2px 8px rgba(0,132,255,0.25)" : "none",
              }}
            >
              {page}
            </button>
          ))}

          <button
            type="button"
            disabled={currentPage === totalPages}
            onClick={() => handlePageChange(currentPage + 1)}
            style={{
              padding: "0.45rem 0.85rem",
              background: "#f1f5f9",
              border: "1px solid #cbd5e1",
              borderRadius: "6px",
              cursor: currentPage === totalPages ? "not-allowed" : "pointer",
              fontSize: "0.85rem",
              fontWeight: 700,
              opacity: currentPage === totalPages ? 0.5 : 1,
            }}
          >
            »
          </button>
        </div>
      ) : null}
    </div>
  );
}
