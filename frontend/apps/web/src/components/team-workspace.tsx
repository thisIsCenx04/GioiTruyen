"use client";

import {
  createBrowserTeamClient,
  StoryApiError,
  type Team,
  type TeamApplication,
  type TeamDashboard,
  type TeamFollow,
  type TeamMembership,
} from "@gioitruyen/api-client";
import { BrandMark, StatusPill } from "@gioitruyen/ui";
import {
  BarChart3,
  BookOpen,
  CalendarDays,
  CloudUpload,
  DollarSign,
  FileCheck2,
  PenLine,
  Send,
  Settings,
} from "lucide-react";
import type { Route } from "next";
import Link from "next/link";
import { type FormEvent, useEffect, useMemo, useState } from "react";

import styles from "./team-workspace.module.css";
import { WithdrawalWorkspace } from "./withdrawal-workspace";

const permissionOptions = [
  ["story:create", "Tạo bản thảo"],
  ["story:edit", "Sửa nội dung"],
  ["story:submit", "Gửi duyệt"],
  ["story:publish", "Xuất bản"],
  ["analytics:read", "Xem số liệu"],
  ["finance:request", "Gửi yêu cầu tài chính"],
] as const;

const numberFormatter = new Intl.NumberFormat("vi-VN");

function message(error: unknown) {
  if (error instanceof StoryApiError) {
    const known: Readonly<Record<string, string>> = {
      AUTHENTICATION_REQUIRED:
        "Phiên làm việc đã hết hạn. Hãy đăng nhập lại để tiếp tục.",
      TEAM_APPLICATION_SLUG_TAKEN:
        "Đường dẫn team này đã được dùng hoặc đang chờ duyệt.",
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
  const [teams, setTeams] = useState<Team[]>([]);
  const [applications, setApplications] = useState<TeamApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([teamsApi.listTeams(), teamsApi.applications().catch(() => [])])
      .then(([teamItems, applicationItems]) => {
        if (!active) return;
        setTeams(teamItems);
        setApplications(applicationItems);
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
    setNotice("");
    const form = event.currentTarget;
    const values = new FormData(form);
    try {
      const application = await teamsApi.createApplication({
        description: String(values.get("description") ?? ""),
        name: String(values.get("name") ?? ""),
        slug: String(values.get("slug") ?? ""),
      });
      setApplications((current) => [
        application,
        ...current.filter((item) => item.id !== application.id),
      ]);
      form.reset();
      setNotice("Đã gửi đăng ký team vào danh sách chờ admin duyệt.");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
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
          <p className={styles.kicker}>Hồ sơ reader</p>
          <h1>Tài khoản đọc truyện.</h1>
        </div>
        <Link className={styles.accountLink} href={"/account/sessions" as Route}>
          Tài khoản
        </Link>
      </header>

      <div className={styles.readerProfileShell}>
        <section className={styles.readerProfileCard}>
          <div>
            <strong>Reader</strong>
            <span>Thông tin cơ bản, phiên đăng nhập và trạng thái đăng ký team.</span>
          </div>
          <nav className={styles.profileTabs} aria-label="Tab hồ sơ reader">
            <Link href={"/account/sessions" as Route}>Phiên đăng nhập</Link>
            <a aria-current="page" href="#team-application">
              Đăng ký team
            </a>
          </nav>
        </section>
      </div>

      <div className={styles.directoryGrid} id="team-application">
        <section className={styles.teamIndex} aria-labelledby="team-index-title">
          <div className={styles.sectionTitle}>
            <span>Team đang hoạt động</span>
            <strong>{teams.length.toString().padStart(2, "0")}</strong>
          </div>
          <h2 id="team-index-title">Nhóm xuất bản</h2>
          {loading && <p className={styles.empty}>Đang mở sổ nhóm...</p>}
          {!loading && teams.length === 0 && (
            <p className={styles.empty}>
              Chưa có nhóm nào được duyệt. Gửi hồ sơ đăng ký ở biểu mẫu bên cạnh.
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

        <section
          className={styles.createSheet}
          id="ads-booking"
          aria-labelledby="create-title"
        >
          <p className={styles.kicker}>Tab đăng ký team</p>
          <h2 id="create-title">Gửi hồ sơ chờ duyệt</h2>
          <p>
            Reader chỉ gửi đăng ký tại đây. Hồ sơ sẽ vào danh sách xem xét, admin
            duyệt xong mới mở quyền xuất bản.
          </p>
          <WorkspaceNotice error={error} notice={notice} />
          <form onSubmit={create}>
            <label>
              Tên team
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
                placeholder="Team viết gì, lịch đăng thế nào?"
                rows={4}
              />
            </label>
            <button disabled={creating} type="submit">
              {creating ? "Đang gửi hồ sơ..." : "Gửi đăng ký chờ duyệt"}
            </button>
          </form>
          {applications.length > 0 && (
            <div className={styles.pendingReviewList}>
              <strong>Đang chờ admin duyệt</strong>
              {applications.map((application) => (
                <article key={application.id}>
                  <span>{application.name}</span>
                  <small>
                    /{application.slug} · gửi{" "}
                    {new Date(application.submittedAt).toLocaleDateString("vi-VN")}
                  </small>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}

export function TeamWorkspace({ teamId }: Readonly<{ teamId: string }>) {
  const teamsApi = useMemo(() => createBrowserTeamClient(), []);
  const [team, setTeam] = useState<Team | null>(null);
  const [dashboard, setDashboard] = useState<TeamDashboard | null>(null);
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
        const [teamResult, followResult, dashboardResult] = await Promise.all([
          teamsApi.getTeam(teamId),
          teamsApi.followStatus(teamId),
          teamsApi.dashboard(teamId),
        ]);
        if (!active) return;
        setTeam(teamResult);
        setDashboard(dashboardResult);
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
        <p>Đang mở hồ sơ team...</p>
      </main>
    );
  }

  const chartItems = dashboard
    ? [
        ["Lượt xem", dashboard.viewCount, "views"],
        ["Bán lẻ", dashboard.saleXu, "sale"],
        ["Bán combo", dashboard.comboSaleXu, "combo"],
        ["Nhận donate", dashboard.donationXu, "donation"],
        ["Event", dashboard.eventXu, "event"],
      ] as const
    : [];
  const chartMax = Math.max(1, ...chartItems.map(([, value]) => value));

  return (
    <main className={styles.workspace}>
      <aside className={styles.spine}>
        <Link href={"/teams" as Route}>
          <BrandMark inverse />
        </Link>
        <div className={styles.folio}>
          <span>Hồ sơ team</span>
          <strong>{team?.slug ?? "không tìm thấy"}</strong>
        </div>
        <nav aria-label="Mục trong không gian team">
          <a href="#overview">Tổng quan</a>
          <a href="#dashboard">Thống kê</a>
          <Link href={`/teams/${teamId}/stories` as Route}>D.S.Chương</Link>
          <Link href={`/teams/${teamId}/analytics` as Route}>Nhật ký</Link>
          <a href="#withdrawals">Yêu cầu duyệt</a>
          <a href="#exclusive">Ký độc quyền</a>
          {canManage && <a href="#settings">Cài đặt & Tiện ích</a>}
          {canManage && <a href="#members">Sổ thành viên</a>}
        </nav>
        <p className={styles.spineNote}>
          Dashboard này dành cho team đăng truyện. Reader thường chỉ có hồ sơ
          cơ bản và tab đăng ký team.
        </p>
      </aside>

      <div className={styles.workspaceBody}>
        <header className={styles.teamSummary} id="overview">
          <div className={styles.teamAvatar}>
            <span>{team?.name.slice(0, 1) ?? "G"}</span>
            <button type="button">
              <CloudUpload aria-hidden="true" />
              Upload
            </button>
          </div>
          <div className={styles.statusLedger}>
            <h1>{team?.name ?? "Không mở được team"}</h1>
            <p>{team?.description || "Team chưa viết lời giới thiệu."}</p>
            <dl>
              <div>
                <dt>Xuất bản:</dt>
                <dd>{dashboard?.publishStatus ?? "Chưa xác định"}</dd>
              </div>
              <div>
                <dt>Tình trạng:</dt>
                <dd>{dashboard?.completionStatus ?? team?.state ?? "Chưa xác định"}</dd>
              </div>
              <div>
                <dt>Độc quyền:</dt>
                <dd>{dashboard?.exclusiveStatus ?? "Chưa ký"}</dd>
              </div>
              <div>
                <dt>Bản quyền:</dt>
                <dd>{dashboard?.copyrightStatus ?? "Chưa xác minh"}</dd>
              </div>
              <div>
                <dt>Doanh thu:</dt>
                <dd>{numberFormatter.format(dashboard?.revenueXu ?? 0)} XU</dd>
              </div>
              <div>
                <dt>Chương cuối:</dt>
                <dd>{dashboard?.latestChapter ?? "Chưa xác định"}</dd>
              </div>
              <div>
                <dt>Vé hỗ trợ:</dt>
                <dd>{dashboard?.supporters ?? "Danh sách"}</dd>
              </div>
              <div>
                <dt>Link truyện:</dt>
                <dd>{dashboard?.storyUrl ?? "Chưa có"}</dd>
              </div>
            </dl>
          </div>
          <button
            className={follow?.following ? styles.following : styles.follow}
            disabled={busy === "follow" || !team}
            onClick={toggleFollow}
            type="button"
          >
            {follow?.following ? "Đang theo dõi" : "Theo dõi team"}
            <span>{follow?.followerCount ?? 0}</span>
          </button>
        </header>

        <nav className={styles.teamDashboardTabs} aria-label="Tab team dashboard">
          <Link href={`/teams/${teamId}/stories` as Route}>
            <BookOpen aria-hidden="true" />
            D.S.Chương
          </Link>
          <a aria-current="page" href="#dashboard">
            <BarChart3 aria-hidden="true" />
            Thống kê
          </a>
          <Link href={`/teams/${teamId}/analytics` as Route}>
            <CalendarDays aria-hidden="true" />
            Nhật ký
          </Link>
          <a href="#withdrawals">
            <Send aria-hidden="true" />
            Yêu cầu duyệt
          </a>
          <a href="#settings">
            <Settings aria-hidden="true" />
            Cài đặt & Tiện ích
          </a>
          <a href="#exclusive">
            <PenLine aria-hidden="true" />
            Ký độc quyền
          </a>
        </nav>

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
              ? "Bạn đang ở chế độ chủ sở hữu team."
              : "Bạn đang xem ở chế độ thành viên hoặc người theo dõi."}
          </p>
        </section>

        <section className={styles.miniDashboard} id="dashboard">
          <header>
            <DollarSign aria-hidden="true" />
            <div>
              <h2>Doanh thu</h2>
              <strong>{numberFormatter.format(dashboard?.revenueXu ?? 0)} XU</strong>
            </div>
          </header>
          <div className={styles.revenueChart}>
            {chartItems.map(([label, value, tone]) => (
              <div key={label}>
                <span>{numberFormatter.format(value)}</span>
                <i
                  data-tone={tone}
                  style={{ height: `${Math.max(8, (value / chartMax) * 100)}%` }}
                />
                <small>{label}</small>
              </div>
            ))}
          </div>
        </section>

        <section className={styles.exclusiveCompare} id="exclusive">
          <header>
            <FileCheck2 aria-hidden="true" />
            <div>
              <h2>Bảng so sánh</h2>
              <p>Hiện tại: {dashboard?.exclusiveStatus ?? "Chưa ký"}</p>
            </div>
          </header>
          <table>
            <thead>
              <tr>
                <th>Tiêu chí</th>
                <th>Không đăng độc quyền</th>
                <th>Truyện đăng độc quyền</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Tỷ lệ chia sẻ doanh thu</td>
                <td>70%</td>
                <td>90%</td>
              </tr>
              <tr>
                <td>Đội ngũ marketing hỗ trợ PR</td>
                <td>Không</td>
                <td>Có</td>
              </tr>
              <tr>
                <td>Tham gia các sự kiện</td>
                <td>Không</td>
                <td>Có</td>
              </tr>
              <tr>
                <td>Cơ hội hiển thị với người dùng</td>
                <td>Bình thường</td>
                <td>Cao hơn</td>
              </tr>
            </tbody>
          </table>
          <div className={styles.exclusiveTerms}>
            <h3>Điều khoản ký đăng độc quyền</h3>
            <p>Truyện cần có tối thiểu 5 chương VIP hoặc tổng giá trị từ 200 XU.</p>
            <p>Tất cả chương VIP của truyện đã ký phải đăng độc quyền tại Giới Truyện.</p>
            <p>Sau khi ký, team không thể tự chuyển ngược lại sang không độc quyền.</p>
            <p>Nếu vi phạm và gây tổn thất, team có thể bị trừ phần doanh thu theo quy định.</p>
          </div>
          <button type="button">Ký ngay</button>
        </section>

        <WithdrawalWorkspace teamId={teamId} />

        {canManage && team && (
          <section className={styles.ruleSection} id="settings">
            <div className={styles.sectionMarker}>
              <span>Thông tin team</span>
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
                  onChange={(event) => setDraftDescription(event.target.value)}
                  rows={4}
                  value={draftDescription}
                />
              </label>
              <button disabled={busy === "team"} type="submit">
                {busy === "team" ? "Đang lưu..." : "Lưu thông tin"}
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
                  onSave={(permissions) => savePermissions(member, permissions)}
                />
              ))}
            </div>
          </section>
        )}

        {canManage && (
          <section className={styles.inviteSection} id="invite">
            <div>
              <p className={styles.kicker}>Lời mời có hạn</p>
              <h2>Mời cộng sự vào bàn viết</h2>
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
                {busy === "invite" ? "Đang ghi lời mời..." : "Gửi lời mời"}
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
  const isOwner = member.role === "OWNER";

  function toggle(permission: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(permission)) next.delete(permission);
      else next.add(permission);
      return next;
    });
  }

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
          <p>Chủ sở hữu có toàn bộ quyền team và không thể bị gỡ tại đây.</p>
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
            Gỡ khỏi team
          </button>
        </div>
      )}
    </article>
  );
}
