import { Eye, ImagePlus, Paperclip, Pencil, Plus, RotateCcw, Trash2, X } from "lucide-react";
import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { coverUrl } from "@/components/story-cover";
import tableStyles from "./admin-table.module.css";
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
import { getAccessToken, refreshAccessToken } from "../../../lib/auth";
import { AdminTable, StatBar, type Column, type Stat } from "./admin-table";
import {
  type ImportedStory,
  parseStoryDocument,
  parseStoryFile,
  WORDS_PER_CHAPTER,
  WORDS_PER_CHAPTER_ZHIHU,
} from "./story-import";

/** How a story is labelled by kind, matching what the publish form offers. */
const STORY_TYPE_LABELS: Record<string, string> = {
  AUDIO: "Truyện audio",
  EXCLUSIVE: "Độc quyền",
  ORIGINAL: "Sáng tác",
  TEXT: "Truyện chữ",
};

/** Where the story stands, as the catalog reports it. */
const COMPLETION_LABELS: Record<string, string> = {
  COMPLETED: "Hoàn thành",
  HIATUS: "Tạm ngưng",
  ONGOING: "Đang ra",
};

type DrawerMode = "archive" | "create" | "delete" | "edit" | "reverse";
type SortOrder = "asc" | "desc";
type StoryChapterDraft = {
  id: string;
  file?: File;
  content: string;
  title: string;
  tags: string[];
  accessType?: "FREE" | "PAID";
  coinPrice?: number;
};

interface SortState<K extends string> {
  key: K;
  order: SortOrder;
}

const numberFormatter = new Intl.NumberFormat("vi-VN");

export function formatShortDate(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value.includes("T") ? value : value.replace(" ", "T"));
  if (Number.isNaN(parsed.getTime())) return value;
  const day = String(parsed.getDate()).padStart(2, "0");
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const year = String(parsed.getFullYear()).slice(-2);
  return `${day}/${month}/${year}`;
}


/**
 * Timestamps as an admin reads them. Every list shows when a record was made
 * and when it last changed - without that, two similar rows are impossible to
 * tell apart.
 */
/**
 * The body of a delete drawer: a warning, and a box the admin must type the
 * record's name into.
 *
 * <p>Deleting is permanent and usually cascades, so a single click is too
 * cheap. Typing the name makes the admin read which record they are about to
 * remove - a confirm dialog alone gets dismissed on reflex.
 */
function DeleteConfirmation({
  confirmation,
  entityLabel,
  name,
  onChange,
  warning,
}: Readonly<{
  confirmation: string;
  entityLabel: string;
  name: string;
  onChange: (value: string) => void;
  warning: string;
}>) {
  return (
    <>
      <p className="drawerDanger">
        <strong>Xóa vĩnh viễn.</strong> {warning}
      </p>
      <Field label={`Gõ lại tên ${entityLabel} để xác nhận`}>
        <input
          autoComplete="off"
          onChange={(event) => onChange(event.currentTarget.value)}
          placeholder={name}
          value={confirmation}
        />
      </Field>
      {confirmation && confirmation.trim() !== name.trim() ? (
        <p className="drawerConfirmMismatch">Tên chưa khớp với “{name}”.</p>
      ) : null}
    </>
  );
}

/** The "created / last updated" line every admin list row carries. */
function Timestamps({
  createdAt,
  updatedAt,
}: Readonly<{ createdAt?: string | null; updatedAt?: string | null }>) {
  if (!createdAt && !updatedAt) return null;
  return (
    <>
      {createdAt ? `Tạo lúc ${formatDateTime(createdAt)}` : ""}
      {createdAt && updatedAt ? " · " : ""}
      {updatedAt ? `Cập nhật ${formatDateTime(updatedAt)}` : ""}
    </>
  );
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value.includes("T") ? value : value.replace(" ", "T"));
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString("vi-VN", {
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
}

function value(form: FormData, name: string) {
  return String(form.get(name) ?? "").trim();
}

function nullableValue(form: FormData, name: string) {
  const fieldValue = value(form, name);
  return fieldValue.length > 0 ? fieldValue : null;
}

function appendNullableField(body: FormData, key: string, fieldValue: string | string[] | null) {
  if (fieldValue === null) return;
  body.append(key, Array.isArray(fieldValue) ? fieldValue.join(",") : fieldValue);
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

async function adminMutation(path: string, method: "DELETE" | "POST" | "PUT", body?: FormData | unknown) {
  const send = (token: string | null) => {
    const headers: Record<string, string> = {
      Accept: "application/json",
    };
    if (body !== undefined && !(body instanceof FormData)) {
      headers["Content-Type"] = "application/json";
    }
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    return fetch(`/api/v1/admin/${path}`, {
      ...(body === undefined ? {} : { body: body instanceof FormData ? body : JSON.stringify(body) }),
      credentials: "same-origin",
      headers,
      method,
    });
  };

  let response = await send(getAccessToken());

  // Renew an expired access token once rather than losing the admin's edit.
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) {
      response = await send(renewed);
    }
  }

  if (!response.ok) {
    // 413 arrives either from the backend (with a problem document naming the
    // real cause) or from the reverse proxy (plain HTML). The backend's message
    // is preferred; the proxy case falls back to the size explanation.
    if (response.status === 413) {
      const problem = (await response.json().catch(() => null)) as
        | { detail?: string }
        | null;
      throw new Error(
        problem?.detail
        ?? "Nội dung quá lớn để tải lên (giới hạn 50 MB). Hãy chia nhỏ số chương "
          + "hoặc dùng ảnh bìa nhẹ hơn rồi lưu lại.",
      );
    }
    if (response.status === 403) {
      throw new Error("Tài khoản không có quyền thực hiện thao tác này.");
    }
    if (response.status === 401) {
      throw new Error("Phiên quản trị đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.");
    }
    const problem = (await response.json().catch(() => null)) as
      | { detail?: string; title?: string; traceId?: string }
      | null;
    const detail = problem?.detail ?? problem?.title
      ?? `Không thể lưu thay đổi (${response.status}).`;
    // A 5xx is the platform's fault; the traceId lets support find the exact log
    // line, so it is shown rather than buried in the console.
    const trace = response.status >= 500 && problem?.traceId
      ? ` (mã lỗi: ${problem.traceId})`
      : "";
    throw new Error(detail + trace);
  }
}

function buildStorySlug(title: string) {
  return title
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
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
      className="adminSortToolbar"
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

function Field({
  children,
  hint,
  label,
}: Readonly<{ children: ReactNode; hint?: ReactNode; label: string }>) {
  return (
    <label className="drawerField">
      <span>{label}</span>
      {children}
      {hint ? <small className="drawerFieldHint">{hint}</small> : null}
    </label>
  );
}

function TagEditor({
  label,
  onChange,
  placeholder = "Nhập tag rồi Enter",
  tags,
}: Readonly<{
  label: string;
  onChange: (tags: string[]) => void;
  placeholder?: string;
  tags: string[];
}>) {
  const [draft, setDraft] = useState("");

  const addTag = () => {
    const next = draft.trim();
    if (!next || tags.some((tag) => tag.toLowerCase() === next.toLowerCase())) return;
    onChange([...tags, next]);
    setDraft("");
  };

  return (
    <div className="drawerField">
      <span>{label}</span>
      <div className="tagEditor">
        <div className="tagEditorChips">
          {tags.map((tag) => (
            <button key={tag} onClick={() => onChange(tags.filter((item) => item !== tag))} type="button">
              {tag}
              <X aria-hidden="true" size={13} />
            </button>
          ))}
          {tags.length === 0 ? <small>Chưa có tag.</small> : null}
        </div>
        <div className="tagEditorInput">
          <input
            onChange={(event) => setDraft(event.currentTarget.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === ",") {
                event.preventDefault();
                addTag();
              }
            }}
            placeholder={placeholder}
            value={draft}
          />
          <button aria-label="Thêm tag" onClick={addTag} type="button">
            <Plus aria-hidden="true" size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}

function ChapterUploadList({
  chapters,
  onChange,
}: Readonly<{
  chapters: StoryChapterDraft[];
  onChange: (chapters: StoryChapterDraft[]) => void;
}>) {
  const updateChapter = (id: string, patch: Partial<StoryChapterDraft>) => {
    onChange(chapters.map((chapter) => (chapter.id === id ? { ...chapter, ...patch } : chapter)));
  };

  return (
    <section className="chapterUploadPanel">
      <header>
        <span>Upload chương</span>
        <label>
          <Paperclip aria-hidden="true" size={16} />
          Chọn nhiều file
          <input
            accept=".txt,.md,.doc,.docx,.pdf,.epub"
            multiple
            onChange={(event) => {
              const files = Array.from(event.currentTarget.files ?? []);
              if (files.length === 0) return;
              const next = files.map((file, index) => ({
                content: "",
                file,
                id: `${file.name}-${file.lastModified}-${index}`,
                tags: [],
                title: file.name.replace(/\.[^.]+$/u, ""),
              }));
              onChange([...chapters, ...next]);
              event.currentTarget.value = "";
            }}
            type="file"
          />
        </label>
      </header>
      {chapters.length === 0 ? (
        <p>Chọn file để tạo nhiều chương cùng lúc. Mỗi chương có tiêu đề và tag riêng.</p>
      ) : (
        <div className="chapterDraftList">
          {chapters.map((chapter, index) => (
            <article className="chapterDraftItem" key={chapter.id}>
              <button
                aria-label={`Xóa chương ${index + 1}`}
                className="chapterDraftRemove"
                onClick={() => onChange(chapters.filter((item) => item.id !== chapter.id))}
                type="button"
              >
                <X aria-hidden="true" size={16} />
              </button>
              <Field label={`Chương ${index + 1}`}>
                <input
                  maxLength={240}
                  onChange={(event) => updateChapter(chapter.id, { title: event.currentTarget.value })}
                  value={chapter.title}
                />
              </Field>
              <small>{chapter.file?.name ?? "Nội dung nhập trực tiếp"}</small>
              <TagEditor
                label="Tag chương"
                onChange={(tags) => updateChapter(chapter.id, { tags })}
                placeholder="Ví dụ: battle, flashback"
                tags={chapter.tags}
              />
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function ChapterImportWorkspace({
  chapters,
  collapsible = false,
  onChange,
}: Readonly<{
  chapters: StoryChapterDraft[];
  /** Editing a long story: chapters start folded so the list stays scannable. */
  collapsible?: boolean;
  onChange: (chapters: StoryChapterDraft[]) => void;
}>) {
  const [error, setError] = useState("");
  const [bulkFreeCount, setBulkFreeCount] = useState(5);
  const [bulkPrice, setBulkPrice] = useState(5);
  const [openChapters, setOpenChapters] = useState<ReadonlySet<string>>(new Set());

  const toggleChapter = (id: string) => {
    setOpenChapters((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const updateChapter = (id: string, patch: Partial<StoryChapterDraft>) => {
    onChange(chapters.map((chapter) => (chapter.id === id ? { ...chapter, ...patch } : chapter)));
  };

  const addManualChapter = () => {
    onChange([...chapters, {
      content: "",
      id: `manual-${Date.now()}`,
      tags: [],
      title: `Chương ${chapters.length + 1}`,
      accessType: "FREE",
      coinPrice: 0,
    }]);
  };

  const applyBulkPricing = () => {
    const updated = chapters.map((chap, idx) => {
      if (idx < bulkFreeCount) {
        return { ...chap, accessType: "FREE" as const, coinPrice: 0 };
      } else {
        return { ...chap, accessType: "PAID" as const, coinPrice: bulkPrice };
      }
    });
    onChange(updated);
  };

  const importFiles = async (files: File[]) => {
    setError("");
    try {
      const imported = (await Promise.all(files.map(async (file) => {
        const parsed = await parseStoryFile(file);
        return parsed.map((chapter, index) => ({
          content: chapter.content,
          file: parsed.length === 1 ? file : undefined,
          id: `${file.name}-${file.lastModified}-${index}`,
          tags: [],
          title: chapter.title,
          accessType: "FREE" as const,
          coinPrice: 0,
        }));
      }))).flat();
      onChange([...chapters, ...imported]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể đọc file chương.");
    }
  };

  return (
    <section className="chapterUploadPanel">
      <header>
        <div>
          <strong>Chương và nội dung</strong>
          <small>Upload file Word để tự tách chương, hoặc bấm + để thêm từng chương.</small>
        </div>
        <div className="chapterUploadActions">
          <button onClick={addManualChapter} type="button"><Plus aria-hidden="true" size={16} /> Thêm chương</button>
          <label>
            <Paperclip aria-hidden="true" size={16} /> Upload file
            <input accept=".txt,.md,.docx" multiple onChange={async (event) => {
              const files = Array.from(event.currentTarget.files ?? []);
              if (files.length === 0) return;
              await importFiles(files);
              event.currentTarget.value = "";
            }} type="file" />
          </label>
        </div>
      </header>

      {/* Bulk Pricing Bar */}
      {chapters.length > 0 && (
        <div
          style={{
            background: "rgba(15, 107, 255, 0.05)",
            border: "1px solid rgba(15, 107, 255, 0.2)",
            borderRadius: "8px",
            padding: "0.75rem 1rem",
            marginBottom: "1rem",
            display: "flex",
            alignItems: "center",
            flexWrap: "wrap",
            gap: "0.75rem",
            fontSize: "0.85rem"
          }}
        >
          <span style={{ fontWeight: 700, color: "#0f6bff" }}>⚡ Thiết lập giá xu nhanh:</span>
          <span>Free</span>
          <input
            type="number"
            min={0}
            style={{ width: "60px", padding: "0.25rem 0.4rem", borderRadius: "4px", border: "1px solid #cbd5e1" }}
            value={bulkFreeCount}
            onChange={(e) => setBulkFreeCount(Number(e.target.value))}
          />
          <span>chương đầu, từ chương {bulkFreeCount + 1} khóa</span>
          <input
            type="number"
            min={1}
            style={{ width: "60px", padding: "0.25rem 0.4rem", borderRadius: "4px", border: "1px solid #cbd5e1" }}
            value={bulkPrice}
            onChange={(e) => setBulkPrice(Number(e.target.value))}
          />
          <span>Xu / chương</span>
          <button
            type="button"
            onClick={applyBulkPricing}
            style={{
              padding: "0.3rem 0.75rem",
              background: "#0f6bff",
              color: "#fff",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer",
              fontWeight: 600,
              fontSize: "0.8rem"
            }}
          >
            Áp dụng cho {chapters.length} chương
          </button>
        </div>
      )}

      {error ? <p className="drawerError" role="alert">{error}</p> : null}
      {chapters.length === 0 ? <p>Chưa có chương. Upload file hoặc bấm + để nhập content trực tiếp.</p> : (
        <div className="chapterDraftList">
          {chapters.map((chapter, index) => {
            // Folded by default when editing, so a 59-chapter story does not
            // render 59 textareas the admin has to scroll past.
            const expanded = !collapsible || openChapters.has(chapter.id);
            return (
            <article className="chapterDraftItem" key={chapter.id}>
              {collapsible ? (
                <button
                  aria-expanded={expanded}
                  className="chapterDraftToggle"
                  onClick={() => toggleChapter(chapter.id)}
                  type="button"
                >
                  <span className="chapterDraftToggleIcon">{expanded ? "−" : "+"}</span>
                  <span className="chapterDraftToggleTitle">
                    {chapter.title || `Chương ${index + 1}`}
                  </span>
                  <span className="chapterDraftToggleMeta">
                    {chapter.accessType === "PAID" ? `${chapter.coinPrice} xu` : "Miễn phí"}
                  </span>
                </button>
              ) : null}

              {expanded ? (
                <>
              <button aria-label={`Xóa chương ${index + 1}`} className="chapterDraftRemove" onClick={() => onChange(chapters.filter((item) => item.id !== chapter.id))} type="button">
                <X aria-hidden="true" size={16} />
              </button>
              <Field label={`Chương ${index + 1}`}>
                <input maxLength={240} onChange={(event) => updateChapter(chapter.id, { title: event.currentTarget.value })} value={chapter.title} />
              </Field>

              {/* Lock & Coin price controls */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem", margin: "0.5rem 0" }}>
                <Field label="Quyền truy cập">
                  <select
                    value={chapter.accessType || "FREE"}
                    onChange={(e) => {
                      const nextType = e.target.value as "FREE" | "PAID";
                      updateChapter(chapter.id, {
                        accessType: nextType,
                        coinPrice: nextType === "FREE" ? 0 : (chapter.coinPrice || 5)
                      });
                    }}
                  >
                    <option value="FREE">Miễn phí (FREE)</option>
                    <option value="PAID">Khóa chương (PAID)</option>
                  </select>
                </Field>

                {chapter.accessType === "PAID" ? (
                  <Field label="Số xu để mở khóa">
                    <input
                      type="number"
                      min={1}
                      max={1000}
                      value={chapter.coinPrice ?? 5}
                      onChange={(e) => updateChapter(chapter.id, { coinPrice: Number(e.target.value) })}
                    />
                  </Field>
                ) : (
                  <Field label="Số xu">
                    <input type="text" disabled value="0 Xu (Free)" style={{ opacity: 0.6 }} />
                  </Field>
                )}
              </div>

              {chapter.file ? <small>File: {chapter.file.name}</small> : null}
              <Field label="Nội dung chương">
                <textarea onChange={(event) => updateChapter(chapter.id, { content: event.currentTarget.value })} placeholder="Nội dung chương..." rows={8} value={chapter.content} />
              </Field>
              <TagEditor label="Tag chương" onChange={(tags) => updateChapter(chapter.id, { tags })} placeholder="Ví dụ: battle, flashback" tags={chapter.tags} />
                </>
              ) : null}
            </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

/**
 * Sits at the top of the create-story drawer: the admin drops in a file, the
 * story fields and chapter list are filled from it, and everything below stays
 * editable by hand afterwards.
 */
/**
 * Picks how the story will be read. This is the first decision in the form
 * because it changes what the rest of the form asks for: a serial needs a
 * chapter list, a one-shot needs a single body of text.
 */
function StoryFormatPicker({
  onChange,
  value,
}: Readonly<{
  onChange: (format: "ONESHOT" | "SERIAL") => void;
  value: "ONESHOT" | "SERIAL";
}>) {
  const options = [
    {
      description: `Tự tách chương mỗi ${WORDS_PER_CHAPTER} từ.`,
      key: "SERIAL" as const,
      title: "Truyện dài",
    },
    {
      description: `Chương dài hơn: tự tách mỗi ${WORDS_PER_CHAPTER_ZHIHU} từ.`,
      key: "ONESHOT" as const,
      title: "Truyện Zhihu",
    },
  ];

  return (
    <fieldset className="storyFormatPicker">
      <legend>Loại truyện</legend>
      <div>
        {options.map((option) => (
          <label
            className={value === option.key ? "storyFormatOption isActive" : "storyFormatOption"}
            key={option.key}
          >
            <input
              checked={value === option.key}
              name="storyFormat"
              onChange={() => onChange(option.key)}
              type="radio"
              value={option.key}
            />
            <span>
              <strong>{option.title}</strong>
              <small>{option.description}</small>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

/**
 * Genres are a many-to-many relationship, so this offers checkboxes rather than
 * a single-value dropdown. Selected genres are echoed as chips above the list so
 * the choice stays readable when the list is long.
 */
function GenreMultiSelect({
  categories,
  onChange,
  selected,
}: Readonly<{
  categories: AdminCategoryRow[];
  onChange: (ids: string[]) => void;
  selected: string[];
}>) {
  const [query, setQuery] = useState("");

  const toggle = (id: string) => {
    onChange(selected.includes(id) ? selected.filter((value) => value !== id) : [...selected, id]);
  };
  const chosen = categories.filter((category) => selected.includes(category.id));

  // Hundreds of genres do not fit on screen, so the list filters as the admin
  // types. Diacritics are stripped on both sides: "kiem hiep" finds "Kiếm hiệp".
  const needle = normaliseForSearch(query);
  const visible = needle
    ? categories.filter((category) => normaliseForSearch(category.name).includes(needle))
    : categories;

  return (
    <div className="genreMultiSelect">
      <span className="genreMultiSelectLabel">Thể loại (chọn nhiều)</span>
      <div className="genreChosenRow">
        {chosen.length > 0
          ? chosen.map((category) => (
            <button key={category.id} onClick={() => toggle(category.id)} type="button">
              {category.name}
              <X aria-hidden="true" size={12} />
            </button>
          ))
          : <em>Chưa chọn thể loại nào.</em>}
      </div>
      <input
        className="genreSearchInput"
        onChange={(event) => setQuery(event.currentTarget.value)}
        placeholder="Tìm thể loại… (ví dụ: kiem hiep, ngon tinh)"
        type="search"
        value={query}
      />
      <div className="genreOptionGrid">
        {visible.length === 0
          ? <em className="genreNoMatch">Không có thể loại nào khớp “{query}”.</em>
          : visible.map((category) => (
            <label className={selected.includes(category.id) ? "isChecked" : undefined} key={category.id}>
              <input
                checked={selected.includes(category.id)}
                onChange={() => toggle(category.id)}
                type="checkbox"
              />
              <span>{category.name}</span>
            </label>
          ))}
      </div>
    </div>
  );
}

/** Lower-cased and stripped of diacritics, so search works without tone marks. */
function normaliseForSearch(value: string) {
  return value
    .normalize("NFD")
    // The combining-diacritics block, which NFD split the tone marks into.
    .replace(/[̀-ͯ]/gu, "")
    .replace(/đ/giu, "d")
    .toLowerCase()
    .trim();
}

/** The whole story in one field, for the Zhihu one-page format. */
function OneshotContentEditor({
  onChange,
  value,
}: Readonly<{ onChange: (content: string) => void; value: string }>) {
  const words = value.trim() ? value.trim().split(/\s+/u).length : 0;
  // 200 wpm is the usual Vietnamese reading estimate; round up so a very short
  // piece still reads "1 phút" rather than "0 phút".
  const minutes = Math.max(1, Math.ceil(words / 200));

  return (
    <section className="oneshotEditor">
      <header>
        <div>
          <strong>Nội dung truyện</strong>
          <small>Dán toàn bộ truyện vào đây, hoặc upload file ở trên để tự điền.</small>
        </div>
        {words > 0 ? <span>{words.toLocaleString("vi-VN")} từ · ~{minutes} phút đọc</span> : null}
      </header>
      <textarea
        onChange={(event) => onChange(event.currentTarget.value)}
        placeholder="Toàn bộ nội dung truyện ngắn..."
        rows={18}
        value={value}
      />
    </section>
  );
}

function StoryDocumentImport({
  busy,
  onImported,
  wordsPerChapter,
}: Readonly<{
  busy: boolean;
  onImported: (imported: ImportedStory) => void;
  wordsPerChapter: number;
}>) {
  const [error, setError] = useState("");
  const [summary, setSummary] = useState("");
  const [parsing, setParsing] = useState(false);

  async function handleFile(file: File | null) {
    if (!file) return;
    setParsing(true);
    setError("");
    setSummary("");
    try {
      const imported = await parseStoryDocument(file, wordsPerChapter);
      onImported(imported);
      setSummary(
        `Đã đọc "${file.name}": ${imported.chapters.length} chương`
        + ` (mỗi ${wordsPerChapter} từ)`
        + (imported.authorName ? ` · tác giả ${imported.authorName}` : "")
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không đọc được file truyện.");
    } finally {
      setParsing(false);
    }
  }

  return (
    <section className="storyImportPanel">
      <header>
        <div>
          <strong>Upload file truyện để tự điền</strong>
          <small>
            Hỗ trợ .docx, .txt, .md. Hệ thống đọc tên truyện, tác giả, giới thiệu và tự tách
            chương mỗi {wordsPerChapter} từ; bạn vẫn sửa tay được ở các ô bên dưới.
          </small>
        </div>
        <label className="storyImportButton">
          <Paperclip aria-hidden="true" size={16} />
          {parsing ? "Đang đọc..." : "Chọn file"}
          <input
            accept=".txt,.md,.docx"
            disabled={busy || parsing}
            onChange={async (event) => {
              const file = event.currentTarget.files?.[0] ?? null;
              event.currentTarget.value = "";
              await handleFile(file);
            }}
            type="file"
          />
        </label>
      </header>
      {error ? <p className="drawerError" role="alert">{error}</p> : null}
      {summary ? <p className="storyImportSummary" role="status">{summary}</p> : null}
    </section>
  );
}

/**
 * Sticky action bar for every drawer. Destructive and overwriting actions ask
 * once before running: an admin has full rights here, so the guard is against
 * an accidental click rather than a missing permission.
 */
function FormActions({ busy, close, confirmMessage, submitLabel }: Readonly<{
  busy: boolean;
  close: () => void;
  confirmMessage?: string;
  submitLabel: string;
}>) {
  return (
    <footer className="drawerActions">
      <button className="secondaryButton" disabled={busy} onClick={close} type="button">Hủy</button>
      <button
        disabled={busy}
        onClick={(event) => {
          if (confirmMessage && !window.confirm(confirmMessage)) {
            event.preventDefault();
          }
        }}
        type="submit"
      >
        {busy ? "Đang lưu..." : submitLabel}
      </button>
    </footer>
  );
}

function WorkspaceHeader({
  action,
  actionLabel = "Tạo mới",
  eyebrow,
  stats,
  title,
}: Readonly<{
  action: () => void;
  actionLabel?: string;
  eyebrow: string;
  stats?: readonly Stat[];
  title: string;
}>) {
  return (
    <header className="adminTopbar" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "1rem", flexWrap: "wrap", marginBottom: "0.85rem" }}>
      <div>
        <p style={{ margin: 0, fontSize: "0.68rem", color: "#64748b", textTransform: "uppercase", fontWeight: 700, letterSpacing: "0.05em" }}>{eyebrow}</p>
        <h1 style={{ margin: "0.15rem 0 0", fontSize: "1.35rem", fontWeight: 850, color: "#0f172a" }}>{title}</h1>
      </div>

      {stats && stats.length > 0 && (
        <div style={{ display: "flex", alignItems: "center", gap: "0.55rem", flexWrap: "wrap", margin: "0 auto 0 1rem" }}>
          {stats.map((stat) => (
            <div
              key={stat.label}
              style={{
                background: "#ffffff",
                border: "1px solid #dfeaf6",
                borderRadius: "0.5rem",
                padding: "0.35rem 0.75rem",
                display: "flex",
                alignItems: "center",
                gap: "0.45rem",
                boxShadow: "0 1px 3px rgba(15, 23, 42, 0.04)",
              }}
            >
              <span style={{ fontSize: "0.7rem", color: "#64748b", fontWeight: 700, textTransform: "uppercase" }}>{stat.label}:</span>
              <strong style={{ fontSize: "0.92rem", color: "#0f5fff", fontWeight: 850 }}>{stat.value}</strong>
            </div>
          ))}
        </div>
      )}

      <button onClick={action} type="button">
        {actionLabel}
      </button>
    </header>
  );
}

/**
 * Errors surface as a dismissible popup rather than a line of text at the foot
 * of the form: a story drawer runs to several screens, so an inline message sat
 * off-screen and the save looked like it had silently done nothing.
 */
function MutationNotice({ error, onDismiss }: Readonly<{ error: string; onDismiss?: () => void }>) {
  if (!error) return null;
  return (
    <div className="mutationToastLayer" role="alert">
      <div className="mutationToast">
        <div className="mutationToastIcon" aria-hidden="true">!</div>
        <div className="mutationToastBody">
          <strong>Không thể lưu</strong>
          <p>{error}</p>
        </div>
        {onDismiss ? (
          <button aria-label="Đóng thông báo" onClick={onDismiss} type="button">
            <X aria-hidden="true" size={16} />
          </button>
        ) : null}
      </div>
    </div>
  );
}

/**
 * Business rules checked before the request goes out, so the admin gets a
 * precise reason instead of a generic failure from the server.
 */
function validateStoryDraft(input: Readonly<{
  chapterCount: number;
  chapters: readonly StoryChapterDraft[];
  isOneshot: boolean;
  oneshotContent: string;
  slug: string;
  teamId: string;
  title: string;
}>): string {
  if (!input.title.trim()) {
    return "Chưa nhập tên truyện.";
  }
  if (!input.teamId) {
    return "Chưa chọn team đăng truyện.";
  }
  if (input.slug && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/u.test(input.slug)) {
    return "Slug chỉ được gồm chữ thường không dấu, số và dấu gạch ngang. "
      + "Ví dụ: tuyet-tan-kien-quan-tam. Để trống thì hệ thống tự tạo từ tên truyện.";
  }
  // Zhihu now produces chapter rows like any other story, so either the pasted
  // one-page text or an imported chapter list satisfies it.
  if (input.isOneshot && input.chapterCount === 0 && !input.oneshotContent.trim()) {
    return "Truyện Zhihu cần có nội dung. Hãy dán nội dung hoặc upload file truyện.";
  }
  // A PAID chapter priced at 0 unlocks for free, which reads as a bug to
  // everyone involved: the reader gets it free and the team earns nothing.
  const freePaidChapter = input.chapters.findIndex(
    (chapter) => chapter.accessType === "PAID" && !(chapter.coinPrice && chapter.coinPrice > 0),
  );
  if (freePaidChapter >= 0) {
    const chapter = input.chapters[freePaidChapter]!;
    return `Chương "${chapter.title || `Chương ${freePaidChapter + 1}`}" đang để trả phí `
      + "nhưng giá bằng 0. Hãy nhập giá lớn hơn 0 xu, hoặc chuyển chương này về miễn phí.";
  }
  return "";
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
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [coverPreview, setCoverPreview] = useState("");
  const [storyTags, setStoryTags] = useState<string[]>([]);
  const [chapterDrafts, setChapterDrafts] = useState<StoryChapterDraft[]>([]);
  // Chapters are replaced wholesale on the server. Loading them into the form
  // is not a reason to send them back, so an edit only submits the list once
  // the admin has actually changed something in it.
  const [chaptersDirty, setChaptersDirty] = useState(false);
  // Typed back by the admin before a permanent delete goes through.
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
  // Controlled so an uploaded file can populate them.
  const [storyTitle, setStoryTitle] = useState("");
  const [storySlug, setStorySlug] = useState("");
  const [storyAuthor, setStoryAuthor] = useState("");
  const [storySynopsis, setStorySynopsis] = useState("");
  const [slugTouched, setSlugTouched] = useState(false);
  // SERIAL keeps the chapter workspace; ONESHOT collapses it to one body of text.
  const [storyFormat, setStoryFormat] = useState<"ONESHOT" | "SERIAL">("SERIAL");
  const [oneshotContent, setOneshotContent] = useState("");
  // Controlled so an uploaded file's "Đã hoàn thành" marker can set it.
  const [completionStatus, setCompletionStatus] = useState<"COMPLETED" | "ONGOING">("ONGOING");
  // A story can belong to several genres at once.
  const [storyCategoryIds, setStoryCategoryIds] = useState<string[]>([]);
  const [comboPriceXu, setComboPriceXu] = useState<number | "">("");
  const selected = drawer?.story;
  const isOneshot = storyFormat === "ONESHOT";

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

  useEffect(() => {
    setDeleteConfirmation("");
    if (!drawer || drawer.mode === "archive" || drawer.mode === "delete") {
      setCoverFile(null);
      setCoverPreview("");
      setStoryTags([]);
      setChapterDrafts([]);
      setChaptersDirty(false);
      return;
    }

    setCoverFile(null);
    setCoverPreview(selected?.coverUrl ?? "");
    setStoryTags(selected?.tags ?? []);
    setChapterDrafts([]);
    setChaptersDirty(false);
    setStoryTitle(selected?.title ?? "");
    setStorySlug(selected?.slug ?? "");
    setStoryAuthor(selected?.authorName ?? "");
    setStorySynopsis(selected?.synopsis ?? "");
    setStoryFormat(selected?.storyFormat === "ONESHOT" ? "ONESHOT" : "SERIAL");
    setOneshotContent("");
    setCompletionStatus(selected?.completionStatus === "COMPLETED" ? "COMPLETED" : "ONGOING");
    // Falls back to the single legacy field for rows saved before multi-genre.
    setStoryCategoryIds(
      selected?.categoryIds?.length
        ? selected.categoryIds
        : selected?.categoryId
          ? [selected.categoryId]
          : [],
    );
    // Editing keeps the saved slug; creating lets it follow the title.
    setSlugTouched(Boolean(selected));

    if (selected?.id) {
      const token = getAccessToken();
      fetch(`/api/v1/admin/content/stories/${selected.id}/chapters`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        credentials: "same-origin"
      })
        .then((res) => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          return res.json();
        })
        .then((data: any[]) => {
          if (Array.isArray(data) && data.length > 0) {
            if (selected.storyFormat === "ONESHOT") {
              setOneshotContent(data[0]?.content || "");
            } else {
              setChapterDrafts(data.map((chap, idx) => ({
                content: chap.content || "",
                id: chap.id || `chap-${idx}`,
                tags: [],
                title: chap.title || `Chương ${idx + 1}`,
                accessType: chap.accessType === "PAID" ? "PAID" : "FREE",
                coinPrice: Number(chap.coinPrice || 0)
              })));
            }
          }
        })
        // Failing quietly here used to look identical to a story with no
        // chapters, which is the state that made saving destructive.
        .catch(() => {
          setError("Không tải được danh sách chương. Đóng form và mở lại trước khi sửa chương.");
        });
    }
  }, [drawer, selected]);

  const { sortedList, sortState, toggleSort } = useSortableList<AdminStoryRow, keyof AdminStoryRow>(stories, "updatedAt", "desc");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!drawer) return;
    setBusy(true);
    setError("");
    try {
      if (drawer.mode === "delete" && selected) {
        await adminMutation(`content/stories/${selected.id}/permanent`, "DELETE");
      } else if (drawer.mode === "archive" && selected) {
        await adminMutation(`content/stories/${selected.id}`, "DELETE");
      } else {
        const form = new FormData(event.currentTarget);

        const problem = validateStoryDraft({
          chapterCount: chapterDrafts.length,
          chapters: chapterDrafts,
          isOneshot,
          oneshotContent,
          slug: value(form, "slug"),
          teamId: value(form, "teamId"),
          title: value(form, "title"),
        });
        if (problem) {
          setError(problem);
          setBusy(false);
          return;
        }

        const payload = {
          authorName: nullableValue(form, "authorName"),
          // The first genre keeps older readers of this API working.
          categoryId: storyCategoryIds[0] ?? null,
          categoryIds: storyCategoryIds,
          // A one-page story has nothing left to serialise, so it is always complete.
          completionStatus: isOneshot ? "COMPLETED" : value(form, "completionStatus"),
          contentType: value(form, "contentType"),
          slug: value(form, "slug"),
          storyFormat,
          storyType: value(form, "storyType"),
          synopsis: nullableValue(form, "synopsis"),
          summary: nullableValue(form, "synopsis"),
          tags: storyTags,
          teamId: value(form, "teamId"),
          title: value(form, "title"),
          workflowStatus: value(form, "workflowStatus"),
        };

        // Zhihu text pasted straight into the box is stored as a single chapter
        // row named after the story; an imported Zhihu file has already been cut
        // into 1400-word chapters and keeps them.
        const loadedChapters: StoryChapterDraft[] = isOneshot && chapterDrafts.length === 0
          ? (oneshotContent.trim()
            ? [{ content: oneshotContent, id: "oneshot", tags: [], title: value(form, "title") }]
            : [])
          : chapterDrafts;

        // On the server a submitted list replaces every chapter the story has.
        // Editing metadata must therefore send nothing: re-posting a list that
        // was merely loaded for display is what previously wiped the chapters
        // whenever the load was partial or had not finished.
        const sendChapters = !selected || chaptersDirty;
        const submittedChapters = sendChapters ? loadedChapters : [];

        const hasUpload = Boolean(coverFile || submittedChapters.length > 0);
        const body = hasUpload ? new FormData() : payload;
        if (body instanceof FormData) {
          // The backend reads plain form fields and chapters[i].*; sending the
          // same text again as "story"/"chapters" JSON blobs only doubled the
          // upload size and pushed long stories past the 50 MB proxy limit.
          Object.entries(payload).forEach(([key, fieldValue]) => {
            appendNullableField(body, key, fieldValue);
          });
          if (coverFile) {
            body.append("coverImage", coverFile);
          }
          // Explicit opt-in: without it the server keeps the stored chapters.
          if (submittedChapters.length > 0) {
            body.append("replaceChapters", "true");
          }
          // Tomcat caps a multipart request at a fixed number of parts, so each
          // chapter sends only what the server cannot work out for itself:
          // an empty tag list and a slug derivable from the title are skipped.
          submittedChapters.forEach((chapter, index) => {
            // A chapter carries either an attached file or inline text, never both:
            // the server prefers the file and ignores the textarea when one exists.
            if (chapter.file) {
              body.append(`chapters[${index}].file`, chapter.file);
            } else {
              body.append(`chapters[${index}].content`, chapter.content);
            }
            body.append(`chapters[${index}].title`, chapter.title);
            body.append(`chapters[${index}].slug`, buildStorySlug(chapter.title || chapter.file?.name || `chapter-${index + 1}`));
            body.append(`chapters[${index}].accessType`, chapter.accessType || "FREE");
            body.append(`chapters[${index}].coinPrice`, String(chapter.coinPrice || 0));
            if (chapter.tags.length > 0) {
              body.append(`chapters[${index}].tags`, chapter.tags.join(","));
            }
          });
        }
        await adminMutation(
          selected ? `content/stories/${selected.id}` : "content/stories",
          selected ? "PUT" : "POST",
          body,
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

function formatShortDate(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value.includes("T") ? value : value.replace(" ", "T"));
  if (Number.isNaN(parsed.getTime())) return value;
  const day = String(parsed.getDate()).padStart(2, "0");
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const year = String(parsed.getFullYear()).slice(-2);
  return `${day}/${month}/${year}`;
}

  const storyColumns: Array<Column<AdminStoryRow, string>> = [
    {
      key: "coverUrl",
      label: "Bìa",
      render: (story) => {
        const url = story.coverUrl ? coverUrl(story.coverUrl) : "";
        return (
          <div style={{ width: "36px", height: "48px", borderRadius: "4px", overflow: "hidden", background: "#e2e8f0", flexShrink: 0 }}>
            {url ? (
              <img src={url} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            ) : (
              <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", background: "#0f172a", color: "#fff", fontWeight: 800, fontSize: "12px" }}>
                {story.title.trim().slice(0, 1).toUpperCase() || "G"}
              </div>
            )}
          </div>
        );
      },
    },
    {
      key: "title",
      label: "Tên truyện",
      render: (story) => (
        <div>
          <strong style={{ fontSize: "0.88rem", color: "#0f172a", display: "block" }}>{story.title}</strong>
          <span style={{ fontSize: "0.75rem", color: "#64748b" }}>
            {story.authorName ? `Tác giả: ${story.authorName}` : ""}
            {story.authorName && story.teamName ? " · " : ""}
            {story.teamName ? `Nhóm: ${story.teamName}` : ""}
          </span>
        </div>
      ),
      sortValue: (story) => story.title,
    },
    {
      key: "storyType",
      label: "Loại truyện",
      render: (story) => STORY_TYPE_LABELS[story.storyType ?? "TEXT"] ?? story.storyType ?? "Truyện chữ",
      sortValue: (story) => story.storyType ?? "TEXT",
    },
    {
      key: "updatedAt",
      label: "Ngày",
      render: (story) => <span style={{ fontSize: "0.82rem", color: "#475569" }}>{formatShortDate(story.updatedAt || story.createdAt)}</span>,
      sortValue: (story) => story.updatedAt ?? story.createdAt,
    },
    {
      key: "completionStatus",
      label: "Tiến độ",
      render: (story) => COMPLETION_LABELS[story.completionStatus] ?? story.completionStatus,
      sortValue: (story) => story.completionStatus,
    },
    {
      key: "workflowStatus",
      label: "Trạng thái",
      render: (story) => {
        const label = translateStatus(story.workflowStatus);
        const isPub = story.workflowStatus === "PUBLISHED";
        const isPend = story.workflowStatus === "PENDING_REVIEW";
        return (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "0.3rem",
              padding: "0.2rem 0.6rem",
              borderRadius: "9999px",
              fontSize: "0.75rem",
              fontWeight: 750,
              background: isPub ? "#dcfce7" : isPend ? "#fef3c7" : "#f1f5f9",
              color: isPub ? "#15803d" : isPend ? "#b45309" : "#475569",
              border: `1px solid ${isPub ? "#86efac" : isPend ? "#fde68a" : "#cbd5e1"}`,
            }}
          >
            {isPub ? "✔ " : isPend ? "⏳ " : "📝 "}
            {label}
          </span>
        );
      },
      sortValue: (story) => story.workflowStatus,
    },
  ];

  const storyFilters = [
    {
      id: "category",
      label: "Thể loại",
      matches: (story: AdminStoryRow, value: string) =>
        (story.categoryNames ?? [story.categoryName ?? ""]).includes(value),
      options: [...new Set(stories.flatMap((story) => story.categoryNames ?? [story.categoryName ?? ""]))]
        .filter(Boolean)
        .sort((left, right) => left.localeCompare(right, "vi"))
        .map((name) => ({ label: name, value: name })),
    },
    {
      id: "completion",
      label: "Tiến độ",
      matches: (story: AdminStoryRow, value: string) => story.completionStatus === value,
      options: Object.entries(COMPLETION_LABELS).map(([value, label]) => ({ label, value })),
    },
    {
      id: "type",
      label: "Loại",
      matches: (story: AdminStoryRow, value: string) => (story.storyType ?? "TEXT") === value,
      options: Object.entries(STORY_TYPE_LABELS).map(([value, label]) => ({ label, value })),
    },
  ];

  const storyStats = [
    { label: "Tổng truyện", value: stories.length },
    { label: "Hoàn thành", value: stories.filter((s) => s.completionStatus === "COMPLETED").length },
    { label: "Đang ra", value: stories.filter((s) => s.completionStatus === "ONGOING").length },
    { label: "Tạm ngưng", value: stories.filter((s) => s.completionStatus === "HIATUS").length },
    { label: "Độc quyền", value: stories.filter((s) => s.storyType === "EXCLUSIVE").length },
  ];

  return (
    <>
      <WorkspaceHeader
        action={() => setDrawer({ mode: "create" })}
        eyebrow="Nội dung xuất bản"
        stats={storyStats}
        title="Quản lý truyện"
      />
      <AdminTable
        actions={(story) => (
          <div className={tableStyles.actionIconsGroup}>
            <Link
              to={`/truyen/${story.slug}`}
              target="_blank"
              className={tableStyles.iconBtn}
              title="Xem chi tiết / Đọc thử"
            >
              <Eye size={15} />
            </Link>
            <button
              onClick={() => setDrawer({ mode: "edit", story })}
              className={tableStyles.iconBtn}
              title="Chỉnh sửa truyện"
              type="button"
            >
              <Pencil size={15} />
            </button>
            <button
              onClick={() => setDrawer({ mode: "delete", story })}
              className={`${tableStyles.iconBtn} ${tableStyles.iconBtnDanger}`}
              title="Xóa vĩnh viễn"
              type="button"
            >
              <Trash2 size={15} />
            </button>
          </div>
        )}
        columns={storyColumns}
        emptyMessage="Chưa có truyện trong thư viện."
        filters={storyFilters}
        rowKey={(story) => story.id}
        rowClassName={(story) => {
          if (story.workflowStatus === "PUBLISHED") return tableStyles.rowPublished;
          if (story.workflowStatus === "PENDING_REVIEW") return tableStyles.rowPending;
          return tableStyles.rowDraft;
        }}
        rows={stories}
        searchPlaceholder="Tìm theo tên truyện, nhóm dịch hoặc tác giả…"
        searchValues={(story) => [story.title, story.teamName, story.authorName]}
      />
      {drawer && (
        <Drawer
          close={() => setDrawer(null)}
          description={selected ? selected.title : "Thêm đầu truyện mới vào thư viện."}
          title={drawer.mode === "delete"
            ? "Xóa truyện vĩnh viễn"
            : drawer.mode === "archive"
              ? "Ngừng hiển thị truyện"
              : selected ? "Chỉnh sửa truyện" : "Tạo truyện"}
        >
          <form className="drawerForm" onSubmit={submit}>
            {drawer.mode === "delete" && selected ? (
              <DeleteConfirmation
                confirmation={deleteConfirmation}
                entityLabel="truyện"
                name={selected.title}
                onChange={setDeleteConfirmation}
                warning="Toàn bộ chương của truyện sẽ bị xóa cùng và không khôi phục được. Nếu chỉ muốn ẩn khỏi người đọc, hãy dùng “Ngừng hiển thị”."
              />
            ) : drawer.mode === "archive" && selected ? (
              <p className="drawerConfirm">Truyện sẽ chuyển sang trạng thái lưu trữ và không còn xuất hiện trên client. Dữ liệu chương vẫn được giữ nguyên.</p>
            ) : (
              <>
                {/*
                  One form, no tabs. Splitting the fields across tabs meant the
                  inputs on the hidden tab were unmounted, so FormData never saw
                  the title and every edit failed with "Chưa nhập tên truyện".

                  Creating a story still leads with the file upload and the
                  format choice; editing an existing one shows neither, because
                  re-uploading would replace the chapters that are already there.
                */}
                {selected ? null : (
                  <>
                    <StoryFormatPicker onChange={setStoryFormat} value={storyFormat} />
                    <StoryDocumentImport
                      busy={busy}
                      wordsPerChapter={isOneshot ? WORDS_PER_CHAPTER_ZHIHU : WORDS_PER_CHAPTER}
                      onImported={(imported) => {
                        setStoryTitle(imported.title);
                        setStorySlug(buildStorySlug(imported.title));
                        if (imported.authorName) setStoryAuthor(imported.authorName);
                        if (imported.synopsis) setStorySynopsis(imported.synopsis);
                        setCompletionStatus(imported.completionStatus);
                        // Both formats produce real chapter rows; only the word
                        // budget differs. Zhihu used to collapse back into one
                        // field here, which undid the split that just ran.
                        setChaptersDirty(true);
                        setChapterDrafts(imported.chapters.map((chapter, index) => ({
                          content: chapter.content,
                          id: `imported-${index}-${Date.now()}`,
                          tags: [],
                          title: chapter.title,
                          accessType: "FREE",
                          coinPrice: 0,
                        })));
                      }}
                    />
                  </>
                )}

                <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
                    <div className="storyCoverUpload">
                      <div className="storyCoverPreview">
                        {coverPreview ? <img alt="" src={coverPreview} /> : <ImagePlus aria-hidden="true" size={34} />}
                      </div>
                      <Field label="Ảnh avatar truyện">
                        <input
                          accept="image/*"
                          name="coverImage"
                          onChange={(event) => {
                            const file = event.currentTarget.files?.[0] ?? null;
                            setCoverFile(file);
                            setCoverPreview(file ? URL.createObjectURL(file) : selected?.coverUrl ?? "");
                          }}
                          type="file"
                        />
                      </Field>
                    </div>
                    <Field label="Tên truyện">
                      <input
                        maxLength={240}
                        name="title"
                        onChange={(event) => {
                          const value = event.currentTarget.value;
                          setStoryTitle(value);
                          if (!slugTouched) setStorySlug(buildStorySlug(value));
                        }}
                        required
                        value={storyTitle}
                      />
                    </Field>
                    <Field
                      hint={
                        <>
                          Địa chỉ truyện trên web:{" "}
                          <code>gioitruyen.com/truyen/{storySlug || "ten-truyen-khong-dau"}</code>
                          <br />
                          Chỉ dùng chữ thường không dấu, số và dấu gạch ngang. Ví dụ:
                          {" "}“Tuyết Tận Kiến Quân Tâm” → <code>tuyet-tan-kien-quan-tam</code>
                        </>
                      }
                      label="Slug (đường dẫn)"
                    >
                      <input
                        maxLength={160}
                        name="slug"
                        onChange={(event) => {
                          setSlugTouched(true);
                          setStorySlug(event.currentTarget.value);
                        }}
                        pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
                        placeholder="tuyet-tan-kien-quan-tam"
                        title="Chỉ gồm chữ thường không dấu, số và dấu gạch ngang. Ví dụ: tuyet-tan-kien-quan-tam"
                        value={storySlug}
                      />
                    </Field>
                    <Field label="Tác giả">
                      <input
                        maxLength={160}
                        name="authorName"
                        onChange={(event) => setStoryAuthor(event.currentTarget.value)}
                        value={storyAuthor}
                      />
                    </Field>
                    <Field label="Giới thiệu">
                      <textarea
                        maxLength={10000}
                        name="synopsis"
                        onChange={(event) => setStorySynopsis(event.currentTarget.value)}
                        rows={6}
                        value={storySynopsis}
                      />
                    </Field>
                    <div className="drawerFieldGrid">
                      <Field label="Team"><select defaultValue={selected?.teamId} name="teamId" required><option value="">Chọn team</option>{teams.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}</select></Field>
                      <GenreMultiSelect
                        categories={categories.filter((category) => category.active)}
                        onChange={setStoryCategoryIds}
                        selected={storyCategoryIds}
                      />
                      <Field
                        hint="Quyết định truyện xuất hiện ở kệ nào ngoài trang truyện."
                        label="Phân loại truyện"
                      >
                        <select defaultValue={selected?.storyType ?? "TEXT"} name="storyType">
                          <option value="TEXT">Truyện chữ (mặc định)</option>
                          <option value="EXCLUSIVE">Truyện độc quyền</option>
                          <option value="ORIGINAL">Truyện sáng tác</option>
                          <option value="AUDIO">Truyện audio</option>
                        </select>
                      </Field>
                      <Field label="Loại nội dung"><select defaultValue="TEXT" name="contentType"><option value="TEXT">Truyện chữ</option><option value="AUDIO">Audio</option><option value="TEXT_AUDIO">Truyện chữ + audio</option></select></Field>
                      <Field label="Xuất bản"><select defaultValue={selected?.workflowStatus ?? "DRAFT"} name="workflowStatus"><option value="DRAFT">Bản nháp</option><option value="PUBLISHED">Đã xuất bản</option><option value="PENDING_REVIEW">Chờ duyệt</option></select></Field>
                      {isOneshot ? null : (
                        <Field
                          hint="Tự đặt thành “Đã hoàn thành” nếu dòng đầu file upload ghi như vậy."
                          label="Tiến độ"
                        >
                          <select
                            name="completionStatus"
                            onChange={(event) =>
                              setCompletionStatus(event.currentTarget.value === "COMPLETED" ? "COMPLETED" : "ONGOING")}
                            value={completionStatus}
                          >
                            <option value="ONGOING">Đang ra chương</option>
                            <option value="COMPLETED">Đã hoàn thành</option>
                          </select>
                        </Field>
                      )}

                      {completionStatus === "COMPLETED" && !isOneshot && (() => {
                        const rawPaidChapters = chapterDrafts.filter((ch) => ch.accessType === "PAID" || (ch.coinPrice || 0) > 0);
                        const rawTotalXu = chapterDrafts.reduce((sum, ch) => sum + (ch.coinPrice || 0), 0);

                        const totalChaptersCount = chapterDrafts.length > 0 ? chapterDrafts.length : 20;
                        const paidChaptersCount = rawPaidChapters.length > 0
                          ? rawPaidChapters.length
                          : Math.max(1, totalChaptersCount - 3);

                        const totalRetailPrice = rawTotalXu > 0
                          ? rawTotalXu
                          : (paidChaptersCount * 10);

                        return (
                          <div
                            style={{
                              gridColumn: "1 / -1",
                              background: "linear-gradient(135deg, #f0f4ff 0%, #e0e7ff 100%)",
                              border: "2px solid #6366f1",
                              borderRadius: "8px",
                              padding: "0.85rem 1rem",
                              marginTop: "0.5rem",
                              boxShadow: "0 4px 14px rgba(99, 102, 241, 0.15)",
                            }}
                          >
                            <div style={{ marginBottom: "0.45rem", fontSize: "0.84rem", color: "#1e1b4b", fontWeight: 700 }}>
                              Đang có <span style={{ color: "#4f46e5", fontWeight: 850 }}>{paidChaptersCount} chương đang khóa</span>, tổng xu mua lẻ là <span style={{ color: "#4f46e5", fontWeight: 850 }}>{totalRetailPrice} Xu</span>.
                            </div>

                            <Field label="Giá Combo Full (Xu)">
                              <input
                                type="number"
                                min={0}
                                max={totalRetailPrice || 99999}
                                name="comboPriceXu"
                                placeholder="Nhập số Xu"
                                style={{ borderColor: "#818cf8", fontWeight: 700 }}
                                value={comboPriceXu}
                                onChange={(e) => setComboPriceXu(e.target.value === "" ? "" : Number(e.target.value))}
                              />
                            </Field>
                          </div>
                        );
                      })()}
                    </div>
                    <TagEditor label="Tag truyện" onChange={setStoryTags} placeholder="Ví dụ: shounen, fantasy" tags={storyTags} />

                    {isOneshot && !selected ? (
                      <OneshotContentEditor
                        onChange={(next) => {
                          setChaptersDirty(true);
                          setOneshotContent(next);
                        }}
                        value={oneshotContent}
                      />
                    ) : (
                      <ChapterImportWorkspace
                        chapters={chapterDrafts}
                        collapsible={Boolean(selected)}
                        onChange={(next) => {
                          setChaptersDirty(true);
                          setChapterDrafts(next);
                        }}
                      />
                    )}
                </div>
              </>
            )}
            <MutationNotice error={error} onDismiss={() => setError("")} />
            <FormActions
              // The delete button stays inert until the typed name matches:
              // that, plus the confirm dialog, is the second step.
              busy={busy || (drawer.mode === "delete"
                && deleteConfirmation.trim() !== (selected?.title.trim() ?? ""))}
              close={() => setDrawer(null)}
              confirmMessage={drawer.mode === "delete"
                ? `Xóa vĩnh viễn truyện "${selected?.title}" và toàn bộ chương?`
                : drawer.mode === "archive"
                  ? `Ngừng hiển thị truyện "${selected?.title}"?`
                  : selected
                    ? `Cập nhật truyện "${selected.title}"?`
                    : undefined}
              submitLabel={drawer.mode === "delete"
                ? "Xóa vĩnh viễn"
                : drawer.mode === "archive" ? "Xác nhận lưu trữ" : "Lưu truyện"}
            />
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
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
  const selected = drawer?.category;

  useEffect(() => {
    setDeleteConfirmation("");
  }, [drawer]);

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
      if (drawer.mode === "delete" && selected) {
        await adminMutation(`content/categories/${selected.id}/permanent`, "DELETE");
      } else if (drawer.mode === "archive" && selected) {
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

  const categoryColumns: Array<Column<AdminCategoryRow, string>> = [
    {
      key: "name",
      label: "Tên thể loại",
      render: (cat) => (
        <div>
          <strong style={{ fontSize: "0.88rem", color: "#0f172a", display: "block" }}>{cat.name}</strong>
          <span style={{ fontSize: "0.75rem", color: "#64748b" }}>{cat.description}</span>
        </div>
      ),
      sortValue: (cat) => cat.name,
    },
    {
      key: "slug",
      label: "Slug (đường dẫn)",
      render: (cat) => (
        <code style={{ fontSize: "0.78rem", background: "#f1f5f9", padding: "0.2rem 0.45rem", borderRadius: "4px", color: "#0f5fff" }}>
          {cat.slug}
        </code>
      ),
      sortValue: (cat) => cat.slug,
    },
    {
      key: "sortOrder",
      label: "Thứ tự",
      numeric: true,
      render: (cat) => cat.sortOrder,
      sortValue: (cat) => cat.sortOrder,
    },
    {
      key: "updatedAt",
      label: "Cập nhật",
      render: (cat) => formatShortDate(cat.updatedAt || cat.createdAt),
      sortValue: (cat) => cat.updatedAt || cat.createdAt,
    },
    {
      key: "active",
      label: "Trạng thái",
      render: (cat) => (
        <span
          style={{
            padding: "0.2rem 0.55rem",
            borderRadius: "4px",
            fontSize: "0.72rem",
            fontWeight: 750,
            background: cat.active ? "#dcfce7" : "#f1f5f9",
            color: cat.active ? "#15803d" : "#64748b",
          }}
        >
          {cat.active ? "Đang hiển thị" : "Đã ẩn"}
        </span>
      ),
    },
  ];

  const categoryStats = [
    { label: "Tổng thể loại", value: categories.length },
    { label: "Đang hiển thị", value: categories.filter((c) => c.active).length },
    { label: "Đã ẩn", value: categories.filter((c) => !c.active).length },
  ];

  return (
    <>
      <WorkspaceHeader
        action={() => setDrawer({ mode: "create" })}
        eyebrow="Danh mục thư viện"
        stats={categoryStats}
        title="Quản lý thể loại"
      />
      <AdminTable
        actions={(category) => (
          <div className={tableStyles.actionIconsGroup}>
            <button
              className={tableStyles.iconBtn}
              onClick={() => setDrawer({ category, mode: "edit" })}
              title="Chỉnh sửa thể loại"
              type="button"
            >
              <Pencil style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
            <button
              className={tableStyles.iconBtn}
              onClick={() => setDrawer({ category, mode: "archive" })}
              title={category.active ? "Ẩn thể loại" : "Hiện thể loại"}
              type="button"
            >
              <Eye style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
            <button
              className={`${tableStyles.iconBtn} ${tableStyles.iconBtnDanger}`}
              onClick={() => setDrawer({ category, mode: "delete" })}
              title="Xóa vĩnh viễn thể loại"
              type="button"
            >
              <Trash2 style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
          </div>
        )}
        columns={categoryColumns}
        rowClassName={(category) => (category.active ? tableStyles.rowPublished : tableStyles.rowDraft)}
        rowKey={(category) => category.id}
        rows={categories}
        searchPlaceholder="Tìm theo tên thể loại, slug..."
        searchValues={(category) => [category.name, category.slug, category.description]}
      />
      {drawer && <Drawer close={() => setDrawer(null)} description={selected?.description ?? "Thể loại giúp reader tìm đúng mạch truyện yêu thích."} title={drawer.mode === "delete" ? "Xóa thể loại vĩnh viễn" : drawer.mode === "archive" ? "Ẩn thể loại" : selected ? "Chỉnh sửa thể loại" : "Tạo thể loại"}>
        <form className="drawerForm" onSubmit={submit}>
          {drawer.mode === "delete" && selected ? (
            <DeleteConfirmation
              confirmation={deleteConfirmation}
              entityLabel="thể loại"
              name={selected.name}
              onChange={setDeleteConfirmation}
              warning="Thể loại sẽ bị xóa khỏi hệ thống và không khôi phục được. Nếu chỉ muốn ẩn khỏi người đọc, hãy dùng “Ẩn thể loại”."
            />
          ) : drawer.mode === "archive" && selected ? <p className="drawerConfirm">Thể loại sẽ ngừng xuất hiện trên client. Liên kết với truyện hiện tại vẫn được giữ.</p> : <>
            <Field label="Tên thể loại"><input defaultValue={selected?.name} maxLength={120} name="name" required /></Field>
            <Field
              hint={<>Chữ thường không dấu, nối bằng dấu gạch ngang. Ví dụ: “Tiên Hiệp” → <code>tien-hiep</code></>}
              label="Slug (đường dẫn)"
            >
              <input defaultValue={selected?.slug} maxLength={80} name="slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="tien-hiep" required />
            </Field>
            <Field label="Mô tả cho reader"><textarea defaultValue={selected?.description} maxLength={500} name="description" required rows={5} /></Field>
            <Field label="Thứ tự hiển thị"><input defaultValue={selected?.sortOrder ?? categories.length + 1} min={0} name="sortOrder" required type="number" /></Field>
            <label className="drawerCheck"><input defaultChecked={selected?.active ?? true} name="active" type="checkbox" /><span>Hiển thị trên client</span></label>
          </>}
          <MutationNotice error={error} onDismiss={() => setError("")} /><FormActions busy={busy || (drawer.mode === "delete" && deleteConfirmation.trim() !== (selected?.name.trim() ?? ""))} close={() => setDrawer(null)} confirmMessage={drawer.mode === "delete" ? `Xóa vĩnh viễn thể loại "${selected?.name}"?` : drawer.mode === "archive" ? `Ẩn thể loại "${selected?.name}"?` : selected ? `Cập nhật thể loại "${selected.name}"?` : undefined} submitLabel={drawer.mode === "delete" ? "Xóa vĩnh viễn" : drawer.mode === "archive" ? "Xác nhận ẩn" : "Lưu thể loại"} />
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
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
  const selected = drawer?.team;

  useEffect(() => {
    setDeleteConfirmation("");
  }, [drawer]);

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
      if (drawer.mode === "delete" && selected) await adminMutation(`content/teams/${selected.id}/permanent`, "DELETE");
      else if (drawer.mode === "archive" && selected) await adminMutation(`content/teams/${selected.id}`, "DELETE");
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

  const teamColumns: Array<Column<AdminTeamRow, string>> = [
    {
      key: "name",
      label: "Tên team",
      render: (team) => (
        <div>
          <strong style={{ fontSize: "0.88rem", color: "#0f172a", display: "block" }}>{team.name}</strong>
          <span style={{ fontSize: "0.75rem", color: "#64748b" }}>{team.description}</span>
        </div>
      ),
      sortValue: (team) => team.name,
    },
    {
      key: "ownerName",
      label: "Chủ sở hữu",
      render: (team) => team.ownerName || "Chưa phân công",
      sortValue: (team) => team.ownerName || "",
    },
    {
      key: "memberCount",
      label: "Số thành viên",
      numeric: true,
      render: (team) => team.memberCount,
      sortValue: (team) => team.memberCount,
    },
    {
      key: "state",
      label: "Trạng thái",
      render: (team) => {
        const isLive = team.state === "ACTIVE";
        const isPending = team.state === "PENDING_REVIEW";
        return (
          <span
            style={{
              padding: "0.2rem 0.55rem",
              borderRadius: "4px",
              fontSize: "0.72rem",
              fontWeight: 750,
              background: isLive ? "#dcfce7" : isPending ? "#fef3c7" : "#f1f5f9",
              color: isLive ? "#15803d" : isPending ? "#b45309" : "#64748b",
            }}
          >
            {translateStatus(team.state)}
          </span>
        );
      },
      sortValue: (team) => team.state,
    },
    {
      key: "updatedAt",
      label: "Cập nhật",
      render: (team) => formatShortDate(team.updatedAt || team.createdAt),
      sortValue: (team) => team.updatedAt || team.createdAt,
    },
  ];

  const teamStats = [
    { label: "Tổng số team", value: teams.length },
    { label: "Đang hoạt động", value: teams.filter((t) => t.state === "ACTIVE").length },
    { label: "Chờ xét duyệt", value: teams.filter((t) => t.state === "PENDING_REVIEW").length },
    { label: "Tạm khóa", value: teams.filter((t) => t.state === "SUSPENDED").length },
    { label: "Tổng thành viên", value: teams.reduce((acc, t) => acc + (t.memberCount || 0), 0) },
  ];

  return (
    <>
      <WorkspaceHeader
        action={() => setDrawer({ mode: "create" })}
        eyebrow="Đối tác nội dung"
        stats={teamStats}
        title="Quản lý team"
      />
      <AdminTable
        actions={(team) => (
          <div className={tableStyles.actionIconsGroup}>
            <button
              className={tableStyles.iconBtn}
              onClick={() => setDrawer({ mode: "edit", team })}
              title="Xét duyệt / Sửa team"
              type="button"
            >
              <Pencil style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
            <button
              className={tableStyles.iconBtn}
              onClick={() => setDrawer({ mode: "archive", team })}
              title="Tạm khóa team"
              type="button"
            >
              <Eye style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
            <button
              className={`${tableStyles.iconBtn} ${tableStyles.iconBtnDanger}`}
              onClick={() => setDrawer({ mode: "delete", team })}
              title="Xóa team vĩnh viễn"
              type="button"
            >
              <Trash2 style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
          </div>
        )}
        columns={teamColumns}
        rowClassName={(team) => (team.state === "ACTIVE" ? tableStyles.rowPublished : team.state === "PENDING_REVIEW" ? tableStyles.rowPending : tableStyles.rowDraft)}
        rowKey={(team) => team.id}
        rows={teams}
        searchPlaceholder="Tìm theo tên team, đại diện, giới thiệu..."
        searchValues={(team) => [team.name, team.ownerName, team.description, team.slug]}
      />
      {drawer && <Drawer close={() => setDrawer(null)} description={selected?.description ?? "Tạo hồ sơ team và chỉ định chủ sở hữu."} title={drawer.mode === "delete" ? "Xóa team vĩnh viễn" : drawer.mode === "archive" ? "Tạm khóa team" : selected ? "Cập nhật team" : "Tạo team"}>
        <form className="drawerForm" onSubmit={submit}>
          {drawer.mode === "delete" && selected ? (
            <DeleteConfirmation
              confirmation={deleteConfirmation}
              entityLabel="team"
              name={selected.name}
              onChange={setDeleteConfirmation}
              warning="Team và toàn bộ thành viên sẽ bị xóa, không khôi phục được. Nếu chỉ muốn dừng hoạt động, hãy dùng “Tạm khóa”."
            />
          ) : drawer.mode === "archive" && selected ? <p className="drawerConfirm">Team sẽ bị tạm khóa. Truyện đã xuất bản vẫn được giữ để admin tiếp tục xử lý.</p> : <>
            <Field label="Tên team"><input defaultValue={selected?.name} maxLength={160} name="name" required /></Field><Field hint={<>Chữ thường không dấu, nối bằng dấu gạch ngang. Ví dụ: “Nhà Dịch Ánh Trăng” → <code>nha-dich-anh-trang</code></>} label="Slug (đường dẫn)"><input defaultValue={selected?.slug} maxLength={80} name="slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="nha-dich-anh-trang" required /></Field><Field label="Giới thiệu"><textarea defaultValue={selected?.description} maxLength={2000} name="description" required rows={5} /></Field><Field label="Chủ sở hữu"><select defaultValue={selected?.ownerUserId} name="ownerUserId" required><option value="">Chọn người dùng</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName || user.email}</option>)}</select></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "PENDING_REVIEW"} name="state"><option value="PENDING_REVIEW">Chờ xét duyệt</option><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field>
          </>}
          <MutationNotice error={error} onDismiss={() => setError("")} /><FormActions busy={busy || (drawer.mode === "delete" && deleteConfirmation.trim() !== (selected?.name.trim() ?? ""))} close={() => setDrawer(null)} confirmMessage={drawer.mode === "delete" ? `Xóa vĩnh viễn team "${selected?.name}"?` : drawer.mode === "archive" ? `Tạm khóa team "${selected?.name}"?` : selected ? `Cập nhật team "${selected.name}"?` : undefined} submitLabel={drawer.mode === "delete" ? "Xóa vĩnh viễn" : drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu team"} />
        </form>
      </Drawer>}
    </>
  );
}

export function UserCrudWorkspace({ users: initialUsers }: Readonly<{ users: AdminUserRow[] }>) {
  const [users, setUsers] = useState<AdminUserRow[]>(initialUsers);
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; user?: AdminUserRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [deleteConfirmation, setDeleteConfirmation] = useState("");
  const selected = drawer?.user;

  useEffect(() => {
    setDeleteConfirmation("");
  }, [drawer]);

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
      if (drawer.mode === "delete" && selected) await adminMutation(`content/users/${selected.id}/permanent`, "DELETE");
      else if (drawer.mode === "archive" && selected) await adminMutation(`content/users/${selected.id}`, "DELETE");
      else { const form = new FormData(event.currentTarget); await adminMutation(selected ? `content/users/${selected.id}` : "content/users", selected ? "PUT" : "POST", { bio: value(form, "bio"), displayName: value(form, "displayName"), email: value(form, "email"), ...(selected ? {} : { password: value(form, "password") }), roles: roles(form), state: value(form, "state") }); }
      setDrawer(null); await refreshData();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi."); } finally { setBusy(false); }
  }

  const userColumns: Array<Column<AdminUserRow, string>> = [
    {
      key: "displayName",
      label: "Tên / Email",
      render: (user) => (
        <div>
          <strong style={{ fontSize: "0.88rem", color: "#0f172a", display: "block" }}>{user.displayName || user.email}</strong>
          <span style={{ fontSize: "0.75rem", color: "#64748b" }}>{user.email}</span>
        </div>
      ),
      sortValue: (user) => user.displayName || user.email,
    },
    {
      key: "availableXu",
      label: "Số dư Xu",
      numeric: true,
      render: (user) => `${numberFormatter.format(user.availableXu)} xu`,
      sortValue: (user) => user.availableXu,
    },
    {
      key: "roles",
      label: "Vai trò",
      render: (user) => {
        const isAdmin = user.roles?.toUpperCase().includes("ADMIN");
        return (
          <span
            style={{
              padding: "0.2rem 0.55rem",
              borderRadius: "4px",
              fontSize: "0.72rem",
              fontWeight: 750,
              background: isAdmin ? "#dbeafe" : "#f1f5f9",
              color: isAdmin ? "#1d4ed8" : "#475569",
            }}
          >
            {isAdmin ? "Quản trị viên" : "Độc giả"}
          </span>
        );
      },
      sortValue: (user) => user.roles,
    },
    {
      key: "state",
      label: "Trạng thái",
      render: (user) => {
        const isActive = user.state === "ACTIVE";
        return (
          <span
            style={{
              padding: "0.2rem 0.55rem",
              borderRadius: "4px",
              fontSize: "0.72rem",
              fontWeight: 750,
              background: isActive ? "#dcfce7" : "#fee2e2",
              color: isActive ? "#15803d" : "#b91c1c",
            }}
          >
            {translateStatus(user.state)}
          </span>
        );
      },
      sortValue: (user) => user.state,
    },
    {
      key: "createdAt",
      label: "Ngày tham gia",
      render: (user) => formatShortDate(user.createdAt),
      sortValue: (user) => user.createdAt,
    },
  ];

  const userStats = [
    { label: "Tổng tài khoản", value: users.length },
    { label: "Đang hoạt động", value: users.filter((u) => u.state === "ACTIVE").length },
    { label: "Quản trị viên", value: users.filter((u) => u.roles?.toUpperCase().includes("ADMIN")).length },
    { label: "Tạm khóa", value: users.filter((u) => u.state === "SUSPENDED").length },
    { label: "Tổng số dư xu", value: `${numberFormatter.format(users.reduce((acc, u) => acc + (u.availableXu || 0), 0))} xu` },
  ];

  return (
    <>
      <WorkspaceHeader
        action={() => setDrawer({ mode: "create" })}
        eyebrow="Tài khoản và phân quyền"
        stats={userStats}
        title="Quản lý người dùng"
      />
      <AdminTable
        actions={(user) => (
          <div className={tableStyles.actionIconsGroup}>
            <button
              className={tableStyles.iconBtn}
              onClick={() => setDrawer({ mode: "edit", user })}
              title="Chỉnh sửa tài khoản"
              type="button"
            >
              <Pencil style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
            <button
              className={tableStyles.iconBtn}
              onClick={() => setDrawer({ mode: "archive", user })}
              title="Tạm khóa tài khoản"
              type="button"
            >
              <Eye style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
            <button
              className={`${tableStyles.iconBtn} ${tableStyles.iconBtnDanger}`}
              onClick={() => setDrawer({ mode: "delete", user })}
              title="Xóa tài khoản vĩnh viễn"
              type="button"
            >
              <Trash2 style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
          </div>
        )}
        columns={userColumns}
        rowClassName={(user) => (user.state === "ACTIVE" ? tableStyles.rowPublished : tableStyles.rowDraft)}
        rowKey={(user) => user.id}
        rows={users}
        searchPlaceholder="Tìm theo tên, email, vai trò..."
      />
      {drawer && <Drawer close={() => setDrawer(null)} description={selected?.email ?? "Tạo tài khoản thử nghiệm hoặc tài khoản vận hành."} title={drawer.mode === "delete" ? "Xóa tài khoản vĩnh viễn" : drawer.mode === "archive" ? "Tạm khóa người dùng" : selected ? "Chỉnh sửa người dùng" : "Tạo người dùng"}><form className="drawerForm" onSubmit={submit}>{drawer.mode === "delete" && selected ? <DeleteConfirmation confirmation={deleteConfirmation} entityLabel="tài khoản (email)" name={selected.email} onChange={setDeleteConfirmation} warning="Tài khoản sẽ bị xóa khỏi hệ thống và không khôi phục được. Nếu chỉ muốn chặn đăng nhập, hãy dùng “Tạm khóa”." /> : drawer.mode === "archive" && selected ? <p className="drawerConfirm">Tài khoản sẽ bị tạm khóa và toàn bộ phiên đăng nhập cũ mất hiệu lực.</p> : <><Field label="Tên hiển thị"><input defaultValue={selected?.displayName} maxLength={100} name="displayName" required /></Field><Field label="Email"><input defaultValue={selected?.email} maxLength={254} name="email" required type="email" /></Field>{!selected && <Field label="Mật khẩu ban đầu"><input minLength={12} name="password" required type="password" /></Field>}<Field label="Giới thiệu"><textarea defaultValue={selected?.bio} maxLength={1000} name="bio" rows={4} /></Field><Field label="Vai trò"><select defaultValue={selected?.roles?.toUpperCase().includes("ADMIN") ? "ADMIN" : "READER"} name="roles"><option value="READER">Độc giả</option><option value="ADMIN">Quản trị viên</option></select></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "ACTIVE"} name="state"><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field></>}<MutationNotice error={error} onDismiss={() => setError("")} /><FormActions busy={busy || (drawer.mode === "delete" && deleteConfirmation.trim() !== (selected?.email.trim() ?? ""))} close={() => setDrawer(null)} confirmMessage={drawer.mode === "delete" ? `Xóa vĩnh viễn tài khoản "${selected?.email}"?` : drawer.mode === "archive" ? `Tạm khóa tài khoản "${selected?.email}"?` : selected ? `Cập nhật tài khoản "${selected.email}"?` : undefined} submitLabel={drawer.mode === "delete" ? "Xóa vĩnh viễn" : drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu người dùng"} /></form></Drawer>}
    </>
  );
}

export function CashFlowCrudWorkspace({ entries: initialEntries, users: initialUsers }: Readonly<{ entries: AdminCashFlowRow[]; users: AdminUserRow[] }>) {
  const [entries, setEntries] = useState<AdminCashFlowRow[]>(initialEntries);
  const [users, setUsers] = useState<AdminUserRow[]>(initialUsers);
  const [drawer, setDrawer] = useState<{ mode: DrawerMode; entry?: AdminCashFlowRow } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selected = drawer?.entry;
  // Reversing moves real balances, so the button stays inert until the admin
  // ticks the confirmation.
  const [confirmed, setConfirmed] = useState(false);

  useEffect(() => {
    setConfirmed(false);
  }, [drawer]);

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

  const cashFlowColumns: Array<Column<AdminCashFlowRow, string>> = [
    {
      key: "entryType",
      label: "Giao dịch",
      render: (entry) => (
        <div>
          <strong style={{ fontSize: "0.88rem", color: "#0f172a", display: "block" }}>
            {ENTRY_TYPE_LABELS[entry.entryType] ?? entry.description}
          </strong>
          <span style={{ fontSize: "0.75rem", color: "#64748b" }}>{entry.description}</span>
        </div>
      ),
      sortValue: (entry) => entry.entryType,
    },
    {
      key: "amountXu",
      label: "Số Xu",
      numeric: true,
      render: (entry) => (
        <strong style={{ color: entry.amountXu > 0 ? "#16a34a" : "#dc2626", fontSize: "0.9rem" }}>
          {entry.amountXu > 0 ? "+" : ""}{numberFormatter.format(entry.amountXu)} xu
        </strong>
      ),
      sortValue: (entry) => entry.amountXu,
    },
    {
      key: "userEmail",
      label: "Tài khoản",
      render: (entry) => entry.userEmail || "—",
      sortValue: (entry) => entry.userEmail || "",
    },
    {
      key: "referenceType",
      label: "Tham chiếu",
      render: (entry) => REFERENCE_LABELS[entry.referenceType] ?? entry.referenceType,
      sortValue: (entry) => entry.referenceType,
    },
    {
      key: "createdAt",
      label: "Thời gian",
      render: (entry) => formatShortDate(entry.createdAt),
      sortValue: (entry) => entry.createdAt,
    },
  ];

  const cashFlowStats = [
    { label: "Tổng bút toán", value: entries.length },
    {
      label: "Tổng xu nạp (+)",
      value: `${numberFormatter.format(entries.filter((e) => e.amountXu > 0).reduce((acc, e) => acc + e.amountXu, 0))} xu`,
    },
    {
      label: "Tổng xu hoàn (-)",
      value: `${numberFormatter.format(Math.abs(entries.filter((e) => e.amountXu < 0).reduce((acc, e) => acc + e.amountXu, 0)))} xu`,
    },
    { label: "Giao dịch nạp", value: entries.filter((e) => e.entryType === "TOPUP").length },
    { label: "Bút toán hoàn tiền", value: entries.filter((e) => e.entryType === "REVERSAL").length },
  ];

  return (
    <>
      <WorkspaceHeader
        action={() => setDrawer({ mode: "create" })}
        actionLabel="Tạo bút toán"
        eyebrow="Sổ cái xu"
        stats={cashFlowStats}
        title="Quản lý doanh thu"
      />
      <AdminTable
        actions={(entry) => (
          <div className={tableStyles.actionIconsGroup}>
            <button
              className={tableStyles.iconBtn}
              disabled={entry.entryType === "REVERSAL"}
              onClick={() => setDrawer({ entry, mode: "reverse" })}
              title={entry.entryType === "REVERSAL" ? "Đã hoàn tiền" : "Hoàn tiền"}
              type="button"
            >
              <RotateCcw style={{ width: "0.85rem", height: "0.85rem" }} />
            </button>
          </div>
        )}
        columns={cashFlowColumns}
        rowClassName={(entry) => (entry.amountXu > 0 ? tableStyles.rowPublished : tableStyles.rowDraft)}
        rowKey={(entry) => entry.id}
        rows={entries}
        searchPlaceholder="Tìm theo email, loại giao dịch, nội dung..."
        searchValues={(entry) => [entry.userEmail, entry.description, entry.entryType, entry.referenceType]}
      />

      {drawer && (
        <Drawer
          close={() => setDrawer(null)}
          description={selected
            ? `Bút toán ${selected.id} · ghi nhận lúc ${formatDateTime(selected.createdAt)}`
            : "Mỗi thay đổi được ghi thành một bút toán mới để bảo toàn lịch sử."}
          title={selected ? "Hoàn tiền giao dịch" : "Tạo bút toán"}
        >
          <form className="drawerForm" onSubmit={submit}>
            {selected ? (
              <>
                <p className="drawerConfirm">
                  Hệ thống sẽ tạo bút toán mới với số tiền đối ứng
                  ({numberFormatter.format(-selected.amountXu)} xu). Bản ghi gốc không bị xóa.
                </p>
                <Field label="Lý do hoàn tiền (bắt buộc)">
                  <textarea
                    maxLength={300}
                    name="reason"
                    placeholder="Ví dụ: giao dịch trùng, người dùng báo sai số tiền…"
                    required
                    rows={4}
                  />
                </Field>
                {/* Money moves on submit, so the intent is confirmed explicitly
                    rather than relying on the reader not to misclick. */}
                <label className="drawerConfirmCheck">
                  <input
                    checked={confirmed}
                    onChange={(event) => setConfirmed(event.currentTarget.checked)}
                    type="checkbox"
                  />
                  <span>Tôi xác nhận hoàn {numberFormatter.format(Math.abs(selected.amountXu))} xu cho {selected.userEmail}.</span>
                </label>
              </>
            ) : (
              <>
                <Field label="Người dùng">
                  <select name="userId" required>
                    <option value="">Chọn tài khoản</option>
                    {users.map((user) => (
                      <option key={user.id} value={user.id}>{user.displayName || user.email}</option>
                    ))}
                  </select>
                </Field>
                <div className="drawerFieldGrid">
                  <Field label="Loại giao dịch">
                    <select name="entryType">
                      <option value="TOPUP">Nạp xu</option>
                      <option value="DONATION">Ủng hộ</option>
                      <option value="ADJUSTMENT">Điều chỉnh</option>
                      <option value="REWARD">Thưởng</option>
                    </select>
                  </Field>
                  <Field label="Số xu">
                    <input name="amountXu" required type="number" />
                  </Field>
                </div>
                <Field label="Loại tham chiếu">
                  <input defaultValue="ADMIN_ADJUSTMENT" name="referenceType" pattern="[A-Z_]{3,40}" required />
                </Field>
                <Field label="Mã tham chiếu">
                  <input defaultValue="admin-adjustment" maxLength={100} name="referenceId" required />
                </Field>
                <Field label="Nội dung">
                  <textarea maxLength={500} name="description" required rows={4} />
                </Field>
              </>
            )}
            <MutationNotice error={error} onDismiss={() => setError("")} />
            <FormActions
              busy={busy || (Boolean(selected) && !confirmed)}
              close={() => setDrawer(null)}
              submitLabel={selected ? "Xác nhận hoàn tiền" : "Ghi bút toán"}
            />
          </form>
        </Drawer>
      )}
    </>
  );
}

/** Ledger vocabulary, in the language the admin screen is written in. */
const ENTRY_TYPE_LABELS: Record<string, string> = {
  ADJUSTMENT: "Điều chỉnh thủ công",
  DONATION: "Ủng hộ đội ngũ",
  PURCHASE: "Mua chương",
  REVERSAL: "Bút toán hoàn tiền",
  REWARD: "Thưởng nhiệm vụ",
  TOPUP: "Nạp xu",
  WITHDRAWAL: "Rút tiền",
};

const REFERENCE_LABELS: Record<string, string> = {
  ADMIN_ADJUSTMENT: "Điều chỉnh của quản trị viên",
  PURCHASE_ORDER: "Đơn mua chương",
  QUEST: "Nhiệm vụ",
  STORY_PROMOTION: "Bố cáo truyện",
  TOPUP: "Yêu cầu nạp xu",
};
