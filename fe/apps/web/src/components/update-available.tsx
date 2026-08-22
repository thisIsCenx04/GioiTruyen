"use client";

import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";

/**
 * Tells a reader when the site has been redeployed under them.
 *
 * <p>A single-page app keeps running whatever JavaScript it started with. After
 * a deploy, a tab left open goes on using the old bundle indefinitely - which is
 * how a publisher came to report chapter-splitting bugs that had already been
 * fixed and shipped: the fix was on the server, their tab was not.
 *
 * <p>index.html is served with no-store and names the hashed bundle, so
 * comparing the bundle it points at against the one running is enough to know a
 * deploy has happened. Nothing reloads on its own: a reader mid-chapter, or a
 * publisher with an unsaved form, decides when.
 *
 * <p>Nhưng bundle mới không có nghĩa là trang đã dùng được. Máy chủ web lên
 * trước, backend khởi động sau và mất khoảng mười lăm hai mươi giây mới trả lời
 * - trong quãng đó mọi lệnh gọi API đều 502. Mời người đọc tải lại đúng lúc ấy
 * là đẩy họ vào một trang trắng. Vì vậy banner chỉ hiện khi cả hai điều cùng
 * đúng: có bản mới, VÀ backend đã trả lời khoẻ.
 */

/** How often to look. Long enough to be invisible, short enough to matter. */
const CHECK_INTERVAL_MS = 5 * 60 * 1000;

/**
 * Nhịp hỏi lại khi đã thấy bản mới nhưng backend chưa sẵn sàng.
 *
 * <p>Ngắn hơn nhịp thường vì lúc này đang chờ một việc kéo dài vài chục giây,
 * không phải rình một việc xảy ra vài ngày một lần.
 */
const RETRY_INTERVAL_MS = 10 * 1000;

/**
 * Backend đã trả lời khoẻ chưa.
 *
 * <p>Hỏi thẳng actuator/health thay vì suy từ một lệnh gọi bất kỳ: một API
 * nghiệp vụ có thể trả 200 từ đệm của proxy trong khi backend vẫn đang khởi
 * động, còn health thì không.
 */
async function backendHealthy(): Promise<boolean> {
  try {
    const response = await fetch(`/api/v1/actuator/health?v=${Date.now()}`, { cache: "no-store" });
    if (!response.ok) return false;
    const body = await response.json() as { status?: string };
    return body.status === "UP";
  } catch {
    return false;
  }
}

/** The bundle this tab is running, read from its own script tag. */
function runningBundle(): string | null {
  const scripts = [...document.querySelectorAll<HTMLScriptElement>("script[src]")];
  const entry = scripts.find((script) => /\/assets\/index-[^/]+\.js/u.test(script.src));
  return entry ? new URL(entry.src).pathname : null;
}

/** The bundle the server is currently serving, or null if it cannot be read. */
async function deployedBundle(): Promise<string | null> {
  try {
    // cache: no-store on top of the no-store header, because a service worker or
    // an intermediary proxy is not bound by the header alone.
    const response = await fetch(`/index.html?v=${Date.now()}`, { cache: "no-store" });
    if (!response.ok) return null;
    const html = await response.text();
    return /\/assets\/index-[^"']+\.js/u.exec(html)?.[0] ?? null;
  } catch {
    // Offline, or the check was blocked. Silence is right: this is a courtesy,
    // and a failed check must never interrupt reading.
    return null;
  }
}

export function UpdateAvailable() {
  const [stale, setStale] = useState(false);

  useEffect(() => {
    const running = runningBundle();
    // In dev there is no hashed bundle to compare, so there is nothing to do.
    if (!running) return undefined;

    let cancelled = false;
    let retry: number | null = null;

    const stopRetry = () => {
      if (retry != null) {
        window.clearInterval(retry);
        retry = null;
      }
    };

    const check = async () => {
      const deployed = await deployedBundle();
      if (cancelled || !deployed || deployed === running) return;

      if (await backendHealthy()) {
        if (cancelled) return;
        stopRetry();
        setStale(true);
        return;
      }
      // Có bản mới nhưng backend chưa lên. Im lặng chờ và hỏi lại thường xuyên
      // hơn, thay vì mời người đọc tải lại vào một trang chưa chạy được.
      if (!cancelled && retry == null) {
        retry = window.setInterval(() => void check(), RETRY_INTERVAL_MS);
      }
    };

    const timer = window.setInterval(() => void check(), CHECK_INTERVAL_MS);
    // Coming back to a tab left open overnight is the case that matters most.
    const onVisible = () => { if (document.visibilityState === "visible") void check(); };
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
      stopRetry();
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, []);

  if (!stale) return null;

  return (
    <div className="updateBanner" role="status">
      <RefreshCw aria-hidden="true" size={16} />
      <span>Đã có bản cập nhật mới của trang.</span>
      <button
        onClick={() => window.location.reload()}
        type="button"
      >
        Tải lại ngay
      </button>
      <button
        aria-label="Đóng thông báo"
        className="updateBannerDismiss"
        onClick={() => setStale(false)}
        type="button"
      >
        ×
      </button>
    </div>
  );
}
