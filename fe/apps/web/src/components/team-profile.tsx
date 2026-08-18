"use client";

import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

/**
 * A team's public profile.
 *
 * Replaces a page that fell back to invented figures whenever its API calls
 * failed - 15,850 Xu of revenue, "Chương 128", "24 độc giả VIP" - and whose
 * calls failed always, because the endpoints it wanted were never built. Every
 * number here comes from a response; when something is missing it is left out.
 */

type Team = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  avatarUrl: string | null;
  storyCount: number;
};

type StorySummary = {
  id: string;
  slug: string;
  title: string;
  teamId: string;
  coverAssetId: string | null;
  viewCount: number;
  saveCount: number;
  publishedAt: string;
  storyFormat: string;
  storyType: string;
};

const number = new Intl.NumberFormat("vi-VN");

function initial(name: string): string {
  return name.trim().charAt(0).toLocaleUpperCase("vi") || "?";
}

export function TeamProfile({ teamId }: Readonly<{ teamId: string }>) {
  const [team, setTeam] = useState<Team | null>(null);
  const [stories, setStories] = useState<StorySummary[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "error">("loading");

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const response = await authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}`);
        if (!active) return;
        if (response.status === 404) {
          setState("missing");
          return;
        }
        if (!response.ok) {
          setState("error");
          return;
        }
        const loaded = (await response.json()) as Team;
        setTeam(loaded);
        setState("ready");

        // The catalog is the only public source of a team's stories - the
        // /teams/{id}/stories endpoint is for members and includes drafts.
        const sections = await authedFetch(`${API_BASE_URL}/stories/sections`)
          .then((res) => (res.ok ? (res.json() as Promise<Array<{ stories: StorySummary[] }>>) : []))
          .catch(() => []);
        if (!active) return;
        const mine = [
          ...new Map(
            sections
              .flatMap((section) => section.stories)
              .filter((story) => story.teamId === loaded.id)
              .map((story) => [story.id, story] as const),
          ).values(),
        ];
        setStories(mine);
      } catch {
        if (active) setState("error");
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [teamId]);

  if (state === "loading") {
    return (
      <main className="plainPageShell">
        <div className="plainPageContainer">
          <p className="plainEmpty">Đang tải thông tin nhóm…</p>
        </div>
      </main>
    );
  }

  if (state !== "ready" || !team) {
    return (
      <main className="plainPageShell">
        <div className="plainPageContainer">
          <p className="plainEmpty">
            {state === "missing" ? "Không tìm thấy nhóm này. " : "Không tải được thông tin nhóm. "}
            <Link to="/teams">Xem tất cả nhóm</Link>
          </p>
        </div>
      </main>
    );
  }

  return (
    <main className="plainPageShell">
      <div className="plainPageContainer">
        <header className="teamProfileHead">
          <span className="teamAvatar teamAvatarLarge">
            {team.avatarUrl ? (
              <img alt="" aria-hidden="true" src={team.avatarUrl}  decoding="async" loading="lazy" />
            ) : (
              <span aria-hidden="true">{initial(team.name)}</span>
            )}
          </span>
          <div>
            <h1>{team.name}</h1>
            <p className="teamProfileMeta">{number.format(team.storyCount)} truyện đã đăng</p>
            {team.description ? <p className="teamProfileDesc">{team.description}</p> : null}
          </div>
        </header>

        <section>
          <header className="plainPageHeading">
            <h2>TRUYỆN CỦA NHÓM</h2>
            <span>{stories.length > 0 ? `${stories.length} truyện` : ""}</span>
          </header>
          {stories.length > 0 ? (
            <div className="catalogGrid catalogGridVertical plainStoryGrid">
              {stories.map((story, index) => (
                <CatalogStoryCard index={index} key={story.id} story={story as never} />
              ))}
            </div>
          ) : (
            <p className="plainEmpty">
              {team.storyCount > 0
                ? "Truyện của nhóm chưa xuất hiện trong danh sách đang hiển thị."
                : "Nhóm chưa đăng truyện nào."}
            </p>
          )}
        </section>
      </div>
    </main>
  );
}
