"use client";

import {
  createBrowserPublishingClient,
  createPublicCatalogClient,
  StoryApiError,
  type CategoryTaxonomy,
  type PublishingChapter,
  type PublishingStory,
} from "@gioitruyen/api-client";
import { BrandMark, StatusPill } from "@gioitruyen/ui";
import type { Route } from "next";
import Link from "next/link";
import {
  type FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { localDateTimeWithOffset } from "../lib/publishing-time";
import styles from "./publishing-workspace.module.css";

const editableStates = new Set(["DRAFT", "CHANGES_REQUESTED"]);
const stages = [
  ["DRAFT", "Bản thảo"],
  ["IN_REVIEW", "Đang duyệt"],
  ["APPROVED", "Đã duyệt"],
  ["SCHEDULED", "Đã lên lịch"],
  ["PUBLISHED", "Đã xuất bản"],
] as const;

function errorMessage(error: unknown) {
  if (error instanceof StoryApiError) {
    const messages: Readonly<Record<string, string>> = {
      CHAPTER_VERSION_STALE:
        "Chương đã được sửa ở nơi khác. Tải bản mới trước khi viết tiếp.",
      STORY_VERSION_STALE:
        "Thông tin truyện đã đổi ở nơi khác. Tải lại để tránh ghi đè.",
      STORY_CHAPTER_REQUIRED:
        "Truyện cần ít nhất một chương hoàn chỉnh trước khi gửi duyệt.",
      PUBLISHING_SCHEDULE_EXISTS: "Truyện đã có một lịch xuất bản.",
      APPROVED_REVISION_NOT_FOUND:
        "Chỉ revision đã được duyệt mới có thể lên lịch.",
    };
    return (
      messages[error.problem.code] ??
      error.problem.detail ??
      "Yêu cầu chưa hoàn tất. Kiểm tra dữ liệu rồi thử lại."
    );
  }
  return "Không thể kết nối máy chủ. Kiểm tra mạng rồi thử lại.";
}

type StoryDraftFields = Pick<
  PublishingStory,
  "title" | "synopsis" | "categoryIds" | "coverAssetId" | "completionStatus"
>;

export function PublishingWorkspace({
  teamId,
}: Readonly<{ teamId: string }>) {
  const api = useMemo(() => createBrowserPublishingClient(), []);
  const catalog = useMemo(
    () => createPublicCatalogClient({ baseUrl: "/api/catalog" }),
    [],
  );
  const [stories, setStories] = useState<PublishingStory[]>([]);
  const [selected, setSelected] = useState<PublishingStory | null>(null);
  const [storyDraft, setStoryDraft] = useState<StoryDraftFields | null>(null);
  const [chapters, setChapters] = useState<PublishingChapter[]>([]);
  const [chapter, setChapter] = useState<PublishingChapter | null>(null);
  const [chapterTitle, setChapterTitle] = useState("");
  const [chapterContent, setChapterContent] = useState("");
  const [taxonomy, setTaxonomy] = useState<CategoryTaxonomy | null>(null);
  const [loading, setLoading] = useState(true);
  const [storyDirty, setStoryDirty] = useState(false);
  const [chapterDirty, setChapterDirty] = useState(false);
  const [savingStory, setSavingStory] = useState(false);
  const [savingChapter, setSavingChapter] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const storyGeneration = useRef(0);
  const chapterGeneration = useRef(0);

  const loadChapters = useCallback(
    async (story: PublishingStory) => {
      const items = await api.chapters(teamId, story.id);
      setChapters(items);
      setChapter((current) => {
        const next =
          items.find((item) => item.id === current?.id) ?? items[0] ?? null;
        setChapterTitle(next?.title ?? "");
        setChapterContent(next?.contentHtml ?? "");
        setChapterDirty(false);
        return next;
      });
    },
    [api, teamId],
  );

  const chooseStory = useCallback(
    async (story: PublishingStory) => {
      setSelected(story);
      setStoryDraft({
        categoryIds: story.categoryIds,
        completionStatus: story.completionStatus,
        coverAssetId: story.coverAssetId,
        synopsis: story.synopsis,
        title: story.title,
      });
      setStoryDirty(false);
      setError("");
      await loadChapters(story);
    },
    [loadChapters],
  );

  const reload = useCallback(async () => {
    try {
      const [storyItems, taxonomyValue] = await Promise.all([
        api.stories(teamId),
        catalog.categories(),
      ]);
      setStories(storyItems);
      setTaxonomy(taxonomyValue);
      if (storyItems[0]) await chooseStory(storyItems[0]);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [api, catalog, chooseStory, teamId]);

  useEffect(() => {
    const bootstrap = window.setTimeout(() => {
      void reload();
    }, 0);

    return () => window.clearTimeout(bootstrap);
  }, [reload]);

  useEffect(() => {
    if (
      !storyDirty ||
      !selected ||
      !storyDraft ||
      savingStory ||
      !editableStates.has(selected.workflowStatus)
    ) {
      return;
    }
    const generation = storyGeneration.current;
    const timer = window.setTimeout(async () => {
      setSavingStory(true);
      try {
        const updated = await api.updateStory(
          teamId,
          selected.id,
          selected.version,
          storyDraft,
        );
        setSelected(updated);
        setStories((current) =>
          current.map((item) => (item.id === updated.id ? updated : item)),
        );
        if (generation === storyGeneration.current) setStoryDirty(false);
        setError("");
      } catch (requestError) {
        setError(errorMessage(requestError));
        setStoryDirty(false);
      } finally {
        setSavingStory(false);
      }
    }, 900);
    return () => window.clearTimeout(timer);
  }, [api, savingStory, selected, storyDirty, storyDraft, teamId]);

  useEffect(() => {
    if (
      !chapterDirty ||
      !chapter ||
      !selected ||
      savingChapter ||
      !editableStates.has(selected.workflowStatus)
    ) {
      return;
    }
    const generation = chapterGeneration.current;
    const timer = window.setTimeout(async () => {
      setSavingChapter(true);
      try {
        const updated = await api.updateChapter(
          teamId,
          selected.id,
          chapter.id,
          chapter.version,
          { contentHtml: chapterContent, title: chapterTitle },
        );
        setChapter(updated);
        setChapters((current) =>
          current.map((item) => (item.id === updated.id ? updated : item)),
        );
        if (generation === chapterGeneration.current) setChapterDirty(false);
        setError("");
      } catch (requestError) {
        setError(errorMessage(requestError));
        setChapterDirty(false);
      } finally {
        setSavingChapter(false);
      }
    }, 900);
    return () => window.clearTimeout(timer);
  }, [
    api,
    chapter,
    chapterContent,
    chapterDirty,
    chapterTitle,
    savingChapter,
    selected,
    teamId,
  ]);

  function editStory(patch: Partial<StoryDraftFields>) {
    storyGeneration.current += 1;
    setStoryDraft((current) => (current ? { ...current, ...patch } : current));
    setStoryDirty(true);
  }

  function editChapter(title: string, content: string) {
    chapterGeneration.current += 1;
    setChapterTitle(title);
    setChapterContent(content);
    setChapterDirty(true);
  }

  async function createStory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const categoryIds = values
      .getAll("categoryId")
      .map(String)
      .filter(Boolean);
    if (categoryIds.length === 0) {
      setError("Chọn ít nhất một thể loại cho bản thảo.");
      return;
    }
    setError("");
    try {
      const created = await api.createStory(
        teamId,
        {
          categoryIds,
          coverAssetId: null,
          language: String(values.get("language")),
          origin: String(values.get("origin")) as "ORIGINAL" | "TRANSLATED",
          synopsis: String(values.get("synopsis")),
          title: String(values.get("title")),
        },
        crypto.randomUUID(),
      );
      setStories((current) => [created, ...current]);
      form.reset();
      await chooseStory(created);
      setNotice("Đã mở bản thảo mới.");
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  async function createChapter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const form = event.currentTarget;
    const values = new FormData(form);
    try {
      await api.createChapter(teamId, selected.id, {
        contentHtml: String(values.get("contentHtml")),
        number: chapters.length + 1,
        title: String(values.get("title")),
      });
      form.reset();
      await loadChapters(selected);
      setNotice("Đã thêm chương và revision đầu tiên.");
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  async function submitForReview() {
    if (!selected || storyDirty || chapterDirty) return;
    try {
      await api.submit(teamId, selected.id, crypto.randomUUID());
      const next = { ...selected, workflowStatus: "IN_REVIEW" };
      setSelected(next);
      setStories((current) =>
        current.map((item) => (item.id === next.id ? next : item)),
      );
      setNotice("Đã đóng băng revision và gửi sang hàng duyệt.");
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  async function schedule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const values = new FormData(event.currentTarget);
    const localValue = String(values.get("publishAt"));
    try {
      await api.schedule(teamId, selected.id, {
        publishAt: localDateTimeWithOffset(
          localValue,
          new Date(localValue).getTimezoneOffset(),
        ),
        revision: selected.currentRevision,
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      });
      const next = { ...selected, workflowStatus: "SCHEDULED" };
      setSelected(next);
      setStories((current) =>
        current.map((item) => (item.id === next.id ? next : item)),
      );
      setNotice("Đã khóa revision và lên lịch xuất bản.");
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  if (loading) {
    return (
      <main className={styles.loading}>
        <BrandMark />
        <p>Đang mở bàn bản thảo…</p>
      </main>
    );
  }

  const currentStage = stages.findIndex(
    ([state]) => state === selected?.workflowStatus,
  );
  const editable = selected
    ? editableStates.has(selected.workflowStatus)
    : false;

  return (
    <main className={styles.workspace}>
      <aside className={styles.ledger}>
        <Link href={`/teams/${teamId}` as Route}>
          <BrandMark inverse />
        </Link>
        <div className={styles.ledgerTitle}>
          <span>Bàn bản thảo</span>
          <strong>{stories.length.toString().padStart(2, "0")} truyện</strong>
        </div>
        <nav aria-label="Danh sách bản thảo">
          {stories.map((story) => (
            <button
              aria-current={story.id === selected?.id ? "page" : undefined}
              key={story.id}
              onClick={() => void chooseStory(story)}
              type="button"
            >
              <i aria-hidden="true" />
              <span>{story.title}</span>
              <small>{story.workflowStatus.replaceAll("_", " ")}</small>
            </button>
          ))}
        </nav>
        <details className={styles.newStory} open={stories.length === 0}>
          <summary>Mở bản thảo mới</summary>
          <form onSubmit={createStory}>
            <label>
              Tên truyện
              <input maxLength={200} name="title" required />
            </label>
            <label>
              Tóm tắt
              <textarea maxLength={5000} name="synopsis" required rows={4} />
            </label>
            <div className={styles.splitFields}>
              <label>
                Nguồn
                <select defaultValue="ORIGINAL" name="origin">
                  <option value="ORIGINAL">Sáng tác</option>
                  <option value="TRANSLATED">Chuyển ngữ</option>
                </select>
              </label>
              <label>
                Ngôn ngữ
                <input defaultValue="vi" maxLength={16} name="language" required />
              </label>
            </div>
            <fieldset>
              <legend>Thể loại</legend>
              {taxonomy?.groups.flatMap((group) =>
                group.categories.map((category) => (
                  <label key={category.id}>
                    <input name="categoryId" type="checkbox" value={category.id} />
                    {category.name}
                  </label>
                )),
              )}
            </fieldset>
            <button type="submit">Tạo bản thảo</button>
          </form>
        </details>
      </aside>

      <section className={styles.desk}>
        <header className={styles.deskHeader}>
          <div>
            <p>Nhóm biên tập · {teamId.slice(0, 8)}</p>
            <h1>{selected?.title ?? "Chưa có bản thảo"}</h1>
          </div>
          <div className={styles.saveState} role="status">
            <i
              className={
                storyDirty || chapterDirty ? styles.unsaved : styles.saved
              }
            />
            {savingStory || savingChapter
              ? "Đang lưu revision…"
              : storyDirty || chapterDirty
                ? "Có thay đổi chưa lưu"
                : "Đã lưu an toàn"}
          </div>
        </header>

        {error && (
          <div className={styles.error} role="alert">
            <span>{error}</span>
            <button onClick={() => void reload()} type="button">
              Tải bản mới
            </button>
          </div>
        )}
        {notice && !error && (
          <p className={styles.notice} role="status">
            {notice}
          </p>
        )}

        {selected && storyDraft ? (
          <>
            <ol className={styles.proofline} aria-label="Tiến độ xuất bản">
              {stages.map(([state, label], index) => (
                <li
                  className={index <= currentStage ? styles.reached : undefined}
                  key={state}
                >
                  <span>{label}</span>
                </li>
              ))}
            </ol>

            <div className={styles.editorGrid}>
              <section className={styles.storySheet}>
                <div className={styles.sheetLabel}>
                  <span>Hồ sơ truyện</span>
                  <strong>revision {selected.revisionNo}</strong>
                </div>
                <label>
                  Tên truyện
                  <input
                    disabled={!editable}
                    maxLength={200}
                    onChange={(event) => editStory({ title: event.target.value })}
                    value={storyDraft.title}
                  />
                </label>
                <label>
                  Tóm tắt
                  <textarea
                    disabled={!editable}
                    maxLength={5000}
                    onChange={(event) =>
                      editStory({ synopsis: event.target.value })
                    }
                    rows={7}
                    value={storyDraft.synopsis}
                  />
                </label>
                <label>
                  Tiến độ nội dung
                  <select
                    disabled={!editable}
                    onChange={(event) =>
                      editStory({
                        completionStatus: event.target
                          .value as StoryDraftFields["completionStatus"],
                      })
                    }
                    value={storyDraft.completionStatus}
                  >
                    <option value="ONGOING">Đang viết</option>
                    <option value="COMPLETED">Hoàn thành</option>
                    <option value="HIATUS">Tạm nghỉ</option>
                  </select>
                </label>
                <fieldset className={styles.categoryEdit}>
                  <legend>Thể loại</legend>
                  {taxonomy?.groups.flatMap((group) =>
                    group.categories.map((category) => (
                      <label key={category.id}>
                        <input
                          checked={storyDraft.categoryIds.includes(category.id)}
                          disabled={!editable}
                          onChange={(event) =>
                            editStory({
                              categoryIds: event.target.checked
                                ? [...storyDraft.categoryIds, category.id]
                                : storyDraft.categoryIds.filter(
                                    (id) => id !== category.id,
                                  ),
                            })
                          }
                          type="checkbox"
                        />
                        {category.name}
                      </label>
                    )),
                  )}
                </fieldset>
              </section>

              <section className={styles.chapterDesk}>
                <div className={styles.chapterSpines}>
                  {chapters.map((item) => (
                    <button
                      aria-pressed={item.id === chapter?.id}
                      key={item.id}
                      onClick={() => {
                        setChapter(item);
                        setChapterTitle(item.title);
                        setChapterContent(item.contentHtml);
                        setChapterDirty(false);
                      }}
                      type="button"
                    >
                      <span>{String(item.number).padStart(2, "0")}</span>
                      <strong>{item.title}</strong>
                      <small>r{item.revisionNo}</small>
                    </button>
                  ))}
                  {editable && (
                    <details className={styles.addChapter}>
                      <summary>+ Chương</summary>
                      <form onSubmit={createChapter}>
                        <input name="title" placeholder="Tên chương" required />
                        <textarea
                          name="contentHtml"
                          placeholder="<p>Nội dung chương…</p>"
                          required
                          rows={5}
                        />
                        <button type="submit">Thêm chương</button>
                      </form>
                    </details>
                  )}
                </div>

                {chapter ? (
                  <div className={styles.chapterSheet}>
                    <div className={styles.sheetLabel}>
                      <span>Chương {chapter.number}</span>
                      <strong>{chapter.wordCount} từ</strong>
                    </div>
                    <input
                      aria-label="Tên chương"
                      className={styles.chapterTitle}
                      disabled={!editable}
                      onChange={(event) =>
                        editChapter(event.target.value, chapterContent)
                      }
                      value={chapterTitle}
                    />
                    <textarea
                      aria-label="Nội dung HTML"
                      className={styles.manuscript}
                      disabled={!editable}
                      onChange={(event) =>
                        editChapter(chapterTitle, event.target.value)
                      }
                      spellCheck
                      value={chapterContent}
                    />
                  </div>
                ) : (
                  <div className={styles.emptyChapter}>
                    <strong>Chưa có chương.</strong>
                    <span>Mở gáy “+ Chương” để viết revision đầu tiên.</span>
                  </div>
                )}
              </section>
            </div>

            <footer className={styles.workflowActions}>
              <div>
                <StatusPill
                  tone={
                    selected.workflowStatus === "CHANGES_REQUESTED"
                      ? "attention"
                      : "active"
                  }
                >
                  {selected.workflowStatus.replaceAll("_", " ")}
                </StatusPill>
                <span>Story v{selected.version}</span>
              </div>
              {editable && (
                <button
                  disabled={
                    chapters.length === 0 || storyDirty || chapterDirty
                  }
                  onClick={() => void submitForReview()}
                  type="button"
                >
                  Gửi duyệt revision
                </button>
              )}
              {selected.workflowStatus === "APPROVED" && (
                <form onSubmit={schedule}>
                  <input
                    aria-label="Thời điểm xuất bản"
                    name="publishAt"
                    required
                    type="datetime-local"
                  />
                  <button type="submit">Lên lịch xuất bản</button>
                </form>
              )}
            </footer>
          </>
        ) : (
          <div className={styles.blankDesk}>
            <strong>Bàn viết đang trống.</strong>
            <span>Mở một bản thảo ở cột bên trái để bắt đầu.</span>
          </div>
        )}
      </section>
    </main>
  );
}
