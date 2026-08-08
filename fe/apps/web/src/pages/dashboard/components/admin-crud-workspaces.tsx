"use client";

import { type FormEvent, type ReactNode, useEffect, useState } from "react";

import {
  type AdminCashFlowRow,
  type AdminCategoryRow,
  type AdminStoryRow,
  type AdminTeamRow,
  type AdminUserRow,
  loadAdminCashFlow,
  loadAdminCategories,
  loadAdminStories,
  loadAdminTeams,
  loadAdminUsers,
} from "../admin-data";

type DrawerMode = "archive" | "create" | "edit" | "reverse";
type SortOrder = "asc" | "desc";

interface SortState<K extends string> {
  key: K;
  order: SortOrder;
}

const numberFormatter = new Intl.NumberFormat("vi-VN");

function value(form: FormData, name: string) {
  return String(form.get(name) ?? "").trim();
}

function roles(form: FormData) {
  return value(form, "roles")
    .split(",")
    .map((role) => role.trim().toUpperCase())
    .filter(Boolean);
}

function translateStatus(status: string) {
  return (
    {
      ACTIVE: "Đang hoạt động",
      ARCHIVED: "Đã lưu trữ",
      COMPLETED: "Đã hoàn thành",
      DRAFT: "Bản nháp",
      ONGOING: "Đang ra chương",
      PENDING_REVIEW: "Chờ xét duyệt",
      PUBLISHED: "Đã xuất bản",
      SUSPENDED: "Tạm khóa",
    }[status] ?? status
  );
}

async function adminMutation(path: string, method: "DELETE" | "POST" | "PUT", body?: unknown) {
  const token = typeof window !== "undefined"
    ? localStorage.getItem("access_token") || (document.cookie.match(/(?:^|; )access_token=([^;]*)/)?.[1] ? decodeURIComponent(document.cookie.match(/(?:^|; )access_token=([^;]*)/)![1]) : null)
    : null;

  const headers: Record<string, string> = {
    Accept: "application/json",
  };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`/api/v1/admin/${path}`, {
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    credentials: "same-origin",
    headers,
    method,
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { title?: string } | null;
    throw new Error(problem?.title ?? `Không thể lưu thay đổi (${response.status}).`);
  }
}

function useSortableList<T, K extends string & keyof T>(
  list: T[],
  initialKey?: K,
  initialOrder: SortOrder = "asc"
) {
  const [sortState, setSortState] = useState<SortState<K> | null>(
    initialKey ? { key: initialKey, order: initialOrder } : null
  );

  const toggleSort = (key: K) => {
    if (sortState && sortState.key === key) {
      if (sortState.order === "asc") {
        setSortState({ key, order: "desc" });
      } else {
        setSortState(null);
      }
    } else {
      setSortState({ key, order: "asc" });
    }
  };

  const sortedList = [...list].sort((a, b) => {
    if (!sortState) return 0;
    const { key, order } = sortState;
    const valA = a[key as keyof T];
    const valB = b[key as keyof T];

    if (valA === valB) return 0;
    if (valA === null || valA === undefined) return 1;
    if (valB === null || valB === undefined) return -1;

    let comp = 0;
    if (typeof valA === "number" && typeof valB === "number") {
      comp = valA - valB;
    } else if (typeof valA === "boolean" && typeof valB === "boolean") {
      comp = valA === valB ? 0 : valA ? -1 : 1;
    } else {
      comp = String(valA).localeCompare(String(valB), "vi", { sensitivity: "base" });
    }

    return order === "asc" ? comp : -comp;
  });

  return { sortedList, sortState, toggleSort };
}

function SortToolbar<K extends string>({
  columns,
  sortState,
  onSort,
}: Readonly<{
  columns: Array<{ key: K; label: string }>;
  sortState: SortState<K> | null;
  onSort: (key: K) => void;
}>) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "0.5rem",
        flexWrap: "wrap",
        padding: "0.5rem 0.75rem",
        background: "#ffffff",
        border: "1px solid #dfeaf6",
        borderRadius: "10px",
        marginBottom: "0.75rem",
        boxShadow: "0 0.5rem 1.2rem rgba(24, 47, 100, 0.04)",
      }}
    >
      <span
        style={{
          fontSize: "0.8rem",
          fontWeight: 700,
          color: "#64748b",
          textTransform: "uppercase",
          letterSpacing: "0.05em",
          marginRight: "0.3rem",
          display: "inline-flex",
          alignItems: "center",
          gap: "0.4rem",
        }}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="m3 16 4 4 4-4" />
          <path d="M7 20V4" />
          <path d="m21 8-4-4-4 4" />
          <path d="M17 4v16" />
        </svg>
        Sắp xếp theo cột:
      </span>
      {columns.map((col) => {
        const isActive = sortState?.key === col.key;
        const order = isActive ? sortState.order : null;
        return (
          <button
            key={col.key}
            type="button"
            onClick={() => onSort(col.key)}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "0.4rem",
              padding: "0.35rem 0.75rem",
              borderRadius: "6px",
              fontSize: "0.825rem",
              fontWeight: isActive ? 700 : 500,
              background: isActive
                ? order === "asc"
                  ? "#eef8ff"
                  : "#f5f3ff"
                : "rgba(241, 245, 249, 0.6)",
              border: isActive
                ? order === "asc"
                  ? "1px solid #0f6bff"
                  : "1px solid #9333ea"
                : "1px solid #cbd5e1",
              color: isActive
                ? order === "asc"
                  ? "#0f6bff"
                  : "#9333ea"
                : "#475569",
              cursor: "pointer",
              transition: "all 0.15s ease",
            }}
          >
            <span>{col.label}</span>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 800,
                color: isActive ? (order === "asc" ? "#0f6bff" : "#9333ea") : "#94a3b8",
              }}
            >
              {order === "asc" ? "▲ Tăng" : order === "desc" ? "▼ Giảm" : "⇅"}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function Drawer({
  children,
  close,
  description,
  title,
}: Readonly<{
  children: ReactNode;
  close: () => void;
  description: string;
  title: string;
}>) {
  return (
    <div className="crudDrawerLayer" role="presentation">
      <button aria-label="Đóng bảng thao tác" className="crudDrawerBackdrop" onClick={close} type="button" />
      <aside aria-modal="true" className="crudDrawer" role="dialog">
        <header>
          <div>
            <p>Quản trị nội dung</p>
            <h2>{title}</h2>
            <span>{description}</span>
          </div>
          <button aria-label="Đóng" className="drawerClose" onClick={close} type="button">×</button>
        </header>
        {children}
      </aside>
    </div>
  );
}

function Field({ children, label }: Readonly<{ children: ReactNode; label: string }>) {
  return <label className="drawerField"><span>{label}</span>{children}</label>;
}

function FormActions({ busy, close, submitLabel }: Readonly<{
  busy: boolean;
  close: () => void;
  submitLabel: string;
}>) {
  return (
    <footer className="drawerActions">
      <button className="secondaryButton" disabled={busy} onClick={close} type="button">Hủy</button>
      <button disabled={busy} type="submit">{busy ? "Đang lưu..." : submitLabel}</button>
    </footer>
  );
}

function WorkspaceHeader({ action, eyebrow, title }: Readonly<{
  action: () => void;
  eyebrow: string;
  title: string;
}>) {
  return (
    <header className="adminTopbar">
      <div><p>{eyebrow}</p><h1>{title}</h1></div>
      <button onClick={action} type="button">Tạo mới</button>
    </header>
  );
}

function MutationNotice({ error }: Readonly<{ error: string }>) {
  return error ? <p className="drawerError" role="alert">{error}</p> : null;
}

export function StoryCrudWorkspace({
  categories: initialCategories,
  stories: initialStories,
  teams: initialTeams,
}: Readonly<{
  categories: AdminCategoryRow[];
  stories: AdminStoryRow[];
  teams: AdminTeamRow[];
}>) {
  const [stories, setStories] = useState<AdminStoryRow[]>(initialStories);
  const [categories, setCategories] = useState<AdminCategoryRow[]>(initialCategories);
  const [teams, setTeams] = useState<AdminTeamRow[]>(initialTeams);
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; story?: AdminStoryRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.story;

  const refreshData = async () => {
    try {
      const [cRes, sRes, tRes] = await Promise.all([
        loadAdminCategories(),
        loadAdminStories(),
        loadAdminTeams(),
      ]);
      setCategories(cRes);
      setStories(sRes);
      setTeams(tRes);
    } catch {
      // Ignore
    }
  };

  useEffect(() => {
    void refreshData();
  }, []);

  const { sortedList, sortState, toggleSort } = useSortableList<AdminStoryRow, keyof AdminStoryRow>(stories, "updatedAt", "desc");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!drawer) return;
    setBusy(true);
    setError("");
    try {
      if (drawer.mode === "archive" && selected) {
        await adminMutation(`content/stories/${selected.id}`, "DELETE");
      } else {
        const form = new FormData(event.currentTarget);
        const payload = {
          authorName: value(form, "authorName"),
          categoryId: value(form, "categoryId"),
          completionStatus: value(form, "completionStatus"),
          slug: value(form, "slug"),
          synopsis: value(form, "synopsis"),
          teamId: value(form, "teamId"),
          title: value(form, "title"),
          workflowStatus: value(form, "workflowStatus"),
        };
        await adminMutation(
          selected ? `content/stories/${selected.id}` : "content/stories",
          selected ? "PUT" : "POST",
          payload,
        );
      }
      setDrawer(null);
      await refreshData();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi.");
    } finally {
      setBusy(false);
    }
  }

  const columns: Array<{ key: keyof AdminStoryRow; label: string }> = [
    { key: "title", label: "Tên truyện" },
    { key: "authorName", label: "Tác giả" },
    { key: "teamName", label: "Nhóm dịch" },
    { key: "categoryName", label: "Thể loại" },
    { key: "workflowStatus", label: "Trạng thái" },
    { key: "updatedAt", label: "Cập nhật" },
  ];

  return (
    <>
      <WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Nội dung xuất bản" title="Quản lý truyện" />
      <SortToolbar columns={columns} onSort={toggleSort} sortState={sortState} />
      <section className="adminCrudPanel">
        {sortedList.length === 0 ? <p className="adminEmptyState">Chưa có truyện trong thư viện.</p> : sortedList.map((story) => (
          <article key={story.id}>
            <div className="adminCrudDetails">
              <strong>{story.title}</strong>
              <small>{story.teamName} · {story.authorName} · {story.categoryName || "Chưa phân loại"}</small>
            </div>
            <span>{translateStatus(story.workflowStatus)}</span>
            <div className="adminCrudActions">
              <button onClick={() => setDrawer({ mode: "edit", story })} type="button">Chỉnh sửa</button>
              <button onClick={() => setDrawer({ mode: "archive", story })} type="button">Ngừng hiển thị</button>
            </div>
          </article>
        ))}
      </section>
      {drawer && (
        <Drawer close={() => setDrawer(null)} description={selected ? selected.title : "Thêm đầu truyện mới vào thư viện."} title={drawer.mode === "archive" ? "Ngừng hiển thị truyện" : selected ? "Chỉnh sửa truyện" : "Tạo truyện"}>
          <form className="drawerForm" onSubmit={submit}>
            {drawer.mode === "archive" && selected ? (
              <p className="drawerConfirm">Truyện sẽ chuyển sang trạng thái lưu trữ và không còn xuất hiện trên client. Dữ liệu chương vẫn được giữ nguyên.</p>
            ) : (
              <>
                <Field label="Tên truyện"><input defaultValue={selected?.title} maxLength={240} name="title" required /></Field>
                <Field label="Đường dẫn"><input defaultValue={selected?.slug} maxLength={160} name="slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" required /></Field>
                <Field label="Tác giả"><input defaultValue={selected?.authorName} maxLength={160} name="authorName" required /></Field>
                <Field label="Giới thiệu"><textarea defaultValue={selected?.synopsis} maxLength={10000} name="synopsis" required rows={6} /></Field>
                <div className="drawerFieldGrid">
                  <Field label="Team"><select defaultValue={selected?.teamId} name="teamId" required><option value="">Chọn team</option>{teams.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}</select></Field>
                  <Field label="Thể loại"><select defaultValue={selected?.categoryId} name="categoryId" required><option value="">Chọn thể loại</option>{categories.filter((category) => category.active).map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></Field>
                  <Field label="Xuất bản"><select defaultValue={selected?.workflowStatus ?? "DRAFT"} name="workflowStatus"><option value="DRAFT">Bản nháp</option><option value="PUBLISHED">Đã xuất bản</option><option value="PENDING_REVIEW">Chờ duyệt</option></select></Field>
                  <Field label="Tiến độ"><select defaultValue={selected?.completionStatus ?? "ONGOING"} name="completionStatus"><option value="ONGOING">Đang ra chương</option><option value="COMPLETED">Đã hoàn thành</option></select></Field>
                </div>
              </>
            )}
            <MutationNotice error={error} />
            <FormActions busy={busy} close={() => setDrawer(null)} submitLabel={drawer.mode === "archive" ? "Xác nhận lưu trữ" : "Lưu truyện"} />
          </form>
        </Drawer>
      )}
    </>
  );
}

export function CategoryCrudWorkspace({ categories: initialCategories }: Readonly<{ categories: AdminCategoryRow[] }>) {
  const [categories, setCategories] = useState<AdminCategoryRow[]>(initialCategories);
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; category?: AdminCategoryRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.category;

  const refreshData = async () => {
    try {
      const cRes = await loadAdminCategories();
      setCategories(cRes);
    } catch {
      // Ignore
    }
  };

  useEffect(() => {
    void refreshData();
  }, []);

  const { sortedList, sortState, toggleSort } = useSortableList<AdminCategoryRow, keyof AdminCategoryRow>(categories, "sortOrder", "asc");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!drawer) return;
    setBusy(true);
    setError("");
    try {
      if (drawer.mode === "archive" && selected) {
        await adminMutation(`content/categories/${selected.id}`, "DELETE");
      } else {
        const form = new FormData(event.currentTarget);
        await adminMutation(
          selected ? `content/categories/${selected.id}` : "content/categories",
          selected ? "PUT" : "POST",
          {
            active: form.get("active") === "on",
            description: value(form, "description"),
            name: value(form, "name"),
            slug: value(form, "slug"),
            sortOrder: Number(value(form, "sortOrder")),
          },
        );
      }
      setDrawer(null);
      await refreshData();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi.");
    } finally {
      setBusy(false);
    }
  }

  const columns: Array<{ key: keyof AdminCategoryRow; label: string }> = [
    { key: "name", label: "Tên thể loại" },
    { key: "sortOrder", label: "Thứ tự" },
    { key: "slug", label: "Đường dẫn" },
    { key: "active", label: "Trạng thái hiển thị" },
  ];

  return (
    <>
      <WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Danh mục thư viện" title="Quản lý thể loại" />
      <SortToolbar columns={columns} onSort={toggleSort} sortState={sortState} />
      <section className="adminCrudPanel">
        {sortedList.map((category) => (
          <article key={category.id}>
            <div className="adminCrudDetails"><strong>{category.name}</strong><small>{category.description}</small></div>
            <span>{category.active ? "Đang hiển thị" : "Đã ẩn"}</span>
            <div className="adminCrudActions"><button onClick={() => setDrawer({ mode: "edit", category })} type="button">Chỉnh sửa</button><button onClick={() => setDrawer({ mode: "archive", category })} type="button">Ẩn thể loại</button></div>
          </article>
        ))}
      </section>
      {drawer && <Drawer close={() => setDrawer(null)} description={selected?.description ?? "Thể loại giúp reader tìm đúng mạch truyện yêu thích."} title={drawer.mode === "archive" ? "Ẩn thể loại" : selected ? "Chỉnh sửa thể loại" : "Tạo thể loại"}>
        <form className="drawerForm" onSubmit={submit}>
          {drawer.mode === "archive" && selected ? <p className="drawerConfirm">Thể loại sẽ ngừng xuất hiện trên client. Liên kết với truyện hiện tại vẫn được giữ.</p> : <>
            <Field label="Tên thể loại"><input defaultValue={selected?.name} maxLength={120} name="name" required /></Field>
            <Field label="Đường dẫn"><input defaultValue={selected?.slug} maxLength={80} name="slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" required /></Field>
            <Field label="Mô tả cho reader"><textarea defaultValue={selected?.description} maxLength={500} name="description" required rows={5} /></Field>
            <Field label="Thứ tự hiển thị"><input defaultValue={selected?.sortOrder ?? categories.length + 1} min={0} name="sortOrder" required type="number" /></Field>
            <label className="drawerCheck"><input defaultChecked={selected?.active ?? true} name="active" type="checkbox" /><span>Hiển thị trên client</span></label>
          </>}
          <MutationNotice error={error} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={drawer.mode === "archive" ? "Xác nhận ẩn" : "Lưu thể loại"} />
        </form>
      </Drawer>}
    </>
  );
}

export function TeamCrudWorkspace({ teams: initialTeams, users: initialUsers }: Readonly<{ teams: AdminTeamRow[]; users: AdminUserRow[] }>) {
  const [teams, setTeams] = useState<AdminTeamRow[]>(initialTeams);
  const [users, setUsers] = useState<AdminUserRow[]>(initialUsers);
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; team?: AdminTeamRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.team;

  const refreshData = async () => {
    try {
      const [tRes, uRes] = await Promise.all([loadAdminTeams(), loadAdminUsers()]);
      setTeams(tRes);
      setUsers(uRes);
    } catch {
      // Ignore
    }
  };

  useEffect(() => {
    void refreshData();
  }, []);

  const { sortedList, sortState, toggleSort } = useSortableList<AdminTeamRow, keyof AdminTeamRow>(teams, "updatedAt", "desc");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!drawer) return;
    setBusy(true); setError("");
    try {
      if (drawer.mode === "archive" && selected) await adminMutation(`content/teams/${selected.id}`, "DELETE");
      else {
        const form = new FormData(event.currentTarget);
        await adminMutation(selected ? `content/teams/${selected.id}` : "content/teams", selected ? "PUT" : "POST", {
          description: value(form, "description"), name: value(form, "name"), ownerUserId: value(form, "ownerUserId"), slug: value(form, "slug"), state: value(form, "state"),
        });
      }
      setDrawer(null); await refreshData();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi."); }
    finally { setBusy(false); }
  }

  const columns: Array<{ key: keyof AdminTeamRow; label: string }> = [
    { key: "name", label: "Tên team" },
    { key: "ownerName", label: "Chủ sở hữu" },
    { key: "memberCount", label: "Số thành viên" },
    { key: "state", label: "Trạng thái" },
    { key: "updatedAt", label: "Cập nhật" },
  ];

  return <>
    <WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Đối tác nội dung" title="Quản lý team" />
    <SortToolbar columns={columns} onSort={toggleSort} sortState={sortState} />
    <section className="adminCrudPanel">{sortedList.map((team) => <article key={team.id}><div className="adminCrudDetails"><strong>{team.name}</strong><small>{team.ownerName} · {team.memberCount} thành viên</small></div><span>{translateStatus(team.state)}</span><div className="adminCrudActions"><button onClick={() => setDrawer({ mode: "edit", team })} type="button">Xét duyệt / sửa</button><button onClick={() => setDrawer({ mode: "archive", team })} type="button">Tạm khóa</button></div></article>)}</section>
    {drawer && <Drawer close={() => setDrawer(null)} description={selected?.description ?? "Tạo hồ sơ team và chỉ định chủ sở hữu."} title={drawer.mode === "archive" ? "Tạm khóa team" : selected ? "Cập nhật team" : "Tạo team"}><form className="drawerForm" onSubmit={submit}>
      {drawer.mode === "archive" && selected ? <p className="drawerConfirm">Team sẽ bị tạm khóa. Truyện đã xuất bản vẫn được giữ để admin tiếp tục xử lý.</p> : <>
        <Field label="Tên team"><input defaultValue={selected?.name} maxLength={160} name="name" required /></Field><Field label="Đường dẫn"><input defaultValue={selected?.slug} maxLength={80} name="slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" required /></Field><Field label="Giới thiệu"><textarea defaultValue={selected?.description} maxLength={2000} name="description" required rows={5} /></Field><Field label="Chủ sở hữu"><select defaultValue={selected?.ownerUserId} name="ownerUserId" required><option value="">Chọn người dùng</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName || user.email}</option>)}</select></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "PENDING_REVIEW"} name="state"><option value="PENDING_REVIEW">Chờ xét duyệt</option><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field>
      </>}<MutationNotice error={error} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu team"} />
    </form></Drawer>}
  </>;
}

export function UserCrudWorkspace({ users: initialUsers }: Readonly<{ users: AdminUserRow[] }>) {
  const [users, setUsers] = useState<AdminUserRow[]>(initialUsers);
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; user?: AdminUserRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.user;

  const refreshData = async () => {
    try {
      const uRes = await loadAdminUsers();
      setUsers(uRes);
    } catch {
      // Ignore
    }
  };

  useEffect(() => {
    void refreshData();
  }, []);

  const { sortedList, sortState, toggleSort } = useSortableList<AdminUserRow, keyof AdminUserRow>(users, "createdAt", "desc");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!drawer) return; setBusy(true); setError("");
    try {
      if (drawer.mode === "archive" && selected) await adminMutation(`content/users/${selected.id}`, "DELETE");
      else { const form = new FormData(event.currentTarget); await adminMutation(selected ? `content/users/${selected.id}` : "content/users", selected ? "PUT" : "POST", { bio: value(form, "bio"), displayName: value(form, "displayName"), email: value(form, "email"), ...(selected ? {} : { password: value(form, "password") }), roles: roles(form), state: value(form, "state") }); }
      setDrawer(null); await refreshData();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi."); } finally { setBusy(false); }
  }

  const columns: Array<{ key: keyof AdminUserRow; label: string }> = [
    { key: "displayName", label: "Tên / Email" },
    { key: "availableXu", label: "Số dư Xu" },
    { key: "roles", label: "Vai trò" },
    { key: "state", label: "Trạng thái" },
    { key: "createdAt", label: "Ngày tham gia" },
  ];

  return <><WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Tài khoản và phân quyền" title="Quản lý người dùng" /><SortToolbar columns={columns} onSort={toggleSort} sortState={sortState} /><section className="adminCrudPanel">{sortedList.map((user) => <article key={user.id}><div className="adminCrudDetails"><strong>{user.displayName || user.email}</strong><small>{user.email} · Ví {numberFormatter.format(user.availableXu)} XU · {user.roles}</small></div><span>{translateStatus(user.state)}</span><div className="adminCrudActions"><button onClick={() => setDrawer({ mode: "edit", user })} type="button">Chỉnh sửa</button><button onClick={() => setDrawer({ mode: "archive", user })} type="button">Tạm khóa</button></div></article>)}</section>{drawer && <Drawer close={() => setDrawer(null)} description={selected?.email ?? "Tạo tài khoản thử nghiệm hoặc tài khoản vận hành."} title={drawer.mode === "archive" ? "Tạm khóa người dùng" : selected ? "Chỉnh sửa người dùng" : "Tạo người dùng"}><form className="drawerForm" onSubmit={submit}>{drawer.mode === "archive" && selected ? <p className="drawerConfirm">Tài khoản sẽ bị tạm khóa và toàn bộ phiên đăng nhập cũ mất hiệu lực.</p> : <><Field label="Tên hiển thị"><input defaultValue={selected?.displayName} maxLength={100} name="displayName" required /></Field><Field label="Email"><input defaultValue={selected?.email} maxLength={254} name="email" required type="email" /></Field>{!selected && <Field label="Mật khẩu ban đầu"><input minLength={12} name="password" required type="password" /></Field>}<Field label="Giới thiệu"><textarea defaultValue={selected?.bio} maxLength={1000} name="bio" rows={4} /></Field><Field label="Vai trò, cách nhau bằng dấu phẩy"><input defaultValue={selected?.roles || "USER"} name="roles" required /></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "ACTIVE"} name="state"><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field></>}<MutationNotice error={error} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu người dùng"} /></form></Drawer>}</>;
}

export function CashFlowCrudWorkspace({ entries: initialEntries, users: initialUsers }: Readonly<{ entries: AdminCashFlowRow[]; users: AdminUserRow[] }>) {
  const [entries, setEntries] = useState<AdminCashFlowRow[]>(initialEntries);
  const [users, setUsers] = useState<AdminUserRow[]>(initialUsers);
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; entry?: AdminCashFlowRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.entry;

  const refreshData = async () => {
    try {
      const [eRes, uRes] = await Promise.all([loadAdminCashFlow(), loadAdminUsers()]);
      setEntries(eRes);
      setUsers(uRes);
    } catch {
      // Ignore
    }
  };

  useEffect(() => {
    void refreshData();
  }, []);

  const { sortedList, sortState, toggleSort } = useSortableList<AdminCashFlowRow, keyof AdminCashFlowRow>(entries, "createdAt", "desc");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!drawer) return; setBusy(true); setError("");
    try {
      const form = new FormData(event.currentTarget);
      if (drawer.mode === "reverse" && selected) await adminMutation(`finance/cash-flow/${selected.id}/reverse`, "POST", { reason: value(form, "reason") });
      else await adminMutation("finance/cash-flow", "POST", { amountXu: Number(value(form, "amountXu")), description: value(form, "description"), entryType: value(form, "entryType"), referenceId: value(form, "referenceId"), referenceType: value(form, "referenceType"), userId: value(form, "userId") });
      setDrawer(null); await refreshData();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi."); } finally { setBusy(false); }
  }

  const columns: Array<{ key: keyof AdminCashFlowRow; label: string }> = [
    { key: "amountXu", label: "Số XU" },
    { key: "userEmail", label: "Email người dùng" },
    { key: "entryType", label: "Loại giao dịch" },
    { key: "referenceType", label: "Tham chiếu" },
    { key: "createdAt", label: "Thời gian" },
  ];

  return <><WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Sổ cái XU" title="Dòng tiền" /><SortToolbar columns={columns} onSort={toggleSort} sortState={sortState} /><section className="adminCrudPanel">{sortedList.map((entry) => <article key={entry.id}><div className="adminCrudDetails"><strong>{entry.description}</strong><small>{entry.userEmail} · {entry.referenceType}/{entry.referenceId}</small></div><span>{entry.amountXu > 0 ? "+" : ""}{numberFormatter.format(entry.amountXu)} XU</span><div className="adminCrudActions"><button disabled={entry.entryType === "REVERSAL"} onClick={() => setDrawer({ entry, mode: "reverse" })} type="button">Hoàn ngược</button></div></article>)}</section>{drawer && <Drawer close={() => setDrawer(null)} description={selected ? `Bút toán ${selected.id}` : "Mỗi thay đổi được ghi thành một bút toán mới để bảo toàn lịch sử."} title={selected ? "Hoàn ngược giao dịch" : "Tạo bút toán"}><form className="drawerForm" onSubmit={submit}>{selected ? <><p className="drawerConfirm">Hệ thống sẽ tạo bút toán mới với số tiền đối ứng. Bản ghi gốc không bị xóa.</p><Field label="Lý do hoàn ngược"><textarea maxLength={300} name="reason" required rows={4} /></Field></> : <><Field label="Người dùng"><select name="userId" required><option value="">Chọn tài khoản</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName || user.email}</option>)}</select></Field><div className="drawerFieldGrid"><Field label="Loại giao dịch"><select name="entryType"><option value="TOPUP">Nạp XU</option><option value="DONATION">Ủng hộ</option><option value="ADJUSTMENT">Điều chỉnh</option><option value="REWARD">Thưởng</option></select></Field><Field label="Số XU"><input name="amountXu" required type="number" /></Field></div><Field label="Loại tham chiếu"><input defaultValue="ADMIN_ADJUSTMENT" name="referenceType" pattern="[A-Z_]{3,40}" required /></Field><Field label="Mã tham chiếu"><input defaultValue="admin-adjustment" maxLength={100} name="referenceId" required /></Field><Field label="Nội dung"><textarea maxLength={500} name="description" required rows={4} /></Field></>}<MutationNotice error={error} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={selected ? "Tạo bút toán hoàn ngược" : "Ghi bút toán"} /></form></Drawer>}</>;
}
