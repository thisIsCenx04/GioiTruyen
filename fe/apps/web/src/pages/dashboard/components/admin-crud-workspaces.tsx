"use client";

import { ImagePlus, Paperclip, Plus, X } from "lucide-react";
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
import { getAccessToken, refreshAccessToken } from "../../../lib/auth";
import { type ImportedStory, parseStoryDocument, parseStoryFile } from "./story-import";

type DrawerMode = "archive" | "create" | "edit" | "reverse";
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
  onChange,
}: Readonly<{
  chapters: StoryChapterDraft[];
  onChange: (chapters: StoryChapterDraft[]) => void;
}>) {
  const [error, setError] = useState("");
  const [bulkFreeCount, setBulkFreeCount] = useState(5);
  const [bulkPrice, setBulkPrice] = useState(5);

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
          {chapters.map((chapter, index) => (
            <article className="chapterDraftItem" key={chapter.id}>
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
            </article>
          ))}
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
      description: "Nhiều chương, đăng dần theo thời gian.",
      key: "SERIAL" as const,
      title: "Truyện dài",
    },
    {
      description: "Đọc trọn trong một trang, không chia chương.",
      key: "ONESHOT" as const,
      title: "Truyện ngắn Zhihu",
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
  const toggle = (id: string) => {
    onChange(selected.includes(id) ? selected.filter((value) => value !== id) : [...selected, id]);
  };
  const chosen = categories.filter((category) => selected.includes(category.id));

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
      <div className="genreOptionGrid">
        {categories.map((category) => (
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
}: Readonly<{
  busy: boolean;
  onImported: (imported: ImportedStory) => void;
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
      const imported = await parseStoryDocument(file);
      onImported(imported);
      setSummary(
        `Đã đọc "${file.name}": ${imported.chapters.length} chương`
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
            Hỗ trợ .docx, .txt, .md. Hệ thống đọc tên truyện, tác giả, giới thiệu và tách chương;
            bạn vẫn sửa tay được ở các ô bên dưới.
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
  if (input.isOneshot && !input.oneshotContent.trim()) {
    return "Truyện ngắn Zhihu cần có nội dung. Hãy dán nội dung hoặc upload file truyện.";
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
  const [activeTab, setActiveTab] = useState<"content" | "general">("content");
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
    if (!drawer || drawer.mode === "archive") {
      setCoverFile(null);
      setCoverPreview("");
      setStoryTags([]);
      setChapterDrafts([]);
      return;
    }

    setActiveTab("content");
    setCoverFile(null);
    setCoverPreview(selected?.coverUrl ?? "");
    setStoryTags(selected?.tags ?? []);
    setChapterDrafts([]);
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
        .then((res) => (res.ok ? res.json() : []))
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
        .catch(() => {});
    }
  }, [drawer, selected]);

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

        const problem = validateStoryDraft({
          chapterCount: chapterDrafts.length,
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

        // A one-shot still stores its text as a chapter row - exactly one, named
        // after the story - so the reader and unlock logic need no special case.
        const submittedChapters: StoryChapterDraft[] = isOneshot
          ? (oneshotContent.trim()
            ? [{ content: oneshotContent, id: "oneshot", tags: [], title: value(form, "title") }]
            : [])
          : chapterDrafts;

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
                {/* 2-Tab Navigation Header */}
                <div
                  style={{
                    display: "flex",
                    gap: "0.5rem",
                    borderBottom: "2px solid #e2e8f0",
                    marginBottom: "1.25rem",
                    paddingBottom: "0.25rem"
                  }}
                >
                  <button
                    type="button"
                    onClick={() => setActiveTab("content")}
                    style={{
                      padding: "0.65rem 1.25rem",
                      fontWeight: 700,
                      fontSize: "0.9rem",
                      borderRadius: "8px 8px 0 0",
                      border: "none",
                      background: activeTab === "content" ? "#0f6bff" : "rgba(241, 245, 249, 0.8)",
                      color: activeTab === "content" ? "#fff" : "#475569",
                      cursor: "pointer",
                      transition: "all 0.2s ease"
                    }}
                  >
                    📝 Edit Nội Dung & Chương ({isOneshot ? "1 phần" : `${chapterDrafts.length} chương`})
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveTab("general")}
                    style={{
                      padding: "0.65rem 1.25rem",
                      fontWeight: 700,
                      fontSize: "0.9rem",
                      borderRadius: "8px 8px 0 0",
                      border: "none",
                      background: activeTab === "general" ? "#0f6bff" : "rgba(241, 245, 249, 0.8)",
                      color: activeTab === "general" ? "#fff" : "#475569",
                      cursor: "pointer",
                      transition: "all 0.2s ease"
                    }}
                  >
                    ⚙️ Edit Thông Tin Chung
                  </button>
                </div>

                {/* TAB 1: Edit Nội Dung (Nội dung từng chương & Khoá xu) */}
                {activeTab === "content" && (
                  <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
                    <StoryFormatPicker
                      onChange={setStoryFormat}
                      value={storyFormat}
                    />
                    <StoryDocumentImport
                      busy={busy}
                      onImported={(imported) => {
                        setStoryTitle(imported.title);
                        setStorySlug(buildStorySlug(imported.title));
                        if (imported.authorName) setStoryAuthor(imported.authorName);
                        if (imported.synopsis) setStorySynopsis(imported.synopsis);
                        setCompletionStatus(imported.completionStatus);
                        if (isOneshot) {
                          setOneshotContent(imported.chapters.map((chapter) => chapter.content).join("\n\n"));
                        } else {
                          setChapterDrafts(imported.chapters.map((chapter, index) => ({
                            content: chapter.content,
                            id: `imported-${index}-${Date.now()}`,
                            tags: [],
                            title: chapter.title,
                            accessType: "FREE",
                            coinPrice: 0,
                          })));
                        }
                      }}
                    />
                    {isOneshot ? (
                      <OneshotContentEditor onChange={setOneshotContent} value={oneshotContent} />
                    ) : (
                      <ChapterImportWorkspace chapters={chapterDrafts} onChange={setChapterDrafts} />
                    )}
                  </div>
                )}

                {/* TAB 2: Edit Thông Tin Chung (Tên, Ảnh bìa, Tác giả, Synopsis, Thể loại, Team, Status...) */}
                {activeTab === "general" && (
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
                    </div>
                    <TagEditor label="Tag truyện" onChange={setStoryTags} placeholder="Ví dụ: shounen, fantasy" tags={storyTags} />
                  </div>
                )}
              </>
            )}
            <MutationNotice error={error} onDismiss={() => setError("")} />
            <FormActions
              busy={busy}
              close={() => setDrawer(null)}
              confirmMessage={drawer.mode === "archive"
                ? `Ngừng hiển thị truyện "${selected?.title}"?`
                : selected
                  ? `Cập nhật truyện "${selected.title}"?`
                  : undefined}
              submitLabel={drawer.mode === "archive" ? "Xác nhận lưu trữ" : "Lưu truyện"}
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
          <MutationNotice error={error} onDismiss={() => setError("")} /><FormActions busy={busy} close={() => setDrawer(null)} confirmMessage={drawer.mode === "archive" ? `Ẩn thể loại "${selected?.name}"?` : selected ? `Cập nhật thể loại "${selected.name}"?` : undefined} submitLabel={drawer.mode === "archive" ? "Xác nhận ẩn" : "Lưu thể loại"} />
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
        <Field label="Tên team"><input defaultValue={selected?.name} maxLength={160} name="name" required /></Field><Field hint={<>Chữ thường không dấu, nối bằng dấu gạch ngang. Ví dụ: “Nhà Dịch Ánh Trăng” → <code>nha-dich-anh-trang</code></>} label="Slug (đường dẫn)"><input defaultValue={selected?.slug} maxLength={80} name="slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="nha-dich-anh-trang" required /></Field><Field label="Giới thiệu"><textarea defaultValue={selected?.description} maxLength={2000} name="description" required rows={5} /></Field><Field label="Chủ sở hữu"><select defaultValue={selected?.ownerUserId} name="ownerUserId" required><option value="">Chọn người dùng</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName || user.email}</option>)}</select></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "PENDING_REVIEW"} name="state"><option value="PENDING_REVIEW">Chờ xét duyệt</option><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field>
      </>}<MutationNotice error={error} onDismiss={() => setError("")} /><FormActions busy={busy} close={() => setDrawer(null)} confirmMessage={drawer.mode === "archive" ? `Tạm khóa team "${selected?.name}"?` : selected ? `Cập nhật team "${selected.name}"?` : undefined} submitLabel={drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu team"} />
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

  return <><WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Tài khoản và phân quyền" title="Quản lý người dùng" /><SortToolbar columns={columns} onSort={toggleSort} sortState={sortState} /><section className="adminCrudPanel">{sortedList.map((user) => <article key={user.id}><div className="adminCrudDetails"><strong>{user.displayName || user.email}</strong><small>{user.email} · Ví {numberFormatter.format(user.availableXu)} XU · {user.roles}</small></div><span>{translateStatus(user.state)}</span><div className="adminCrudActions"><button onClick={() => setDrawer({ mode: "edit", user })} type="button">Chỉnh sửa</button><button onClick={() => setDrawer({ mode: "archive", user })} type="button">Tạm khóa</button></div></article>)}</section>{drawer && <Drawer close={() => setDrawer(null)} description={selected?.email ?? "Tạo tài khoản thử nghiệm hoặc tài khoản vận hành."} title={drawer.mode === "archive" ? "Tạm khóa người dùng" : selected ? "Chỉnh sửa người dùng" : "Tạo người dùng"}><form className="drawerForm" onSubmit={submit}>{drawer.mode === "archive" && selected ? <p className="drawerConfirm">Tài khoản sẽ bị tạm khóa và toàn bộ phiên đăng nhập cũ mất hiệu lực.</p> : <><Field label="Tên hiển thị"><input defaultValue={selected?.displayName} maxLength={100} name="displayName" required /></Field><Field label="Email"><input defaultValue={selected?.email} maxLength={254} name="email" required type="email" /></Field>{!selected && <Field label="Mật khẩu ban đầu"><input minLength={12} name="password" required type="password" /></Field>}<Field label="Giới thiệu"><textarea defaultValue={selected?.bio} maxLength={1000} name="bio" rows={4} /></Field><Field label="Vai trò, cách nhau bằng dấu phẩy"><input defaultValue={selected?.roles || "USER"} name="roles" required /></Field><Field label="Trạng thái"><select defaultValue={selected?.state ?? "ACTIVE"} name="state"><option value="ACTIVE">Đang hoạt động</option><option value="SUSPENDED">Tạm khóa</option></select></Field></>}<MutationNotice error={error} onDismiss={() => setError("")} /><FormActions busy={busy} close={() => setDrawer(null)} confirmMessage={drawer.mode === "archive" ? `Tạm khóa tài khoản "${selected?.email}"?` : selected ? `Cập nhật tài khoản "${selected.email}"?` : undefined} submitLabel={drawer.mode === "archive" ? "Xác nhận tạm khóa" : "Lưu người dùng"} /></form></Drawer>}</>;
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

  return <><WorkspaceHeader action={() => setDrawer({ mode: "create" })} eyebrow="Sổ cái XU" title="Dòng tiền" /><SortToolbar columns={columns} onSort={toggleSort} sortState={sortState} /><section className="adminCrudPanel">{sortedList.map((entry) => <article key={entry.id}><div className="adminCrudDetails"><strong>{entry.description}</strong><small>{entry.userEmail} · {entry.referenceType}/{entry.referenceId}</small></div><span>{entry.amountXu > 0 ? "+" : ""}{numberFormatter.format(entry.amountXu)} XU</span><div className="adminCrudActions"><button disabled={entry.entryType === "REVERSAL"} onClick={() => setDrawer({ entry, mode: "reverse" })} type="button">Hoàn ngược</button></div></article>)}</section>{drawer && <Drawer close={() => setDrawer(null)} description={selected ? `Bút toán ${selected.id}` : "Mỗi thay đổi được ghi thành một bút toán mới để bảo toàn lịch sử."} title={selected ? "Hoàn ngược giao dịch" : "Tạo bút toán"}><form className="drawerForm" onSubmit={submit}>{selected ? <><p className="drawerConfirm">Hệ thống sẽ tạo bút toán mới với số tiền đối ứng. Bản ghi gốc không bị xóa.</p><Field label="Lý do hoàn ngược"><textarea maxLength={300} name="reason" required rows={4} /></Field></> : <><Field label="Người dùng"><select name="userId" required><option value="">Chọn tài khoản</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName || user.email}</option>)}</select></Field><div className="drawerFieldGrid"><Field label="Loại giao dịch"><select name="entryType"><option value="TOPUP">Nạp XU</option><option value="DONATION">Ủng hộ</option><option value="ADJUSTMENT">Điều chỉnh</option><option value="REWARD">Thưởng</option></select></Field><Field label="Số XU"><input name="amountXu" required type="number" /></Field></div><Field label="Loại tham chiếu"><input defaultValue="ADMIN_ADJUSTMENT" name="referenceType" pattern="[A-Z_]{3,40}" required /></Field><Field label="Mã tham chiếu"><input defaultValue="admin-adjustment" maxLength={100} name="referenceId" required /></Field><Field label="Nội dung"><textarea maxLength={500} name="description" required rows={4} /></Field></>}<MutationNotice error={error} onDismiss={() => setError("")} /><FormActions busy={busy} close={() => setDrawer(null)} submitLabel={selected ? "Tạo bút toán hoàn ngược" : "Ghi bút toán"} /></form></Drawer>}</>;
}
