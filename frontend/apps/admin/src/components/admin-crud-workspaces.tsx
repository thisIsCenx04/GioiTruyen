"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, type ReactNode, useState } from "react";

import type {
  AdminCashFlowRow,
  AdminCategoryRow,
  AdminStoryRow,
  AdminTeamRow,
  AdminUserRow,
} from "../app/admin-data";

type DrawerMode = "archive" | "create" | "edit" | "reverse";

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
  return {
    ACTIVE: "Đang hoạt động",
    ARCHIVED: "Đã lưu trữ",
    COMPLETED: "Đã hoàn thành",
    DRAFT: "Bản nháp",
    ONGOING: "Đang ra chương",
    PENDING_REVIEW: "Chờ xét duyệt",
    PUBLISHED: "Đã xuất bản",
    SUSPENDED: "Tạm khóa",
  }[status] ?? status;
}

async function adminMutation(path: string, method: "DELETE" | "POST" | "PUT", body?: unknown) {
  const response = await fetch(`/api/admin/${path}`, {
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    ...(body === undefined ? {} : { headers: { "Content-Type": "application/json" } }),
    credentials: "same-origin",
    method,
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { title?: string } | null;
    throw new Error(problem?.title ?? `Không thể lưu thay đổi (${response.status}).`);
  }
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

export function StoryCrudWorkspace({ categories, stories, teams }: Readonly<{
  categories: AdminCategoryRow[];
  stories: AdminStoryRow[];
  teams: AdminTeamRow[];
}>) {
  const router = useRouter();
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; story?: AdminStoryRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.story;

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
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Nội dung xuất bản" title="Quản lý truyện" />
      <section className="adminCrudPanel">
        {stories.length === 0 ? <p className="adminEmptyState">Chưa có truyện trong thư viện.</p> : stories.map((story) => (
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

export function CategoryCrudWorkspace({ categories }: Readonly<{ categories: AdminCategoryRow[] }>) {
  const router = useRouter();
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; category?: AdminCategoryRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.category;

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
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Danh mục thư viện" title="Quản lý thể loại" />
      <section className="adminCrudPanel">
        {categories.map((category) => (
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

export function TeamCrudWorkspace({ teams, users }: Readonly<{ teams: AdminTeamRow[]; users: AdminUserRow[] }>) {
  const router = useRouter();
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; team?: AdminTeamRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.team;

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
      setDrawer(null); router.refresh();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi."); }
    finally { setBusy(false); }
  }

  return <>
    <WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Đối tác nội dung" title="Quản lý team" />
    <section className="adminCrudPanel">{teams.map((team) => <article key={team.id}><div className="adminCrudDetails"><strong>{team.name}</strong><small>{team.ownerName} · {team.memberCount} thành viên</small></div><span>{translateStatus(team.state)}</span><div className="adminCrudActions"><button onClick={() => setDrawer({ mode: "edit", team })} type="button">Xét duyệt / sửa</button><button onClick={() => setDrawer({ mode: "archive", team })} type="button">Tạm khóa</button></div></article>)}</section>
    {drawer && <Drawer close={() => setDrawer(null)} description={selected?.description ?? "Tạo hồ sơ team và chỉ định chủ sở hữu."} title={drawer.mode === "archive" ? "Tạm khóa team" : selected ? "Cập nhật team" : "Tạo team"}><form className="drawerForm" onSubmit={submit}>
      {drawer.mode === "archive" && selected ? <p className="drawerConfirm">Team sẽ bị tạm khóa. Truyện đã xuất bản vẫn được giữ để admin tiếp tục xử lý.</p> : <>
        <Field label="Tên team"><input defaultValue={selected?.name} maxLength={160} name="name" required /></Field><Field label="Đường dẫn"><input defaultValue={selected?.slug} maxLength={80} name="slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" required /></Field><Field label="Giới thiệu"><textarea defaultValue={selected?.description} maxLength={2000} name="description" required rows={5} /></Field><Field label="Chủ sở hữu"><select defaultValue={selected?.ownerUserId} name="ownerUserId" required><option value="">Chọn người dùng</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName || user.email}</option>)}</select></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "PENDING_REVIEW"} name="state"><option value="PENDING_REVIEW">Chờ xét duyệt</option><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field>
      </>}<MutationNotice error={error} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu team"} />
    </form></Drawer>}
  </>;
}

export function UserCrudWorkspace({ users }: Readonly<{ users: AdminUserRow[] }>) {
  const router = useRouter();
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; user?: AdminUserRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.user;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!drawer) return; setBusy(true); setError("");
    try {
      if (drawer.mode === "archive" && selected) await adminMutation(`content/users/${selected.id}`, "DELETE");
      else { const form = new FormData(event.currentTarget); await adminMutation(selected ? `content/users/${selected.id}` : "content/users", selected ? "PUT" : "POST", { bio: value(form, "bio"), displayName: value(form, "displayName"), email: value(form, "email"), ...(selected ? {} : { password: value(form, "password") }), roles: roles(form), state: value(form, "state") }); }
      setDrawer(null); router.refresh();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi."); } finally { setBusy(false); }
  }
  return <><WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Tài khoản và phân quyền" title="Quản lý người dùng" /><section className="adminCrudPanel">{users.map((user) => <article key={user.id}><div className="adminCrudDetails"><strong>{user.displayName || user.email}</strong><small>{user.email} · Ví {numberFormatter.format(user.availableXu)} XU · {user.roles}</small></div><span>{translateStatus(user.state)}</span><div className="adminCrudActions"><button onClick={() => setDrawer({ mode: "edit", user })} type="button">Chỉnh sửa</button><button onClick={() => setDrawer({ mode: "archive", user })} type="button">Tạm khóa</button></div></article>)}</section>{drawer && <Drawer close={() => setDrawer(null)} description={selected?.email ?? "Tạo tài khoản thử nghiệm hoặc tài khoản vận hành."} title={drawer.mode === "archive" ? "Tạm khóa người dùng" : selected ? "Chỉnh sửa người dùng" : "Tạo người dùng"}><form className="drawerForm" onSubmit={submit}>{drawer.mode === "archive" && selected ? <p className="drawerConfirm">Tài khoản sẽ bị tạm khóa và toàn bộ phiên đăng nhập cũ mất hiệu lực.</p> : <><Field label="Tên hiển thị"><input defaultValue={selected?.displayName} maxLength={100} name="displayName" required /></Field><Field label="Email"><input defaultValue={selected?.email} maxLength={254} name="email" required type="email" /></Field>{!selected && <Field label="Mật khẩu ban đầu"><input minLength={12} name="password" required type="password" /></Field>}<Field label="Giới thiệu"><textarea defaultValue={selected?.bio} maxLength={1000} name="bio" rows={4} /></Field><Field label="Vai trò, cách nhau bằng dấu phẩy"><input defaultValue={selected?.roles || "USER"} name="roles" required /></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "ACTIVE"} name="state"><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field></>}<MutationNotice error={error} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu người dùng"} /></form></Drawer>}</>;
}

export function CashFlowCrudWorkspace({ entries, users }: Readonly<{ entries: AdminCashFlowRow[]; users: AdminUserRow[] }>) {
  const router = useRouter();
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; entry?: AdminCashFlowRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.entry;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!drawer) return; setBusy(true); setError("");
    try {
      const form = new FormData(event.currentTarget);
      if (drawer.mode === "reverse" && selected) await adminMutation(`finance/cash-flow/${selected.id}/reverse`, "POST", { reason: value(form, "reason") });
      else await adminMutation("finance/cash-flow", "POST", { amountXu: Number(value(form, "amountXu")), description: value(form, "description"), entryType: value(form, "entryType"), referenceId: value(form, "referenceId"), referenceType: value(form, "referenceType"), userId: value(form, "userId") });
      setDrawer(null); router.refresh();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi."); } finally { setBusy(false); }
  }
  return <><WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Sổ cái XU" title="Dòng tiền" /><section className="adminCrudPanel">{entries.map((entry) => <article key={entry.id}><div className="adminCrudDetails"><strong>{entry.description}</strong><small>{entry.userEmail} · {entry.referenceType}/{entry.referenceId}</small></div><span>{entry.amountXu > 0 ? "+" : ""}{numberFormatter.format(entry.amountXu)} XU</span><div className="adminCrudActions"><button disabled={entry.entryType === "REVERSAL"} onClick={() => setDrawer({ entry, mode: "reverse" })} type="button">Hoàn ngược</button></div></article>)}</section>{drawer && <Drawer close={() => setDrawer(null)} description={selected ? `Bút toán ${selected.id}` : "Mỗi thay đổi được ghi thành một bút toán mới để bảo toàn lịch sử."} title={selected ? "Hoàn ngược giao dịch" : "Tạo bút toán"}><form className="drawerForm" onSubmit={submit}>{selected ? <><p className="drawerConfirm">Hệ thống sẽ tạo bút toán mới với số tiền đối ứng. Bản ghi gốc không bị xóa.</p><Field label="Lý do hoàn ngược"><textarea maxLength={300} name="reason" required rows={4} /></Field></> : <><Field label="Người dùng"><select name="userId" required><option value="">Chọn tài khoản</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName || user.email}</option>)}</select></Field><div className="drawerFieldGrid"><Field label="Loại giao dịch"><select name="entryType"><option value="TOPUP">Nạp XU</option><option value="DONATION">Ủng hộ</option><option value="ADJUSTMENT">Điều chỉnh</option><option value="REWARD">Thưởng</option></select></Field><Field label="Số XU"><input name="amountXu" required type="number" /></Field></div><Field label="Loại tham chiếu"><input defaultValue="ADMIN_ADJUSTMENT" name="referenceType" pattern="[A-Z_]{3,40}" required /></Field><Field label="Mã tham chiếu"><input defaultValue="admin-adjustment" maxLength={100} name="referenceId" required /></Field><Field label="Nội dung"><textarea maxLength={500} name="description" required rows={4} /></Field></>}<MutationNotice error={error} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={selected ? "Tạo bút toán hoàn ngược" : "Ghi bút toán"} /></form></Drawer>}</>;
}
