"use client";

import { useEffect, useState } from "react";

/**
 * Advanced content copy protection for story readers and manuscripts.
 *
 * Prevents text selection, right-click context menu, drag-to-save, print-to-pdf,
 * and copy/inspect keyboard shortcuts inside protected story and chapter zones.
 */
export function CopyGuard({
  attribution = "gioitruyen.com",
  selector = ".chapterProse, .manuscript, .oneshotBody, .storyDetailSynopsis",
}: Readonly<{
  /** Appended or used as copyright notice. */
  attribution?: string;
  selector?: string;
}>) {
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  useEffect(() => {
    let toastTimer: number | null = null;

    const showWarning = (msg: string) => {
      setToastMessage(msg);
      if (toastTimer) window.clearTimeout(toastTimer);
      toastTimer = window.setTimeout(() => {
        setToastMessage(null);
      }, 2500);
    };

    const isInsideProtected = (target: EventTarget | null) => {
      if (!(target instanceof Node)) return false;
      const element = target instanceof Element ? target : target.parentElement;
      return Boolean(element?.closest(selector));
    };

    // Block right-click context menu
    const onContextMenu = (event: MouseEvent) => {
      if (isInsideProtected(event.target)) {
        event.preventDefault();
        showWarning("🛡️ Bản quyền thuộc về tác giả & Giới Truyện. Không thể sao chép.");
      }
    };

    // Block drag and drop
    const onDragStart = (event: DragEvent) => {
      if (isInsideProtected(event.target)) {
        event.preventDefault();
      }
    };

    // Block select start
    const onSelectStart = (event: Event) => {
      if (isInsideProtected(event.target)) {
        event.preventDefault();
      }
    };

    // Block copy / cut and sanitize clipboard
    const onCopyOrCut = (event: ClipboardEvent) => {
      if (isInsideProtected(event.target) || window.getSelection()?.toString()) {
        event.preventDefault();
        if (event.clipboardData) {
          event.clipboardData.setData(
            "text/plain",
            `Nội dung được bảo hộ bản quyền tại Giới Truyện (${attribution}). Vui lòng không sao chép trái phép.`
          );
        }
        showWarning("⚠️ Nội dung truyện được bảo hộ bản quyền. Không sao chép trái phép.");
      }
    };

    // Block common copy / inspect / save shortcut combinations
    const onKeyDown = (event: KeyboardEvent) => {
      const isCtrlOrCmd = event.ctrlKey || event.metaKey;
      const key = event.key.toLowerCase();

      // Block Ctrl+C, Ctrl+X, Ctrl+A inside chapter
      if (isCtrlOrCmd && (key === "c" || key === "x" || key === "a")) {
        if (isInsideProtected(event.target) || window.getSelection()?.toString()) {
          event.preventDefault();
          showWarning("🛡️ Tính năng sao chép bị vô hiệu hóa để bảo vệ bản quyền tác giả.");
        }
      }

      // Block Ctrl+S (save page), Ctrl+P (print), Ctrl+U (view source)
      if (isCtrlOrCmd && (key === "s" || key === "p" || key === "u")) {
        event.preventDefault();
        showWarning("🛡️ Trang đọc truyện được bảo hộ bản quyền.");
      }

      // Block F12, Ctrl+Shift+I, Ctrl+Shift+J, Ctrl+Shift+C (inspect shortcuts)
      if (
        event.key === "F12" ||
        (isCtrlOrCmd && event.shiftKey && (key === "i" || key === "j" || key === "c"))
      ) {
        event.preventDefault();
      }
    };

    document.addEventListener("contextmenu", onContextMenu);
    document.addEventListener("dragstart", onDragStart);
    document.addEventListener("selectstart", onSelectStart);
    document.addEventListener("copy", onCopyOrCut);
    document.addEventListener("cut", onCopyOrCut);
    document.addEventListener("keydown", onKeyDown);

    return () => {
      if (toastTimer) window.clearTimeout(toastTimer);
      document.removeEventListener("contextmenu", onContextMenu);
      document.removeEventListener("dragstart", onDragStart);
      document.removeEventListener("selectstart", onSelectStart);
      document.removeEventListener("copy", onCopyOrCut);
      document.removeEventListener("cut", onCopyOrCut);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [attribution, selector]);

  if (!toastMessage) return null;

  return (
    <div
      style={{
        position: "fixed",
        bottom: "2rem",
        left: "50%",
        transform: "translateX(-50%)",
        background: "#071739",
        color: "#ffffff",
        padding: "0.75rem 1.4rem",
        borderRadius: "6px",
        border: "2px solid #fbbf24",
        boxShadow: "0 8px 24px rgba(0, 0, 0, 0.4)",
        fontSize: "0.85rem",
        fontWeight: 750,
        zIndex: 99999,
        pointerEvents: "none",
        textAlign: "center",
        animation: "fadeIn 0.2s ease-out",
      }}
    >
      {toastMessage}
    </div>
  );
}
