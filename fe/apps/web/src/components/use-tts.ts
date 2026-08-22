"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { TtsService, type ChunkUrl, type SpeechChunk, type TtsState, type TtsVoice } from "@/lib/tts-service";

/**
 * Cầu nối giữa {@link TtsService} và React.
 *
 * <p>Bộ máy phát sống trong một ref, không phải trong state: nó phải là đúng
 * một thực thể suốt vòng đời của màn hình. Dựng lại nó mỗi lần render là để lại
 * một thẻ audio mồ côi vẫn đang kêu, và người dùng nghe hai giọng chồng nhau.
 *
 * <p>Hook cũng lo hai chỗ dễ quên trong một ứng dụng một trang: rời trang bằng
 * điều hướng nội bộ (component unmount) và rời trang bằng đóng tab
 * (`pagehide`). Thiếu chỗ thứ hai, tiếng vẫn phát sau khi tab đã đóng.
 */

const VOICE_KEY = "gt:audio:voice";
const RATE_KEY = "gt:audio:rate";

export function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    // Máy chặn localStorage - vẫn nghe được, chỉ là không nhớ giữa các lần vào.
    return null;
  }
}

export function writeStored(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* Ghi nhớ chỉ là tiện thêm, không phải điều kiện để nghe. */
  }
}

export type TtsController = {
  load: (chunks: SpeechChunk[], paragraphCount: number, urlFor: ChunkUrl, fromChunk?: number) => void;
  pause: () => void;
  play: () => void;
  position: () => { chunk: number; paragraph: number };
  seek: (chunkIndex: number) => void;
  setRate: (rate: number) => void;
  setVoice: (voice: TtsVoice) => void;
  state: TtsState;
  stop: () => void;
};

export function useTts(onFinished?: () => void): TtsController {
  const serviceRef = useRef<TtsService | null>(null);
  const finishedRef = useRef(onFinished);
  const [state, setState] = useState<TtsState>(() => ({
    chunkCount: 0,
    chunkIndex: 0,
    message: "",
    paragraphCount: 0,
    paragraphIndex: 0,
    rate: 1,
    status: "idle",
    voice: "female",
  }));

  // Callback đi qua ref để việc đổi hàm xử lý không phải dựng lại bộ máy.
  useEffect(() => {
    finishedRef.current = onFinished;
  }, [onFinished]);

  if (serviceRef.current == null && typeof window !== "undefined") {
    const voice: TtsVoice = readStored(VOICE_KEY) === "male" ? "male" : "female";
    const rate = Number(readStored(RATE_KEY)) || 1;
    serviceRef.current = new TtsService(voice, rate);
  }

  useEffect(() => {
    const service = serviceRef.current;
    if (!service) return undefined;

    service.onFinished = () => finishedRef.current?.();
    const unsubscribe = service.subscribe(setState);

    // Đóng tab hoặc chuyển sang trang khác ngoài ứng dụng: unmount không chạy,
    // nên phải bắt riêng, nếu không tiếng còn kêu sau khi tab đã đóng.
    const hide = () => service.stop();
    window.addEventListener("pagehide", hide);

    return () => {
      window.removeEventListener("pagehide", hide);
      unsubscribe();
      service.destroy();
      serviceRef.current = null;
    };
  }, []);

  const load = useCallback((
    chunks: SpeechChunk[],
    paragraphCount: number,
    urlFor: ChunkUrl,
    fromChunk = 0,
  ) => {
    serviceRef.current?.load(chunks, paragraphCount, urlFor, fromChunk);
  }, []);

  const play = useCallback(() => serviceRef.current?.play(), []);
  const pause = useCallback(() => serviceRef.current?.pause(), []);
  const stop = useCallback(() => serviceRef.current?.stop(), []);
  const seek = useCallback((chunkIndex: number) => serviceRef.current?.seek(chunkIndex), []);

  const setVoice = useCallback((voice: TtsVoice) => {
    writeStored(VOICE_KEY, voice);
    serviceRef.current?.setVoice(voice);
  }, []);

  const setRate = useCallback((rate: number) => {
    writeStored(RATE_KEY, String(rate));
    serviceRef.current?.setRate(rate);
  }, []);

  const position = useCallback(
    () => serviceRef.current?.position() ?? { chunk: 0, paragraph: 0 },
    [],
  );

  return { load, pause, play, position, seek, setRate, setVoice, state, stop };
}
