"use client";

import type {
  PublicChapter,
  PublishedChapterDetail,
} from "@gioitruyen/api-client";
import {
  ListMusic,
  LoaderCircle,
  Pause,
  Play,
  SkipBack,
  SkipForward,
  Volume2,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

type PlaybackState = "idle" | "loading" | "paused" | "playing" | "ended";

function htmlToSpeechChunks(contentHtml: string) {
  const document = new DOMParser().parseFromString(contentHtml, "text/html");
  document.querySelectorAll("script, style, noscript").forEach((node) => node.remove());
  const text = (document.body.textContent ?? "").replace(/\s+/gu, " ").trim();
  const sentences = text.match(/[^.!?…]+[.!?…]+|[^.!?…]+$/gu) ?? [text];
  const chunks: string[] = [];
  let current = "";

  for (const sentence of sentences) {
    const value = sentence.trim();
    if (!value) continue;
    if (current && `${current} ${value}`.length > 800) {
      chunks.push(current);
      current = value;
    } else {
      current = current ? `${current} ${value}` : value;
    }
  }
  if (current) chunks.push(current);
  return chunks;
}

export function BrowserAudioPlayer({
  chapters,
  storyTitle,
}: Readonly<{
  chapters: readonly PublicChapter[];
  storyTitle: string;
}>) {
  const episodes = useMemo(
    () => [...chapters].sort((left, right) => left.number - right.number),
    [chapters],
  );
  const [activeIndex, setActiveIndex] = useState(0);
  const [chunkProgress, setChunkProgress] = useState({ current: 0, total: 0 });
  const [error, setError] = useState("");
  const [playback, setPlayback] = useState<PlaybackState>("idle");
  const [rate, setRate] = useState(1);
  const activeIndexRef = useRef(0);
  const cacheRef = useRef(new Map<string, string[]>());
  const chunkIndexRef = useRef(0);
  const chunksRef = useRef<string[]>([]);
  const playChapterRef = useRef<(index: number) => Promise<void>>(async () => undefined);
  const rateRef = useRef(1);
  const tokenRef = useRef(0);

  const speakCurrentChunk = useCallback((token: number) => {
    if (token !== tokenRef.current) return;
    const chunk = chunksRef.current[chunkIndexRef.current];
    if (!chunk) {
      const nextIndex = activeIndexRef.current + 1;
      if (nextIndex < episodes.length) {
        void playChapterRef.current(nextIndex);
      } else {
        setPlayback("ended");
      }
      return;
    }

    const utterance = new SpeechSynthesisUtterance(chunk);
    utterance.lang = "vi-VN";
    utterance.rate = rateRef.current;
    const vietnameseVoice = window.speechSynthesis
      .getVoices()
      .find((voice) => voice.lang.toLowerCase().startsWith("vi"));
    if (vietnameseVoice) utterance.voice = vietnameseVoice;
    utterance.onend = () => {
      if (token !== tokenRef.current) return;
      chunkIndexRef.current += 1;
      setChunkProgress({
        current: chunkIndexRef.current,
        total: chunksRef.current.length,
      });
      speakCurrentChunk(token);
    };
    utterance.onerror = (event) => {
      if (token !== tokenRef.current || event.error === "canceled" || event.error === "interrupted") return;
      setError("Trình duyệt không thể tiếp tục đọc chương này.");
      setPlayback("idle");
    };
    window.speechSynthesis.speak(utterance);
  }, [episodes.length]);

  const playChapter = useCallback(async (index: number) => {
    const chapter = episodes[index];
    if (!chapter) return;
    if (!("speechSynthesis" in window)) {
      setError("Trình duyệt này chưa hỗ trợ đọc văn bản tiếng Việt.");
      return;
    }

    window.speechSynthesis.cancel();
    const token = tokenRef.current + 1;
    tokenRef.current = token;
    activeIndexRef.current = index;
    chunkIndexRef.current = 0;
    setActiveIndex(index);
    setChunkProgress({ current: 0, total: 0 });
    setError("");
    setPlayback("loading");

    try {
      let chunks = cacheRef.current.get(chapter.id);
      if (!chunks) {
        const response = await fetch(`/api/catalog/chapters/${encodeURIComponent(chapter.id)}`, {
          credentials: "same-origin",
        });
        if (!response.ok) {
          throw new Error(response.status === 403
            ? "Chương này cần được mở khóa trước khi nghe."
            : "Không thể tải nội dung chương.");
        }
        const detail = await response.json() as PublishedChapterDetail;
        chunks = htmlToSpeechChunks(detail.contentHtml);
        cacheRef.current.set(chapter.id, chunks);
      }
      if (token !== tokenRef.current) return;
      if (chunks.length === 0) throw new Error("Chương này chưa có nội dung để đọc.");
      chunksRef.current = chunks;
      setChunkProgress({ current: 0, total: chunks.length });
      setPlayback("playing");
      speakCurrentChunk(token);
    } catch (requestError) {
      if (token !== tokenRef.current) return;
      setError(requestError instanceof Error ? requestError.message : "Không thể phát chương.");
      setPlayback("idle");
    }
  }, [episodes, speakCurrentChunk]);

  useEffect(() => {
    playChapterRef.current = playChapter;
  }, [playChapter]);

  useEffect(() => () => {
    tokenRef.current += 1;
    window.speechSynthesis?.cancel();
  }, []);

  function togglePlayback() {
    if (playback === "playing") {
      window.speechSynthesis.pause();
      setPlayback("paused");
    } else if (playback === "paused") {
      window.speechSynthesis.resume();
      setPlayback("playing");
    } else {
      void playChapter(activeIndex);
    }
  }

  function changeRate(value: number) {
    rateRef.current = value;
    setRate(value);
    if (playback === "playing" || playback === "paused") {
      void playChapter(activeIndexRef.current);
    }
  }

  const activeChapter = episodes[activeIndex];
  const progress = chunkProgress.total === 0
    ? 0
    : Math.round((chunkProgress.current / chunkProgress.total) * 100);

  return (
    <>
      <section className="audioPlayerPanel" aria-labelledby="audio-player-title">
        <header>
          <Volume2 aria-hidden="true" />
          <div>
            <p className="detailEyebrow">{playback === "playing" ? "Đang phát" : "Bản đọc tự động"}</p>
            <h2 id="audio-player-title">{activeChapter?.title ?? "Chưa có tập audio"}</h2>
          </div>
        </header>
        <div className="audioControlSurface">
          <button
            aria-label="Chương trước"
            disabled={activeIndex === 0 || episodes.length === 0}
            onClick={() => void playChapter(activeIndex - 1)}
            type="button"
          >
            <SkipBack aria-hidden="true" />
          </button>
          <button
            aria-label={playback === "playing" ? "Tạm dừng" : `Phát ${storyTitle}`}
            className="audioPlayButton"
            disabled={episodes.length === 0 || playback === "loading"}
            onClick={togglePlayback}
            type="button"
          >
            {playback === "loading" ? <LoaderCircle className="audioSpinner" aria-hidden="true" /> : playback === "playing" ? <Pause aria-hidden="true" /> : <Play aria-hidden="true" />}
          </button>
          <button
            aria-label="Chương tiếp theo"
            disabled={activeIndex >= episodes.length - 1}
            onClick={() => void playChapter(activeIndex + 1)}
            type="button"
          >
            <SkipForward aria-hidden="true" />
          </button>
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
            <select onChange={(event) => changeRate(Number(event.target.value))} value={rate}>
              <option value="0.75">0.75x</option>
              <option value="1">1x</option>
              <option value="1.25">1.25x</option>
              <option value="1.5">1.5x</option>
            </select>
          </label>
        </div>
        {error && <p className="audioPlayerError" role="alert">{error}</p>}
      </section>

      <section className="chapterList audioEpisodeList" aria-labelledby="audio-episodes-title">
        <header>
          <div>
            <p className="detailEyebrow">Danh sách tập</p>
            <h2 id="audio-episodes-title">Tập audio đã có</h2>
          </div>
          <span>{episodes.length} tập</span>
        </header>
        {episodes.length === 0 ? (
          <p className="emptyCatalog">Truyện này chưa có tập audio công khai.</p>
        ) : (
          <ol>
            {episodes.map((chapter, index) => (
              <li key={chapter.id}>
                <button
                  aria-current={index === activeIndex ? "true" : undefined}
                  className="audioEpisodeButton"
                  onClick={() => void playChapter(index)}
                  type="button"
                >
                  <span>{String(chapter.number).padStart(3, "0")}</span>
                  <div>
                    <strong>{chapter.title}</strong>
                    <small>{new Date(chapter.publishedAt).toLocaleDateString("vi-VN")}</small>
                  </div>
                  <Volume2 aria-hidden="true" />
                </button>
              </li>
            ))}
          </ol>
        )}
      </section>
    </>
  );
}
