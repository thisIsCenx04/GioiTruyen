"use client";

import {
  createBrowserTeamClient,
  StoryApiError,
  type Team,
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
import { Link } from "react-router-dom";
import { type FormEvent, useEffect, useMemo, useState } from "react";

import styles from "./team-workspace.module.css";
import { WithdrawalWorkspace } from "./withdrawal-workspace";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

function safeUUID(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

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
        "Đường dẫn nhóm này đã được dùng hoặc đang chờ duyệt.",
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
  const teamsApi = useMemo(() => createBrowserTeamClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }), []);
  const [teams, setTeams] = useState<Team[]>([]);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [activeRankTab, setActiveRankTab] = useState<"views" | "likes" | "stories">("views");
  const [userRole, setUserRole] = useState("");
  const [userTeamId, setUserTeamId] = useState("");

  useEffect(() => {
    let active = true;
    const role = localStorage.getItem("gioitruyen_user_role") || "";
    const teamId = localStorage.getItem("gioitruyen_team_id") || "";
    setUserRole(role);
    setUserTeamId(teamId);

    teamsApi
      .listTeams()
      .then((teamItems) => {
        if (!active) return;
        setTeams(teamItems);
      })
      .catch((requestError: unknown) => {
        if (active) setError(message(requestError));
      });
    return () => {
      active = false;
    };
  }, [teamsApi]);

  const activeTeams = teams.filter((team) => team.state === "ACTIVE");

  if (userRole === "TEAM" || userRole === "ADMIN" || userTeamId) {
    return <TeamWorkspace teamId={userTeamId || (activeTeams[0]?.id ?? "1")} />;
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreating(true);
    setError("");
    setNotice("");
    const form = event.currentTarget;
    const values = new FormData(form);
    const sdt = String(values.get("sdt") ?? "").trim();
    const linkFb = String(values.get("link_fb") ?? "").trim();
    const note = String(values.get("note") ?? "").trim();

    try {
      await teamsApi.createApplication({
        description: `FB: ${linkFb} | Note: ${note}`,
        name: `SĐT ${sdt}`,
        slug: `team-${sdt.replace(/\D/g, "") || Date.now()}`,
      });
      form.reset();
      setNotice("Đã gửi đăng ký thành công! Admin sẽ xem xét và liên hệ lại với bạn qua SĐT/FB trong 1-3 ngày.");
    } catch (requestError) {
      setError(message(requestError));
    } finally {
      setCreating(false);
    }
  }

  const topTeams = activeTeams.slice(0, 5).map((team, index) => ({
    avatarTone: ["indigo", "dark", "gold", "cyan", "blue"][index % 5],
    name: team.name,
    rank: index + 1,
    slug: team.slug,
    state: team.state,
  }));

  return (
    <main className="teamsRegistrationRedesign">
      <section className="teamsFullHeroBanner">
        <div className="teamsHeroBg" />
        <div className="teamsHeroContainer">
          <section className="teamsRuleNotice" aria-label="Quy định quan trọng">
            <strong>Quy định chung quan trọng</strong>
            <p>
              Chỉ gửi hồ sơ khi bạn có quyền đăng tải hoặc được ủy quyền hợp pháp với
              nội dung truyện. Hồ sơ vi phạm bản quyền, mạo danh hoặc đăng nội dung
              trái quy định sẽ bị từ chối và có thể khóa quyền đăng ký.
            </p>
          </section>

          <div className="teamsFormCard teamsFormOverlay">
            <header className="formHeader">
              <h1>Đăng ký nhóm xuất bản</h1>
              <p>Gửi thông tin liên hệ để Giới Truyện xét duyệt nhóm đăng truyện.</p>
            </header>

            <WorkspaceNotice error={error} notice={notice} />

            <form onSubmit={create} className="teamsFormStack">
              <div className="teamsFieldGroup">
                <label htmlFor="field-sdt">Số điện thoại *</label>
                <input
                  id="field-sdt"
                  type="tel"
                  name="sdt"
                  className="teamsInput"
                  placeholder="Ví dụ: 0912345678"
                  required
                />
              </div>

              <div className="teamsFieldGroup">
                <label htmlFor="field-fb">Link Facebook hoặc Fanpage FB *</label>
                <input
                  id="field-fb"
                  type="url"
                  name="link_fb"
                  className="teamsInput"
                  placeholder="https://facebook.com/your.fanpage"
                  required
                />
              </div>

              <div className="teamsFieldGroup">
                <label htmlFor="field-note">Ghi chú</label>
                <textarea
                  id="field-note"
                  name="note"
                  className="teamsTextarea"
                  rows={4}
                  placeholder="Giới thiệu ngắn về nhóm, tác phẩm dự kiến hoặc câu hỏi thêm..."
                />
              </div>

              <div className="teamsAlertBox">
                <p>
                  Admin sẽ xem xét hồ sơ và liên hệ lại trong 1-3 ngày làm việc.
                </p>
              </div>

              <button disabled={creating} type="submit" className="teamsSubmitBtn">
                {creating ? "Đang gửi hồ sơ..." : "Gửi hồ sơ đăng ký"}
              </button>
            </form>
          </div>

          <div className="teamsHeroContent">
            <h1>Nhóm xuất bản & Tác giả</h1>
            <p>
              Cùng Giới Truyện xuất bản tác phẩm rõ bản quyền, quản lý nội dung
              chuyên nghiệp và xây dựng cộng đồng độc giả lâu dài.
            </p>
            <ul className="teamsHeroRules">
              <li>Nội dung có nguồn gốc rõ ràng.</li>
              <li>Không mạo danh tác giả, dịch giả hoặc nhóm khác.</li>
              <li>Tuân thủ quy định đăng truyện của nền tảng.</li>
            </ul>
          </div>
        </div>
      </section>

      <div className="teamsBodyWrapper">
        {/* ── SECTION 1.5: Danh sách các Team xuất bản nổi bật dạng Cards ── */}
        <section className="featuredTeamsSection" style={{ padding: "2.5rem 0 1.5rem" }}>
          <div className="sectionTitleCenter" style={{ textAlign: "center", marginBottom: "2rem" }}>
            <span className="shieldIcon" style={{ fontSize: "2rem" }}>⭐</span>
            <h2 style={{ fontSize: "1.6rem", color: "#0f172a", margin: "0.5rem 0 0.2rem" }}>Danh sách các Nhóm Xuất Bản Nổi Bật</h2>
            <p style={{ color: "#64748b", fontSize: "0.85rem" }}>Khám phá các nhóm dịch & sáng tác truyện tài năng đang hoạt động tại Giới Truyện</p>
          </div>

          <div className="featuredTeamsGrid" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "1.25rem" }}>
            {activeTeams.length > 0 ? activeTeams.map((team, idx) => (
              <div key={team.id || team.slug} className="featuredTeamCard" style={{ background: "white", borderRadius: "1rem", border: "1px solid #e2e8f0", padding: "1.25rem", boxShadow: "0 4px 16px rgba(15, 95, 255, 0.05)", transition: "transform 0.2s ease, box-shadow 0.2s ease" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "0.85rem", marginBottom: "0.85rem" }}>
                  <div className={`teamAvatarSquare avatarTone-${["indigo", "gold", "cyan", "blue"][idx % 4]}`} style={{ width: "3.2rem", height: "3.2rem", borderRadius: "0.75rem", display: "grid", placeItems: "center", color: "white", fontWeight: 800, fontSize: "1.2rem" }}>
                    {team.name.charAt(0)}
                  </div>
                  <div>
                    <h3 style={{ fontSize: "1rem", margin: 0, color: "#0f172a" }}>
                      <Link to={`/teams/${team.slug}`} style={{ textDecoration: "none", color: "inherit" }}>{team.name}</Link>
                      <span style={{ color: "#0f5fff", marginLeft: "0.35rem" }}>✔</span>
                    </h3>
                    <span style={{ fontSize: "0.7rem", color: "#16a34a", fontWeight: 700 }}>● Đang hoạt động</span>
                  </div>
                </div>
                <p style={{ fontSize: "0.78rem", color: "#475569", minHeight: "2.2rem", margin: "0 0 1rem" }}>
                  {team.description || "Nhóm dịch và sáng tác nội dung chuyên nghiệp tại Giới Truyện."}
                </p>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", paddingTop: "0.75rem", borderTop: "1px solid #f1f5f9" }}>
                  <span style={{ fontSize: "0.72rem", color: "#64748b" }}>🔥 Nổi bật</span>
                  <Link to={`/teams/${team.slug}`} className="btnLinkBlue" style={{ fontSize: "0.75rem", fontWeight: 800, textDecoration: "none", color: "#0f5fff" }}>
                    Xem hồ sơ nhóm →
                  </Link>
                </div>
              </div>
            )) : (
              <p className="emptyCatalog" style={{ gridColumn: "1 / -1", textAlign: "center", padding: "2rem" }}>Đang tải danh sách các nhóm xuất bản...</p>
            )}
          </div>
        </section>

        {/* ── SECTION 2: Quy định & điều kiện ── */}
        <section className="termsSection">
        <div className="sectionTitleCenter">
          <span className="shieldIcon">🛡️</span>
          <h2>Quy định & điều kiện</h2>
        </div>

        <div className="termsCardsGrid">
          <div className="termCard">
            <div className="termIconCircle termBlue">👜</div>
            <h3>Nội dung hợp lệ</h3>
            <p>Truyện không vi phạm pháp luật, không chứa nội dung phản cảm, bạo lực, đồi trụy.</p>
          </div>

          <div className="termCard">
            <div className="termIconCircle termGreen">📜</div>
            <h3>Bản quyền rõ ràng</h3>
            <p>Nhóm phải có quyền sở hữu hoặc được ủy quyền hợp pháp đối với nội dung đăng tải.</p>
          </div>

          <div className="termCard">
            <div className="termIconCircle termPurple">🔄</div>
            <h3>Cập nhật thường xuyên</h3>
            <p>Cam kết cập nhật truyện đều đặn, đảm bảo chất lượng nội dung cho độc giả.</p>
          </div>

          <div className="termCard">
            <div className="termIconCircle termOrange">⚖️</div>
            <h3>Tuân thủ quy định</h3>
            <p>Chấp hành mọi quy định của Giới Truyện và quyết định của Ban quản trị.</p>
          </div>
        </div>

        <div className="termsCenterBtn">
          <Link to={"/publishing-rules" as string} className="btnOutline">
            Xem chi tiết quy định &gt;
          </Link>
        </div>
      </section>

      {/* ── SECTION 3: Bảng xếp hạng nhóm ── */}
      <section className="teamsRankingSection">
        <div className="rankingHeaderFlex">
          <h2>Bảng xếp hạng nhóm</h2>
          <Link to={"/rankings" as string} className="btnLinkBlue">
            Xem bảng xếp hạng đầy đủ
          </Link>
        </div>

        <div className="rankingTabsFilter">
          <button
            className={activeRankTab === "views" ? "active" : ""}
            onClick={() => setActiveRankTab("views")}
            type="button"
          >
            Theo lượt xem
          </button>
          <button
            className={activeRankTab === "likes" ? "active" : ""}
            onClick={() => setActiveRankTab("likes")}
            type="button"
          >
            Theo lượt thích
          </button>
          <button
            className={activeRankTab === "stories" ? "active" : ""}
            onClick={() => setActiveRankTab("stories")}
            type="button"
          >
            Theo số truyện
          </button>
        </div>

        <div className="topTeamsGrid">
          {topTeams.length > 0 ? topTeams.map((t) => (
            <div key={t.rank} className="topTeamCard">
              <div className={`rankHexBadge rankHex${t.rank <= 3 ? t.rank : "Normal"}`}>
                {t.rank}
              </div>

              <div className={`teamAvatarSquare avatarTone-${t.avatarTone}`}>
                <span>{t.name.charAt(0)}</span>
              </div>

              <div className="teamInfoMain">
                <h3>
                  <Link to={`/teams/${t.slug}` as string}>{t.name}</Link>
                  <span className="verifiedCheck">✔</span>
                </h3>
                <div className="teamStatsRow">
                  <span>Trạng thái: {t.state === "ACTIVE" ? "Đang hoạt động" : t.state}</span>
                </div>
              </div>
            </div>
          )) : (
            <p className="emptyCatalog">Chưa có nhóm xuất bản đang hoạt động.</p>
          )}
        </div>
      </section>

      {/* ── SECTION 4: Về Giới Truyện ── */}
      <section className="aboutGioiTruyenCard">
        <div className="aboutLeftContent">
          <h2>Về Giới Truyện</h2>
          <p>
            Giới Truyện là nền tảng đọc truyện online miễn phí, nơi kết nối độc giả với những bộ truyện chất lượng và các nhóm dịch, tác giả tài năng.
            <br /><br />
            Chúng tôi luôn đồng hành và hỗ trợ các nhóm sáng tạo để mang đến những câu chuyện hay nhất cho cộng đồng.
          </p>

          <Link to={"/about" as string} className="aboutBtn">
            Tìm hiểu thêm về Giới Truyện
          </Link>
        </div>

        <div className="aboutRightGraphic">
          <div className="pedestal3D">
            <span className="bigG">G</span>
          </div>
        </div>
      </section>
      </div>
    </main>
  );
}


export function TeamWorkspace({ teamId }: Readonly<{ teamId: string }>) {
  const teamsApi = useMemo(() => createBrowserTeamClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }), []);
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
        safeUUID(),
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
        <p>Đang mở hồ sơ nhóm...</p>
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
        <Link to={"/teams" as string}>
          <BrandMark inverse />
        </Link>
        <div className={styles.folio}>
          <span>Hồ sơ nhóm</span>
          <strong>{team?.slug ?? "không tìm thấy"}</strong>
        </div>
        <nav aria-label="Mục trong không gian nhóm">
          <a href="#overview">Tổng quan</a>
          <a href="#dashboard">Thống kê</a>
          <Link to={`/teams/${teamId}/stories` as string}>D.S.Chương</Link>
          <Link to={`/teams/${teamId}/analytics` as string}>Nhật ký</Link>
          <a href="#withdrawals">Yêu cầu duyệt</a>
          <a href="#exclusive">Ký độc quyền</a>
          {canManage && <a href="#settings">Cài đặt & Tiện ích</a>}
          {canManage && <a href="#members">Sổ thành viên</a>}
        </nav>
        <p className={styles.spineNote}>
          Khu thống kê này dành cho nhóm đăng truyện. Độc giả thông thường chỉ có
          hồ sơ cá nhân và mục đăng ký nhóm xuất bản.
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
            <h1>{team?.name ?? "Không mở được hồ sơ nhóm"}</h1>
            <p>{team?.description || "Nhóm chưa viết lời giới thiệu."}</p>
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
            {follow?.following ? "Đang theo dõi" : "Theo dõi nhóm"}
            <span>{follow?.followerCount ?? 0}</span>
          </button>
        </header>

        <nav className={styles.teamDashboardTabs} aria-label="Các mục quản lý nhóm">
          <Link to={`/teams/${teamId}/stories` as string}>
            <BookOpen aria-hidden="true" />
            D.S.Chương
          </Link>
          <a aria-current="page" href="#dashboard">
            <BarChart3 aria-hidden="true" />
            Thống kê
          </a>
          <Link to={`/teams/${teamId}/analytics` as string}>
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
              ? "Bạn đang ở chế độ chủ sở hữu nhóm."
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
            <p>Sau khi ký, nhóm không thể tự chuyển ngược lại sang không độc quyền.</p>
            <p>Nếu vi phạm và gây tổn thất, nhóm có thể bị trừ phần doanh thu theo quy định.</p>
          </div>
          <button type="button">Ký ngay</button>
        </section>

        <WithdrawalWorkspace teamId={teamId} />

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
          <p>Chủ sở hữu có toàn bộ quyền quản lý nhóm và không thể bị gỡ tại đây.</p>
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
