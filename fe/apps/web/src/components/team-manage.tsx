"use client";

import { useCallback, useEffect, useState } from "react";

import { PublisherTabs } from "@/components/publisher-tabs";
import { PublicShell } from "@/components/site-chrome";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

type Member = {
  userId: string;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  memberRole: string;
  status: string;
  joinedAt: string | null;
};

type Overview = { teamName: string; memberRole: string };

const ROLE_LABELS: Readonly<Record<string, string>> = {
  OWNER: "Chủ nhóm",
  MANAGER: "Quản lý",
  EDITOR: "Biên tập",
  MEMBER: "Thành viên",
};

/**
 * Team settings for the people who run one.
 *
 * <p>Everything here is gated on being the OWNER, and the server enforces that
 * independently - the UI only decides what is worth showing. An EDITOR opening
 * this page sees the roster read-only rather than a form that would be refused
 * on submit.
 */
export function TeamManage({ teamId }: Readonly<{ teamId: string }>) {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [name, setName] = useState("");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [description, setDescription] = useState("");
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState("MEMBER");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    const [overviewRes, membersRes, teamRes] = await Promise.all([
      authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/dashboard`),
      authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/members`),
      authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}`),
    ]);
    if (overviewRes.ok) setOverview((await overviewRes.json()) as Overview);
    if (membersRes.ok) setMembers((await membersRes.json()) as Member[]);
    if (teamRes.ok) {
      const team = await teamRes.json() as { name?: string; avatarUrl?: string; description?: string };
      setName(team.name ?? "");
      setAvatarUrl(team.avatarUrl ?? "");
      setDescription(team.description ?? "");
    }
  }, [teamId]);

  useEffect(() => { void load(); }, [load]);

  const isOwner = overview?.memberRole === "OWNER";

  async function send(path: string, method: string, body?: unknown, ok = "Đã lưu.") {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const response = await authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}${path}`, {
        method,
        headers: body ? { "Content-Type": "application/json" } : undefined,
        body: body ? JSON.stringify(body) : undefined,
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => ({})) as { detail?: string };
        setError(problem.detail ?? "Không thực hiện được.");
        return false;
      }
      setNotice(ok);
      await load();
      return true;
    } finally {
      setBusy(false);
    }
  }

  return (
    <PublicShell>
      <main className="publisherShell">
        <div className="publisherContainer">
          <header className="publisherHeading">
            <div>
              <h1>QUẢN LÝ NHÓM</h1>
              <span>{overview?.teamName ?? ""}{isOwner ? " · Chủ nhóm" : ""}</span>
            </div>
          </header>

          <PublisherTabs active="team" teamId={teamId} />

          {error ? <p className="questError" role="alert">{error}</p> : null}
          {notice ? <p className="questNotice" role="status">{notice}</p> : null}

          {!isOwner ? (
            <p className="pubHint">
              Chỉ chủ nhóm mới sửa được thông tin nhóm và thêm thành viên. Bạn đang
              xem ở chế độ chỉ đọc.
            </p>
          ) : null}

          <section className="publisherCard">
            <h2>Thông tin nhóm</h2>
            <label className="pubField">
              <span>Tên nhóm *</span>
              <input disabled={!isOwner} maxLength={180}
                onChange={(event) => setName(event.target.value)} value={name} />
            </label>
            {/* A file picker, not a URL box: an owner has the image on their
                machine, not on a host they control. The upload saves the avatar
                on its own, so it is not lost if they leave without pressing
                the save button below. */}
            <div className="teamAvatarField">
              <span>Ảnh đại diện</span>
              <div>
                {avatarUrl
                  ? <img alt="Ảnh đại diện nhóm" className="teamAvatarPreview" src={avatarUrl}  decoding="async" loading="lazy" />
                  : <span className="teamAvatarPreview teamAvatarEmpty" aria-hidden="true" />}
                {isOwner ? (
                  <label className="teamAvatarPick">
                    {busy ? "Đang tải ảnh…" : "Chọn ảnh…"}
                    <input
                      accept="image/*"
                      disabled={busy}
                      onChange={async (event) => {
                        const file = event.target.files?.[0];
                        // Clear immediately so picking the same file twice in a
                        // row still fires a change event.
                        event.target.value = "";
                        if (!file) return;
                        setBusy(true);
                        setError("");
                        setNotice("");
                        try {
                          const body = new FormData();
                          body.append("file", file);
                          const response = await authedFetch(
                            `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/avatar`,
                            { method: "POST", body },
                          );
                          if (!response.ok) {
                            const problem = await response.json().catch(() => ({})) as { detail?: string };
                            setError(problem.detail ?? "Không tải được ảnh lên.");
                            return;
                          }
                          const saved = await response.json() as { avatarUrl: string };
                          setAvatarUrl(saved.avatarUrl);
                          setNotice("Đã cập nhật ảnh đại diện nhóm.");
                        } finally {
                          setBusy(false);
                        }
                      }}
                      type="file"
                    />
                  </label>
                ) : null}
              </div>
            </div>
            <label className="pubField">
              <span>Mô tả nhóm</span>
              <textarea disabled={!isOwner} onChange={(event) => setDescription(event.target.value)}
                rows={4} value={description} />
            </label>
            {/* The slug is not offered: it is in the URL of every story this
                team has published, so changing it would break those links. */}
            <p className="pubHint">Đường dẫn (slug) của nhóm không đổi được vì đã nằm trong link các truyện đã đăng.</p>
            {isOwner ? (
              <button className="publisherPrimaryBtn" disabled={busy || !name.trim()}
                onClick={() => void send("/profile", "PUT", { name, avatarUrl, description })}
                type="button">
                Lưu thông tin nhóm
              </button>
            ) : null}
          </section>

          <section className="publisherCard">
            <header className="publisherCardHeader">
              <h2>Thành viên</h2>
              <span>{members.length} người</span>
            </header>

            {isOwner ? (
              <div className="teamInviteRow">
                <input onChange={(event) => setInviteEmail(event.target.value)}
                  placeholder="Email của người muốn thêm" type="email" value={inviteEmail} />
                <select onChange={(event) => setInviteRole(event.target.value)} value={inviteRole}>
                  <option value="MEMBER">Thành viên</option>
                  <option value="EDITOR">Biên tập</option>
                  <option value="MANAGER">Quản lý</option>
                </select>
                <button
                  disabled={busy || !inviteEmail.trim()}
                  onClick={() => void send("/members", "POST",
                    { email: inviteEmail.trim(), memberRole: inviteRole }, "Đã thêm thành viên.")
                    .then((ok) => { if (ok) setInviteEmail(""); })}
                  type="button"
                >
                  Thêm
                </button>
              </div>
            ) : null}

            <ul className="teamMemberList">
              {members.map((member) => (
                <li key={member.userId}>
                  <div>
                    <strong>{member.displayName || member.username}</strong>
                    <small>{ROLE_LABELS[member.memberRole] ?? member.memberRole}</small>
                  </div>
                  {isOwner && member.memberRole !== "OWNER" ? (
                    <button
                      className="teamMemberRemove"
                      disabled={busy}
                      onClick={() => {
                        if (!window.confirm(
                          `Gỡ ${member.displayName || member.username} khỏi nhóm?\n\n`
                          + "Họ sẽ mất quyền đăng và sửa truyện của nhóm ngay lập tức.",
                        )) return;
                        void send(`/members/${member.userId}`, "DELETE", undefined, "Đã gỡ thành viên.");
                      }}
                      type="button"
                    >
                      Gỡ
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
          </section>
        </div>
      </main>
    </PublicShell>
  );
}
