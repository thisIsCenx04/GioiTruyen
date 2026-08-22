"use client";

import { LoaderCircle, Pause, Play, SkipBack, SkipForward, Square } from "lucide-react";

import type { TtsController } from "@/components/use-tts";

/**
 * Thanh điều khiển bản đọc, dùng chung cho trang nghe và trang đọc chương.
 *
 * <p>Chỉ vẽ, không tự quyết định gì: mọi nút gọi thẳng vào bộ máy qua
 * `controller`. Nhờ vậy hai màn hình không thể trôi ra hai hành vi khác nhau -
 * thứ đã xảy ra khi mỗi trang tự dựng nút phát của riêng nó.
 */

export const TTS_RATES = [0.75, 1, 1.25, 1.5, 1.75, 2] as const;

/** Nút chính phải nói đúng việc nó sắp làm, không phải trạng thái hiện tại. */
function primaryLabel(status: TtsController["state"]["status"]): string {
  if (status === "playing") return "Tạm dừng";
  if (status === "paused") return "Đọc tiếp";
  return "Đọc truyện";
}

export function TtsControls({
  controller,
  onNextChapter,
  onPreviousChapter,
}: Readonly<{
  controller: TtsController;
  /** Bỏ trống nếu màn hình không có chương kế tiếp để chuyển. */
  onNextChapter?: () => void;
  onPreviousChapter?: () => void;
}>) {
  const { pause, play, setRate, state, stop } = controller;
  // "loading" là lúc máy chủ đang dựng tiếng cho mẩu chưa ai nghe bao giờ:
  // khoá nút để cú bấm thứ hai không cắt ngang việc đang chạy.
  const loading = state.status === "loading";
  const playing = state.status === "playing";
  const progress = state.chunkCount === 0
    ? 0
    : Math.round((state.chunkIndex / state.chunkCount) * 100);

  return (
    <div className="audioControlSurface">
      {onPreviousChapter ? (
        <button aria-label="Chương trước" onClick={onPreviousChapter} type="button">
          <SkipBack aria-hidden="true" />
        </button>
      ) : null}

      <button
        aria-label={primaryLabel(state.status)}
        className="audioPlayButton"
        disabled={loading}
        onClick={playing ? pause : play}
        type="button"
      >
        {loading
          ? <LoaderCircle className="audioSpinner" aria-hidden="true" />
          : playing ? <Pause aria-hidden="true" /> : <Play aria-hidden="true" />}
      </button>

      <button
        aria-label="Dừng hẳn"
        // Dừng chỉ có nghĩa khi đang có gì đó để dừng; bật nút lúc chưa đọc chỉ
        // làm người dùng bấm thử rồi tự hỏi vì sao không có gì xảy ra.
        disabled={state.status !== "playing" && state.status !== "paused"}
        onClick={stop}
        type="button"
      >
        <Square aria-hidden="true" />
      </button>

      {onNextChapter ? (
        <button aria-label="Chương tiếp theo" onClick={onNextChapter} type="button">
          <SkipForward aria-hidden="true" />
        </button>
      ) : null}

      <div
        aria-label={`Tiến độ ${progress}%`}
        aria-valuemax={100}
        aria-valuemin={0}
        aria-valuenow={progress}
        className="audioProgress"
        role="progressbar"
      >
        <span style={{ width: `${progress}%` }} />
      </div>

      <label className="audioRate">
        Tốc độ
        <select onChange={(event) => setRate(Number(event.target.value))} value={state.rate}>
          {TTS_RATES.map((value) => (
            <option key={value} value={value}>{value}x</option>
          ))}
        </select>
      </label>
    </div>
  );
}

/** Nút Nữ/Nam và ô tự chuyển chương - phần tuỳ chọn dưới thanh điều khiển. */
export function TtsOptions({
  autoNext,
  controller,
  onAutoNext,
}: Readonly<{
  autoNext?: boolean;
  controller: TtsController;
  onAutoNext?: (next: boolean) => void;
}>) {
  const { setVoice, state } = controller;

  return (
    <div className="audioOptionRow">
      {/* Hai giọng do máy chủ dựng, nên ai cũng có đủ cả hai - không còn phụ
          thuộc vào việc máy người nghe đã cài giọng tiếng Việt hay chưa. */}
      <div className="audioVoiceRow" role="group" aria-label="Giọng đọc">
        <span>Giọng đọc</span>
        <div className="audioVoicePick">
          <button
            aria-pressed={state.voice === "female"}
            onClick={() => setVoice("female")}
            type="button"
          >
            Nữ
          </button>
          <button
            aria-pressed={state.voice === "male"}
            onClick={() => setVoice("male")}
            type="button"
          >
            Nam
          </button>
        </div>
      </div>

      {onAutoNext ? (
        <label className="audioAutoNext">
          <input
            checked={autoNext ?? false}
            onChange={(event) => onAutoNext(event.target.checked)}
            type="checkbox"
          />
          Tự động đọc chương tiếp theo
        </label>
      ) : null}
    </div>
  );
}

/**
 * Thông báo khi bản đọc không dùng được, kèm cách khắc phục.
 *
 * <p>Trả về null trong lúc còn đang tra danh sách giọng: kết luận sớm là dội
 * vào mặt người dùng một cảnh báo sai trong khi máy họ hoàn toàn ổn.
 */
export function TtsNotice({ controller }: Readonly<{ controller: TtsController }>) {
  const { state } = controller;
  if (!state.message) return null;
  return <p className="audioPlayerError" role="alert">{state.message}</p>;
}
