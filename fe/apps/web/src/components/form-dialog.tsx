"use client";

import { useEffect, useRef, useState } from "react";

/**
 * Hộp thoại hỏi người dùng, thay cho window.prompt và window.confirm.
 *
 * <p>Prompt của trình duyệt chỉ nhận được đúng một dòng chữ trần: không nhãn,
 * không gợi ý, không kiểm tra rỗng, và không thể hỏi hai thứ cùng lúc - chỗ
 * "từ chối bài" phải bật liên tiếp hai prompt, ai bấm Cancel ở hộp thứ hai thì
 * mất luôn lý do vừa gõ ở hộp thứ nhất. Nó cũng khoá cứng cả tab và mang nhãn
 * "gioitruyen.com says" của trình duyệt, trông như một cảnh báo lừa đảo.
 *
 * <p>Ở đây một hộp thoại hỏi trọn vẹn mọi trường, nói rõ mỗi trường dùng để làm
 * gì, và chặn ngay tại chỗ khi thiếu - kèm lý do cụ thể ngay dưới ô nhập.
 */

export type DialogField = {
  name: string;
  label: string;
  kind?: "text" | "textarea" | "number" | "select";
  placeholder?: string;
  /** Câu giải thích nhỏ dưới nhãn: trường này dùng để làm gì, ai đọc được. */
  hint?: string;
  required?: boolean;
  /** Câu báo lỗi khi bỏ trống một trường bắt buộc. Nói rõ vì sao cần. */
  requiredMessage?: string;
  value?: string;
  maxLength?: number;
  min?: number;
  max?: number;
  options?: { value: string; label: string }[];
};

export type DialogRequest = {
  title: string;
  /** Một đoạn dẫn ngắn, nói việc sắp xảy ra. */
  intro?: string;
  /** Các dữ kiện cần đọc trước khi bấm - số tiền, hệ quả, thời hạn. */
  lines?: string[];
  /** Điều không lấy lại được, in đậm hơn phần còn lại. */
  warning?: string;
  fields?: DialogField[];
  submitLabel: string;
  cancelLabel?: string;
  tone?: "default" | "danger";
  /** Trả về gì cũng được - hộp thoại chỉ đợi nó xong rồi đóng. */
  onSubmit: (values: Record<string, string>) => unknown;
};

/**
 * Lỗi của từng trường, theo tên trường. Rỗng nghĩa là gửi được.
 *
 * <p>Tách khỏi component để kiểm được bằng test: đây là chỗ quyết định một
 * khiếu nại có lý do hay không, và một hộp thoại cho gửi khiếu nại trống thì
 * quản trị viên nhận về một dòng rỗng không phân xử được.
 */
export function validateFields(
  fields: DialogField[],
  values: Record<string, string>,
): Record<string, string> {
  const found: Record<string, string> = {};
  for (const field of fields) {
    const value = (values[field.name] ?? "").trim();
    if (field.required && !value) {
      found[field.name] = field.requiredMessage ?? `Cần điền “${field.label}”.`;
      continue;
    }
    // Một ô số bỏ trống mà không bắt buộc thì coi như không nhập, không phải
    // là số sai - đừng bắt lỗi thứ người ta cố tình để trống.
    if (field.kind !== "number" || !value) continue;
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      found[field.name] = "Chỉ nhập số.";
    } else if (field.min != null && parsed < field.min) {
      found[field.name] = `Không được nhỏ hơn ${field.min.toLocaleString("vi-VN")}.`;
    } else if (field.max != null && parsed > field.max) {
      found[field.name] = `Không được lớn hơn ${field.max.toLocaleString("vi-VN")}.`;
    }
  }
  return found;
}

function initialValues(fields: DialogField[]): Record<string, string> {
  const values: Record<string, string> = {};
  for (const field of fields) {
    values[field.name] = field.value
      ?? (field.kind === "select" ? field.options?.[0]?.value ?? "" : "");
  }
  return values;
}

export function FormDialog({
  onClose,
  request,
}: Readonly<{ onClose: () => void; request: DialogRequest | null }>) {
  const fields = request?.fields ?? [];
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [sending, setSending] = useState(false);
  const first = useRef<HTMLInputElement & HTMLTextAreaElement & HTMLSelectElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);

  // Mỗi lần mở một yêu cầu khác là một tờ giấy trắng: giá trị cũ của lần trước
  // không được dính sang, nếu không người dùng từ chối bài B bằng lý do của A.
  useEffect(() => {
    if (!request) return;
    setValues(initialValues(request.fields ?? []));
    setErrors({});
  }, [request]);

  useEffect(() => {
    if (!request) return undefined;
    // Con trỏ vào thẳng ô đầu tiên - không có ô nào thì vào nút Huỷ, để Esc và
    // Tab hoạt động ngay mà không phải bấm chuột trước.
    (first.current ?? cancel.current)?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !sending) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, request, sending]);

  if (!request) return null;

  function set(name: string, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    // Lỗi biến mất ngay khi người dùng bắt đầu sửa, không đợi đến lần gửi sau.
    setErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (sending || !request) return;

    const found = validateFields(fields, values);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }

    const trimmed: Record<string, string> = {};
    for (const field of fields) trimmed[field.name] = (values[field.name] ?? "").trim();

    setSending(true);
    try {
      await request.onSubmit(trimmed);
      onClose();
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="opDialogBackdrop" onClick={() => !sending && onClose()} role="presentation">
      <form
        aria-labelledby="ask-dialog-title"
        aria-modal="true"
        className="opDialog askDialog"
        data-tone={request.tone ?? "default"}
        // Nền đóng hộp thoại; bấm bên trong thì không.
        onClick={(event) => event.stopPropagation()}
        onSubmit={submit}
        role="dialog"
      >
        <h2 className="askDialogTitle" id="ask-dialog-title">{request.title}</h2>
        {request.intro ? <p className="askDialogIntro">{request.intro}</p> : null}

        {request.lines && request.lines.length > 0 ? (
          <ul className="askDialogLines">
            {request.lines.map((line) => <li key={line}>{line}</li>)}
          </ul>
        ) : null}

        {request.warning ? <p className="askDialogWarning">{request.warning}</p> : null}

        {fields.map((field, index) => {
          const id = `ask-${field.name}`;
          const error = errors[field.name];
          const shared = {
            "aria-describedby": error ? `${id}-error` : undefined,
            "aria-invalid": error ? true : undefined,
            id,
            onChange: (
              event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
            ) => set(field.name, event.target.value),
            ref: index === 0 ? first : undefined,
            value: values[field.name] ?? "",
          };
          return (
            <div className="askDialogField" key={field.name}>
              <label htmlFor={id}>
                {field.label}
                {field.required ? <span className="askDialogRequired"> *</span> : null}
              </label>
              {field.hint ? <p className="askDialogHint">{field.hint}</p> : null}

              {field.kind === "textarea" ? (
                <textarea
                  {...shared}
                  maxLength={field.maxLength}
                  placeholder={field.placeholder}
                  rows={4}
                />
              ) : field.kind === "select" ? (
                <select {...shared}>
                  {field.options?.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              ) : (
                <input
                  {...shared}
                  inputMode={field.kind === "number" ? "numeric" : undefined}
                  max={field.max}
                  maxLength={field.maxLength}
                  min={field.min}
                  placeholder={field.placeholder}
                  type={field.kind === "number" ? "number" : "text"}
                />
              )}

              {error ? <p className="askDialogError" id={`${id}-error`}>{error}</p> : null}
            </div>
          );
        })}

        <div className="askDialogActions">
          <button
            className="pubGhostBtn"
            disabled={sending}
            onClick={onClose}
            ref={cancel}
            type="button"
          >
            {request.cancelLabel ?? "Huỷ"}
          </button>
          <button
            className={request.tone === "danger" ? "pubDangerBtn" : "pubPrimaryBtn"}
            disabled={sending}
            type="submit"
          >
            {sending ? "Đang gửi…" : request.submitLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
