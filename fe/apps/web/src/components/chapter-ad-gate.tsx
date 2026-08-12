"use client";

import { Play } from "lucide-react";
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
 */

/** How long the ad is on screen before the continue button opens. */
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

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const ready = remaining <= 0;

  return (
    <div className="adGateOverlay" role="presentation">
      <div aria-labelledby="adGateTitle" aria-modal="true" className="adGateDialog" role="dialog">
        <img alt="Giới Truyện" className="adGateLogo" src="/logo-full.png" />

        <h2 id="adGateTitle">Quảng cáo giúp Giới Truyện duy trì hệ thống.</h2>
        <p className="adGateLead">
          Hãy dành chút thời gian xem quảng cáo ngắn, cảm ơn bạn rất nhiều!
        </p>

        <div className="adGateSlot">
          <AdsenseUnit format="rectangle" style={{ display: "block", minHeight: "250px" }} />
        </div>

        <button
          className="adGateContinue"
          disabled={!ready}
          onClick={() => navigate(href as string)}
          type="button"
        >
          <span>
            <strong>{ready ? "Đọc tiếp chương sau" : `Vui lòng đợi ${remaining}s`}</strong>
            <small>{ready ? "Cảm ơn bạn đã ủng hộ" : "Nút sẽ mở ngay sau đó"}</small>
          </span>
          <Play aria-hidden="true" size={18} />
        </button>

        <button className="adGateSkip" onClick={onClose} type="button">Đóng</button>
      </div>
    </div>
  );
}
