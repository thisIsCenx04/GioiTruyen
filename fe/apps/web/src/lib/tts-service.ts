/**
 * Bộ máy phát bản đọc, tách hẳn khỏi giao diện.
 *
 * <p>Tiếng do máy chủ dựng sẵn thành từng mẩu; ở đây chỉ có việc phát chúng nối
 * nhau đúng thứ tự. Bản trước dùng bộ đọc của trình duyệt (Web Speech API) và
 * hỏng ở chỗ nằm ngoài tầm với: máy nào chưa cài giọng tiếng Việt thì im lặng,
 * mà phần lớn máy Windows chưa cài. Nay giọng đến từ máy chủ nên mọi người nghe
 * cùng một giọng, kể cả trên iPhone hay một máy Windows trắng tinh.
 *
 * <p>Đổi sang thẻ audio còn dọn luôn một mớ vá víu: không còn đồng hồ canh
 * chừng phòng khi engine chết giữa chừng, không còn phải gọi resume() mỗi tám
 * giây để Chrome khỏi treo hàng đọc, không còn đợi sự kiện voiceschanged. Thẻ
 * audio chỉ phát, và nó phát tiếp cả khi màn hình điện thoại đã tắt.
 */

export type TtsStatus =
  /** Chưa nạp chương nào. */
  | "idle"
  /** Đang chờ máy chủ dựng tiếng cho mẩu sắp phát. */
  | "loading"
  | "playing"
  | "paused"
  /** Phát hết hàng đợi. */
  | "completed"
  /** Máy chủ chưa dựng được tiếng, hoặc mạng hỏng. */
  | "error";

export type TtsVoice = "female" | "male";

export type TtsState = {
  chunkCount: number;
  chunkIndex: number;
  /** Câu giải thích khi status là error. */
  message: string;
  paragraphCount: number;
  paragraphIndex: number;
  rate: number;
  status: TtsStatus;
  voice: TtsVoice;
};

/** Một mẩu tiếng, kèm số thứ tự đoạn văn nó thuộc về. */
export type SpeechChunk = { paragraph: number; text: string };

/** Đường dẫn tới tệp tiếng của một mẩu. */
export type ChunkUrl = (index: number, voice: TtsVoice) => string;

export class TtsService {
  private chunks: SpeechChunk[] = [];
  private index = 0;
  private urlFor: ChunkUrl | null = null;
  private listeners = new Set<(state: TtsState) => void>();
  private state: TtsState;

  private audio: HTMLAudioElement | null = null;
  /** Mẩu kế tiếp, tải sẵn trong lúc mẩu này đang phát. */
  private ahead: HTMLAudioElement | null = null;
  private destroyed = false;

  /** Gọi khi phát hết hàng đợi - nơi màn hình quyết định có sang chương sau không. */
  onFinished: (() => void) | null = null;

  constructor(voice: TtsVoice = "female", rate = 1) {
    this.state = {
      chunkCount: 0,
      chunkIndex: 0,
      message: "",
      paragraphCount: 0,
      paragraphIndex: 0,
      rate,
      status: "idle",
      voice,
    };
  }

  /* ── Trạng thái ──────────────────────────────────────────────────── */

  subscribe(listener: (state: TtsState) => void): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => this.listeners.delete(listener);
  }

  getState(): TtsState {
    return this.state;
  }

  private patch(next: Partial<TtsState>) {
    this.state = { ...this.state, ...next };
    for (const listener of this.listeners) listener(this.state);
  }

  /* ── Hàng đợi ────────────────────────────────────────────────────── */

  /**
   * Nạp một chương mới.
   *
   * <p>Luôn cắt phiên cũ trước. Đây là chỗ chống việc phát chồng khi người dùng
   * bấm sang chương khác giữa chừng: mẩu của chương cũ có thể đang phát dở, và
   * nếu không dừng thì nó vẫn kêu sau khi màn hình đã hiện tên chương mới.
   */
  load(chunks: SpeechChunk[], paragraphCount: number, urlFor: ChunkUrl, fromChunk = 0): void {
    this.cut();
    this.chunks = chunks;
    this.urlFor = urlFor;
    this.index = Math.max(0, Math.min(fromChunk, Math.max(chunks.length - 1, 0)));
    this.patch({
      chunkCount: chunks.length,
      chunkIndex: this.index,
      message: "",
      paragraphCount,
      paragraphIndex: chunks[this.index]?.paragraph ?? 0,
      status: "idle",
    });
  }

  /** Vị trí hiện tại, để bên gọi ghi nhớ chỗ đang nghe dở. */
  position(): { chunk: number; paragraph: number } {
    return { chunk: this.index, paragraph: this.chunks[this.index]?.paragraph ?? 0 };
  }

  /* ── Điều khiển ──────────────────────────────────────────────────── */

  play(): void {
    if (this.state.status === "playing") return;
    if (this.state.status === "paused" && this.audio) {
      // Thẻ audio nhớ đúng vị trí trong mẩu, nên nghe tiếp từ giữa câu chứ
      // không phải nghe lại cả mẩu.
      void this.audio.play().catch((cause) => this.fail(cause));
      this.patch({ status: "playing" });
      return;
    }
    if (this.chunks.length === 0 || !this.urlFor) return;
    this.startChunk(this.index);
  }

  pause(): void {
    if (this.state.status !== "playing") return;
    this.audio?.pause();
    this.patch({ status: "paused" });
  }

  stop(): void {
    this.cut();
    this.index = 0;
    this.patch({
      chunkIndex: 0,
      message: "",
      paragraphIndex: this.chunks[0]?.paragraph ?? 0,
      status: "idle",
    });
  }

  /** Nhảy tới một mẩu bất kỳ và phát từ đó. */
  seek(chunkIndex: number): void {
    if (chunkIndex < 0 || chunkIndex >= this.chunks.length) return;
    this.startChunk(chunkIndex);
  }

  setVoice(voice: TtsVoice): void {
    if (voice === this.state.voice) return;
    const wasPlaying = this.state.status === "playing" || this.state.status === "paused";
    this.patch({ voice });
    // Giọng nằm trong đường dẫn tệp, nên phải nạp lại mẩu đang dở mới nghe
    // thấy nó đổi.
    if (wasPlaying) this.startChunk(this.index);
  }

  /**
   * Đổi tốc độ đọc.
   *
   * <p>Chỉ chỉnh tốc độ phát của thẻ audio, không dựng lại tiếng: cùng một tệp
   * phát nhanh hay chậm đều được, nên đổi tốc độ không tốn thêm CPU máy chủ và
   * không có quãng chờ nào.
   */
  setRate(rate: number): void {
    this.patch({ rate });
    if (this.audio) this.audio.playbackRate = rate;
    if (this.ahead) this.ahead.playbackRate = rate;
  }

  destroy(): void {
    this.destroyed = true;
    this.cut();
    this.listeners.clear();
    this.onFinished = null;
  }

  /* ── Phát ────────────────────────────────────────────────────────── */

  private cut() {
    for (const element of [this.audio, this.ahead]) {
      if (!element) continue;
      element.onended = null;
      element.onerror = null;
      element.oncanplay = null;
      element.pause();
      // Gỡ nguồn để trình duyệt thả kết nối đang tải dở; thiếu bước này thì
      // mỗi lần chuyển chương lại bỏ lại một tải xuống chạy nền vô ích.
      element.removeAttribute("src");
      element.load();
    }
    this.audio = null;
    this.ahead = null;
  }

  private startChunk(index: number) {
    if (this.destroyed || !this.urlFor) return;
    const chunk = this.chunks[index];
    if (!chunk) {
      this.cut();
      this.patch({ status: "completed" });
      this.onFinished?.();
      return;
    }

    this.cut();
    this.index = index;
    this.patch({
      chunkIndex: index,
      message: "",
      paragraphIndex: chunk.paragraph,
      // Mẩu chưa từng nghe phải chờ máy chủ dựng tiếng vài giây; nói ra để
      // người dùng biết đang chờ chứ không phải hỏng.
      status: "loading",
    });

    const element = new Audio(this.urlFor(index, this.state.voice));
    element.preload = "auto";
    element.playbackRate = this.state.rate;
    this.audio = element;

    element.oncanplay = () => {
      if (this.audio !== element) return;
      if (this.state.status === "loading") this.patch({ status: "playing" });
      // Tải sẵn mẩu kế tiếp ngay khi mẩu này chạy được: máy chủ dựng tiếng
      // nhanh hơn tốc độ nghe khoảng ba lần, nên mẩu sau luôn kịp.
      this.prefetch(index + 1);
    };
    element.onended = () => {
      if (this.audio !== element) return;
      this.startChunk(index + 1);
    };
    element.onerror = () => {
      if (this.audio !== element) return;
      this.fail(element.error);
    };

    void element.play().catch((cause) => {
      // Trình duyệt chặn tự phát khi người dùng chưa chạm vào trang. Không phải
      // lỗi để kêu ầm lên: cứ để nguyên ở trạng thái chờ, cú bấm sau sẽ chạy.
      if (cause instanceof DOMException && cause.name === "NotAllowedError") {
        this.patch({ status: "paused" });
        return;
      }
      this.fail(cause);
    });
  }

  private prefetch(index: number) {
    if (!this.urlFor || !this.chunks[index]) return;
    const ahead = new Audio(this.urlFor(index, this.state.voice));
    ahead.preload = "auto";
    ahead.playbackRate = this.state.rate;
    this.ahead = ahead;
  }

  private fail(cause: unknown) {
    this.cut();
    const offline = typeof navigator !== "undefined" && navigator.onLine === false;
    this.patch({
      message: offline
        ? "Mất kết nối mạng. Bản đọc cần mạng vì tiếng được dựng trên máy chủ."
        : "Không tải được tiếng của đoạn này. Bấm phát lại giúp; nếu vẫn lỗi thì thử lại sau ít phút.",
      status: "error",
    });
    if (cause) console.warn("Bản đọc lỗi:", cause);
  }
}
