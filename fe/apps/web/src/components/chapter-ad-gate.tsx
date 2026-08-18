"use client";

import { Play, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { AdsenseUnit } from "./adsense-unit";

/**
 * The interstitial shown when a reader moves to the next chapter.
 *
 * <p>Two rules shape this. AdSense forbids making content conditional on
 * viewing an ad, so the ad is never a gate: the countdown runs on a timer and
 * finishes whether or not an ad was filled, and a reader with an ad blocker
 * waits the same few seconds as everyone else. And the reader is never trapped
 * - the dialog can always be dismissed.
 *
 * <p>When the countdown ends the reader is taken to the next chapter without
 * being asked to click anything. Making them press "Đọc tiếp" after already
 * waiting charged them twice for the same interruption; the continue button
 * stays only as a way to skip the rest of the wait.
 */

/** How long the ad is on screen before the reader is sent onward. */
const COUNTDOWN_SECONDS = 5;

export function ChapterAdGate({
  href,
  onClose,
}: Readonly<{
  href: string;
  onClose: () => void;
}>) {
  const navigate = useNavigate();
  const [remaining, setRemaining] = useState(COUNTDOWN_SECONDS);

  useEffect(() => {
    if (remaining <= 0) return undefined;
    const timer = window.setTimeout(() => setRemaining((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [remaining]);

  const ready = remaining <= 0;

  // Closing as well as navigating: the dialog lives in the chapter page's state,
  // and a route change alone does not unmount it, so it would still be sitting
  // over the next chapter when the reader arrived.
  useEffect(() => {
    if (!ready) return undefined;
    const timer = window.setTimeout(() => {
      onClose();
      navigate(href);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [ready, href, navigate, onClose]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="adGateOverlay" role="presentation">
      <div aria-labelledby="adGateTitle" aria-modal="true" className="adGateDialog" role="dialog">
        <button className="adGateCloseIconBtn" onClick={onClose} type="button" aria-label="Đóng">
          <X size={18} />
        </button>
        <img alt="Giới Truyện" className="adGateLogo" src="/logo-full.png"  decoding="async" loading="lazy" />

        <h2 id="adGateTitle">Quảng cáo giúp Giới Truyện duy trì hệ thống.</h2>
        <p className="adGateLead">
          Hãy xem quảng cáo để ủng hộ người đăng
        </p>

        <div className="adGateSlot">
          {/* No fixed minHeight here: the slot's own CSS reserves the space and
              shrinks it per breakpoint, and an inline height would override
              every one of those media queries. */}
          <AdsenseUnit format="rectangle" style={{ display: "block", width: "100%" }} />
        </div>

        {/* Enabled the whole time: it now skips the remaining wait rather than
            being the only way out, so disabling it would just be an obstacle. */}
        <button
          className="adGateContinue"
          onClick={() => {
            onClose();
            navigate(href as string);
          }}
          type="button"
        >
          <span>
            <strong>Đọc tiếp chương sau</strong>
            <small>
              {ready
                ? "Đang chuyển…"
                : `Tự động chuyển sau ${remaining}s · bấm để đi ngay`}
            </small>
          </span>
          <Play aria-hidden="true" size={18} />
        </button>

        <button className="adGateSkip" onClick={onClose} type="button">Đóng</button>
      </div>
    </div>
  );
}
