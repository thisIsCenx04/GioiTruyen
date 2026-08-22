"use client";

import { Volume2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";

import { TtsControls, TtsNotice, TtsOptions } from "@/components/tts-controls";
import { readStored, useTts, writeStored } from "@/components/use-tts";
import { API_BASE_URL } from "@/lib/api-base";
import { chapterSpeech, estimateSeconds, formatDuration } from "@/lib/speech";
import type { TtsVoice } from "@/lib/tts-service";

/**
 * Bản đọc tự động ngay trên trang đọc chương.
 *
 * <p>Chữ để đọc lấy từ chính `contentHtml` mà trang dùng để dựng chương - cùng
 * một prop, cùng một chuỗi. Không có chỗ nào quét DOM, nên menu, quảng cáo,
 * bình luận hay chân trang không có đường lọt vào bản đọc.
 *
 * <p>Phần tô sáng thì ngược lại, phải chạm vào DOM: chữ chương được trang mẹ
 * đổ vào bằng `dangerouslySetInnerHTML`, nên component này không sở hữu các thẻ
 * đó. Nó tìm đúng các thẻ khối trong khung chữ theo cùng quy tắc mà
 * {@link chapterSpeech} dùng để tách đoạn, nên chỉ số đoạn của hai bên khớp
 * nhau - và chỉ thêm/bớt một class, không đụng tới nội dung.
 */

const AUTO_NEXT_KEY = "gt:audio:autonext";
const BLOCK_TAGS = "p, div, li, h1, h2, h3, h4, h5, h6, blockquote, pre, td";

export function ChapterTtsPlayer({
  chapterId,
  chapterTitle,
  contentHtml,
  nextHref,
  previousHref,
  proseSelector = ".chapterProse",
}: Readonly<{
  chapterId: string;
  chapterTitle: string;
  contentHtml: string;
  nextHref?: string;
  previousHref?: string;
  /** Khung chứa chữ chương trên trang, để tô sáng đúng chỗ. */
  proseSelector?: string;
}>) {
  const navigate = useNavigate();
  const autoNextRef = useRef(true);
  const litRef = useRef<Element | null>(null);

  const speech = useMemo(() => chapterSpeech(contentHtml), [contentHtml]);

  /* Hết chương thì sang chương sau, nhưng chỉ khi người đọc đã bật. Chuyển
     trang cũng đồng thời gỡ component này, nên bộ máy đọc được dọn sạch trước
     khi chương mới dựng bộ máy của nó - không có hai hàng đợi cùng sống. */
  const finish = useCallback(() => {
    if (autoNextRef.current && nextHref) navigate(nextHref);
  }, [navigate, nextHref]);

  const tts = useTts(finish);
  const { load, state } = tts;

  useEffect(() => {
    autoNextRef.current = readStored(AUTO_NEXT_KEY) !== "off";
  }, []);

  // Chương đổi là hàng đợi đổi. load() tự cắt phiên cũ, nên chữ của chương
  // trước không thể còn nằm trong hàng của engine.
  // Đường dẫn tệp tiếng của một mẩu. Máy chủ cắt chương theo đúng luật mà
  // chapterSpeech dùng ở đây, nên chỉ số mẩu hai bên khớp nhau.
  const urlFor = useCallback(
    (index: number, voice: TtsVoice) =>
      `${API_BASE_URL}/chapters/${encodeURIComponent(chapterId)}/tts/${index}.opus?voice=${voice}`,
    [chapterId],
  );

  useEffect(() => {
    load(speech.chunks, speech.paragraphs.length, urlFor);
  }, [load, speech, urlFor]);

  /* Tô sáng đoạn đang đọc trên chính chữ chương mà trang đã dựng. */
  useEffect(() => {
    if (state.status !== "playing" && state.status !== "paused") {
      litRef.current?.classList.remove("isSpeaking");
      litRef.current = null;
      return;
    }
    const prose = document.querySelector(proseSelector);
    if (!prose) return;
    const blocks = [...prose.querySelectorAll(BLOCK_TAGS)]
      // Cùng một luật lọc như lúc tách đoạn: thẻ bọc ngoài chứa khối khác không
      // phải một đoạn. Lệch luật ở đây là tô sáng lệch dòng.
      .filter((node) => !node.querySelector(BLOCK_TAGS))
      .filter((node) => (node.textContent ?? "").trim());

    const target = blocks[state.paragraphIndex];
    if (!target || target === litRef.current) return;
    litRef.current?.classList.remove("isSpeaking");
    target.classList.add("isSpeaking");
    litRef.current = target;
    if (state.status === "playing") {
      target.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }, [proseSelector, state.paragraphIndex, state.status]);

  // Gỡ class khi rời trang: chữ chương thuộc về trang mẹ, không được để lại
  // dấu vết của một phiên đọc đã kết thúc.
  useEffect(() => () => litRef.current?.classList.remove("isSpeaking"), []);

  const seconds = useMemo(
    () => estimateSeconds(speech.chunks.map((chunk) => chunk.text).join(" "), state.rate),
    [speech.chunks, state.rate],
  );
  const progress = state.chunkCount === 0
    ? 0
    : Math.round((state.chunkIndex / state.chunkCount) * 100);
  const reading = state.status === "playing" || state.status === "paused";

  return (
    <section aria-label="Bản đọc tự động" className="chapterTtsPanel">
      <header>
        <Volume2 aria-hidden="true" size={18} />
        <div>
          <strong>Bản đọc tự động</strong>
          <small>
            {reading
              ? `Đoạn ${state.paragraphIndex + 1} / ${state.paragraphCount} · đã đọc ${progress}%`
              : `${speech.paragraphs.length} đoạn · khoảng ${formatDuration(seconds)}`}
          </small>
        </div>
      </header>

      <TtsControls
        controller={tts}
        onNextChapter={nextHref ? () => navigate(nextHref) : undefined}
        onPreviousChapter={previousHref ? () => navigate(previousHref) : undefined}
      />

      <TtsOptions
        autoNext={autoNextRef.current}
        controller={tts}
        onAutoNext={(next) => {
          autoNextRef.current = next;
          writeStored(AUTO_NEXT_KEY, next ? "on" : "off");
        }}
      />

      <TtsNotice controller={tts} />

      {state.status === "completed" ? (
        <p className="audioPlayerNotice" role="status">
          Đã đọc xong “{chapterTitle}”.
          {nextHref && !autoNextRef.current
            ? " Bật “Tự động đọc chương tiếp theo” để nghe liền mạch."
            : ""}
        </p>
      ) : null}

      {/* Vị trí đang nghe dở, để lần sau quay lại trang này còn biết chỗ. */}
      {reading ? (
        <p className="audioPlayerHint">
          Đang đọc mẩu {state.chunkIndex + 1}/{state.chunkCount}
          {" · "}còn khoảng {formatDuration(Math.max(1, Math.round(seconds * (1 - progress / 100))))}
          {` · giọng ${state.voice === "male" ? "nam" : "nữ"}`}
          {" · "}
          <button
            className="chapterTtsJump"
            onClick={() => tts.seek(0)}
            type="button"
          >
            đọc lại từ đầu chương
          </button>
        </p>
      ) : null}
    </section>
  );
}
