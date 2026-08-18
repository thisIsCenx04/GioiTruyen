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
};

const number = new Intl.NumberFormat("vi-VN");

/** Initial shown in place of a missing picture - a letter, not artwork. */
function initial(name: string): string {
  return name.trim().charAt(0).toLocaleUpperCase("vi") || "?";
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
        setTeams((await response.json()) as Team[]);
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

        {teams.length > 0 ? (
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
