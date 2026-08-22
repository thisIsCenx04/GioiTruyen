"use client";

import type { ChapterPage, PublicChapter, PublishedChapterDetail } from "@gioitruyen/api-client";
import { Info, Lock, Pause, RotateCcw, Volume2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";

import { TtsControls, TtsNotice, TtsOptions } from "@/components/tts-controls";
import { readStored, useTts, writeStored } from "@/components/use-tts";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { chapterSpeech, estimateSeconds, formatDuration } from "@/lib/speech";
import type { TtsVoice } from "@/lib/tts-service";

/**
 * Nghe cả bộ truyện: một trang riêng, đọc liên tục từ chương này sang chương kia.
 *
 * <p>Bộ máy đọc nằm trong {@link TtsService}, dùng chung với trang đọc chương.
 * Việc của component này chỉ là ba thứ mà bộ máy không biết: chương nào đang
 * nghe, tải nội dung chương ấy về, và nhớ chỗ đang nghe dở của từng truyện.
 *
 * <p>Chữ để đọc lấy từ API của chương, không quét DOM - nên menu, quảng cáo hay
 * chân trang không có đường lọt vào bản đọc. Danh sách đoạn hiện trên màn hình
 * và danh sách mẩu đưa cho bộ đọc sinh ra từ cùng một lần cắt, nên chỗ tô sáng
 * luôn là chỗ đang được đọc.
 */

const AUTO_NEXT_KEY = "gt:audio:autonext";

/** Số chương giữ lại trong bộ nhớ đệm; đủ để tua lui vài chương mà không phình. */
const CACHE_LIMIT = 8;

function isLocked(chapter: PublicChapter): boolean {
  return chapter.accessType === "PAID" && !chapter.unlocked;
}

type Bookmark = { index: number; chunk: number };

function bookmarkKey(storyId: string) {
  return `gt:audio:${storyId}`;
}

function readBookmark(storyId: string): Bookmark | null {
  const raw = readStored(bookmarkKey(storyId));
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<Bookmark>;
    if (typeof parsed.index !== "number" || parsed.index < 0) return null;
    return { chunk: Math.max(0, parsed.chunk ?? 0), index: parsed.index };
  } catch {
    return null;
  }
}

export function BrowserAudioPlayer({
  initial,
  storyId,
  storyIdOrSlug,
  storySlug,
  storyTitle,
}: Readonly<{
  initial: ChapterPage;
  storyId: string;
  storyIdOrSlug: string;
  storySlug: string;
  storyTitle: string;
}>) {
  const size = Math.max(initial.size, 1);
  const total = initial.total;
  const totalPages = initial.totalPages;

  const [pages, setPages] = useState<Record<number, PublicChapter[]>>({ 1: initial.items });
  const [activeIndex, setActiveIndex] = useState(0);
  const [listPage, setListPage] = useState(1);
  const [paragraphs, setParagraphs] = useState<string[]>([]);
  const [loadingChapter, setLoadingChapter] = useState(false);
  const [chapterError, setChapterError] = useState("");
  const [autoNext, setAutoNext] = useState(true);
  const [resumePoint, setResumePoint] = useState<Bookmark | null>(null);

  const activeIndexRef = useRef(0);
  const autoNextRef = useRef(true);
  const cacheRef = useRef(new Map<string, ReturnType<typeof chapterSpeech>>());
  const pagesRef = useRef<Record<number, PublicChapter[]>>({ 1: initial.items });
  const openRef = useRef<(index: number, fromChunk?: number, autoplay?: boolean) => Promise<void>>(
    async () => undefined,
  );

  useEffect(() => {
    pagesRef.current = pages;
  }, [pages]);

  useEffect(() => {
    setResumePoint(readBookmark(storyId));
    const saved = readStored(AUTO_NEXT_KEY) !== "off";
    setAutoNext(saved);
    autoNextRef.current = saved;
  }, [storyId]);

  /* Hết chương thì sang chương sau - mục đích của cả màn hình này là nghe liền
     mạch, nên dừng lại sau mỗi chương thì vô nghĩa. */
  const finish = useCallback(() => {
    const next = activeIndexRef.current + 1;
    if (autoNextRef.current && next < total) void openRef.current(next, 0, true);
  }, [total]);

  const tts = useTts(finish);
  const { load, play, position, state, stop } = tts;

  const chapterAt = useCallback((index: number): PublicChapter | undefined => {
    const page = Math.floor(index / size) + 1;
    return pagesRef.current[page]?.[index % size];
  }, [size]);

  /** Nạp trang chương chứa vị trí này, nếu chưa có. */
  const ensurePage = useCallback(async (index: number): Promise<PublicChapter | undefined> => {
    const page = Math.floor(index / size) + 1;
    if (page < 1 || page > totalPages) return undefined;
    if (!pagesRef.current[page]) {
      const response = await authedFetch(
        `${API_BASE_URL}/stories/${encodeURIComponent(storyIdOrSlug)}/chapters?page=${page}&size=${size}`,
      ).catch(() => null);
      if (!response?.ok) return undefined;
      const loaded = (await response.json()) as ChapterPage;
      pagesRef.current = { ...pagesRef.current, [page]: loaded.items };
      setPages(pagesRef.current);
    }
    return pagesRef.current[page]?.[index % size];
  }, [size, storyIdOrSlug, totalPages]);

  /**
   * Mở một chương: tải chữ, giao cho bộ máy đọc, và phát nếu được yêu cầu.
   *
   * <p>Gọi `stop()` ngay đầu là điều kiện để không bao giờ đọc chồng: mẩu của
   * chương cũ có thể đang nằm trong hàng của engine, và nếu không huỷ thì nó
   * vẫn phát tiếp sau khi màn hình đã hiện tên chương mới.
   */
  const open = useCallback(async (index: number, fromChunk = 0, autoplay = true) => {
    if (index < 0 || index >= total) return;
    stop();
    activeIndexRef.current = index;
    setActiveIndex(index);
    setListPage(Math.floor(index / size) + 1);
    setChapterError("");
    setLoadingChapter(true);

    try {
      const chapter = await ensurePage(index);
      if (activeIndexRef.current !== index) return;
      if (!chapter) {
        setChapterError("Không tải được danh sách chương. Kiểm tra kết nối rồi thử lại.");
        return;
      }
      if (isLocked(chapter)) {
        setChapterError(
          `Chương ${chapter.number} là chương trả phí và bạn chưa mở khoá, nên bản đọc dừng ở đây. `
          + "Mở khoá chương rồi quay lại để nghe tiếp.",
        );
        setParagraphs([]);
        return;
      }

      let prepared = cacheRef.current.get(chapter.id);
      if (!prepared) {
        const response = await authedFetch(
          `${API_BASE_URL}/chapters/${encodeURIComponent(chapter.id)}`,
          { headers: { Accept: "application/json" } },
        );
        if (!response.ok) throw new Error("Không tải được nội dung chương này.");
        const detail = (await response.json()) as PublishedChapterDetail;
        prepared = chapterSpeech(detail.contentHtml ?? "");
        cacheRef.current.set(chapter.id, prepared);
        // Bộ nhớ đệm có trần: nghe một bộ hai nghìn chương mà giữ hết thì tab
        // phình theo thời gian nghe. Bỏ mục cũ nhất, giữ vài chương gần đây để
        // tua lui không phải gọi mạng lại.
        if (cacheRef.current.size > CACHE_LIMIT) {
          const oldest = cacheRef.current.keys().next().value;
          if (oldest) cacheRef.current.delete(oldest);
        }
      }
      if (activeIndexRef.current !== index) return;

      if (prepared.chunks.length === 0) {
        setChapterError(`Chương ${chapter.number} không có chữ nào để đọc.`);
        setParagraphs([]);
        return;
      }

      setParagraphs(prepared.paragraphs);
      // Đường dẫn tệp tiếng gắn với đúng chương vừa mở; đóng kín id ở đây nên
      // không có đường nào phát nhầm tiếng của chương khác.
      const chapterId = chapter.id;
      load(
        prepared.chunks,
        prepared.paragraphs.length,
        (chunkIndex: number, voice: TtsVoice) =>
          `${API_BASE_URL}/chapters/${encodeURIComponent(chapterId)}/tts/${chunkIndex}.opus?voice=${voice}`,
        fromChunk,
      );
      if (autoplay) play();

      // Nạp sẵn trang chương kế tiếp để lúc hết chương cuối trang không bị một
      // quãng im lặng chờ mạng.
      void ensurePage(index + 1);
    } catch (cause) {
      if (activeIndexRef.current !== index) return;
      setChapterError(cause instanceof Error ? cause.message : "Không phát được chương này.");
    } finally {
      if (activeIndexRef.current === index) setLoadingChapter(false);
    }
  }, [ensurePage, load, play, size, stop, total]);

  useEffect(() => {
    openRef.current = open;
  }, [open]);

  /* Ghi nhớ chỗ đang nghe dở. Chỉ ghi khi đang thật sự đọc, để việc mở trang
     rồi bỏ đi không xoá mất vị trí của lần nghe trước. */
  useEffect(() => {
    if (state.status !== "playing") return;
    writeStored(
      bookmarkKey(storyId),
      JSON.stringify({ chunk: position().chunk, index: activeIndexRef.current }),
    );
  }, [position, state.chunkIndex, state.status, storyId]);

  /* Điều khiển trên màn hình khoá và tai nghe. Nghe truyện thường là lúc màn
     hình đã tắt, khi đó nút bấm trên trang không với tới được. */
  useEffect(() => {
    if (typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    const chapter = chapterAt(activeIndex);
    navigator.mediaSession.metadata = new MediaMetadata({
      album: storyTitle,
      artist: "Đọc tự động · gioitruyen.com",
      title: chapter?.title ?? storyTitle,
    });
    navigator.mediaSession.playbackState = state.status === "playing" ? "playing" : "paused";
  }, [activeIndex, chapterAt, state.status, storyTitle]);

  /* Cuộn theo đoạn đang đọc, nhưng chỉ khi đang thật sự phát: người đang tự
     cuộn để tìm một đoạn khác sẽ rất bực nếu trang giật về chỗ cũ. */
  useEffect(() => {
    if (state.status !== "playing") return;
    document
      .querySelector(`[data-paragraph="${state.paragraphIndex}"]`)
      ?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [state.paragraphIndex, state.status]);

  /** Bấm Phát khi chưa mở chương nào thì mở chương đang chọn. */
  function playFromHere() {
    if (state.chunkCount === 0) {
      void open(activeIndex, 0, true);
      return;
    }
    play();
  }

  const activeChapter = chapterAt(activeIndex);
  const listItems = pages[listPage];
  const resumeChapter = resumePoint ? chapterAt(resumePoint.index) : undefined;
  const reading = state.status === "playing" || state.status === "paused";
  const progress = state.chunkCount === 0
    ? 0
    : Math.round((state.chunkIndex / state.chunkCount) * 100);
  const seconds = paragraphs.length === 0 ? null : estimateSeconds(paragraphs.join(" "), state.rate);

  // Nút Phát của thanh dùng chung gọi thẳng tts.play(); ở trang này lần bấm đầu
  // còn phải tải chương, nên đưa cho nó một controller có play() riêng.
  const controller = { ...tts, play: playFromHere };

  return (
    <>
      <section className="audioPlayerPanel" aria-labelledby="audio-player-title">
        <header>
          <Volume2 aria-hidden="true" />
          <div>
            <p className="detailEyebrow">
              {loadingChapter ? "Đang tải chương" : STATUS_LABELS[state.status]}
            </p>
            <h2 id="audio-player-title">
              {activeChapter?.title ?? `${storyTitle} — chưa có chương nào`}
            </h2>
            {activeChapter ? (
              <p className="audioNowMeta">
                Chương {activeChapter.number} / {total}
                {seconds ? ` · khoảng ${formatDuration(seconds)}` : ""}
                {` · giọng ${state.voice === "male" ? "nam" : "nữ"}`}
              </p>
            ) : null}
          </div>
        </header>

        {/* Nghe tiếp từ lần trước. Một bộ truyện dài hàng trăm chương thì việc
            nhớ hộ vị trí quan trọng ngang cái nút phát. */}
        {resumePoint && resumePoint.index !== activeIndex && !reading ? (
          <button
            className="audioResumeBar"
            onClick={() => void open(resumePoint.index, resumePoint.chunk, true)}
            type="button"
          >
            <RotateCcw aria-hidden="true" size={15} />
            Nghe tiếp chương {resumeChapter?.number ?? resumePoint.index + 1}
          </button>
        ) : null}

        <TtsControls
          controller={controller}
          onNextChapter={activeIndex < total - 1 ? () => void open(activeIndex + 1) : undefined}
          onPreviousChapter={activeIndex > 0 ? () => void open(activeIndex - 1) : undefined}
        />

        {/* Tiến độ bằng chữ, không chỉ bằng thanh màu: người nghe muốn biết còn
            bao lâu nữa, và một thanh chạy không trả lời được câu đó. */}
        {reading && state.chunkCount > 0 ? (
          <p className="audioProgressLine">
            <span>
              Đang đọc: <strong>Đoạn {state.paragraphIndex + 1} / {state.paragraphCount}</strong>
              {" "}· đã đọc {progress}% chương này
            </span>
            {seconds != null && progress < 100 ? (
              <span>Còn khoảng {formatDuration(Math.max(1, Math.round(seconds * (1 - progress / 100))))}</span>
            ) : null}
          </p>
        ) : null}

        {chapterError ? <p className="audioPlayerError" role="alert">{chapterError}</p> : null}
        <TtsNotice controller={tts} />

        {state.status === "completed" && activeIndex >= total - 1 ? (
          <p className="audioPlayerNotice" role="status">
            Đã nghe hết {total} chương của “{storyTitle}”.
          </p>
        ) : null}

        <TtsOptions
          autoNext={autoNext}
          controller={tts}
          onAutoNext={(next) => {
            autoNextRef.current = next;
            setAutoNext(next);
            writeStored(AUTO_NEXT_KEY, next ? "on" : "off");
          }}
        />

        <p className="audioPlayerHint">
          <Info aria-hidden="true" size={14} />
          Bản đọc do trình duyệt tạo trực tiếp từ chữ của chương, không phải file thu sẵn — hãy giữ
          tab này mở trong lúc nghe. Trang tự nhớ chỗ đang nghe dở của từng truyện.
        </p>
      </section>

      {/* Chữ của chương, đúng những đoạn đang được đọc. Không có bản chữ thứ hai
          nào ở đây: cả phần hiển thị lẫn phần đọc đều lấy từ cùng một lần cắt. */}
      {paragraphs.length > 0 ? (
        <section className="audioChapterText" aria-label="Nội dung chương đang đọc">
          {paragraphs.map((paragraph, index) => (
            <p
              className={reading && index === state.paragraphIndex ? "isSpeaking" : undefined}
              data-paragraph={index}
              key={`${index}-${paragraph.slice(0, 24)}`}
            >
              {paragraph}
            </p>
          ))}
        </section>
      ) : null}

      <section className="chapterList audioEpisodeList" aria-labelledby="audio-episodes-title">
        <header>
          <div>
            <p className="detailEyebrow">Danh sách chương</p>
            <h2 id="audio-episodes-title">Nghe từ chương bất kỳ</h2>
          </div>
          <span>{total} chương</span>
        </header>

        {total === 0 ? (
          <p className="emptyCatalog">Truyện này chưa có chương nào được đăng.</p>
        ) : (
          <>
            <ol>
              {(listItems ?? []).map((chapter, offset) => {
                const index = (listPage - 1) * size + offset;
                const locked = isLocked(chapter);
                const current = index === activeIndex && reading;
                return (
                  <li key={chapter.id}>
                    <button
                      aria-current={index === activeIndex ? "true" : undefined}
                      className="audioEpisodeButton"
                      disabled={locked}
                      onClick={() => void open(index)}
                      type="button"
                    >
                      <span>{String(chapter.number).padStart(3, "0")}</span>
                      <div>
                        <strong>{chapter.title}</strong>
                        <small>
                          {locked
                            ? `Chương trả phí · ${chapter.coinPrice} Xu`
                            : current
                              ? `Đang nghe · ${progress}%`
                              : new Date(chapter.publishedAt).toLocaleDateString("vi-VN")}
                        </small>
                        {current ? (
                          <span className="audioRowProgress">
                            <span style={{ width: `${progress}%` }} />
                          </span>
                        ) : null}
                      </div>
                      {locked
                        ? <Lock aria-hidden="true" />
                        : current && state.status === "playing"
                          ? <Pause aria-hidden="true" />
                          : <Volume2 aria-hidden="true" />}
                    </button>
                  </li>
                );
              })}
            </ol>

            {listItems == null ? <p className="emptyCatalog">Đang tải danh sách chương…</p> : null}

            {totalPages > 1 ? (
              <div className="audioListPager">
                <button
                  disabled={listPage === 1}
                  onClick={() => {
                    const page = listPage - 1;
                    setListPage(page);
                    void ensurePage((page - 1) * size);
                  }}
                  type="button"
                >
                  Trang trước
                </button>
                <span>
                  Chương {(listPage - 1) * size + 1}–{Math.min(listPage * size, total)}
                </span>
                <button
                  disabled={listPage >= totalPages}
                  onClick={() => {
                    const page = listPage + 1;
                    setListPage(page);
                    void ensurePage((page - 1) * size);
                  }}
                  type="button"
                >
                  Trang sau
                </button>
              </div>
            ) : null}
          </>
        )}

        <p className="audioReadInstead">
          Muốn đọc bằng mắt? <Link to={`/stories/${storySlug}`}>Mở bản chữ của {storyTitle}</Link>
        </p>
      </section>
    </>
  );
}

/** Nhãn phải nói đúng trạng thái máy đang ở, không phải tên tính năng. */
const STATUS_LABELS: Record<string, string> = {
  completed: "Đã đọc xong",
  error: "Bản đọc gặp sự cố",
  idle: "Bản đọc tự động",
  loading: "Đang dựng tiếng",
  paused: "Đang tạm dừng",
  playing: "Đang phát",
};
