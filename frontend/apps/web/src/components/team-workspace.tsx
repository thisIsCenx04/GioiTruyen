"use client";

import {
  createBrowserTeamClient,
  StoryApiError,
  type Team,
  type TeamFollow,
  type TeamMembership,
} from "@gioitruyen/api-client";
import { BrandMark, StatusPill } from "@gioitruyen/ui";
import type { Route } from "next";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  type FormEvent,
  useEffect,
  useMemo,
  useState,
} from "react";

import styles from "./team-workspace.module.css";

const permissionOptions = [
  ["story:create", "Tạo bản thảo"],
  ["story:edit", "Sửa nội dung"],
  ["story:submit", "Gửi duyệt"],
  ["story:publish", "Xuất bản"],
  ["analytics:read", "Xem số liệu"],
  ["finance:request", "Gửi yêu cầu tài chính"],
] as const;

function message(error: unknown) {
  if (error instanceof StoryApiError) {
    const known: Readonly<Record<string, string>> = {
      AUTHENTICATION_REQUIRED:
        "Phiên làm việc đã hết hạn. Hãy đăng nhập lại để tiếp tục.",
      TEAM_LAST_OWNER: "Không thể gỡ chủ sở hữu cuối cùng của nhóm.",
      TEAM_MEMBER_EXISTS: "Người này đã có trong sổ thành viên.",
      TEAM_MEMBERSHIP_VERSION_CONFLICT:
        "Quyền thành viên vừa thay đổi. Tải lại rồi thử lại.",
      TEAM_OWNER_REQUIRED:
        "Chỉ chủ sở hữu đang hoạt động mới quản lý được thành viên.",
      TEAM_SLUG_TAKEN: "Đường dẫn nhóm này đã được sử dụng.",
      TEAM_TARGET_UNAVAILABLE:
        "Không thể mời tài khoản này vào lúc này.",
    };
    return (
      known[error.problem.code] ??
      error.problem.detail ??
      "Yêu cầu chưa hoàn tất. Hãy kiểm tra dữ liệu và thử lại."
    );
  }
  return "Không thể kết nối máy chủ. Hãy kiểm tra mạng và thử lại.";
}

function WorkspaceNotice({
  error,
  notice,
}: Readonly<{ error: string; notice: string }>) {
  if (error) {
    return (
      <p className={styles.error} role="alert">
        {error}
      </p>
    );
  }
  if (notice) {
    return (
      <p className={styles.notice} role="status">
        {notice}
      </p>
    );
  }
  return <span aria-live="polite" />;
}

export function TeamDirectory() {
  const teamsApi = useMemo(() => createBrowserTeamClient(), []);
  const router = useRouter();
  const [teams, setTeams] = useState<Team[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    teamsApi
      .listTeams()
      .then((items) => {
        if (active) setTeams(items);
      })
      .catch((requestError: unknown) => {
        if (active) setError(message(requestError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [teamsApi]);

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreating(true);
    setError("");
    const values = new FormData(event.currentTarget);
    try {
      const team = await teamsApi.createTeam({
        description: String(values.get("description")),
        name: String(values.get("name")),
        slug: String(values.get("slug")),
      });
      router.push(`/teams/${team.id}` as Route);
    } catch (requestError) {
      setError(message(requestError));
      setCreating(false);
    }
  }

  return (
    <main className={styles.directory}>
      <header className={styles.directoryHeader}>
        <Link href={"/" as Route}>
          <BrandMark />
        </Link>
        <div>
          <p className={styles.kicker}>Phòng biên tập</p>
          <h1>Chọn một nhóm để viết tiếp.</h1>
        </div>
        <Link className={styles.accountLink} href={"/account/sessions" as Route}>
          Tài khoản
        </Link>
      </header>

      <div className={styles.directoryGrid}>
        <section className={styles.teamIndex} aria-labelledby="team-index-title">
          <div className={styles.sectionTitle}>
            <span>Hồ sơ đang mở</span>
            <strong>{teams.length.toString().padStart(2, "0")}</strong>
          </div>
          <h2 id="team-index-title">Nhóm xuất bản</h2>
          {loading && <p className={styles.empty}>Đang mở sổ nhóm…</p>}
          {!loading && teams.length === 0 && (
            <p className={styles.empty}>
              Chưa có nhóm nào. Tạo nhóm đầu tiên ở biểu mẫu bên cạnh.
            </p>
          )}
          <ol className={styles.teamList}>
            {teams.map((team, index) => (
              <li key={team.id}>
                <Link href={`/teams/${team.id}` as Route}>
                  <span>{String(index + 1).padStart(2, "0")}</span>
                  <div>
                    <strong>{team.name}</strong>
                    <small>{team.description || "Chưa có lời giới thiệu"}</small>
                  </div>
                  <i aria-hidden="true">↗</i>
                </Link>
              </li>
            ))}
          </ol>
        </section>

        <section className={styles.createSheet} aria-labelledby="create-title">
          <p className={styles.kicker}>Mở hồ sơ mới</p>
          <h2 id="create-title">Lập nhóm xuất bản</h2>
          <p>
            Bạn sẽ trở thành chủ sở hữu đầu tiên và có thể mời cộng sự ngay
            sau khi tạo.
          </p>
          <WorkspaceNotice error={error} notice="" />
          <form onSubmit={create}>
            <label>
              Tên nhóm
              <input
                maxLength={100}
                minLength={2}
                name="name"
                placeholder="Ví dụ: Lam Dạ"
                required
              />
            </label>
            <label>
              Đường dẫn
              <span className={styles.slugField}>
                gioitruyen.vn/teams/
                <input
                  maxLength={50}
                  minLength={3}
                  name="slug"
                  pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
                  placeholder="lam-da"
                  required
                />
              </span>
            </label>
            <label>
              Lời giới thiệu
              <textarea
                maxLength={1000}
                name="description"
                placeholder="Nhóm viết gì, dành cho ai?"
                rows={4}
              />
            </label>
            <button disabled={creating} type="submit">
              {creating ? "Đang lập nhóm…" : "Lập nhóm và mở sổ"}
            </button>
          </form>
        </section>
      </div>
    </main>
  );
}

export function TeamWorkspace({ teamId }: Readonly<{ teamId: string }>) {
  const teamsApi = useMemo(() => createBrowserTeamClient(), []);
  const [team, setTeam] = useState<Team | null>(null);
  const [members, setMembers] = useState<TeamMembership[]>([]);
  const [follow, setFollow] = useState<TeamFollow | null>(null);
  const [canManage, setCanManage] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [draftName, setDraftName] = useState("");
  const [draftDescription, setDraftDescription] = useState("");

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const [teamResult, followResult] = await Promise.all([
          teamsApi.getTeam(teamId),
          teamsApi.followStatus(teamId),
        ]);
        if (!active) return;
        setTeam(teamResult);
        setDraftName(teamResult.name);
        setDraftDescription(teamResult.description);
        setFollow(followResult);
        try {
          const memberResult = await teamsApi.listMembers(teamId);
          if (!active) return;
          setMembers(memberResult);
          setCanManage(true);
        } catch (memberError) {
          if (
            memberError instanceof StoryApiError &&
            memberError.problem.status === 403
          ) {
            setCanManage(false);
          } else {
            throw memberError;
          }
        }
      } catch (requestError) {
        if (active) setError(message(requestError));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [teamId, teamsApi]);

  const activeMembers = useMemo(
    () => members.filter((member) => member.state === "ACTIVE").length,
    [members],
  );

  function begin(action: string) {
    setBusy(action);
    setError("");
    setNotice("");
  }

  async function toggleFollow() {
    begin("follow");
    try {
      const next = follow?.following
        ? await teamsApi.unfollow(teamId)
        : await teamsApi.follow(teamId);
      setFollow(next);
      setNotice(next.following ? "Đã theo dõi nhóm." : "Đã bỏ theo dõi nhóm.");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setBusy("");
    }
  }

  async function saveTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!team) return;
    begin("team");
    try {
      const updated = await teamsApi.updateTeam(teamId, {
        description: draftDescription,
        name: draftName,
        version: team.version,
      });
      setTeam(updated);
      setNotice("Đã lưu thông tin nhóm.");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setBusy("");
    }
  }

  async function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    begin("invite");
    const form = event.currentTarget;
    const values = new FormData(form);
    const permissions = permissionOptions
      .map(([permission]) => permission)
      .filter((permission) => values.get(permission) === "on");
    try {
      const member = await teamsApi.inviteMember(
        teamId,
        {
          permissions,
          userId: String(values.get("userId")),
        },
        crypto.randomUUID(),
      );
      setMembers((current) => [
        ...current.filter((item) => item.userId !== member.userId),
        member,
      ]);
      form.reset();
      setNotice("Đã ghi lời mời vào sổ thành viên.");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setBusy("");
    }
  }

  async function savePermissions(
    member: TeamMembership,
    permissions: readonly string[],
  ) {
    begin(`permission:${member.userId}`);
    try {
      const updated = await teamsApi.updateMemberPermissions(
        teamId,
        member.userId,
        member.version,
        permissions,
      );
      setMembers((current) =>
        current.map((item) =>
          item.userId === updated.userId ? updated : item,
        ),
      );
      setNotice("Đã cập nhật quyền thành viên.");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setBusy("");
    }
  }

  async function remove(member: TeamMembership) {
    begin(`remove:${member.userId}`);
    try {
      await teamsApi.removeMember(teamId, member.userId, member.version);
      setMembers((current) =>
        current.filter((item) => item.userId !== member.userId),
      );
      setNotice("Đã gỡ thành viên khỏi nhóm.");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setBusy("");
    }
  }

  if (loading) {
    return (
      <main className={styles.loading}>
        <BrandMark />
        <p>Đang mở sổ biên tập…</p>
      </main>
    );
  }

  return (
    <main className={styles.workspace}>
      <aside className={styles.spine}>
        <Link href={"/teams" as Route}>
          <BrandMark inverse />
        </Link>
        <div className={styles.folio}>
          <span>Hồ sơ nhóm</span>
          <strong>{team?.slug ?? "không tìm thấy"}</strong>
        </div>
        <nav aria-label="Mục trong không gian nhóm">
          <a href="#overview">Tổng quan</a>
          <Link href={`/teams/${teamId}/stories` as Route}>
            Bàn bản thảo
          </Link>
          <Link href={`/teams/${teamId}/analytics` as Route}>
            Chất lượng lượt đọc
          </Link>
          {canManage && <a href="#settings">Thông tin nhóm</a>}
          {canManage && <a href="#members">Sổ thành viên</a>}
          {canManage && <a href="#invite">Mời cộng sự</a>}
        </nav>
        <p className={styles.spineNote}>
          Mỗi thay đổi quyền được ghi theo phiên bản để tránh ghi đè công việc
          của người khác.
        </p>
      </aside>

      <div className={styles.workspaceBody}>
        <header className={styles.workspaceHeader} id="overview">
          <div>
            <p className={styles.kicker}>Nhóm xuất bản · {team?.state}</p>
            <h1>{team?.name ?? "Không mở được nhóm"}</h1>
            <p>{team?.description || "Nhóm chưa viết lời giới thiệu."}</p>
          </div>
          <button
            className={follow?.following ? styles.following : styles.follow}
            disabled={busy === "follow" || !team}
            onClick={toggleFollow}
            type="button"
          >
            {follow?.following ? "Đang theo dõi" : "Theo dõi nhóm"}
            <span>{follow?.followerCount ?? 0}</span>
          </button>
        </header>

        <WorkspaceNotice error={error} notice={notice} />

        <section className={styles.manifest}>
          <div>
            <span>Thành viên hoạt động</span>
            <strong>{canManage ? activeMembers : "—"}</strong>
          </div>
          <div>
            <span>Người theo dõi</span>
            <strong>{follow?.followerCount ?? 0}</strong>
          </div>
          <div>
            <span>Phiên bản hồ sơ</span>
            <strong>v{team?.version ?? 0}</strong>
          </div>
          <p>
            {canManage
              ? "Bạn đang ở chế độ chủ sở hữu."
              : "Bạn đang xem ở chế độ thành viên hoặc người theo dõi."}
          </p>
        </section>

        {canManage && team && (
          <section className={styles.ruleSection} id="settings">
            <div className={styles.sectionMarker}>
              <span>Thông tin nhóm</span>
              <strong>Biên tập hồ sơ</strong>
            </div>
            <form className={styles.settingsForm} onSubmit={saveTeam}>
              <label>
                Tên hiển thị
                <input
                  maxLength={100}
                  minLength={2}
                  onChange={(event) => setDraftName(event.target.value)}
                  required
                  value={draftName}
                />
              </label>
              <label>
                Lời giới thiệu
                <textarea
                  maxLength={1000}
                  onChange={(event) =>
                    setDraftDescription(event.target.value)
                  }
                  rows={4}
                  value={draftDescription}
                />
              </label>
              <button disabled={busy === "team"} type="submit">
                {busy === "team" ? "Đang lưu…" : "Lưu thông tin"}
              </button>
            </form>
          </section>
        )}

        {canManage && (
          <section className={styles.ruleSection} id="members">
            <div className={styles.sectionMarker}>
              <span>Sổ thành viên</span>
              <strong>{members.length} hồ sơ</strong>
            </div>
            <div className={styles.memberLedger}>
              {members.map((member) => (
                <PermissionEditor
                  busy={busy.endsWith(member.userId)}
                  key={`${member.userId}:${member.version}`}
                  member={member}
                  onRemove={() => remove(member)}
                  onSave={(permissions) =>
                    savePermissions(member, permissions)
                  }
                />
              ))}
            </div>
          </section>
        )}

        {canManage && (
          <section className={styles.inviteSection} id="invite">
            <div>
              <p className={styles.kicker}>Lời mời có hạn</p>
              <h2>Mời một cộng sự vào bàn viết</h2>
              <p>
                Chọn đúng phần việc cần thiết. Bạn có thể thay đổi hoặc thu hồi
                quyền sau khi lời mời được chấp nhận.
              </p>
            </div>
            <form onSubmit={invite}>
              <label>
                Mã người dùng
                <input
                  name="userId"
                  pattern="[0-9a-fA-F-]{36}"
                  placeholder="UUID của cộng sự"
                  required
                />
              </label>
              <fieldset>
                <legend>Quyền ban đầu</legend>
                {permissionOptions.map(([permission, label]) => (
                  <label key={permission}>
                    <input
                      defaultChecked={permission === "story:create"}
                      name={permission}
                      type="checkbox"
                    />
                    <span>{label}</span>
                  </label>
                ))}
              </fieldset>
              <button disabled={busy === "invite"} type="submit">
                {busy === "invite" ? "Đang ghi lời mời…" : "Gửi lời mời"}
              </button>
            </form>
          </section>
        )}
      </div>
    </main>
  );
}

function PermissionEditor({
  busy,
  member,
  onRemove,
  onSave,
}: Readonly<{
  busy: boolean;
  member: TeamMembership;
  onRemove: () => void;
  onSave: (permissions: readonly string[]) => void;
}>) {
  const [selected, setSelected] = useState(() => new Set(member.permissions));

  function toggle(permission: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(permission)) next.delete(permission);
      else next.add(permission);
      return next;
    });
  }

  const isOwner = member.role === "OWNER";
  return (
    <article className={styles.memberRow}>
      <div className={styles.memberIdentity}>
        <StatusPill tone={member.state === "ACTIVE" ? "active" : "attention"}>
          {member.state === "ACTIVE" ? "Đang làm việc" : "Đã mời"}
        </StatusPill>
        <strong>{member.userId}</strong>
        <small>
          {isOwner ? "Chủ sở hữu" : "Thành viên"} · phiên bản {member.version}
        </small>
      </div>
      <div className={styles.permissionGrid}>
        {isOwner ? (
          <p>Chủ sở hữu có toàn bộ quyền nhóm và không thể bị gỡ tại đây.</p>
        ) : (
          permissionOptions.map(([permission, label]) => (
            <label key={permission}>
              <input
                checked={selected.has(permission)}
                disabled={busy}
                onChange={() => toggle(permission)}
                type="checkbox"
              />
              <span>{label}</span>
            </label>
          ))
        )}
      </div>
      {!isOwner && (
        <div className={styles.memberActions}>
          <button
            disabled={busy || selected.size === 0}
            onClick={() => onSave([...selected])}
            type="button"
          >
            Lưu quyền
          </button>
          <button
            className={styles.remove}
            disabled={busy}
            onClick={onRemove}
            type="button"
          >
            Gỡ khỏi nhóm
          </button>
        </div>
      )}
    </article>
  );
}
