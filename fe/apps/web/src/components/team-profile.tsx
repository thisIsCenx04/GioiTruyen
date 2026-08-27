"use client";

import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { CatalogStoryCard } from "@/components/catalog-story-card";
import { DonationJourney } from "@/components/donation-journey";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { VerifiedBadge } from "./verified-badge";

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
  /** Nhom da duoc ban quan tri xac nhan - dau tich xanh canh ten. */
  verified?: boolean;
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
  /** Carries the FULL and ĐỘC QUYỀN marks onto the cards on this page too. */
  progressStatus?: string;
  latestChapterNumber?: number;
  teamName?: string | null;
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

        // Asked of the database directly. This used to pull the home shelves
        // and keep the entries whose team matched, but a shelf holds only eight
        // stories - so a team whose work had scrolled off them got an empty
        // list under a heading that correctly said it had published two.
        const mine = await authedFetch(
          `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/published-stories`,
        )
          .then((res) => (res.ok ? (res.json() as Promise<StorySummary[]>) : []))
          .catch(() => []);
        if (!active) return;
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
            <h1>
              {team.name}
              {team.verified ? <> <VerifiedBadge size="0.72em" /></> : null}
            </h1>
            {team.verified ? (
              <p className="teamProfileVerified">
                <VerifiedBadge size="0.95em" /> Nhóm đã được Giới Truyện xác nhận
              </p>
            ) : null}
            <p className="teamProfileMeta">{number.format(team.storyCount)} truyện đã đăng</p>
            {team.description ? <p className="teamProfileDesc">{team.description}</p> : null}
          </div>

          {/* Ủng hộ thẳng cho nhóm, không qua truyện nào.
              Lệnh gọi chỉ mang teamId nên khoản này không tính vào thứ hạng của
              bất kỳ truyện nào — đúng nghĩa tiếp sức cho đội ngũ. */}
          <div className="teamProfileDonate">
            <DonationJourney
              context="team"
              storyTitle={team.name}
              teamId={team.id}
              variant="action"
            />
            <small>Ủng hộ cả nhóm bằng Xu.</small>
          </div>
        </header>

        <section>
          <header className="plainPageHeading">
            <h2>TRUYỆN CỦA NHÓM</h2>
            <span>{stories.length > 0 ? `${stories.length} truyện` : ""}</span>
          </header>
          {/* One message for the empty case now: the list holds the team's
              published stories, so nothing in it means there are none. The old
              wording apologised for the shelf-filtering that used to hide
              them. */}
          {stories.length > 0 ? (
            <div className="catalogGrid catalogGridVertical plainStoryGrid">
              {stories.map((story, index) => (
                <CatalogStoryCard index={index} key={story.id} story={story as never} />
              ))}
            </div>
          ) : (
            <p className="plainEmpty">Nhóm chưa đăng truyện nào.</p>
          )}
        </section>
      </div>
    </main>
  );
}
