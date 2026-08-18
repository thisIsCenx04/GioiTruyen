"use client";

import { Loader2, Paperclip, Plus, Save, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { PublisherTabs } from "@/components/publisher-tabs";
import { PublicShell } from "@/components/site-chrome";
import { coverUrl, StoryCoverPlaceholder } from "@/components/story-cover";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
// The same parser the admin drawer uses, so a file dropped here is split into
// chapters exactly the way it would be on the admin side.
import {
  parseStoryDocument,
  WORDS_PER_CHAPTER,
  WORDS_PER_CHAPTER_ZHIHU,
} from "@/pages/dashboard/components/story-import";

/**
 * Story management for a publishing team.
 *
 * Replaces a 786-line mock that rendered an invented revision workflow and
 * called a `/publishing/*` API which never existed - every action answered 403,
 * reported to the user as "Bạn không có quyền thực hiện thao tác này". This talks
 * to the real endpoints and only shows actions the API can actually perform.
 */

type StoryRow = {
  id: string;
  slug: string;
  title: string;
  coverUrl: string | null;
  status: string;
  progressStatus: string;
  storyFormat: string;
  storyType: string;
  viewCount: number;
  chapterCount: number;
  publishedChapterCount: number;
  comboPriceXu: number | null;
  originalAuthor: string | null;
  shortDescription: string | null;
};

type ChapterRow = {
  id: string;
  chapterNumber: number;
  title: string | null;
  accessType: string;
  coinPrice: number;
  status: string;
  contentLength: number;
  /** Chapter text, so it can be loaded into the editor. */
  content: string | null;
};

type Category = { id: string; name: string; slug: string };

/** One chapter as edited in the form, before it is posted. */
type ChapterDraft = {
  title: string;
  content: string;
  accessType: "FREE" | "PAID";
  coinPrice: number;
  /**
   * An attached file, when the chapter came from one. A chapter carries either a
   * file or inline text, never both: the server reads `chapters[i].file` in
   * preference to `chapters[i].content` and ignores the textarea when a file is
   * present, so sending both would only inflate the upload.
   */
  file: File | null;
};

/** Same set the admin workspace accepts. */
const CHAPTER_FILE_TYPES = ".txt,.md,.doc,.docx,.pdf,.epub";

/** Slug for a chapter, derived the way the admin form derives it. */
function slugifyChapter(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/gu, "")
    .replace(/đ/gu, "d")
    .replace(/Đ/gu, "D")
    .toLowerCase()
    .replace(/[^a-z0-9]+/gu, "-")
    .replace(/^-+|-+$/gu, "");
}

/** Filename without its extension - the chapter title when a file is attached. */
function titleFromFileName(name: string): string {
  return name.replace(/\.[^.]+$/u, "");
}

/** Longest a real story title plausibly runs before it is prose, not a title. */
const TITLE_MAX_LENGTH = 120;

/**
 * Decides whether the parser found a title or just the opening sentence.
 *
 * A file with no title line above its first chapter heading makes the parser
 * offer the first line of the story instead - one sample file yielded a
 * 118-character narrative sentence. Prose gives itself away by length or by
 * ending in sentence punctuation, and in that case the filename is the better
 * title.
 */
function looksLikeProse(value: string): boolean {
  const text = value.trim();
  if (text.length > TITLE_MAX_LENGTH) return true;
  return /[.!?…]$/u.test(text) || text.split(/\s+/u).length > 14;
}

type FormState = {
  title: string;
  authorName: string;
  summary: string;
  categoryIds: string[];
  storyFormat: "SERIAL" | "ONESHOT";
  storyType: "TEXT" | "AUDIO" | "EXCLUSIVE" | "ORIGINAL";
  workflowStatus: "DRAFT" | "PENDING_REVIEW" | "PUBLISHED";
  completionStatus: "ONGOING" | "COMPLETED" | "PAUSED";
  tags: string;
  /** Blank means no bundle deal: the combo costs the sum of the chapters. */
  comboPriceXu: string;
};

const EMPTY_FORM: FormState = {
  title: "",
  authorName: "",
  summary: "",
  categoryIds: [],
  storyFormat: "SERIAL",
  storyType: "TEXT",
  workflowStatus: "DRAFT",
  completionStatus: "ONGOING",
  tags: "",
  comboPriceXu: "",
};

const STATUS_LABELS: Readonly<Record<string, string>> = {
  DRAFT: "Bản nháp",
  PENDING_REVIEW: "Chờ duyệt",
  PUBLISHED: "Đã đăng",
  REJECTED: "Bị từ chối",
  HIDDEN: "Đã ẩn",
};

const number = new Intl.NumberFormat("vi-VN");

export function PublishingWorkspace({ teamId }: Readonly<{ teamId: string }>) {
  const [stories, setStories] = useState<StoryRow[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  /** Filters the genre checkbox list; there are ~84 of them. */
  const [genreQuery, setGenreQuery] = useState("");
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [chapters, setChapters] = useState<ChapterDraft[]>([]);
  const [existingChapters, setExistingChapters] = useState<ChapterRow[]>([]);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [importSummary, setImportSummary] = useState("");
  const [state, setState] = useState<"loading" | "ready" | "forbidden" | "error">("loading");
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  /** Chapters ticked for a bulk action, by their index in the draft list. */
  const [picked, setPicked] = useState<ReadonlySet<number>>(new Set());
  /** Xu applied to the ticked chapters; blank until a figure is typed. */
  const [bulkPrice, setBulkPrice] = useState<number | ''>('');
  /** Typed confirmation, required before deleting more than a handful. */
  const [bulkDeleteConfirm, setBulkDeleteConfirm] = useState('');
  const [confirmingBulkDelete, setConfirmingBulkDelete] = useState(false);

  const togglePicked = (index: number) => setPicked((current) => {
    const next = new Set(current);
    if (next.has(index)) next.delete(index); else next.add(index);
    return next;
  });

  const toggleAllPicked = () => setPicked((current) =>
    current.size === chapters.length && chapters.length > 0
      ? new Set()
      : new Set(chapters.map((_, index) => index)));

  /** Prices only the ticked chapters. 0 means free: the server refuses a PAID
   *  chapter priced at zero, since it would unlock for nothing. */
  const applyBulkPrice = () => {
    const price = Number(bulkPrice);
    setChapters((current) => current.map((chapter, index) => (
      picked.has(index)
        ? { ...chapter, accessType: price > 0 ? 'PAID' as const : 'FREE' as const, coinPrice: price }
        : chapter
    )));
    setBulkPrice('');
  };

  const executeBulkDelete = () => {
    setChapters((current) => current.filter((_, index) => !picked.has(index)));
    setPicked(new Set());
    setConfirmingBulkDelete(false);
    setBulkDeleteConfirm('');
  };

  const loadStories = useCallback(async () => {
    const res = await authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories`);
    if (res.status === 403 || res.status === 404) {
      setState("forbidden");
      return;
    }
    if (!res.ok) {
      setState("error");
      return;
    }
    setStories((await res.json()) as StoryRow[]);
    setState("ready");
  }, [teamId]);

  useEffect(() => {
    void loadStories();
    void authedFetch(`${API_BASE_URL}/categories`)
      .then(async (res) => {
        if (!res.ok) return;
        const data = (await res.json()) as { groups?: Array<{ categories: Category[] }> };
        setCategories((data.groups ?? []).flatMap((group) => group.categories));
      })
      .catch(() => {
        // The form still submits; the genre picker is simply empty.
      });
  }, [loadStories]);

  /** Loads a story into the form, chapters included and editable. */
  async function selectStory(story: StoryRow) {
    setSelectedId(story.id);
    setNotice(null);
    setCoverFile(null);
    setChapters([]);
    setForm({
      title: story.title,
      authorName: story.originalAuthor ?? "",
      summary: story.shortDescription ?? "",
      categoryIds: [],
      storyFormat: story.storyFormat === "ONESHOT" ? "ONESHOT" : "SERIAL",
      storyType: (story.storyType as FormState["storyType"]) ?? "TEXT",
      workflowStatus: (story.status as FormState["workflowStatus"]) ?? "DRAFT",
      completionStatus: (story.progressStatus as FormState["completionStatus"]) ?? "ONGOING",
      tags: "",
      comboPriceXu: story.comboPriceXu ? String(story.comboPriceXu) : "",
    });
    const res = await authedFetch(
      `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories/${encodeURIComponent(story.id)}/chapters`,
    );
    if (!res.ok) {
      setExistingChapters([]);
      // Saving replaces the whole chapter set, so an empty draft list after a
      // failed load would wipe the story. Say so rather than let it happen.
      setNotice({
        kind: "err",
        text: "Không tải được danh sách chương. Đóng và mở lại truyện trước khi sửa chương.",
      });
      return;
    }
    const rows = (await res.json()) as ChapterRow[];
    setExistingChapters(rows);
    // Existing chapters load into the editable draft list, not a read-only
    // sidebar. They used to be listed for reference only, so a publisher could
    // never change a chapter's title, price or text after publishing it - the
    // one thing the admin form could do that this one could not.
    setChapters(rows.map((row) => ({
      title: row.title ?? "",
      content: row.content ?? "",
      accessType: row.accessType === "PAID" ? "PAID" : "FREE",
      coinPrice: row.coinPrice ?? 0,
      file: null,
    })));
  }

  function startNewStory() {
    setSelectedId(null);
    setForm(EMPTY_FORM);
    setChapters([]);
    setExistingChapters([]);
    setCoverFile(null);
    setNotice(null);
  }

  function addChapter() {
    setChapters((rows) => [
      ...rows,
      { title: "", content: "", accessType: "FREE", coinPrice: 0, file: null },
    ]);
  }

  /**
   * Reads one story file and fills the whole form from it.
   *
   * Chapter headings in the file decide the split; without them the parser falls
   * back to splitting every N words - fewer for a serial, more for a one-shot,
   * matching the admin drawer. Anything the file does not declare (title,
   * author, synopsis) is filled from the filename or left for manual entry
   * rather than overwriting what is already typed.
   */
  async function importStoryFile(file: File | null) {
    if (!file) return;
    setImporting(true);
    setImportSummary("");
    setNotice(null);
    try {
      const wordsPerChapter = form.storyFormat === "ONESHOT"
        ? WORDS_PER_CHAPTER_ZHIHU
        : WORDS_PER_CHAPTER;
      const imported = await parseStoryDocument(file, wordsPerChapter);

      // Fall back to the filename when the file had no title line of its own, so
      // the story is not named after its opening sentence.
      const fileTitle = titleFromFileName(file.name).trim();
      const parsedTitle = imported.title.trim();
      const resolvedTitle = parsedTitle && !looksLikeProse(parsedTitle)
        ? parsedTitle
        : fileTitle || parsedTitle.slice(0, TITLE_MAX_LENGTH);

      setForm((current) => ({
        ...current,
        // The file wins for fields it actually declares; a field it is silent
        // about keeps whatever is already in the form.
        title: resolvedTitle || current.title,
        authorName: imported.authorName || current.authorName,
        summary: imported.synopsis || current.summary,
        completionStatus: imported.completionStatus,
      }));
      setChapters(
        imported.chapters.map((chapter) => ({
          title: chapter.title,
          content: chapter.content,
          accessType: "FREE" as const,
          coinPrice: 0,
          file: null,
        })),
      );
      setImportSummary(
        `Đã đọc “${file.name}”: ${imported.chapters.length} chương`
        + (imported.autoSplit ? ` (tự cắt mỗi ${wordsPerChapter} từ)` : " (theo tiêu đề chương trong file)")
        + (imported.authorName ? ` · tác giả ${imported.authorName}` : "")
        + (imported.completionStatus === "COMPLETED" ? " · đã hoàn" : ""),
      );
    } catch (cause) {
      setNotice({
        kind: "err",
        text: cause instanceof Error ? cause.message : "Không đọc được file truyện.",
      });
    } finally {
      setImporting(false);
    }
  }

  /** One picked file becomes one chapter, titled after the file. */
  function addChaptersFromFiles(files: readonly File[]) {
    if (files.length === 0) return;
    setChapters((rows) => [
      ...rows,
      ...files.map((file) => ({
        title: titleFromFileName(file.name),
        content: "",
        accessType: "FREE" as const,
        coinPrice: 0,
        file,
      })),
    ]);
  }

  function updateChapter(index: number, patch: Partial<ChapterDraft>) {
    setChapters((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  async function handleSave() {
    if (!form.title.trim()) {
      setNotice({ kind: "err", text: "Chưa nhập tên truyện." });
      return;
    }
    if (form.categoryIds.length === 0) {
      setNotice({ kind: "err", text: "Chọn ít nhất một thể loại." });
      return;
    }
    const paidWithoutPrice = chapters.some((c) => c.accessType === "PAID" && c.coinPrice <= 0);
    if (paidWithoutPrice) {
      setNotice({ kind: "err", text: "Chương trả phí phải có giá lớn hơn 0 Xu." });
      return;
    }
    // The server refuses a chapter with no content rather than dropping it
    // silently; saying so here names the chapter before the upload starts.
    const emptyIndex = chapters.findIndex((c) => !c.file && !c.content.trim());
    if (emptyIndex >= 0) {
      const chapter = chapters[emptyIndex]!;
      setNotice({
        kind: "err",
        text: `Chương ${emptyIndex + 1}${chapter.title ? ` ("${chapter.title}")` : ""} chưa có nội dung. `
          + "Hãy nhập nội dung hoặc đính kèm file, hoặc xoá chương này.",
      });
      return;
    }

    setSaving(true);
    setNotice(null);
    try {
      // Multipart, because a cover file and chapter bodies travel with the form.
      const body = new FormData();
      body.append("teamId", teamId);
      body.append("title", form.title.trim());
      if (form.authorName.trim()) body.append("authorName", form.authorName.trim());
      if (form.summary.trim()) body.append("summary", form.summary.trim());
      // One comma-joined field, not one part per id: the server reads this with
      // getParameter(), which returns only the first value of a repeated part -
      // so appending them separately silently saved just one genre.
      body.append("categoryIds", form.categoryIds.join(","));
      body.append("storyFormat", form.storyFormat);
      body.append("storyType", form.storyType);
      body.append("workflowStatus", form.workflowStatus);
      body.append("completionStatus", form.completionStatus);
      // Same contract for tags.
      body.append("tags", form.tags.split(",").map((t) => t.trim()).filter(Boolean).join(","));
      // Blank clears the bundle deal, so it is always sent.
      body.append("comboPriceXu", form.comboPriceXu.trim());
      if (coverFile) body.append("coverImage", coverFile);

      // Chapters are replaced wholesale, so only opt in when the form actually
      // carries them - otherwise editing the title would wipe the chapter list.
      if (chapters.length > 0) {
        body.append("replaceChapters", "true");
        chapters.forEach((chapter, index) => {
          const title = chapter.title || titleFromFileName(chapter.file?.name ?? "") || `Chương ${index + 1}`;
          // Either the file or the inline text, never both - the server prefers
          // the file and ignores the textarea when one is attached.
          if (chapter.file) {
            body.append(`chapters[${index}].file`, chapter.file);
          } else {
            body.append(`chapters[${index}].content`, chapter.content);
          }
          body.append(`chapters[${index}].title`, title);
          body.append(`chapters[${index}].slug`, slugifyChapter(title) || `chuong-${index + 1}`);
          body.append(`chapters[${index}].accessType`, chapter.accessType);
          body.append(`chapters[${index}].coinPrice`, String(chapter.coinPrice));
        });
      }

      const path = selectedId
        ? `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories/${encodeURIComponent(selectedId)}`
        : `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories`;
      const res = await authedFetch(path, { method: selectedId ? "PUT" : "POST", body });

      if (!res.ok) {
        const problem = (await res.json().catch(() => ({}))) as { detail?: string };
        setNotice({ kind: "err", text: problem.detail ?? `Lưu thất bại (HTTP ${res.status}).` });
        return;
      }
      const saved = (await res.json()) as { id: string };
      setNotice({ kind: "ok", text: selectedId ? "Đã lưu thay đổi." : "Đã tạo truyện mới." });
      setChapters([]);
      await loadStories();
      setSelectedId(saved.id);
    } catch {
      setNotice({ kind: "err", text: "Không kết nối được máy chủ." });
    } finally {
      setSaving(false);
    }
  }

  async function handleHide() {
    if (!selectedId) return;
    setSaving(true);
    try {
      const res = await authedFetch(
        `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories/${encodeURIComponent(selectedId)}`,
        { method: "DELETE" },
      );
      if (!res.ok) {
        const problem = (await res.json().catch(() => ({}))) as { detail?: string };
        setNotice({ kind: "err", text: problem.detail ?? "Không ẩn được truyện." });
        return;
      }
      setNotice({ kind: "ok", text: "Đã ẩn truyện khỏi trang." });
      await loadStories();
      startNewStory();
    } finally {
      setSaving(false);
    }
  }

  if (state === "loading") {
    return (
      <PublicShell>
        <main className="publisherShell">
          <p className="publisherNotice">Đang tải danh sách truyện…</p>
        </main>
      </PublicShell>
    );
  }

  if (state === "forbidden" || state === "error") {
    return (
      <PublicShell>
        <main className="publisherShell">
          <div className="publisherNotice">
            <h1>
              {state === "forbidden"
                ? "Bạn chưa có quyền đăng truyện ở nhóm này"
                : "Không tải được danh sách truyện"}
            </h1>
            <p>
              {state === "forbidden"
                ? "Chỉ chủ nhóm, quản lý và biên tập mới quản lý được truyện của nhóm."
                : "Kết nối tới máy chủ đang gián đoạn. Vui lòng thử lại sau giây lát."}
            </p>
            <Link className="publisherPrimaryBtn" to="/dang-ky-dang-truyen">
              Đăng ký đăng truyện
            </Link>
          </div>
        </main>
      </PublicShell>
    );
  }

  const deleteBlocked = picked.size > 3 && bulkDeleteConfirm.trim().toUpperCase() !== 'XOA';

  return (
    <PublicShell>
      {/* Same care as the admin form: the chapters are named, and past a
          handful a click is too cheap for something that removes text. */}
      {confirmingBulkDelete ? (
        <div className="pubConfirmLayer" role="alertdialog">
          <div className="pubConfirmCard">
            <h3>Xoá {picked.size} chương khỏi danh sách?</h3>
            <ul className="pubConfirmList">
              {chapters.filter((_, index) => picked.has(index)).map((chapter, index) => (
                <li key={index}>{index + 1}. {chapter.title || '(chưa có tiêu đề)'}</li>
              ))}
            </ul>
            <p className="pubHint">
              Chỉ có hiệu lực sau khi bấm Lưu. Chương đã có độc giả mua sẽ được
              máy chủ giữ lại.
            </p>
            {picked.size > 3 ? (
              <label className="pubConfirmType">
                Gõ <strong>XOA</strong> để xác nhận:
                <input
                  autoFocus
                  onChange={(event) => setBulkDeleteConfirm(event.target.value)}
                  value={bulkDeleteConfirm}
                />
              </label>
            ) : null}
            <div className="pubConfirmActions">
              <button
                className="pubGhostBtn"
                onClick={() => { setConfirmingBulkDelete(false); setBulkDeleteConfirm(''); }}
                type="button"
              >
                Huỷ
              </button>
              <button
                className="pubDangerBtn"
                disabled={deleteBlocked}
                onClick={executeBulkDelete}
                type="button"
              >
                Xoá {picked.size} chương
              </button>
            </div>
          </div>
        </div>
      ) : null}
      <main className="publisherShell">
        <div className="publisherContainer">
          <header className="publisherHeading">
            <div>
              <h1>QUẢN LÝ TRUYỆN</h1>
              <span>{stories.length} truyện trong nhóm</span>
            </div>
          </header>

          <PublisherTabs active="stories" teamId={teamId} />

          <div className="pubWorkspace">
            {/* Story picker */}
            <aside className="publisherCard pubStoryPicker">
              <button className="pubNewBtn" onClick={startNewStory} type="button">
                <Plus aria-hidden="true" size={15} />
                Tạo truyện mới
              </button>
              <ul>
                {stories.map((story) => {
                  const cover = coverUrl(story.coverUrl);
                  return (
                    <li key={story.id}>
                      <button
                        className={story.id === selectedId ? "pubStoryBtn isActive" : "pubStoryBtn"}
                        onClick={() => void selectStory(story)}
                        type="button"
                      >
                        <span className="publisherStoryThumb">
                          {cover ? <img alt="" src={cover}  decoding="async" loading="lazy" /> : <StoryCoverPlaceholder />}
                        </span>
                        <span className="pubStoryText">
                          <strong>{story.title}</strong>
                          <small>
                            {STATUS_LABELS[story.status] ?? story.status}
                            {" · "}
                            {story.publishedChapterCount}/{story.chapterCount} chương
                            {" · "}
                            {number.format(story.viewCount)} đọc
                          </small>
                        </span>
                      </button>
                    </li>
                  );
                })}
                {stories.length === 0 ? (
                  <li className="pubEmptyHint">Chưa có truyện. Bấm “Tạo truyện mới”.</li>
                ) : null}
              </ul>
            </aside>

            {/* Editor */}
            <section className="publisherCard pubEditor">
              <header className="publisherCardHeader">
                <h2>{selectedId ? "Sửa truyện" : "Truyện mới"}</h2>
                {selectedId ? <span>{existingChapters.length} chương hiện có</span> : null}
              </header>

              {notice ? (
                <p className={notice.kind === "ok" ? "pubNoticeOk" : "pubNoticeErr"}>{notice.text}</p>
              ) : null}

              {/* First thing in the editor: drop a file and the rest fills itself.
                  Everything below stays editable by hand afterwards. */}
              <section className="pubImportPanel">
                <div>
                  <strong>Upload file truyện để tự điền</strong>
                  <small>
                    Hỗ trợ .docx, .txt, .md. Hệ thống đọc tên truyện, tác giả, giới thiệu và tự cắt
                    chương theo tiêu đề trong file; nếu file không có tiêu đề chương thì cắt mỗi{" "}
                    {form.storyFormat === "ONESHOT" ? WORDS_PER_CHAPTER_ZHIHU : WORDS_PER_CHAPTER} từ.
                    Thông số file không có sẽ lấy từ tên file hoặc để bạn nhập tay.
                  </small>
                </div>
                <label className="pubImportBtn">
                  <Paperclip aria-hidden="true" size={15} />
                  {importing ? "Đang đọc…" : "Chọn file truyện"}
                  <input
                    accept=".txt,.md,.docx"
                    disabled={importing}
                    onChange={(event) => {
                      const file = event.currentTarget.files?.[0] ?? null;
                      void importStoryFile(file);
                      event.currentTarget.value = "";
                    }}
                    type="file"
                  />
                </label>
              </section>
              {importSummary ? <p className="pubNoticeOk">{importSummary}</p> : null}

              <label className="pubField">
                <span>Tên truyện *</span>
                <input
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="Nhập tên truyện"
                  value={form.title}
                />
              </label>

              <label className="pubField">
                <span>Tác giả gốc</span>
                <input
                  onChange={(e) => setForm({ ...form, authorName: e.target.value })}
                  placeholder="Tên tác giả gốc"
                  value={form.authorName}
                />
              </label>

              <label className="pubField">
                <span>Giới thiệu</span>
                <textarea
                  onChange={(e) => setForm({ ...form, summary: e.target.value })}
                  placeholder="Nội dung giới thiệu truyện"
                  rows={4}
                  value={form.summary}
                />
              </label>

              {/*
                Checkboxes, not <select multiple>. Ctrl-click is invisible
                unless you already know it, has no equivalent on a touch screen,
                and one stray click without the modifier silently replaces every
                genre already picked. With 84 genres there is also a search box,
                since scrolling a five-row list to find "Xuyên Không" is its own
                obstacle. Selected genres stay pinned at the top so they are
                still visible once the list is filtered.
              */}
              <div className="pubField">
                <span>Thể loại * — chọn ít nhất 1</span>
                <input
                  className="pubGenreSearch"
                  onChange={(event) => setGenreQuery(event.target.value)}
                  placeholder="Tìm thể loại…"
                  value={genreQuery}
                />
                <div className="pubGenreGrid">
                  {[...categories]
                    .sort((left, right) => {
                      const picked = (id: string) => (form.categoryIds.includes(id) ? 0 : 1);
                      return picked(left.id) - picked(right.id)
                        || left.name.localeCompare(right.name, "vi");
                    })
                    .filter((category) =>
                      form.categoryIds.includes(category.id)
                      || category.name.toLowerCase().includes(genreQuery.trim().toLowerCase()))
                    .map((category) => {
                      const checked = form.categoryIds.includes(category.id);
                      return (
                        <label
                          className={checked ? "pubGenreItem isOn" : "pubGenreItem"}
                          key={category.id}
                        >
                          <input
                            checked={checked}
                            onChange={() => setForm({
                              ...form,
                              categoryIds: checked
                                ? form.categoryIds.filter((id) => id !== category.id)
                                : [...form.categoryIds, category.id],
                            })}
                            type="checkbox"
                          />
                          <span>{category.name}</span>
                        </label>
                      );
                    })}
                </div>
                <small className="pubHint">Đã chọn {form.categoryIds.length} thể loại.</small>
              </div>

              <div className="pubFieldRow">
                <label className="pubField">
                  <span>Dạng truyện</span>
                  <select
                    onChange={(e) => setForm({ ...form, storyFormat: e.target.value as FormState["storyFormat"] })}
                    value={form.storyFormat}
                  >
                    <option value="SERIAL">Truyện dài</option>
                    <option value="ONESHOT">Truyện Zhihu (tách mỗi {WORDS_PER_CHAPTER_ZHIHU} từ)</option>
                  </select>
                </label>
                <label className="pubField">
                  <span>Tiến độ</span>
                  <select
                    onChange={(e) =>
                      setForm({ ...form, completionStatus: e.target.value as FormState["completionStatus"] })
                    }
                    value={form.completionStatus}
                  >
                    <option value="ONGOING">Đang ra</option>
                    <option value="COMPLETED">Đã hoàn</option>
                    <option value="PAUSED">Tạm ngưng</option>
                  </select>
                </label>
                <label className="pubField">
                  <span>Trạng thái</span>
                  <select
                    onChange={(e) =>
                      setForm({ ...form, workflowStatus: e.target.value as FormState["workflowStatus"] })
                    }
                    value={form.workflowStatus}
                  >
                    <option value="DRAFT">Lưu nháp</option>
                    <option value="PENDING_REVIEW">Gửi duyệt</option>
                    <option value="PUBLISHED">Đăng ngay</option>
                  </select>
                </label>
              </div>

              <label className="pubField">
                <span>Tags (cách nhau bằng dấu phẩy)</span>
                <input
                  onChange={(e) => setForm({ ...form, tags: e.target.value })}
                  placeholder="ngôn tình, chữa lành"
                  value={form.tags}
                />
              </label>

              <label className="pubField">
                <span>Ảnh bìa</span>
                <input
                  accept="image/*"
                  onChange={(e) => setCoverFile(e.target.files?.[0] ?? null)}
                  type="file"
                />
              </label>

              <label className="pubField">
                <span>Giá mua cả truyện (Xu)</span>
                <input
                  min={0}
                  onChange={(e) => setForm({ ...form, comboPriceXu: e.target.value })}
                  placeholder="Để trống = bằng tổng giá các chương"
                  type="number"
                  value={form.comboPriceXu}
                />
                <small className="pubHint">
                  Để trống thì combo bằng đúng tổng giá các chương và không hiện giảm giá. Nhập số
                  thấp hơn tổng để tạo giảm giá — phần trăm sẽ tự tính. Truyện miễn phí toàn bộ
                  không hiện mục mua.
                </small>
              </label>

              {/* The editable list below now holds these same chapters, so a
                  second read-only copy of them was pure duplication. Only the
                  warning it carried is worth keeping. */}
              {existingChapters.length > 0 ? (
                <p className="pubWarn">
                  Lưu sẽ <strong>thay thế toàn bộ</strong> {existingChapters.length} chương hiện có
                  bằng danh sách bên dưới. Chương đã có người mua được giữ lại.
                </p>
              ) : null}

              <div className="pubChapterEditor">
                <div className="pubChapterEditorHead">
                  <h3>{chapters.length > 0 ? "Chương sẽ đăng" : "Thêm chương"}</h3>
                  <div className="pubChapterActions">
                    {/* One picked file becomes one chapter, same as the admin
                        workspace: the filename is the title and the bytes are the
                        content. */}
                    <label className="pubGhostBtn pubFileBtn">
                      <Paperclip aria-hidden="true" size={14} />
                      Chọn nhiều file
                      <input
                        accept={CHAPTER_FILE_TYPES}
                        multiple
                        onChange={(event) => {
                          addChaptersFromFiles(Array.from(event.currentTarget.files ?? []));
                          event.currentTarget.value = "";
                        }}
                        type="file"
                      />
                    </label>
                    <button className="pubGhostBtn" onClick={addChapter} type="button">
                      <Plus aria-hidden="true" size={14} />
                      Thêm chương
                    </button>
                  </div>
                </div>

                {/* Bulk actions, matching the admin workspace: tick a set of
                    chapters, then price or remove them in one go. Editing 700
                    chapters one at a time is not a workflow. */}
                {chapters.length > 0 ? (
                  <div className="pubBulkBar">
                    <label className="pubBulkAll">
                      <input
                        checked={picked.size === chapters.length && chapters.length > 0}
                        onChange={toggleAllPicked}
                        type="checkbox"
                      />
                      Chọn tất cả
                    </label>
                    {picked.size > 0 ? (
                      <>
                        <span className="pubBulkCount">Đã chọn {picked.size}/{chapters.length}</span>
                        <input
                          aria-label="Giá xu cho các chương đã chọn"
                          className="pubBulkPrice"
                          min={0}
                          onChange={(event) => setBulkPrice(
                            event.target.value === '' ? '' : Math.max(0, Number(event.target.value)))}
                          placeholder="Giá xu"
                          type="number"
                          value={bulkPrice}
                        />
                        <button
                          className="pubGhostBtn"
                          disabled={bulkPrice === ''}
                          onClick={applyBulkPrice}
                          type="button"
                        >
                          Đặt giá cho {picked.size} chương
                        </button>
                        <button
                          className="pubDangerBtn"
                          onClick={() => setConfirmingBulkDelete(true)}
                          type="button"
                        >
                          <Trash2 aria-hidden="true" size={14} />
                          Xoá {picked.size} chương
                        </button>
                      </>
                    ) : null}
                  </div>
                ) : null}
                {chapters.length === 0 ? (
                  <p className="pubEmpty">
                    Chọn file để tạo nhiều chương cùng lúc, hoặc bấm “Thêm chương” để nhập tay.
                    Hỗ trợ {CHAPTER_FILE_TYPES.replaceAll(",", ", ")}.
                  </p>
                ) : null}
                {chapters.map((chapter, index) => (
                  <div className="pubChapterRow" key={index}>
                    <div className="pubChapterRowHead">
                      <input
                        aria-label={`Chọn chương ${index + 1}`}
                        checked={picked.has(index)}
                        className="pubChapterPick"
                        onChange={() => togglePicked(index)}
                        type="checkbox"
                      />
                      <input
                        onChange={(e) => updateChapter(index, { title: e.target.value })}
                        placeholder={`Tên chương ${index + 1}`}
                        value={chapter.title}
                      />
                      <select
                        onChange={(e) =>
                          updateChapter(index, {
                            accessType: e.target.value as ChapterDraft["accessType"],
                            coinPrice: e.target.value === "FREE" ? 0 : chapter.coinPrice,
                          })
                        }
                        value={chapter.accessType}
                      >
                        <option value="FREE">Miễn phí</option>
                        <option value="PAID">Trả phí</option>
                      </select>
                      {chapter.accessType === "PAID" ? (
                        <input
                          min={1}
                          onChange={(e) => updateChapter(index, { coinPrice: Number(e.target.value) })}
                          placeholder="Xu"
                          type="number"
                          value={chapter.coinPrice || ""}
                        />
                      ) : null}
                      <button
                        aria-label="Xoá chương"
                        className="pubGhostBtn"
                        onClick={() => setChapters((rows) => rows.filter((_, i) => i !== index))}
                        type="button"
                      >
                        <Trash2 aria-hidden="true" size={14} />
                      </button>
                    </div>
                    {chapter.file ? (
                      // The textarea is hidden rather than disabled: the server
                      // ignores inline text when a file is attached, so offering
                      // an editable box here would silently discard what is typed.
                      <p className="pubAttached">
                        <Paperclip aria-hidden="true" size={13} />
                        <span>{chapter.file.name}</span>
                        <button
                          className="pubGhostBtn"
                          onClick={() => updateChapter(index, { file: null })}
                          type="button"
                        >
                          Bỏ file, nhập tay
                        </button>
                      </p>
                    ) : (
                      <>
                        <textarea
                          onChange={(e) => updateChapter(index, { content: e.target.value })}
                          placeholder="Nội dung chương"
                          rows={5}
                          value={chapter.content}
                        />
                        <label className="pubGhostBtn pubFileBtn pubFileBtnInline">
                          <Paperclip aria-hidden="true" size={13} />
                          Đính kèm file cho chương này
                          <input
                            accept={CHAPTER_FILE_TYPES}
                            onChange={(event) => {
                              const file = event.currentTarget.files?.[0] ?? null;
                              if (!file) return;
                              updateChapter(index, {
                                file,
                                title: chapter.title || titleFromFileName(file.name),
                              });
                              event.currentTarget.value = "";
                            }}
                            type="file"
                          />
                        </label>
                      </>
                    )}
                  </div>
                ))}
              </div>

              <div className="pubActions">
                <button className="publisherPrimaryBtn" disabled={saving} onClick={handleSave} type="button">
                  {saving ? <Loader2 aria-hidden="true" size={15} /> : <Save aria-hidden="true" size={15} />}
                  {saving ? "Đang lưu…" : selectedId ? "Lưu thay đổi" : "Tạo truyện"}
                </button>
                {selectedId ? (
                  <button className="pubDangerBtn" disabled={saving} onClick={handleHide} type="button">
                    <Trash2 aria-hidden="true" size={15} />
                    Ẩn truyện
                  </button>
                ) : null}
              </div>
            </section>
          </div>
        </div>
      </main>
    </PublicShell>
  );
}
