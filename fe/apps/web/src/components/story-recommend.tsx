"use client";

import { createBrowserStoryRelationClient, StoryApiError } from "@gioitruyen/api-client";
import { Gem } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useRef, useState } from "react";

import { isLoggedIn, loginHref } from "@/lib/auth";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import styles from "./story-recommend.module.css";

/** Preset gifts, so the common case is one tap rather than typing a number. */
const AMOUNTS = [10, 50, 100, 500] as const;

function errorMessage(cause: unknown) {
  if (cause instanceof StoryApiError) {
    return cause.problem.detail ?? "Chưa đề cử được. Vui lòng thử lại.";
  }
  return "Không thể kết nối máy chủ. Hãy thử lại.";
}

/**
 * Recommending a story with gems.
 *
 * <p>The story's total is deliberately absent: it decides the ranking order and
 * is read by the admin dashboard, but showing it here would turn the button
 * into a public spending leaderboard. A reader sees only their own giving.
 */
export function StoryRecommend({ storyId }: Readonly<{ storyId: string }>) {
  const api = useMemo(
    () => createBrowserStoryRelationClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }),
    [],
  );
  const [open, setOpen] = useState(false);
  const [mine, setMine] = useState<number | null>(null);
  const [balance, setBalance] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const dialogRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!isLoggedIn()) return undefined;
    let cancelled = false;
    api.myRecommendation(storyId)
      .then((value) => { if (!cancelled) setMine(value.myGemAmount); })
      .catch(() => undefined);
    return () => { cancelled = true; };
  }, [api, storyId]);

  // Escape closes it, as a reader expects of any dialog.
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  // Moves the caret into the dialog on open, so a keyboard or screen-reader
  // user lands inside it rather than continuing from the button behind it, and
  // the page behind stops scrolling under the overlay.
  useEffect(() => {
    if (!open) return undefined;
    dialogRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = previousOverflow; };
  }, [open]);

  async function give(gemAmount: number) {
    if (busy) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const receipt = await api.recommend(storyId, gemAmount);
      setBalance(receipt.gemBalance);
      setMine((current) => (current ?? 0) + receipt.gemAmount);
      setNotice(`Đã đề cử ${gemAmount} ngọc. Cảm ơn bạn!`);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  if (!isLoggedIn()) {
    return (
      <Link className={styles.trigger} to={loginHref()}>
        <Gem aria-hidden="true" /> Đề cử
      </Link>
    );
  }

  return (
    <>
      <button className={styles.trigger} onClick={() => setOpen(true)} type="button">
        <Gem aria-hidden="true" /> Đề cử
        {mine ? <small>bạn đã tặng {mine}</small> : null}
      </button>

      {open ? (
        <div className={styles.overlay} onClick={() => setOpen(false)} role="presentation">
          <section
            aria-label="Đề cử truyện bằng ngọc"
            className={styles.dialog}
            ref={dialogRef}
            tabIndex={-1}
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <h2>Đề cử truyện</h2>
            <p className={styles.lede}>
              Dùng ngọc để đẩy truyện lên bảng đề cử. Số ngọc bạn tặng chỉ mình bạn thấy.
            </p>

            <div className={styles.amounts}>
              {AMOUNTS.map((amount) => (
                <button
                  disabled={busy}
                  key={amount}
                  onClick={() => void give(amount)}
                  type="button"
                >
                  {amount} ngọc
                </button>
              ))}
            </div>

            {notice ? <p className={styles.notice} role="status">{notice}</p> : null}
            {error ? <p className={styles.error} role="alert">{error}</p> : null}

            <dl className={styles.facts}>
              <div><dt>Bạn đã đề cử</dt><dd>{mine ?? 0} ngọc</dd></div>
              {balance !== null ? <div><dt>Ngọc còn lại</dt><dd>{balance}</dd></div> : null}
            </dl>

            <div className={styles.actions}>
              <Link className={styles.secondary} to="/wallet">Nạp thêm</Link>
              <button className={styles.secondary} onClick={() => setOpen(false)} type="button">
                Đóng
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </>
  );
}
