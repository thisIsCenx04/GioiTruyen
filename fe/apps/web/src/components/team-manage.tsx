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

type Overview = { teamName: string; memberRole: string; ownerAccess: boolean };

type DonationSupporter = {
  id: string;
  userEmail: string;
  displayName: string | null;
  storyTitle: string | null;
  grossCoin: number;
  teamNetCoin: number;
  message: string | null;
  createdAt: string | null;
};

type RecommendationSupporter = {
  id: string;
  userEmail: string;
  displayName: string | null;
  storyTitle: string | null;
  gemAmount: number;
  createdAt: string | null;
};

type Supporters = {
  donations: DonationSupporter[];
  recommendations: RecommendationSupporter[];
};

const ROLE_LABELS: Readonly<Record<string, string>> = {
  OWNER: "Chủ nhóm",
  MANAGER: "Quản lý",
  EDITOR: "Biên tập",
  MEMBER: "Thành viên",
  ADMIN: "Quản trị viên",
};

const number = new Intl.NumberFormat("vi-VN");

function time(value: string | null) {
  if (!value) return "";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "" : parsed.toLocaleString("vi-VN");
}

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
  const [activeTab, setActiveTab] = useState<"profile" | "supporters">("profile");
  const [supporters, setSupporters] = useState<Supporters>({ donations: [], recommendations: [] });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    const overviewRes = await authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/access`);
    if (overviewRes.ok) {
      const loadedOverview = (await overviewRes.json()) as Overview;
      setOverview(loadedOverview);
      if (!loadedOverview.ownerAccess) return;
    } else {
      setError("Bạn không có quyền thao tác này.");
      return;
    }

    const [membersRes, teamRes, supportersRes] = await Promise.all([
      authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/members`),
      authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}`),
      authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/supporters`),
    ]);
    if (membersRes.ok) setMembers((await membersRes.json()) as Member[]);
    if (supportersRes.ok) setSupporters((await supportersRes.json()) as Supporters);
    if (teamRes.ok) {
      const team = await teamRes.json() as { name?: string; avatarUrl?: string; description?: string };
      setName(team.name ?? "");
      setAvatarUrl(team.avatarUrl ?? "");
      setDescription(team.description ?? "");
    }
  }, [teamId]);

  useEffect(() => { void load(); }, [load]);

  const isOwner = overview?.ownerAccess === true;

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

          <PublisherTabs active="team" memberRole={overview?.memberRole} teamId={teamId} />

          {error ? <p className="questError" role="alert">{error}</p> : null}
          {notice ? <p className="questNotice" role="status">{notice}</p> : null}

          {overview && !isOwner ? (
            <section className="publisherCard">
              <h2>Bạn không có quyền thao tác này.</h2>
              <p className="pubHint">Thành viên trong team chỉ được đăng và quản lý truyện.</p>
            </section>
          ) : overview ? (
            <>
              <div className="pubTabs" role="tablist" aria-label="Quản lý nhóm">
                <button
                  aria-selected={activeTab === "profile"}
                  className={activeTab === "profile" ? "pubTab isActive" : "pubTab"}
                  onClick={() => setActiveTab("profile")}
                  role="tab"
                  type="button"
                >
                  Thông tin
                </button>
                <button
                  aria-selected={activeTab === "supporters"}
                  className={activeTab === "supporters" ? "pubTab isActive" : "pubTab"}
                  onClick={() => setActiveTab("supporters")}
                  role="tab"
                  type="button"
                >
                  Ủng hộ & đề cử
                </button>
              </div>

          {activeTab === "profile" ? (
          <>
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
          </>
          ) : (
            <section className="publisherCard">
              <header className="publisherCardHeader">
                <h2>Ủng hộ & đề cử ngọc</h2>
                <span>{supporters.donations.length + supporters.recommendations.length} lượt</span>
              </header>
              <div className="publisherChartRow">
                <div>
                  <h3>Donate xu</h3>
                  {supporters.donations.length === 0 ? (
                    <p className="pubHint">Chưa có lượt donate.</p>
                  ) : (
                    <ul className="teamMemberList">
                      {supporters.donations.map((row) => (
                        <li key={row.id}>
                          <div>
                            <strong>{row.displayName || row.userEmail}</strong>
                            <small>
                              {number.format(row.grossCoin)} xu · team nhận {number.format(row.teamNetCoin)} xu
                              {row.storyTitle ? ` · ${row.storyTitle}` : ""}
                            </small>
                            {row.message ? <small>{row.message}</small> : null}
                          </div>
                          <small>{time(row.createdAt)}</small>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                <div>
                  <h3>Đề cử ngọc</h3>
                  {supporters.recommendations.length === 0 ? (
                    <p className="pubHint">Chưa có lượt đề cử.</p>
                  ) : (
                    <ul className="teamMemberList">
                      {supporters.recommendations.map((row) => (
                        <li key={row.id}>
                          <div>
                            <strong>{row.displayName || row.userEmail}</strong>
                            <small>
                              {number.format(row.gemAmount)} ngọc
                              {row.storyTitle ? ` · ${row.storyTitle}` : ""}
                            </small>
                          </div>
                          <small>{time(row.createdAt)}</small>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            </section>
          )}
            </>
          ) : null}
        </div>
      </main>
    </PublicShell>
  );
}
