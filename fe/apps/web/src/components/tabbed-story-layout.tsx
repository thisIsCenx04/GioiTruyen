import React, { useState } from "react";
import type { HomeStorySummary } from "@gioitruyen/api-client";
import {
  Flame,
  Clock,
  DollarSign,
  Pin,
  BookOpen,
  CheckCircle2,
  SlidersHorizontal,
  TrendingUp,
} from "lucide-react";

import { CatalogStoryCard } from "./catalog-story-card";
import styles from "./tabbed-story-layout.module.css";

export type RankingTabKey = "trending" | "updated24h" | "gold" | "recommended";
export type UpdatedTabKey = "newChapters" | "newStories" | "recommended" | "completed";

export interface TabOption<T extends string> {
  key: T;
  label: string;
  icon: React.ReactNode;
}

const RANKING_TABS: TabOption<RankingTabKey>[] = [
  { key: "trending", label: "Đang Thịnh Hành", icon: <TrendingUp size={16} /> },
  { key: "updated24h", label: "Cập Nhật 24h", icon: <Clock size={16} /> },
  { key: "gold", label: "Kim Thánh Bảng", icon: <DollarSign size={16} /> },
  { key: "recommended", label: "Kim Bài Đề Cử", icon: <Pin size={16} /> },
];

const UPDATED_TABS: TabOption<UpdatedTabKey>[] = [
  { key: "newChapters", label: "Chương Mới", icon: <Clock size={16} /> },
  { key: "newStories", label: "Truyện Mới", icon: <BookOpen size={16} /> },
  { key: "recommended", label: "Đề Cử Mới", icon: <Pin size={16} /> },
  { key: "completed", label: "Mới Hoàn Thành", icon: <CheckCircle2 size={16} /> },
];

// Helper to generate mock/sample relative update time
function mockUpdateTime(index: number): string {
  const times = [
    "26 giây trước",
    "2 phút trước",
    "3 phút trước",
    "4 phút trước",
    "6 phút trước",
    "6 phút trước",
    "6 phút trước",
    "8 phút trước",
    "9 phút trước",
  ];
  return times[index % times.length];
}

interface TabbedStoryLayoutProps {
  title: string;
  icon?: React.ReactNode;
  type: "rankings" | "updated";
  stories: HomeStorySummary[];
}

export function TabbedStoryLayout({
  title,
  icon,
  type,
  stories,
}: Readonly<TabbedStoryLayoutProps>) {
  const isRanking = type === "rankings";
  const tabs = isRanking ? RANKING_TABS : UPDATED_TABS;

  const [activeTab, setActiveTab] = useState<string>(tabs[0].key);
  const [showFilter, setShowFilter] = useState(false);
  const [selectedGenre, setSelectedGenre] = useState<string>("ALL");
  const [selectedStatus, setSelectedStatus] = useState<string>("ALL");

  // Sort / Filter stories based on active tab
  let displayedStories = [...stories];

  if (isRanking) {
    if (activeTab === "trending") {
      displayedStories.sort((a, b) => (b.viewCount ?? 0) - (a.viewCount ?? 0));
    } else if (activeTab === "updated24h") {
      displayedStories.sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime());
    } else if (activeTab === "gold") {
      displayedStories.sort((a, b) => (b.saveCount ?? 0) - (a.saveCount ?? 0));
    } else if (activeTab === "recommended") {
      displayedStories.reverse();
    }
  } else {
    if (activeTab === "newChapters" || activeTab === "newStories") {
      displayedStories.sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime());
    } else if (activeTab === "completed") {
      displayedStories = displayedStories.filter(
        (s) => (s as any).progressStatus === "COMPLETED" || (s as any).completionStatus === "COMPLETED" || (s as any).isFull,
      );
      if (displayedStories.length === 0) {
        displayedStories = stories.slice(0, 9);
      }
    }
  }

  // Filter dropdown filters
  if (selectedStatus === "FULL") {
    const fullStories = displayedStories.filter(
      (s) => (s as any).progressStatus === "COMPLETED" || (s as any).completionStatus === "COMPLETED" || (s as any).isFull,
    );
    if (fullStories.length > 0) displayedStories = fullStories;
  }

  const cardList = displayedStories.slice(0, 9);

  return (
    <div className={styles.sectionBox}>
      {/* Header */}
      <header className={styles.sectionHeader}>
        <div className={styles.titleGroup}>
          <span className={styles.titleIcon}>{icon ?? (isRanking ? <Flame size={22} /> : <Clock size={22} />)}</span>
          <h2 className={styles.titleText}>{title}</h2>
        </div>
        <button
          className={styles.filterBtn}
          onClick={() => setShowFilter(!showFilter)}
          type="button"
        >
          <SlidersHorizontal size={15} />
          <span>Bộ Lọc</span>
        </button>
      </header>

      {/* Filter Panel toggle */}
      {showFilter ? (
        <div className={styles.filterPanel}>
          <div className={styles.filterGroup}>
            <span className={styles.filterLabel}>Trạng thái:</span>
            <select
              className={styles.filterSelect}
              onChange={(e) => setSelectedStatus(e.target.value)}
              value={selectedStatus}
            >
              <option value="ALL">Tất cả</option>
              <option value="FULL">Đã Hoàn Thành (FULL)</option>
              <option value="ONGOING">Đang Ra</option>
            </select>
          </div>
          <div className={styles.filterGroup}>
            <span className={styles.filterLabel}>Thể loại:</span>
            <select
              className={styles.filterSelect}
              onChange={(e) => setSelectedGenre(e.target.value)}
              value={selectedGenre}
            >
              <option value="ALL">Tất cả thể loại</option>
              <option value="TIEN_HIEP">Tiên Hiệp</option>
              <option value="HUYEN_HUYEN">Huyền Huyễn</option>
              <option value="DO_THI">Đô Thị</option>
              <option value="NGON_TINH">Ngôn Tình</option>
            </select>
          </div>
        </div>
      ) : null}

      {/* Main Body (Side Nav Tabs + Card Grid) */}
      <div className={styles.sectionBody}>
        {/* Left Side Nav */}
        <nav className={styles.sideNav} aria-label={title}>
          {tabs.map((tab) => {
            const isActive = activeTab === tab.key;
            return (
              <button
                className={`${styles.tabItem} ${isActive ? styles.tabItemActive : ""}`}
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                type="button"
              >
                <span className={styles.tabItemIcon}>{tab.icon}</span>
                <span>{tab.label}</span>
              </button>
            );
          })}
        </nav>

        {/* Right Cards Grid */}
        <div className={styles.cardGrid}>
          {cardList.map((story, idx) => (
            <CatalogStoryCard
              hrefBase="/stories"
              index={idx}
              isFull={idx % 3 === 1 || (story as any).progressStatus === "COMPLETED" || (story as any).completionStatus === "COMPLETED"}
              key={`${activeTab}-${story.id}-${idx}`}
              rank={isRanking ? idx + 1 : undefined}
              story={story}
              updatedText={!isRanking ? mockUpdateTime(idx) : undefined}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
