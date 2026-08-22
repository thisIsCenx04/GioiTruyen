"use client";

import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { API_BASE_URL, authedFetch } from "@/lib/api-base";

/**
 * Publishing teams, listed plainly.
 *
 * Replaces a directory built from decorative parts - cartoon badges, a 3D
 * pedestal, a large letter "G" graphic, tinted highlight boxes - none of which
 * carried information. A team shows its picture when it has one and nothing in
 * that place when it does not, which is the same rule the story cards follow.
 */

type Team = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  avatarUrl: string | null;
  storyCount: number;
  totalViews: number;
  totalFavorites: number;
  totalFollows: number;
};

const number = new Intl.NumberFormat("vi-VN");

/** Initial shown in place of a missing picture - a letter, not artwork. */
function initial(name: string): string {
  return name.trim().charAt(0).toLocaleUpperCase("vi") || "?";
}

/** Teams shown before the ranking table: four to a row, six rows. */
const TEAMS_BEFORE_RANKING = 24;
/** Rows every ranking column keeps, filled or not. */
const RANKING_ROWS = 5;

type RankingColumn = {
  key: string;
  title: string;
  value: (team: Team) => number;
};

const RANKING_COLUMNS: RankingColumn[] = [
  { key: "views", title: "Tổng lượt xem", value: (team) => team.totalViews },
  { key: "stories", title: "Truyện đã đăng", value: (team) => team.storyCount },
  { key: "favorites", title: "Lượt yêu thích", value: (team) => team.totalFavorites },
  { key: "follows", title: "Lượt theo dõi", value: (team) => team.totalFollows },
];

/**
 * Five best teams by one measure.
 *
 * Teams with nothing to show for the measure are left out rather than padding
 * the table with zeroes - a leaderboard listing teams on nought reads as if
 * they placed, and the empty rows say "nobody yet" more honestly.
 */
function leaders(teams: Team[], column: RankingColumn): Team[] {
  return teams
    .filter((team) => column.value(team) > 0)
    .sort((left, right) => column.value(right) - column.value(left) || left.name.localeCompare(right.name, "vi"))
    .slice(0, RANKING_ROWS);
}

function TeamRankingBoard({ teams }: { teams: Team[] }) {
  return (
    <section aria-labelledby="team-ranking-title" className="teamRankBoard">
      <header>
        <h2 id="team-ranking-title">BẢNG XẾP HẠNG NHÓM</h2>
        <p>Tổng hợp trên toàn bộ truyện đã xuất bản của mỗi nhóm.</p>
      </header>
      <div className="teamRankColumns">
        {RANKING_COLUMNS.map((column) => {
          const top = leaders(teams, column);
          return (
            <div className="teamRankColumn" key={column.key}>
              <h3>{column.title}</h3>
              <ol>
                {Array.from({ length: RANKING_ROWS }, (_, index) => {
                  const team = top[index];
                  return (
                    <li
                      className="teamRankRow"
                      data-empty={team ? undefined : "true"}
                      data-place={index + 1}
                      key={`${column.key}-${index}`}
                    >
                      <span>{index + 1}</span>
                      {team ? (
                        <>
                          <Link to={`/teams/${team.slug || team.id}`}>{team.name}</Link>
                          <b>{number.format(column.value(team))}</b>
                        </>
                      ) : (
                        <>
                          <span className="teamRankEmpty">—</span>
                          <span className="teamRankEmpty">—</span>
                        </>
                      )}
                    </li>
                  );
                })}
              </ol>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function TeamCards({ teams }: { teams: Team[] }) {
  return (
    <ul className="teamGrid">
      {teams.map((team) => (
        <li key={team.id}>
          <Link className="teamCard" to={`/teams/${team.slug || team.id}`}>
            <span className="teamAvatar">
              {team.avatarUrl ? (
                <img alt="" aria-hidden="true" loading="lazy" src={team.avatarUrl} />
              ) : (
                <span aria-hidden="true">{initial(team.name)}</span>
              )}
            </span>
            <span className="teamCardBody">
              <strong>{team.name}</strong>
              <small>{number.format(team.storyCount)} truyện đã đăng</small>
              {team.description ? <p>{team.description}</p> : null}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function TeamDirectory() {
  const [teams, setTeams] = useState<Team[]>([]);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");

  useEffect(() => {
    let active = true;
    void authedFetch(`${API_BASE_URL}/teams`)
      .then(async (response) => {
        if (!active) return;
        if (!response.ok) {
          setState("error");
          return;
        }
        // The totals arrived with a later backend; an older one still answers
        // this route, so a missing figure counts as zero instead of rendering
        // "NaN" in the ranking table.
        const rows = (await response.json()) as Team[];
        setTeams(
          rows.map((team) => ({
            ...team,
            storyCount: Number(team.storyCount) || 0,
            totalFavorites: Number(team.totalFavorites) || 0,
            totalFollows: Number(team.totalFollows) || 0,
            totalViews: Number(team.totalViews) || 0,
          })),
        );
        setState("ready");
      })
      .catch(() => {
        if (active) setState("error");
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="plainPageShell">
      <div className="plainPageContainer">
        <header className="plainPageHeading">
          <h1>NHÓM XUẤT BẢN</h1>
          <span>{state === "ready" ? `${teams.length} nhóm` : ""}</span>
        </header>

        {state === "loading" ? <p className="plainEmpty">Đang tải danh sách nhóm…</p> : null}

        {state === "error" ? (
          <p className="plainEmpty">
            Không tải được danh sách nhóm. Vui lòng thử lại sau giây lát.
          </p>
        ) : null}

        {state === "ready" && teams.length === 0 ? (
          <p className="plainEmpty">Chưa có nhóm xuất bản nào.</p>
        ) : null}

        {/* Six rows of cards, the ranking table, then whatever is left. The
            table is placed after a fixed number of cards rather than at the end
            so that a reader meets it without scrolling past every team; the
            ranking itself is always computed over all teams, not just the ones
            above it. */}
        {teams.length > 0 ? <TeamCards teams={teams.slice(0, TEAMS_BEFORE_RANKING)} /> : null}

        {teams.length > 0 ? <TeamRankingBoard teams={teams} /> : null}

        {teams.length > TEAMS_BEFORE_RANKING ? (
          <TeamCards teams={teams.slice(TEAMS_BEFORE_RANKING)} />
        ) : null}

        {/* The application form lives at its own route; duplicating it here meant
            two versions of the same flow to keep in step. */}
        <section className="plainCta">
          <div>
            <h2>Muốn đăng truyện của nhóm bạn?</h2>
            <p>Đăng ký một lần, sau khi được duyệt bạn sẽ có bảng điều khiển riêng để đăng truyện và xem thống kê.</p>
          </div>
          <div className="plainCtaActions">
            <Link className="plainBtnPrimary" to="/dang-ky-dang-truyen">
              Đăng ký đăng truyện
            </Link>
            <Link className="plainBtnGhost" to="/publishing-rules">
              Quy định đăng truyện
            </Link>
          </div>
        </section>
      </div>
    </main>
  );
}
