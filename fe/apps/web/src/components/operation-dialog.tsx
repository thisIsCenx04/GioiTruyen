"use client";

import { AlertTriangle, CheckCircle2, XCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";

/**
 * The result of an upload or a save, stated in full.
 *
 * <p>These operations used to report themselves in a thin line of text above
 * the form. A publisher who uploaded a 43,000-word file was told "Đã đọc … 72
 * chương" and nothing else: not whether any text had been dropped, not that two
 * chapters shared a number, not - when it failed - which chapter the server had
 * objected to. Everything the operation learned is shown here, and the reader
 * has to acknowledge it before carrying on.
 */

export type OperationOutcome = {
  kind: "success" | "warning" | "error";
  title: string;
  /** One line per fact. Rendered in order, as a list. */
  details: string[];
  /** What to do about it, when there is something to do. */
  hint?: string;
  /**
   * Một việc người dùng làm được ngay tại đây.
   *
   * <p>Có những kết quả mà lời khuyên đi kèm là một hành động cụ thể - "trang
   * chưa có thể loại này, thêm không?". Bắt người dùng đóng hộp thoại rồi đi
   * tìm màn hình khác để làm việc đó là cách chắc chắn nhất khiến họ bỏ qua.
   */
  action?: {
    label: string;
    run: () => Promise<void> | void;
  };
};

const ICONS = {
  error: XCircle,
  success: CheckCircle2,
  warning: AlertTriangle,
} as const;

const HEADINGS = {
  error: "Không thực hiện được",
  success: "Thành công",
  warning: "Xong, nhưng cần xem lại",
} as const;

export function OperationDialog({
  onClose,
  outcome,
}: Readonly<{ onClose: () => void; outcome: OperationOutcome | null }>) {
  const closeButton = useRef<HTMLButtonElement>(null);
  const [running, setRunning] = useState(false);
  const [done, setDone] = useState("");

  // Mỗi lần mở một kết quả khác là một tờ giấy trắng: nút hành động của lần
  // trước không được để lại trạng thái "đã xong" cho lần này.
  useEffect(() => {
    setRunning(false);
    setDone("");
  }, [outcome]);

  useEffect(() => {
    if (!outcome) return undefined;
    // Focus moves to the dialog so the result is announced and Esc works
    // without the reader having to click first.
    closeButton.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, outcome]);

  if (!outcome) return null;
  const Icon = ICONS[outcome.kind];

  return (
    <div className="opDialogBackdrop" onClick={onClose} role="presentation">
      <div
        aria-labelledby="op-dialog-title"
        aria-modal="true"
        className="opDialog"
        data-kind={outcome.kind}
        // The backdrop closes the dialog; a click inside it must not.
        onClick={(event) => event.stopPropagation()}
        role="alertdialog"
      >
        <header className="opDialogHead">
          <Icon aria-hidden="true" size={22} />
          <div>
            <p className="opDialogEyebrow">{HEADINGS[outcome.kind]}</p>
            <h2 id="op-dialog-title">{outcome.title}</h2>
          </div>
        </header>

        {outcome.details.length > 0 ? (
          <ul className="opDialogDetails">
            {outcome.details.map((line) => <li key={line}>{line}</li>)}
          </ul>
        ) : null}

        {outcome.hint ? <p className="opDialogHint">{outcome.hint}</p> : null}

        {done ? <p className="opDialogDone" role="status">{done}</p> : null}

        <div className="opDialogActions">
          {outcome.action && !done ? (
            <button
              className="opDialogAction"
              disabled={running}
              onClick={async () => {
                if (!outcome.action) return;
                setRunning(true);
                try {
                  await outcome.action.run();
                  setDone("Đã xong.");
                } finally {
                  setRunning(false);
                }
              }}
              type="button"
            >
              {running ? "Đang xử lý…" : outcome.action.label}
            </button>
          ) : null}
          <button className="opDialogClose" onClick={onClose} ref={closeButton} type="button">
            Đã hiểu
          </button>
        </div>
      </div>
    </div>
  );
}
