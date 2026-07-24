"use client";

import {
  createBrowserReadingClient,
  createBrowserReadingSessionClient,
  type PublishedChapterDetail,
  type ReadingSessionGrant,
} from "@gioitruyen/api-client";
import Link from "next/link";
import type { Route } from "next";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";

import { Comments } from "./comments";
import styles from "./chapter-reader.module.css";

export function ChapterReader({
  chapter,
}: Readonly<{ chapter: PublishedChapterDetail }>) {
  const sessions = useMemo(() => createBrowserReadingSessionClient(), []);
  const progressApi = useMemo(() => createBrowserReadingClient(), []);
  const grant = useRef<ReadingSessionGrant | null>(null);
  const sequence = useRef(1);
  const completed = useRef(false);
  const [night, setNight] = useState(false);
  const [fontSize, setFontSize] = useState(20);
  const [read, setRead] = useState(0);
  const [tracking, setTracking] = useState<"starting" | "active" | "local">(
    "starting",
  );

  const position = useCallback(() => {
    const maximum =
      document.documentElement.scrollHeight - window.innerHeight;
    return maximum <= 0
      ? 100
      : Math.min(100, Math.max(0, (window.scrollY / maximum) * 100));
  }, []);

  const heartbeat = useCallback(async () => {
    if (!grant.current || completed.current) return;
    const current = grant.current;
    try {
      const receipt = await sessions.heartbeat(
        current.sessionId,
        current.sessionToken,
        {
          batchId: crypto.randomUUID(),
          heartbeats: [{
            activeSeconds: current.heartbeatIntervalSeconds,
            occurredAt: new Date().toISOString(),
            position: position(),
            sequence: sequence.current,
          }],
        },
      );
      sequence.current = receipt.nextSequence;
    } catch {
      setTracking("local");
    }
  }, [position, sessions]);

  const complete = useCallback(async () => {
    if (!grant.current || completed.current) return;
    completed.current = true;
    try {
      await sessions.complete(
        grant.current.sessionId,
        grant.current.sessionToken,
        {
          completionId: crypto.randomUUID(),
          finalSequence: sequence.current - 1,
          occurredAt: new Date().toISOString(),
          position: position(),
        },
      );
    } catch {
      completed.current = false;
    }
  }, [position, sessions]);

  useEffect(() => {
    let cancelled = false;
    const storedNight = localStorage.getItem("reader-theme") === "night";
    const storedSize = Number(localStorage.getItem("reader-font-size"));
    queueMicrotask(() => {
      if (cancelled) return;
      setNight(storedNight);
      if (storedSize >= 17 && storedSize <= 26) setFontSize(storedSize);
    });
    let anonymousId = localStorage.getItem("reader-anonymous-id");
    if (!anonymousId) {
      anonymousId = crypto.randomUUID();
      localStorage.setItem("reader-anonymous-id", anonymousId);
    }
    sessions.start({
      anonymousId,
      chapterId: chapter.id,
      storyId: chapter.storyId,
    }).then((value) => {
      if (!cancelled) {
        grant.current = value;
        setTracking("active");
      }
    }).catch(() => setTracking("local"));
    return () => { cancelled = true; };
  }, [chapter.id, chapter.storyId, sessions]);

  useEffect(() => {
    const onScroll = () => {
      const current = position();
      setRead(current);
      localStorage.setItem(`reading-progress:${chapter.storyId}`, JSON.stringify({
        chapterId: chapter.id,
        position: current,
        updatedAt: new Date().toISOString(),
      }));
      if (current >= 98) void complete();
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    const timer = window.setInterval(() => {
      void heartbeat();
      void progressApi.synchronize(chapter.storyId, {
        chapterId: chapter.id,
        deviceUpdatedAt: new Date().toISOString(),
        position: position(),
      }).catch(() => undefined);
    }, 15_000);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.clearInterval(timer);
    };
  }, [chapter.id, chapter.storyId, complete, heartbeat, position, progressApi]);

  function resize(delta: number) {
    setFontSize((current) => {
      const next = Math.min(26, Math.max(17, current + delta));
      localStorage.setItem("reader-font-size", String(next));
      return next;
    });
  }

  return (
    <main className={styles.reader} data-theme={night ? "night" : "paper"}>
      <div aria-hidden="true" className={styles.bookmark}
        style={{ "--read-progress": `${read}%` } as CSSProperties} />
      <header className={styles.toolbar}>
        <Link href={`/stories/${chapter.storyId}`}>Giới Truyện</Link>
        <div className={styles.state} role="status">
          {tracking === "active" ? "Đang lưu tiến độ" :
            tracking === "local" ? "Lưu trên thiết bị" : "Đang mở phiên đọc"}
        </div>
        <div aria-label="Tùy chỉnh trình đọc" className={styles.controls}>
          <button aria-label="Giảm cỡ chữ" onClick={() => resize(-1)}>A−</button>
          <output aria-label="Cỡ chữ">{fontSize}</output>
          <button aria-label="Tăng cỡ chữ" onClick={() => resize(1)}>A+</button>
          <button aria-pressed={night} onClick={() => {
            setNight((current) => {
              localStorage.setItem("reader-theme", current ? "paper" : "night");
              return !current;
            });
          }}>{night ? "Nền giấy" : "Ban đêm"}</button>
        </div>
      </header>
      <article className={styles.manuscript}
        style={{ "--reader-font-size": `${fontSize}px` } as CSSProperties}>
        <header>
          <p>Chương {String(chapter.number).padStart(2, "0")}</p>
          <h1>{chapter.title}</h1>
          <span>{chapter.wordCount.toLocaleString("vi-VN")} từ · Ấn bản {chapter.revisionNo}</span>
        </header>
        <div className={styles.prose}
          dangerouslySetInnerHTML={{ __html: chapter.contentHtml }} />
      </article>
      <nav aria-label="Điều hướng chương" className={styles.chapterNav}>
        {chapter.previous ? <Link href={`/read/${chapter.previous.id}` as Route}>
          ← {chapter.previous.title}</Link> : <span />}
        {chapter.next ? <Link href={`/read/${chapter.next.id}` as Route}>
          {chapter.next.title} →</Link> : <Link href={`/stories/${chapter.storyId}`}>
          Về trang truyện</Link>}
      </nav>
      <Comments targetId={chapter.id} targetType="CHAPTER" />
    </main>
  );
}
