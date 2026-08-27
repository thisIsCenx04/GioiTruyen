"use client";

import { Eye, EyeOff, Loader2, Paperclip, Plus, Save, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { failureOutcome } from "@/components/api-problem";
import { FormDialog, type DialogRequest } from "@/components/form-dialog";
import { ChapterEditorPager, chapterPageCount, chapterPageSlice } from "@/components/chapter-editor-pager";
import { MissingChaptersNotice } from "@/components/missing-chapters-notice";
import { OperationDialog, type OperationOutcome } from "@/components/operation-dialog";
import { PublisherTabs } from "@/components/publisher-tabs";
import { PublicShell } from "@/components/site-chrome";
import { coverUrl, StoryCoverPlaceholder } from "@/components/story-cover";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { formatXu } from "@/lib/format";
import { matchCategoryNames } from "@/lib/match-categories";
// The same parser the admin drawer uses, so a file dropped here is split into
// chapters exactly the way it would be on the admin side.
import {
  chapterNumberFromFileName,
  lastChapterNumber,
  parseStoryDocument,
  readChapterDraftsFromFiles,
  readChapterText,
  sortChapterFiles,
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
  /** The full synopsis. shortDescription is only the 500-character teaser. */
  description?: string | null;
  /** Genres already linked to the story, so the form can tick them again. */
  categoryIds?: string[];
  /** Tag labels already linked. */
  tags?: string[];
};

type TeamAccess = {
  memberRole: string;
  ownerAccess: boolean;
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
  /** The chapter being edited, or absent when this draft is a new chapter. */
  id?: string;
  title: string;
  content: string;
  accessType: "FREE" | "PAID";
  coinPrice: number;
};

/**
 * Generic over the draft shape so the admin workspace, whose rows carry an
 * extra id, can share this one matching rule. Two implementations of "which
 * chapter does this file replace?" is how the two screens drifted apart in the
 * first place - the admin one matched by position, so re-uploading a file whose
 * order differed overwrote the wrong chapters.
 */
/** All the matching rule reads off a draft: its number lives in the title. */
type MergeableChapter = { title: string; content: string };

type SmartChapterMerge<T extends MergeableChapter = ChapterDraft> = {
  chapters: T[];
  added: T[];
  updated: T[];
  skipped: T[];
};

/**
 * Formats the reader can actually decode.
 *
 * <p>.doc and .pdf are deliberately absent. Both used to be listed and neither
 * was ever decoded - the file was read as UTF-8 bytes and saved as mojibake, so
 * offering them promised something the upload could not deliver. Picking one
 * anyway (the dialog allows it) now returns a message saying what to do
 * instead.
 */
const CHAPTER_FILE_TYPES = ".txt,.md,.docx,.odt,.epub,.html,.htm,.rtf";
const MAX_STORY_FILE_BYTES = 50 * 1024 * 1024;
/**
 * How long a synopsis may be, in characters. Mirrors the server limit, so the
 * form can say so while typing rather than only after a failed save.
 */
export const SYNOPSIS_MAX_LENGTH = 100_000;

const MAX_CHAPTERS_PER_SAVE = 3000;
const MAX_CHAPTER_BODY_BYTES_PER_SAVE = 90 * 1024 * 1024;

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


function normalizeChapterTitle(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/gu, "")
    .replace(/đ/gu, "d")
    .replace(/Đ/gu, "D")
    .toLowerCase()
    .replace(/\s+/gu, " ")
    .trim();
}

export function chapterNumberFromTitle(title: string): number | null {
  const match = normalizeChapterTitle(title).match(/\b(?:chuong|chapter|chap|c)\s*([0-9]+(?:\.[0-9]+)?)/u);
  if (!match?.[1]) return null;
  const parsed = Number(match[1]);
  return Number.isFinite(parsed) ? parsed : null;
}

function chapterTitleFingerprint(title: string): string {
  const normalized = normalizeChapterTitle(title)
    .replace(/^(?:chuong|chapter|chap|c)\s*[0-9]+(?:\.[0-9]+)?\s*(?:chuong)?\s*/u, "")
    .replace(/^[.:;\-–—_*\s]+/u, "")
    .replace(/[.:;\-–—_*\s]+$/u, "")
    .replace(/[.:;\-–—_*\s]+/gu, " ")
    .trim();
  return normalized || normalizeChapterTitle(title);
}

export function chapterIdentity(title: string, fallbackNumber: number): string {
  const normalizedTitle = normalizeChapterTitle(title);
  const numberPart = chapterNumberFromTitle(title) ?? fallbackNumber;
  return `${numberPart}::${chapterTitleFingerprint(normalizedTitle)}`;
}

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} KB`;
  return `${bytes.toLocaleString("vi-VN")} byte`;
}

function chapterBodiesByteSize(chapterRows: readonly ChapterDraft[]): number {
  return new Blob(chapterRows.map((chapter) => chapter.content)).size;
}

export function mergeImportedChapters<T extends MergeableChapter>(
  currentChapters: readonly T[],
  existingRows: readonly Pick<ChapterRow, "chapterNumber" | "title">[],
  importedChapters: readonly T[],
): SmartChapterMerge<T> {
  const usedExisting = new Set<number>();
  const nextChapters = [...currentChapters];
  const added: T[] = [];
  const updated: T[] = [];
  const skipped: T[] = [];

  importedChapters.forEach((chapter, importIndex) => {
    const importedKey = chapterIdentity(chapter.title, importIndex + 1);
    const matchIndex = nextChapters.findIndex((current, currentIndex) => {
      if (usedExisting.has(currentIndex)) return false;
      const existingNumber = existingRows[currentIndex]?.chapterNumber ?? currentIndex + 1;
      const existingTitle = current.title || existingRows[currentIndex]?.title || `Chương ${existingNumber}`;
      return chapterIdentity(existingTitle, existingNumber) === importedKey;
    });

    if (matchIndex >= 0) {
      const current = nextChapters[matchIndex]!;
      usedExisting.add(matchIndex);
      if (current.content !== chapter.content || current.title !== chapter.title) {
        const merged = {
          ...current,
          content: chapter.content,
          title: chapter.title,
        };
        nextChapters[matchIndex] = merged;
        updated.push(merged);
      } else {
        skipped.push(current);
      }
      return;
    }

    added.push(chapter);
  });

  return { added, chapters: [...nextChapters, ...added], skipped, updated };
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

/**
 * Hai loại này được hưởng mức phí 10% thay vì 30%, nên trước khi chọn phải
 * đọc và xác nhận cam kết. Danh sách này phải khớp với
 * {@code MonetizationFlowService.isExclusive} bên máy chủ.
 */
const EXCLUSIVE_STORY_TYPES: ReadonlyArray<FormState["storyType"]> = ["EXCLUSIVE", "ORIGINAL"];

function isExclusiveType(storyType: FormState["storyType"]): boolean {
  return EXCLUSIVE_STORY_TYPES.includes(storyType);
}

const STATUS_LABELS: Readonly<Record<string, string>> = {
  DRAFT: "Bản nháp",
  PENDING_REVIEW: "Chờ duyệt",
  PUBLISHED: "Đã đăng",
  REJECTED: "Bị từ chối",
  HIDDEN: "Đã ẩn",
};

const number = new Intl.NumberFormat("vi-VN");

/**
 * Positions covered by "chapters N to M", 1-based and inclusive.
 *
 * <p>The ends are sorted and clamped, so a publisher who types them the wrong
 * way round, or past the end of the story, still selects the run they meant
 * rather than nothing at all.
 */
export function chapterRangeIndices(from: number, to: number, total: number): number[] {
  if (!Number.isFinite(from) || !Number.isFinite(to) || total <= 0) return [];
  const low = Math.max(1, Math.min(from, to));
  const high = Math.min(total, Math.max(from, to));
  if (high < low) return [];
  return Array.from({ length: high - low + 1 }, (_, offset) => low + offset - 1);
}

/**
 * The usual shape of a paid story: opening chapters free so a reader can try
 * it, one price on everything after.
 *
 * <p>A price of zero leaves every chapter free rather than creating paid
 * chapters worth nothing - the server refuses a PAID chapter priced at zero,
 * since it would unlock for free anyway.
 */
/**
 * Số chương mà một dòng đang mang.
 *
 * <p>Lấy từ tiêu đề trước, vì đó là con số người đọc nhìn thấy; tiêu đề không
 * ghi số thì mới lấy vị trí trong danh sách.
 */
export function draftChapterNumber(
  rows: readonly { title: string }[],
  index: number,
): number {
  return chapterNumberFromTitle(rows[index]?.title ?? "") ?? index + 1;
}

/**
 * Chỗ mà một chương mang số này thuộc về trong danh sách hiện tại.
 *
 * <p>Chèn ngay trước chương đầu tiên có số lớn hơn. Với số bằng nhau thì chèn
 * xuống sau, nên thêm "chương 86" vào một truyện đã có 86 sẽ nằm ngay sau bản
 * cũ chứ không đẩy bản cũ xuống - và ngoại truyện đánh 86.5 rơi đúng giữa 86
 * với 87.
 */
export function chapterInsertIndex(
  rows: readonly { title: string }[],
  number: number,
): number {
  for (let index = 0; index < rows.length; index += 1) {
    if (draftChapterNumber(rows, index) > number) return index;
  }
  return rows.length;
}

/**
 * Tiêu đề đầy đủ cho một chương thêm tay.
 *
 * <p>Người nhập gõ "Gặp lại" và chọn số 86; thứ được lưu phải là "Chương 86:
 * Gặp lại", vì số chương nằm trong tiêu đề là thứ quyết định vị trí về sau. Gõ
 * sẵn "Chương 86" thì để nguyên, không lồng thêm một lần nữa.
 */
export function composeChapterTitle(number: number, title: string): string {
  const clean = title.trim();
  if (chapterNumberFromTitle(clean) != null) return clean;
  return clean ? `Chương ${number}: ${clean}` : `Chương ${number}`;
}

/**
 * Buộc giá và loại chương khớp nhau.
 *
 * <p>Chỉ có đúng một luật: có giá thì trả phí, không giá thì miễn phí. Trước
 * đây hai trường này đặt rời nhau ở năm chỗ khác nhau, nên chọn "Trả phí" mà
 * chưa gõ giá là tạo ra một chương trả phí giá 0 - máy chủ từ chối, và cả lần
 * lưu hỏng vì một ô chưa điền.
 */
export function normalizeChapterPricing<T extends { accessType: "FREE" | "PAID"; coinPrice: number }>(
  chapter: T,
): T {
  const price = Number.isFinite(chapter.coinPrice) ? Math.max(0, Math.floor(chapter.coinPrice)) : 0;
  return price > 0
    ? { ...chapter, accessType: "PAID" as const, coinPrice: price }
    : { ...chapter, accessType: "FREE" as const, coinPrice: 0 };
}

export function freeThenPaidPricing<T extends { accessType: "FREE" | "PAID"; coinPrice: number }>(
  chapters: readonly T[],
  freeCount: number,
  price: number,
): T[] {
  const free = Math.max(0, freeCount);
  const paid = Math.max(0, price);
  return chapters.map((chapter, index) => (
    index < free
      ? { ...chapter, accessType: "FREE" as const, coinPrice: 0 }
      : { ...chapter, accessType: paid > 0 ? "PAID" as const : "FREE" as const, coinPrice: paid }
  ));
}

export function PublishingWorkspace({ teamId }: Readonly<{ teamId: string }>) {
  const [stories, setStories] = useState<StoryRow[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  /** Filters the genre checkbox list; there are ~84 of them. */
  const [genreQuery, setGenreQuery] = useState("");
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [chapters, setChapters] = useState<ChapterDraft[]>([]);
  const [existingChapters, setExistingChapters] = useState<ChapterRow[]>([]);
  const [access, setAccess] = useState<TeamAccess | null>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  /**
   * Đã tick vào cam kết độc quyền hay chưa.
   *
   * <p>Không lưu xuống máy chủ - đây là lời nhắc trước khi chọn, không phải
   * hợp đồng. Truyện đã ở diện độc quyền sẵn thì coi như đã xác nhận, không
   * bắt ký lại mỗi lần sửa tiêu đề.
   */
  const [exclusiveSigned, setExclusiveSigned] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importSummary, setImportSummary] = useState("");
  const [state, setState] = useState<"loading" | "ready" | "forbidden" | "error">("loading");
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  /**
   * The result of the last upload or save, shown as a dialog the publisher has
   * to acknowledge. A line of text above the form was too easy to miss on a
   * page this long, and had nowhere to put the detail that matters.
   */
  const [outcome, setOutcome] = useState<OperationOutcome | null>(null);

  /** Which page of the chapter list is on screen; see ChapterEditorPager. */
  const [chapterPage, setChapterPage] = useState(1);
  // Titles only, so the pager can label a page by the chapters' own numbers.
  const chapterTitles = useMemo(() => chapters.map((chapter) => chapter.title), [chapters]);

  /** Chapters ticked for a bulk action, by their index in the draft list. */
  const [picked, setPicked] = useState<ReadonlySet<number>>(new Set());
  /** Xu applied to the ticked chapters; blank until a figure is typed. */
  const [bulkPrice, setBulkPrice] = useState<number | ''>('');
  const [ask, setAsk] = useState<DialogRequest | null>(null);
  /** Ends of the "tick chapters N to M" shortcut, by position in the list. */
  const [rangeFrom, setRangeFrom] = useState<number | ''>('');
  const [rangeTo, setRangeTo] = useState<number | ''>('');
  /** The free-then-paid preset: how many opening chapters stay free, and the
   *  price every chapter after them carries. */
  const [freeChapterCount, setFreeChapterCount] = useState<number | ''>(5);
  const [paidChapterPrice, setPaidChapterPrice] = useState<number | ''>(5);
  /** Typed confirmation, required before deleting more than a handful. */
  const [bulkDeleteConfirm, setBulkDeleteConfirm] = useState('');
  const [confirmingBulkDelete, setConfirmingBulkDelete] = useState(false);
  /** Typed confirmation for deleting the whole story, which cannot be undone. */
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState("");

  /** The row being edited, for fields the form shows but does not own. */
  const selectedStory = stories.find((row) => row.id === selectedId) ?? null;

  // Deleting a run of chapters can leave the reader on a page past the end,
  // which renders as an empty list and looks like the chapters were lost.
  useEffect(() => {
    const pages = chapterPageCount(chapters.length);
    if (chapterPage > pages) setChapterPage(pages);
  }, [chapterPage, chapters.length]);

  const togglePicked = (index: number) => setPicked((current) => {
    const next = new Set(current);
    if (next.has(index)) next.delete(index); else next.add(index);
    return next;
  });

  const toggleAllPicked = () => setPicked((current) =>
    current.size === chapters.length && chapters.length > 0
      ? new Set()
      : new Set(chapters.map((_, index) => index)));

  /**
   * Ticks the chapters on the page being looked at.
   *
   * <p>With twenty chapters to a page and a story running to hundreds, "all"
   * and "one at a time" were the only two options; pricing a single volume
   * meant twenty clicks.
   */
  const pickCurrentPage = () => setPicked((current) => {
    const next = new Set(current);
    for (const { index } of chapterPageSlice(chapters, chapterPage)) next.add(index);
    return next;
  });

  /** Ticks a run of chapters by their position, 1-based and inclusive. */
  const pickRange = () => setPicked((current) => {
    const next = new Set(current);
    for (const index of chapterRangeIndices(Number(rangeFrom), Number(rangeTo), chapters.length)) {
      next.add(index);
    }
    return next;
  });

  /** Applied to the whole story rather than to a selection, because that is
   *  the decision being made. */
  const applyFreeThenPaid = () => setChapters((current) => freeThenPaidPricing(
    current,
    Number(freeChapterCount) || 0,
    Number(paidChapterPrice) || 0,
  ));

  /**
   * Mức giá gợi ý khi chuyển một chương sang trả phí.
   *
   * <p>Lấy giá đang dùng ở các chương trả phí khác, vì một bộ truyện gần như
   * luôn dùng chung một mức. Chưa có chương nào trả phí thì lấy ô "giá chương
   * trả phí" ở khối đặt giá hàng loạt.
   */
  const suggestedPaidPrice = (() => {
    const inUse = chapters.map((chapter) => chapter.coinPrice).filter((price) => price > 0);
    if (inUse.length > 0) return inUse[inUse.length - 1]!;
    return Math.max(0, Number(paidChapterPrice) || 0);
  })();

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
    const accessRes = await authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/access`);
    if (accessRes.status === 403 || accessRes.status === 404) {
      setState("forbidden");
      return;
    }
    if (accessRes.ok) {
      setAccess((await accessRes.json()) as TeamAccess);
    }
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
    setChapterPage(1);
    setNotice(null);
    setCoverFile(null);
    setChapters([]);
    setForm({
      title: story.title,
      authorName: story.originalAuthor ?? "",
      // The full synopsis, not the teaser. Loading shortDescription here and
      // saving it back is what clipped every story to 500 characters on its
      // first edit, permanently - the rest was overwritten, not hidden.
      summary: story.description ?? story.shortDescription ?? "",
      // Loaded from the story, not blanked. These two were hardcoded empty, so
      // reopening a story showed no genres and no tags - and because saving
      // sends whatever the form holds, pressing save then wrote that emptiness
      // over the real values.
      categoryIds: story.categoryIds ?? [],
      storyFormat: story.storyFormat === "ONESHOT" ? "ONESHOT" : "SERIAL",
      storyType: (story.storyType as FormState["storyType"]) ?? "TEXT",
      workflowStatus: (story.status as FormState["workflowStatus"]) ?? "DRAFT",
      completionStatus: (story.progressStatus as FormState["completionStatus"]) ?? "ONGOING",
      tags: (story.tags ?? []).join(", "),
      comboPriceXu: story.comboPriceXu ? String(story.comboPriceXu) : "",
    });
    // Truyện đã ở diện độc quyền thì cam kết đã có từ lần đăng đầu; bắt ký
    // lại mỗi lần sửa một dấu phẩy trong tiêu đề chỉ là phiền.
    setExclusiveSigned(isExclusiveType((story.storyType as FormState["storyType"]) ?? "TEXT"));
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
      // Carried so the save updates this chapter rather than whatever ends up
      // at the same position in the list.
      id: row.id,
      title: row.title ?? "",
      content: row.content ?? "",
      accessType: row.accessType === "PAID" ? "PAID" : "FREE",
      coinPrice: row.coinPrice ?? 0,
    })));
  }

  function startNewStory() {
    setSelectedId(null);
    setChapterPage(1);
    setForm(EMPTY_FORM);
    setChapters([]);
    setExistingChapters([]);
    setCoverFile(null);
    setNotice(null);
  }

  /**
   * Thêm một chương ở đúng vị trí người quản lý chọn.
   *
   * <p>Bản trước chỉ nối một dòng trống vào cuối danh sách. Muốn chèn chương 86
   * vào giữa một bộ bảy trăm chương thì phải kéo tay từ cuối lên - việc không
   * ai làm nổi, nên trên thực tế chương chèn giữa luôn nằm sai chỗ.
   */
  function openAddChapter() {
    const suggested = chapters.length > 0
      ? Math.floor(draftChapterNumber(chapters, chapters.length - 1)) + 1
      : 1;
    setAsk({
      fields: [
        {
          hint: "Chương sẽ tự nằm đúng chỗ theo số này. Ngoại truyện dùng số lẻ như 86.5.",
          kind: "number",
          label: "Số chương",
          min: 0,
          name: "number",
          required: true,
          requiredMessage: "Cần số chương thì mới biết đặt nó vào đâu.",
          value: String(suggested),
        },
        {
          hint: "Không cần gõ lại chữ “Chương N”, phần đó tự thêm.",
          label: "Tên chương",
          maxLength: 200,
          name: "title",
          placeholder: "Gặp lại cố nhân",
        },
        {
          kind: "textarea",
          label: "Nội dung",
          name: "content",
          placeholder: "Dán nội dung chương vào đây…",
          required: true,
          requiredMessage: "Máy chủ từ chối chương rỗng, nên nội dung là bắt buộc.",
        },
        {
          hint: "Để 0 là chương miễn phí.",
          kind: "number",
          label: "Giá xu",
          min: 0,
          name: "coinPrice",
          value: "0",
        },
      ],
      intro: "Chương mới sẽ được chèn vào đúng vị trí theo số chương, không phải kéo tay.",
      onSubmit: (values) => {
        const number = Number(values.number);
        const draft = normalizeChapterPricing({
          accessType: "FREE" as const,
          coinPrice: Number(values.coinPrice) || 0,
          content: values.content,
          title: composeChapterTitle(number, values.title),
        });
        setChapters((rows) => {
          const at = chapterInsertIndex(rows, number);
          return [...rows.slice(0, at), draft, ...rows.slice(at)];
        });
      },
      submitLabel: "Thêm chương",
      title: "Thêm chương thủ công",
    });
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
  /**
   * Thêm những thể loại file có ghi mà trang chưa có, rồi tick sẵn chúng.
   *
   * <p>Chỉ chạy khi người dùng bấm nút xác nhận trong hộp thoại báo đọc xong.
   * Tự thêm ngay lúc đọc file thì một tên gõ sai trong file sẽ lặng lẽ thành
   * một thể loại mới, và danh sách thể loại đầy rác trong vài tuần mà không ai
   * biết rác từ đâu ra.
   */
  async function addMissingGenres(names: readonly string[]) {
    const response = await authedFetch(`${API_BASE_URL}/genres`, {
      body: JSON.stringify({ names }),
      headers: { "Content-Type": "application/json" },
      method: "POST",
    });
    if (!response.ok) {
      setOutcome(await failureOutcome("Không thêm được thể loại", response));
      return;
    }
    const added = (await response.json()) as Array<{ id: string; name: string; slug: string }>;
    // Danh sách thể loại của form phải biết tới chúng, nếu không ô chọn hiện ra
    // những id trống tên.
    setCategories((current) => {
      const known = new Set(current.map((category) => category.id));
      return [...current, ...added.filter((category) => !known.has(category.id))];
    });
    setForm((current) => ({
      ...current,
      categoryIds: [...new Set([...current.categoryIds, ...added.map((category) => category.id)])],
    }));
  }

  async function importStoryFile(file: File | null) {
    if (!file) return;
    if (file.size > MAX_STORY_FILE_BYTES) {
      setOutcome({
        details: [`File "${file.name}" nặng ${formatBytes(file.size)}, vượt giới hạn ${formatBytes(MAX_STORY_FILE_BYTES)} mỗi file.`],
        hint: "Hãy tách truyện thành nhiều file nhỏ hơn rồi upload từng phần.",
        kind: "error",
        title: "File quá lớn",
      });
      return;
    }
    setImporting(true);
    setImportSummary("");
    setNotice(null);
    try {
      const isZhihu = form.storyFormat === "ONESHOT";
      const wordsPerChapter = isZhihu ? WORDS_PER_CHAPTER_ZHIHU : WORDS_PER_CHAPTER;
      // Zhihu giữ nguyên cách xuống dòng của file. Truyện dài về đúng định dạng
      // là nhờ đi qua nhánh cắt theo tiêu đề chương; Zhihu không có tiêu đề nên
      // đi qua nhánh cắt theo số từ, và nhánh đó vốn dồn mỗi dòng thành một
      // đoạn - đó là lý do riêng Zhihu bị rớt hàng sai.
      const imported = await parseStoryDocument(file, wordsPerChapter, isZhihu);

      // A file the parser can read but that is structurally wrong - chapters
      // back to front, say - is refused here rather than loaded. Loading it
      // would put the work of unpicking it onto the publisher, chapter by
      // chapter, after the damage was already in the form.
      if (imported.errors.length > 0) {
        setOutcome({
          details: imported.errors,
          hint: "Không có gì được nạp vào form. Sửa file rồi upload lại.",
          kind: "error",
          title: `File “${file.name}” không hợp lệ`,
        });
        return;
      }

      if (imported.chapters.length === 0) {
        setOutcome({
          details: ["File không có nội dung nào đọc được thành chương."],
          hint: "Kiểm tra lại file, hoặc dán nội dung trực tiếp vào ô soạn thảo.",
          kind: "error",
          title: `File “${file.name}” trống`,
        });
        return;
      }

      if (imported.chapters.length > MAX_CHAPTERS_PER_SAVE) {
        setOutcome({
          details: [
            `File đọc ra ${imported.chapters.length.toLocaleString("vi-VN")} chương, vượt giới hạn ${MAX_CHAPTERS_PER_SAVE.toLocaleString("vi-VN")} chương mỗi lần lưu.`,
          ],
          hint: "Hãy tách file thành nhiều phần nhỏ hơn, ví dụ 500-1000 chương mỗi lần.",
          kind: "error",
          title: "Quá nhiều chương",
        });
        return;
      }

      // Fall back to the filename when the file had no title line of its own, so
      // the story is not named after its opening sentence.
      const fileTitle = titleFromFileName(file.name).trim();
      const parsedTitle = imported.title.trim();
      const resolvedTitle = parsedTitle && !looksLikeProse(parsedTitle)
        ? parsedTitle
        : fileTitle || parsedTitle.slice(0, TITLE_MAX_LENGTH);

      // Genres the file listed, matched against the site's own list so the
      // publisher does not tick what the file already told us.
      const genres = matchCategoryNames(imported.categoryNames, categories);

      setForm((current) => ({
        ...current,
        // The file wins for fields it actually declares; a field it is silent
        // about keeps whatever is already in the form.
        title: resolvedTitle || current.title,
        authorName: imported.authorName || current.authorName,
        // Clipped only if the file really does carry more than the ceiling,
        // and the dialog below says so - a silent trim is how synopses got
        // lost before.
        summary: (imported.synopsis || current.summary).slice(0, SYNOPSIS_MAX_LENGTH),
        categoryIds: genres.ids.length > 0 ? genres.ids : current.categoryIds,
        completionStatus: imported.completionStatus,
      }));
      const importedDrafts = imported.chapters.map((chapter) => ({
        title: chapter.title,
        content: chapter.content,
        accessType: "FREE" as const,
        coinPrice: 0,
      }));

      let addedCount = importedDrafts.length;
      let updatedCount = 0;
      let skippedCount = 0;
      let smartDetails: string[] = [];
      if (selectedId && chapters.length > 0) {
        const merged = mergeImportedChapters(chapters, existingChapters, importedDrafts);
        if (merged.chapters.length > MAX_CHAPTERS_PER_SAVE) {
          setOutcome({
            details: [
              `Sau khi gộp sẽ có ${merged.chapters.length.toLocaleString("vi-VN")} chương, vượt giới hạn ${MAX_CHAPTERS_PER_SAVE.toLocaleString("vi-VN")} chương mỗi lần lưu.`,
            ],
            hint: "Hãy tách phần chương mới ra upload sau, hoặc xóa bớt chương nháp chưa cần lưu.",
            kind: "error",
            title: "Quá nhiều chương",
          });
          return;
        }
        addedCount = merged.added.length;
        updatedCount = merged.updated.length;
        skippedCount = merged.skipped.length;
        const sampleAdded = merged.added.map((chapter) => chapter.title).slice(0, 5);
        const sampleUpdated = merged.updated.map((chapter) => chapter.title).slice(0, 5);
        setChapters(merged.chapters);
        smartDetails = [
          `▸ CẬP NHẬT truyện đang có · ghi đè ${updatedCount} chương · thêm mới ${addedCount} chương`
          + `${skippedCount > 0 ? ` · giữ nguyên ${skippedCount} chương` : ""}`,
          "Đã so chương theo cả số chương và tên chương.",
          skippedCount > 0 ? `Bỏ qua ${skippedCount} chương vì số và tên chương đã trùng, nội dung không đổi.` : "",
          sampleUpdated.length > 0 ? `Chương đã cập nhật: ${sampleUpdated.join(", ")}${updatedCount > sampleUpdated.length ? "..." : "."}` : "",
          sampleAdded.length > 0 ? `Chương mới: ${sampleAdded.join(", ")}${addedCount > sampleAdded.length ? "..." : "."}` : "",
        ].filter(Boolean);
      } else {
        setChapters(importedDrafts);
      }

      const summary = `Đã đọc “${file.name}”: ${imported.chapters.length} chương`;
      setImportSummary(summary);

      // Everything the read established, so the publisher can check the file
      // arrived whole before committing it.
      const details = [
        // The very first line says which of the two things is happening, so a
        // publisher never has to work out from the counts below whether this
        // upload is adding a new story or rewriting one already published.
        selectedId && chapters.length > 0
          ? `File có ${imported.chapters.length} chương; form hiện có ${chapters.length} chương.`
          : `▸ ĐĂNG MỚI · nạp ${imported.chapters.length} chương vào form (chưa ghi lên máy chủ).`,
        ...smartDetails,
        selectedId && chapters.length > 0 && addedCount === 0
          ? "Không có chương mới được bổ sung: file chỉ có chương trùng với danh sách hiện tại, hoặc ít hơn/bằng số chương đang có."
          : "",
        imported.autoSplit
          ? `File không đánh dấu chương, nên đã cắt mỗi ${wordsPerChapter.toLocaleString("vi-VN")} từ một chương.`
          : "Giữ đúng các chương mà file đã đánh dấu, không cắt thêm.",
        `Nội dung: ${imported.sourceWords.toLocaleString("vi-VN")} từ trong file, `
          + `${imported.keptWords.toLocaleString("vi-VN")} từ đã vào chương.`,
        `Tên truyện: ${resolvedTitle || "(chưa có, hãy nhập tay)"}`,
        genres.matched.length > 0
          ? `Thể loại đọc từ file, đã tick sẵn: ${genres.matched.join(", ")}.`
          : "Thể loại: file không ghi, hãy tự chọn.",
        genres.unmatched.length > 0
          ? `Trang chưa có thể loại tương ứng: ${genres.unmatched.join(", ")}. `
            + "Bấm nút bên dưới để thêm và tick sẵn, hoặc chọn thủ công nếu không cần."
          : "",
        imported.synopsis
          ? imported.synopsis.length > SYNOPSIS_MAX_LENGTH
            ? `Giới thiệu trong file dài ${imported.synopsis.length.toLocaleString("vi-VN")} ký tự, `
              + `vượt giới hạn ${SYNOPSIS_MAX_LENGTH.toLocaleString("vi-VN")} nên đã cắt bớt phần cuối. `
              + "Hãy kiểm tra lại ô Giới thiệu."
            : `Giới thiệu: đã lấy từ file (${imported.synopsis.length.toLocaleString("vi-VN")} ký tự).`
          : "Giới thiệu: file không ghi, hãy nhập tay.",
        imported.authorName ? `Tác giả: ${imported.authorName}` : "Tác giả: (không có trong file)",
        imported.completionStatus === "COMPLETED" ? "File ghi truyện đã hoàn." : "",
        ...imported.warnings,
      ].filter(Boolean);

      setOutcome({
        // Thể loại file có ghi mà trang chưa có: thêm ngay tại đây, không phải
        // đi tìm màn hình quản trị thể loại - thứ mà chủ nhóm không mở được.
        action: genres.unmatched.length > 0
          ? {
            label: `Thêm ${genres.unmatched.length} thể loại này`,
            run: () => addMissingGenres(genres.unmatched),
          }
          : undefined,
        details,
        hint: selectedId && chapters.length > 0
          ? "Kiểm tra danh sách chương bên dưới rồi bấm Lưu để ghi thay đổi lên máy chủ."
          : "Chương đã nạp vào form bên dưới. Kiểm tra rồi bấm Lưu để đăng.",
        kind: imported.warnings.length > 0 || (selectedId && chapters.length > 0 && addedCount === 0)
          ? "warning"
          : "success",
        title: selectedId && chapters.length > 0
          ? `Đã xử lý thông minh “${file.name}”`
          : `Đọc xong “${file.name}”`,
      });
    } catch (cause) {
      setOutcome({
        details: [cause instanceof Error ? cause.message : "Không đọc được file truyện."],
        hint: `Định dạng đọc được: ${CHAPTER_FILE_TYPES.replaceAll(",", ", ")}.`,
        kind: "error",
        title: `Không đọc được “${file.name}”`,
      });
    } finally {
      setImporting(false);
    }
  }

  /**
   * One picked file becomes one chapter, titled after the file.
   *
   * <p>Each file is decoded here rather than posted as-is. The server reads an
   * attachment as UTF-8 text, so a .docx or .epub chapter used to be stored as
   * the raw bytes of a zip - the chapter saved "successfully" and then read as
   * pages of mojibake. Decoding first also means an unreadable file is reported
   * by name now, instead of after the upload.
   */
  async function addChaptersFromFiles(
    picked: readonly File[],
    mode: "append" | "replace" = "append",
  ) {
    if (picked.length === 0) return;
    // The picker hands files over in the operating system's order, which is
    // alphabetical: "Chương 10" before "Chương 2". Reading them in that order
    // published a story whose chapters were scrambled from chapter ten onwards.
    const files = sortChapterFiles(picked);
    const oversized = files.find((file) => file.size > MAX_STORY_FILE_BYTES);
    if (oversized) {
      setOutcome({
        details: [`File "${oversized.name}" nặng ${formatBytes(oversized.size)}, vượt giới hạn ${formatBytes(MAX_STORY_FILE_BYTES)} mỗi file.`],
        hint: "Hãy tách chương hoặc xuất lại file nhỏ hơn.",
        kind: "error",
        title: "File quá lớn",
      });
      return;
    }
    setImporting(true);
    setNotice(null);
    // One shared implementation with the admin drawer: same ordering, same
    // splitting at the file's own chapter markers, same numbering. Only the
    // permission rules differ between the two screens, and those live
    // elsewhere.
    const read = await readChapterDraftsFromFiles(
      files,
      lastChapterNumber(chapters) + 1,
      form.storyFormat === "ONESHOT" ? WORDS_PER_CHAPTER_ZHIHU : WORDS_PER_CHAPTER,
      // Xem ghi chú ở importStoryFile: chỉ Zhihu cần giữ nguyên xuống dòng.
      form.storyFormat === "ONESHOT",
    );
    const added: ChapterDraft[] = read.chapters.map((chapter) => ({
      title: chapter.title,
      content: chapter.content,
      accessType: "FREE" as const,
      coinPrice: 0,
    }));
    const failed = read.failed;
    const fileWarnings = read.warnings;
    const multiChapterFiles = read.multiChapterFiles;
    let updatedCount = 0;
    let skippedCount = 0;
    let appendedCount = added.length;
    const sampleUpdated: string[] = [];
    const sampleAdded: string[] = [];
    if (added.length > 0) {
      if (mode !== "replace") {
        if (chapters.length + added.length > MAX_CHAPTERS_PER_SAVE) {
          setOutcome({
            details: [
              `Sau khi thêm sẽ có ${(chapters.length + added.length).toLocaleString("vi-VN")} chương, vượt giới hạn ${MAX_CHAPTERS_PER_SAVE.toLocaleString("vi-VN")} chương mỗi lần lưu.`,
            ],
            hint: "Hãy upload ít file hơn trong lần này.",
            kind: "error",
            title: "Quá nhiều chương",
          });
          setImporting(false);
          return;
        }
        sampleAdded.push(...added.map((chapter) => chapter.title).slice(0, 5));
        setChapters([...chapters, ...added]);
      } else {
        const merged = mergeImportedChapters(chapters, existingChapters, added);
        if (merged.chapters.length > MAX_CHAPTERS_PER_SAVE) {
          setOutcome({
            details: [
              `Sau khi gộp sẽ có ${merged.chapters.length.toLocaleString("vi-VN")} chương, vượt giới hạn ${MAX_CHAPTERS_PER_SAVE.toLocaleString("vi-VN")} chương mỗi lần lưu.`,
            ],
            hint: "Hãy upload ít file hơn trong lần này.",
            kind: "error",
            title: "Quá nhiều chương",
          });
          setImporting(false);
          return;
        }
        updatedCount = merged.updated.length;
        skippedCount = merged.skipped.length;
        appendedCount = merged.added.length;
        sampleUpdated.push(...merged.updated.map((chapter) => chapter.title).slice(0, 5));
        sampleAdded.push(...merged.added.map((chapter) => chapter.title).slice(0, 5));
        setChapters(merged.chapters);
      }
    }

    const addedWords = added.reduce(
      (sum, chapter) => sum + chapter.content.split(/\s+/u).filter(Boolean).length,
      0,
    );
    const kind = failed.length === 0 ? "success" : added.length === 0 ? "error" : "warning";
    setOutcome({
      details: [
        // Which of the two modes ran, first and in the same shape as the story
        // import dialog, so the two never have to be told apart by reading the
        // counts underneath them.
        mode === "replace"
          ? `▸ CẬP NHẬT chương đang có · ghi đè ${updatedCount} · thêm mới ${appendedCount}`
            + `${skippedCount > 0 ? ` · giữ nguyên ${skippedCount}` : ""}`
          : `▸ THÊM MỚI ${added.length} chương vào cuối danh sách`,
        added.length > 0
          ? mode === "replace"
            ? `Đã xử lý ${added.length} file, tổng ${addedWords.toLocaleString("vi-VN")} từ.`
            : `Đã thêm ${added.length} chương, tổng ${addedWords.toLocaleString("vi-VN")} từ.`
          : "Không thêm được chương nào.",
        mode === "replace"
          ? "Đã so chương theo cả số chương và tên chương, không ghi đè theo vị trí file."
          : "",
        mode === "replace" && skippedCount > 0
          ? `Bỏ qua ${skippedCount} chương vì số và tên chương đã trùng, nội dung không đổi.`
          : "",
        mode === "replace" && appendedCount === 0
          ? "Không có chương mới được bổ sung: file chỉ có chương trùng với danh sách hiện tại, hoặc ít hơn/bằng số chương đang có."
          : "",
        sampleUpdated.length > 0
          ? `Chương đã cập nhật: ${sampleUpdated.join(", ")}${updatedCount > sampleUpdated.length ? "..." : "."}`
          : "",
        sampleAdded.length > 0
          ? `Chương mới: ${sampleAdded.join(", ")}${appendedCount > sampleAdded.length ? "..." : "."}`
          : "",
        // The rule for this flow, restated where it matters: the file decides
        // where its chapters end, so nothing is cut by word count however long
        // a chapter is.
        ...(added.length > 0
          ? [
            multiChapterFiles > 0
              ? `${multiChapterFiles} file chứa nhiều chương, đã tách theo đúng dòng chương trong file `
                + "(không cắt theo số từ)."
              : "File không có dòng phân chương nên được giữ nguyên thành một chương.",
            // The order chapters were actually read in, shown so a wrongly
            // named file is caught here rather than after the story is up.
            `Thứ tự đọc: ${added.map((c) => c.title).slice(0, 3).join(" → ")}`
              + (added.length > 3
                ? ` → … → ${added.at(-1)?.title ?? ""} (${added.length} chương).`
                : "."),
            multiChapterFiles === 0 && files.some((file) => chapterNumberFromFileName(file.name) == null)
              ? "File không ghi số chương trong tên đã được đánh số nối tiếp chương cuối cùng. "
                + "Hãy đặt tên dạng “Chương 12 - Tên chương” nếu muốn tự quyết định số chương."
              : "",
          ]
          : []),
        ...fileWarnings,
        ...failed.map((entry) => `Bỏ qua: ${entry}`),
        // Blank entries came from the conditionals above and rendered as empty
        // bullets - four of them in a row on a plain append.
      ].filter(Boolean),
      hint: added.length > 0
        ? "Sửa tiêu đề và giá từng chương ở danh sách bên dưới, rồi bấm Lưu."
        : `Định dạng đọc được: ${CHAPTER_FILE_TYPES.replaceAll(",", ", ")}.`,
      kind: mode === "replace" && appendedCount === 0 ? "warning" : kind,
      title: failed.length === 0
        ? mode === "replace"
          ? `Đã cập nhật thông minh ${added.length} file`
          : `Đã thêm ${added.length} chương từ file`
        : `Thêm ${added.length}/${added.length + failed.length} file`,
    });
    setImporting(false);
  }

  function updateChapter(index: number, patch: Partial<ChapterDraft>) {
    setChapters((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  async function handleSave() {
    if (!form.title.trim()) {
      setOutcome({ details: ["Truyện phải có tên trước khi lưu."], kind: "error", title: "Chưa nhập tên truyện" });
      return;
    }
    if (form.categoryIds.length === 0) {
      setOutcome({ details: ["Truyện phải thuộc ít nhất một thể loại thì người đọc mới tìm thấy."], kind: "error", title: "Chưa chọn thể loại" });
      return;
    }
    // Chương để trả phí mà chưa có giá. Trước đây chỗ này chặn cứng lần lưu và
    // bảo người dùng tự đi sửa - với một bộ bảy trăm chương thì đó là đi tìm
    // kim đáy bể. Nay nói rõ chương nào, và cho sửa hết bằng một cú bấm.
    const unpriced = chapters
      .map((chapter, index) => ({ chapter, index }))
      .filter(({ chapter }) => chapter.accessType === "PAID" && chapter.coinPrice <= 0);
    if (unpriced.length > 0) {
      const names = unpriced
        .slice(0, 5)
        .map(({ chapter, index }) => chapter.title || `Chương ${index + 1}`);
      setOutcome({
        action: {
          label: `Chuyển ${unpriced.length} chương đó về miễn phí`,
          run: () => {
            const indexes = new Set(unpriced.map(({ index }) => index));
            setChapters((rows) => rows.map((row, index) => (
              indexes.has(index) ? { ...row, accessType: "FREE" as const, coinPrice: 0 } : row
            )));
          },
        },
        details: [
          `${unpriced.length} chương đang để trả phí nhưng giá bằng 0 Xu: ${names.join(", ")}`
            + (unpriced.length > names.length ? `, và ${unpriced.length - names.length} chương nữa.` : "."),
          "Chương trả phí giá 0 sẽ mở khoá miễn phí, nên máy chủ không nhận.",
        ],
        hint: "Đặt giá lớn hơn 0 cho từng chương, hoặc bấm nút bên dưới để chuyển hết về miễn phí.",
        kind: "error",
        title: "Chương trả phí chưa có giá",
      });
      return;
    }
    // The server refuses a chapter with no content rather than dropping it
    // silently; saying so here names the chapter before the upload starts.
    const emptyIndex = chapters.findIndex((c) => !c.content.trim());
    if (emptyIndex >= 0) {
      const chapter = chapters[emptyIndex]!;
      setNotice({
        kind: "err",
        text: `Chương ${emptyIndex + 1}${chapter.title ? ` ("${chapter.title}")` : ""} chưa có nội dung. `
          + "Hãy nhập nội dung hoặc đính kèm file, hoặc xoá chương này.",
      });
      return;
    }

    // Chapters that exist on the server but are no longer in the form: the
    // publisher removed them with the delete button. The server refuses a
    // shorter list unless it is told the shortening was deliberate, so the
    // confirmation is asked for here and the answer travels with the save.
    const keptIds = new Set(chapters.map((chapter) => chapter.id).filter(Boolean));
    const deleted = existingChapters.filter((row) => !keptIds.has(row.id));
    if (deleted.length > 0) {
      const names = deleted
        .slice(0, 5)
        .map((row) => row.title || `Chương ${row.chapterNumber}`)
        .join(", ");
      const confirmed = window.confirm(
        `Sẽ xoá vĩnh viễn ${deleted.length} chương khỏi truyện:\n\n${names}`
        + `${deleted.length > 5 ? `, và ${deleted.length - 5} chương khác` : ""}.\n\n`
        + "Chương đã có người mua sẽ được giữ lại. Tiếp tục?",
      );
      if (!confirmed) return;
    }

    if (chapters.length > MAX_CHAPTERS_PER_SAVE) {
      setOutcome({
        details: [
          `Danh sách có ${chapters.length.toLocaleString("vi-VN")} chương, vượt giới hạn ${MAX_CHAPTERS_PER_SAVE.toLocaleString("vi-VN")} chương mỗi lần lưu.`,
        ],
        hint: "Hãy tách truyện thành nhiều lần lưu nhỏ hơn.",
        kind: "error",
        title: "Quá nhiều chương",
      });
      return;
    }
    const bodyBytes = chapterBodiesByteSize(chapters);
    if (bodyBytes > MAX_CHAPTER_BODY_BYTES_PER_SAVE) {
      setOutcome({
        details: [
          `Nội dung chương khoảng ${formatBytes(bodyBytes)}, vượt giới hạn an toàn ${formatBytes(MAX_CHAPTER_BODY_BYTES_PER_SAVE)} mỗi lần lưu.`,
        ],
        hint: "Hãy chia file thành nhiều phần rồi lưu từng phần để tránh lỗi timeout hoặc request quá lớn.",
        kind: "error",
        title: "Nội dung quá lớn",
      });
      return;
    }

    if (isExclusiveType(form.storyType) && !exclusiveSigned) {
      setOutcome({
        details: [
            "Bạn đang đặt truyện ở diện độc quyền, mức ăn chia 90% thay vì 70%.",
            "Hãy đọc và tick vào cam kết độc quyền ở ngay dưới ô phân loại truyện.",
        ],
        hint: "Nếu truyện có đăng ở nơi khác, hãy chọn “Truyện chữ” hoặc “Truyện audio”.",
        kind: "error",
        title: "Chưa xác nhận cam kết độc quyền",
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
      // Opting in only when the form carries chapters kept a title edit from
      // wiping the chapter list - but it also meant emptying the list did
      // nothing at all: the delete went through in the form and the chapters
      // were still on the server afterwards. Removing every chapter is a
      // deletion the publisher confirmed, so it is sent as one.
      if (chapters.length > 0 || deleted.length > 0) {
        body.append("replaceChapters", "true");
        if (deleted.length > 0) body.append("allowChapterDeletion", "true");
        chapters.forEach((chapter, index) => {
          const title = chapter.title || `Chương ${index + 1}`;
          // Always the text. Files are decoded in the browser now, so the
          // server never has to guess at an encoding, and what the form shows
          // is exactly what gets stored.
          body.append(`chapters[${index}].content`, chapter.content);
          // The id of the chapter this draft is editing. Without it the server
          // cannot tell an edit from a replacement, and matched drafts to rows
          // by position - so reordering or inserting a chapter rewrote the
          // wrong ones and readers lost what they had bought.
          if (chapter.id) body.append(`chapters[${index}].id`, chapter.id);
          body.append(`chapters[${index}].title`, title);
          body.append(`chapters[${index}].slug`, slugifyChapter(title) || `chuong-${index + 1}`);
          // Chốt chặn cuối: giá và loại chương phải khớp nhau. Năm chỗ trong
          // form đặt hai trường này rời nhau, nên thay vì tin cả năm chỗ thì
          // buộc lại đúng một lần ngay trước khi gửi đi.
          const priced = normalizeChapterPricing(chapter);
          body.append(`chapters[${index}].accessType`, priced.accessType);
          body.append(`chapters[${index}].coinPrice`, String(priced.coinPrice));
        });
      }

      const path = selectedId
        ? `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories/${encodeURIComponent(selectedId)}`
        : `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories`;
      const res = await authedFetch(path, { method: selectedId ? "PUT" : "POST", body });

      if (!res.ok) {
        const problem = (await res.json().catch(() => ({}))) as { detail?: string };
        setOutcome({ details: [problem.detail ?? `Máy chủ trả về lỗi HTTP ${res.status}.`], hint: "Không có thay đổi nào được lưu. Sửa theo thông báo trên rồi bấm Lưu lại.", kind: "error", title: "Lưu thất bại" });
        return;
      }
      const saved = (await res.json()) as { id: string };
      const wasEditing = selectedId != null;
      const sentChapters = chapters.length;
      // Reload the story from the server and show what was actually stored,
      // rather than emptying the chapter list and leaving the form claiming the
      // story has none. The reload also proves the save landed: if the genres
      // or chapters come back different from what was sent, it is visible here
      // instead of at the next edit.
      setCoverFile(null);
      const rows = await authedFetch(`${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories`)
        .then((response) => (response.ok ? (response.json() as Promise<StoryRow[]>) : []))
        .catch(() => [] as StoryRow[]);
      setStories(rows);
      const savedRow = rows.find((row) => row.id === saved.id);
      if (savedRow) {
        await selectStory(savedRow);
        // Reported from what came back, not from what was sent. If the server
        // stored fewer chapters or dropped a genre, the figures below disagree
        // and the publisher sees it now rather than at the next edit.
        const storedChapters = savedRow.chapterCount;
        const details = [
          `Truyện: ${savedRow.title}`,
          sentChapters > 0
            ? `Chương: đã gửi ${sentChapters}, hiện có ${storedChapters} chương trên máy chủ.`
            : `Chương: ${storedChapters} chương (lần lưu này không đổi chương nào).`,
          `Thể loại: ${(savedRow.categoryIds ?? []).length} thể loại đã lưu.`,
          `Tag: ${(savedRow.tags ?? []).length > 0 ? (savedRow.tags ?? []).join(", ") : "không có"}.`,
          savedRow.coverUrl ? "Ảnh bìa: đã có." : "Ảnh bìa: chưa có.",
          `Trạng thái: ${STATUS_LABELS[savedRow.status] ?? savedRow.status}.`,
        ];
        const mismatch = sentChapters > 0 && storedChapters !== sentChapters;
        if (mismatch) {
          details.push(
            "Số chương trên máy chủ khác số chương đã gửi. Thường là do có chương đã được "
            + "độc giả mua nên được giữ lại. Hãy kiểm tra danh sách chương.",
          );
        }
        setOutcome({
          details,
          hint: mismatch ? undefined : "Nội dung đã cập nhật trên trang.",
          kind: mismatch ? "warning" : "success",
          title: wasEditing ? "Đã lưu thay đổi" : "Đã tạo truyện mới",
        });
      } else {
        setSelectedId(saved.id);
        setOutcome({
          details: ["Đã lưu, nhưng chưa tải lại được truyện để đối chiếu."],
          hint: "Tải lại trang để xem nội dung mới nhất.",
          kind: "warning",
          title: wasEditing ? "Đã lưu thay đổi" : "Đã tạo truyện mới",
        });
      }
    } catch {
      setOutcome({ details: ["Không kết nối được máy chủ."], hint: "Kiểm tra mạng rồi bấm Lưu lại. Nội dung trong form vẫn còn nguyên.", kind: "error", title: "Mất kết nối" });
    } finally {
      setSaving(false);
    }
  }

  /**
   * Removes the story for good.
   *
   * <p>The server refuses once anyone has paid for any part of it, and says so;
   * that message is passed straight through, because "hide it instead" is the
   * answer and only the server knows it applies.
   */
  async function handleDelete() {
    if (!selectedId) return;
    const story = selectedStory;
    setSaving(true);
    setConfirmingDelete(false);
    try {
      const res = await authedFetch(
        `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}/stories/${encodeURIComponent(selectedId)}/permanent`,
        { method: "DELETE" },
      );
      if (!res.ok) {
        const problem = (await res.json().catch(() => ({}))) as { detail?: string };
        setOutcome({
          details: [problem.detail ?? `Máy chủ trả về lỗi HTTP ${res.status}.`],
          hint: "Truyện vẫn còn nguyên. Nếu chỉ muốn gỡ khỏi trang, hãy dùng “Ẩn truyện”.",
          kind: "error",
          title: "Xoá truyện thất bại",
        });
        return;
      }
      setOutcome({
        details: [
          `Đã xoá “${story?.title ?? ""}” cùng ${story?.chapterCount ?? 0} chương.`,
          "Thao tác này không hoàn tác được.",
        ],
        kind: "success",
        title: "Đã xoá truyện",
      });
      startNewStory();
      await loadStories();
    } catch {
      setOutcome({
        details: ["Không kết nối được máy chủ."],
        hint: "Truyện chưa bị xoá. Kiểm tra mạng rồi thử lại.",
        kind: "error",
        title: "Mất kết nối",
      });
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
        setOutcome({ details: [problem.detail ?? "Không ẩn được truyện."], kind: "error", title: "Ẩn truyện thất bại" });
        return;
      }
      setOutcome({ details: ["Truyện không còn hiển thị với người đọc. Chương và lượt mua vẫn được giữ nguyên."], kind: "success", title: "Đã ẩn truyện" });
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

          <OperationDialog onClose={() => setOutcome(null)} outcome={outcome} />
          <FormDialog onClose={() => setAsk(null)} request={ask} />

          {/* Deleting a story takes its chapters with it, so the title has to be
              typed out. A single "are you sure" is too easy to click through
              when the list of stories all look alike. */}
          {confirmingDelete && selectedStory ? (
            <div className="opDialogBackdrop" onClick={() => setConfirmingDelete(false)} role="presentation">
              <div
                className="opDialog"
                data-kind="error"
                onClick={(event) => event.stopPropagation()}
                role="alertdialog"
              >
                <header className="opDialogHead">
                  <Trash2 aria-hidden="true" size={22} />
                  <div>
                    <p className="opDialogEyebrow">Không hoàn tác được</p>
                    <h2>Xoá “{selectedStory.title}”?</h2>
                  </div>
                </header>
                <ul className="opDialogDetails">
                  <li>Xoá cả {selectedStory.chapterCount} chương của truyện.</li>
                  <li>Không khôi phục lại được.</li>
                  <li>Truyện đã có người mua thì máy chủ sẽ từ chối — khi đó hãy dùng “Ẩn truyện”.</li>
                </ul>
                <label className="pubConfirmType">
                  Gõ đúng tên truyện để xác nhận:
                  <input
                    onChange={(event) => setDeleteConfirm(event.target.value)}
                    placeholder={selectedStory.title}
                    value={deleteConfirm}
                  />
                </label>
                <div className="pubConfirmActions">
                  <button className="pubGhostBtn" onClick={() => setConfirmingDelete(false)} type="button">
                    Huỷ
                  </button>
                  <button
                    className="pubDangerBtn"
                    disabled={deleteConfirm.trim() !== selectedStory.title.trim()}
                    onClick={() => void handleDelete()}
                    type="button"
                  >
                    Xoá vĩnh viễn
                  </button>
                </div>
              </div>
            </div>
          ) : null}
          <PublisherTabs active="stories" memberRole={access?.memberRole} teamId={teamId} />

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
                            <Eye aria-label="Lượt xem" className="pubViewIcon" size={12} />
                            {number.format(story.viewCount)}
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

              {/* Holes in the numbering, stated as soon as the story is opened.
                  A saved story shows no sign of them: chapter_number is
                  reassigned by position on save, so it always reads 1…N however
                  many the source skipped. */}
              {selectedId ? <MissingChaptersNotice titles={chapterTitles} /> : null}

              {/* First thing in the editor: drop a file and the rest fills itself.
                  Everything below stays editable by hand afterwards. */}
              <section className="pubImportPanel">
                <div>
                  <strong>Upload file truyện để tự điền</strong>
                  <small>
                    Hỗ trợ {CHAPTER_FILE_TYPES.replaceAll(",", ", ")}. File nào có tiêu đề chương
                    (“Chương 1”, “Chương 2”…) thì giữ nguyên đúng số chương của file, không cắt
                    thêm. File không đánh dấu chương thì cắt mỗi{" "}
                    {form.storyFormat === "ONESHOT" ? WORDS_PER_CHAPTER_ZHIHU : WORDS_PER_CHAPTER} từ
                    một chương. Tên truyện, tác giả và giới thiệu được đọc từ file; thiếu thì lấy
                    theo tên file hoặc để bạn nhập tay.
                  </small>
                </div>
                <label className="pubImportBtn">
                  <Paperclip aria-hidden="true" size={15} />
                  {importing ? "Đang đọc…" : "Chọn file truyện"}
                  <input
                    accept={CHAPTER_FILE_TYPES}
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

              {/* Full width and tall: a synopsis lifted from a file routinely
                  runs to several paragraphs, and four rows in a half-width
                  column showed about two lines of it. */}
              <label className="pubField pubFieldWide">
                <span>
                  Giới thiệu
                  <small className="pubFieldCount">
                    {form.summary.length.toLocaleString("vi-VN")} /{" "}
                    {SYNOPSIS_MAX_LENGTH.toLocaleString("vi-VN")} ký tự
                  </small>
                </span>
                <textarea
                  className="pubSynopsis"
                  maxLength={SYNOPSIS_MAX_LENGTH}
                  onChange={(e) => setForm({ ...form, summary: e.target.value })}
                  placeholder="Nội dung giới thiệu truyện"
                  rows={12}
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
                  <span>Phân loại truyện</span>
                  <select
                    onChange={(e) => {
                      const storyType = e.target.value as FormState["storyType"];
                      setForm({ ...form, storyType });
                      // Đổi sang diện độc quyền thì phải đọc lại cam kết; đổi ra
                      // khỏi diện đó thì chữ ký cũ không còn ý nghĩa.
                      if (!isExclusiveType(storyType)) setExclusiveSigned(false);
                    }}
                    value={form.storyType}
                  >
                    <option value="TEXT">Truyện chữ (đăng lại)</option>
                    <option value="AUDIO">Truyện audio (đăng lại)</option>
                    <option value="EXCLUSIVE">Truyện độc quyền</option>
                    <option value="ORIGINAL">Truyện sáng tác</option>
                  </select>
                </label>
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

              {/* Chỉ hiện khi người đăng thực sự chọn diện độc quyền. Đây là lời
                  nhắc trước khi chọn, không phải hợp đồng - nhưng phải nói thật rõ
                  được gì và phải giữ điều gì, vì mức ăn chia thay đổi theo lựa chọn
                  này. */}
              {isExclusiveType(form.storyType) ? (
                <div className="pubExclusive">
                  <strong>Cam kết độc quyền</strong>
                  <p>
                    Truyện ở diện <b>độc quyền</b> hay <b>sáng tác</b> được hưởng{" "}
                    <b>90% doanh thu</b> (nền tảng giữ 10%), thay vì 70% như truyện đăng lại.
                    Đổi lại, truyện chỉ được phát hành tại Giới Truyện.
                  </p>
                  <ul>
                    <li>Truyện do nhóm bạn sáng tác hoặc có quyền phát hành hợp lệ.</li>
                    <li>Không đăng cùng nội dung này ở nền tảng đọc truyện khác.</li>
                    <li>
                      Nếu truyện đã có ở nơi khác, hãy chọn “Truyện chữ” hoặc
                      “Truyện audio” — vẫn đăng được bình thường, chỉ khác mức ăn chia.
                    </li>
                  </ul>
                  <label className="pubExclusiveTick">
                    <input
                      checked={exclusiveSigned}
                      onChange={(e) => setExclusiveSigned(e.target.checked)}
                      type="checkbox"
                    />
                    <span>
                      Tôi xác nhận truyện này chỉ phát hành tại Giới Truyện và chịu trách
                      nhiệm về cam kết này.
                    </span>
                  </label>
                </div>
              ) : null}

              <label className="pubField">
                <span>Tags (cách nhau bằng dấu phẩy)</span>
                <input
                  onChange={(e) => setForm({ ...form, tags: e.target.value })}
                  placeholder="ngôn tình, chữa lành"
                  value={form.tags}
                />
              </label>

              {/* The cover already on the story is shown beside the picker.
                  Without it the field looked empty on every reopen, which read
                  as "the cover was lost" - and left no way to tell whether a
                  save had kept it. */}
              <label className="pubField">
                <span>Ảnh bìa</span>
                <div className="pubCoverField">
                  {coverFile || selectedStory?.coverUrl ? (
                    <img
                      alt="Ảnh bìa hiện tại"
                      className="pubCoverPreview"
                      src={coverFile
                        ? URL.createObjectURL(coverFile)
                        : coverUrl(selectedStory?.coverUrl)}
                    />
                  ) : (
                    <span className="pubCoverPreview pubCoverEmpty" aria-hidden="true" />
                  )}
                  <div>
                    <input
                      accept="image/*"
                      onChange={(e) => setCoverFile(e.target.files?.[0] ?? null)}
                      type="file"
                    />
                    <small className="pubHint">
                      {coverFile
                        ? "Ảnh mới sẽ thay ảnh cũ khi bấm Lưu."
                        : selectedStory?.coverUrl
                          ? "Đang dùng ảnh này. Không chọn file mới thì ảnh cũ được giữ nguyên."
                          : "Chưa có ảnh bìa."}
                    </small>
                  </div>
                </div>
              </label>

              {/* A combo sells the whole story at once, which only means
                  anything once the story is finished - on a running story the
                  buyer would be paying for chapters that do not exist yet. The
                  field appears when the story is marked complete and is hidden
                  otherwise. */}
              {form.completionStatus === "COMPLETED" ? (
                <label className="pubField">
                  <span>Giá mua cả truyện (Xu)</span>
                  <input
                    min={0}
                    onChange={(e) => setForm({ ...form, comboPriceXu: e.target.value })}
                    placeholder="Để trống = không bán combo"
                    type="number"
                    value={form.comboPriceXu}
                  />
                  <small className="pubHint">
                    {(() => {
                      const retail = chapters
                        .filter((chapter) => chapter.accessType === "PAID")
                        .reduce((sum, chapter) => sum + (chapter.coinPrice || 0), 0);
                      const combo = Number(form.comboPriceXu);
                      if (!form.comboPriceXu.trim()) {
                        return retail > 0
                          ? `Để trống thì không có combo. Mua lẻ từng chương hết ${formatXu(retail)} Xu.`
                          : "Để trống thì không có combo.";
                      }
                      if (!Number.isFinite(combo) || combo <= 0) return "Giá combo phải lớn hơn 0.";
                      if (retail <= 0) return `Combo ${formatXu(combo)} Xu. Hiện chưa có chương nào tính phí.`;
                      if (combo >= retail) {
                        return `Combo ${formatXu(combo)} Xu đang cao hơn mua lẻ (${formatXu(retail)} Xu) `
                          + "nên người đọc sẽ không mua. Hãy nhập thấp hơn.";
                      }
                      const saved = Math.round(((retail - combo) / retail) * 100);
                      return `Mua lẻ ${formatXu(retail)} Xu, combo ${formatXu(combo)} Xu — người đọc tiết kiệm ${saved}%.`;
                    })()}
                  </small>
                </label>
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
                      Thêm file
                      <input
                        accept={CHAPTER_FILE_TYPES}
                        multiple
                        onChange={(event) => {
                          void addChaptersFromFiles(Array.from(event.currentTarget.files ?? []));
                          event.currentTarget.value = "";
                        }}
                        type="file"
                      />
                    </label>
                    {/* Updates by chapter identity: same number and same title.
                        A different title with the same number is a new chapter,
                        not a positional overwrite. */}
                    {chapters.length > 0 ? (
                      <label className="pubGhostBtn pubFileBtn">
                        <Paperclip aria-hidden="true" size={14} />
                        Cập nhật thông minh
                        <input
                          accept={CHAPTER_FILE_TYPES}
                          multiple
                          onChange={(event) => {
                            const files = Array.from(event.currentTarget.files ?? []);
                            event.currentTarget.value = "";
                            if (files.length > 0) void addChaptersFromFiles(files, "replace");
                          }}
                          type="file"
                        />
                      </label>
                    ) : null}
                    <button className="pubGhostBtn" onClick={openAddChapter} type="button">
                      <Plus aria-hidden="true" size={14} />
                      Thêm chương
                    </button>
                  </div>
                </div>

                {/* Bulk actions, matching the admin workspace: tick a set of
                    chapters, then price or remove them in one go. Editing 700
                    chapters one at a time is not a workflow. */}
                {/* The preset that fits almost every paid story: a few opening
                    chapters free, one price on the rest. Applied to the whole
                    list, so it needs no selection at all. */}
                {chapters.length > 0 ? (
                  <div className="pubQuickPrice">
                    <strong>Đặt giá nhanh</strong>
                    <span>Miễn phí</span>
                    <input
                      aria-label="Số chương đầu miễn phí"
                      min={0}
                      onChange={(event) => setFreeChapterCount(
                        event.target.value === '' ? '' : Math.max(0, Number(event.target.value)))}
                      type="number"
                      value={freeChapterCount}
                    />
                    <span>
                      chương đầu, từ chương {(Number(freeChapterCount) || 0) + 1} trở đi khoá
                    </span>
                    <input
                      aria-label="Giá xu mỗi chương khoá"
                      min={0}
                      onChange={(event) => setPaidChapterPrice(
                        event.target.value === '' ? '' : Math.max(0, Number(event.target.value)))}
                      type="number"
                      value={paidChapterPrice}
                    />
                    <span>Xu / chương</span>
                    <button className="pubGhostBtn" onClick={applyFreeThenPaid} type="button">
                      Áp dụng cho {chapters.length} chương
                    </button>
                  </div>
                ) : null}

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
                    {/* Shortcuts, because with twenty chapters to a page "all"
                        and "one at a time" were the only two ways to select. */}
                    <button className="pubLinkBtn" onClick={pickCurrentPage} type="button">
                      Chọn cả trang này
                    </button>
                    <span className="pubRangePick">
                      Chọn chương
                      <input
                        aria-label="Từ chương thứ"
                        min={1}
                        onChange={(event) => setRangeFrom(
                          event.target.value === '' ? '' : Number(event.target.value))}
                        placeholder="từ"
                        type="number"
                        value={rangeFrom}
                      />
                      –
                      <input
                        aria-label="Đến chương thứ"
                        min={1}
                        onChange={(event) => setRangeTo(
                          event.target.value === '' ? '' : Number(event.target.value))}
                        placeholder="đến"
                        type="number"
                        value={rangeTo}
                      />
                      <button
                        className="pubLinkBtn"
                        disabled={rangeFrom === '' || rangeTo === ''}
                        onClick={pickRange}
                        type="button"
                      >
                        Chọn
                      </button>
                    </span>
                    {picked.size > 0 ? (
                      <button className="pubLinkBtn" onClick={() => setPicked(new Set())} type="button">
                        Bỏ chọn
                      </button>
                    ) : null}
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
                {/* Only the current page is mounted. A 2,500-chapter story used
                    to render 2,500 textareas at once. */}
                <ChapterEditorPager onChange={setChapterPage} page={chapterPage} titles={chapterTitles} total={chapters.length} />
                {chapterPageSlice(chapters, chapterPage).map(({ chapter, index }) => (
                  <div className="pubChapterRow" key={chapter.id ?? index}>
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
                          // Chọn "Trả phí" mà giá vẫn là 0 tạo ra một chương máy
                          // chủ từ chối, và cả lần lưu hỏng vì một ô chưa điền.
                          // Điền sẵn mức giá đang dùng cho các chương khác.
                          updateChapter(index, e.target.value === "FREE"
                            ? { accessType: "FREE", coinPrice: 0 }
                            : {
                              accessType: "PAID",
                              coinPrice: chapter.coinPrice > 0 ? chapter.coinPrice : suggestedPaidPrice,
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
                    {/* Always the text, never a file placeholder. Files picked
                        at the top of the panel are decoded into this box before
                        they get here, so what is on screen is what will be
                        saved - and it stays editable. */}
                    <textarea
                      onChange={(e) => updateChapter(index, { content: e.target.value })}
                      placeholder="Nội dung chương"
                      rows={8}
                      value={chapter.content}
                    />
                  </div>
                ))}
                {/* Repeated below the rows: after editing twenty chapters the top pager has scrolled away. */}
                <ChapterEditorPager onChange={setChapterPage} page={chapterPage} titles={chapterTitles} total={chapters.length} />
              </div>

              <div className="pubActions">
                <button className="publisherPrimaryBtn" disabled={saving} onClick={handleSave} type="button">
                  {saving ? <Loader2 aria-hidden="true" size={15} /> : <Save aria-hidden="true" size={15} />}
                  {saving ? "Đang lưu…" : selectedId ? "Lưu thay đổi" : "Tạo truyện"}
                </button>
                {selectedId ? (
                  <>
                  <button className="pubDangerBtn" disabled={saving} onClick={handleHide} type="button">
                    <EyeOff aria-hidden="true" size={15} />
                    Ẩn truyện
                  </button>
                  {/* Deleting is separate from hiding and asks for the title to
                      be typed. Hiding is reversible; this is not. */}
                  <button
                    className="pubDangerBtn"
                    disabled={saving}
                    onClick={() => { setDeleteConfirm(""); setConfirmingDelete(true); }}
                    type="button"
                  >
                    <Trash2 aria-hidden="true" size={15} />
                    Xoá truyện
                  </button>
                  </>
                ) : null}
              </div>
            </section>
          </div>
        </div>
      </main>
    </PublicShell>
  );
}
