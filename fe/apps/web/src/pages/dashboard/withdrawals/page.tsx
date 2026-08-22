"use client";

import { useCallback, useEffect, useState } from "react";

import { failureOutcome } from "@/components/api-problem";
import { FormDialog, type DialogRequest } from "@/components/form-dialog";
import { OperationDialog, type OperationOutcome } from "@/components/operation-dialog";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

/**
 * Bàn duyệt rút tiền.
 *
 * <p>Trước khi có màn hình này không tồn tại cách nào để đổi trạng thái một yêu
 * cầu rút: người dùng gửi đi, xu bị trừ, và bản ghi nằm mãi ở "Chờ duyệt" dù
 * quản trị viên đã chuyển tiền hay đã quyết định không chuyển.
 *
 * <p>Số tài khoản hiện đầy đủ ở đây - quản trị viên phải gõ nó vào giao diện
 * ngân hàng, che đi thì không chuyển tiền được. Đây cũng là màn hình duy nhất
 * lộ số tài khoản; mọi nơi khác đều che.
 */

type Withdrawal = {
  id: string;
  userId: string;
  userName: string | null;
  userEmail: string;
  teamId: string | null;
  teamName: string | null;
  accountName: string;
  accountNumber: string;
  bankName: string;
  grossAmountXu: number;
  feeXu: number;
  netAmountXu: number;
  state: string;
  adminNote: string | null;
  transferReference: string | null;
  confirmNote: string | null;
  reviewedAt: string | null;
  paidAt: string | null;
  confirmedAt: string | null;
  createdAt: string;
};

const STATE_LABELS: Record<string, string> = {
  APPROVED: "Đã duyệt · chờ chuyển tiền",
  COMPLETED: "Người rút đã xác nhận",
  DISPUTED: "Người rút báo chưa nhận",
  FAILED: "Chuyển tiền lỗi",
  PAID: "Đã chuyển · chờ xác nhận",
  PENDING_REVIEW: "Chờ duyệt",
  PROCESSING: "Đang xử lý",
  REJECTED: "Đã huỷ · đã hoàn xu",
};

/** Thứ tự trong bộ lọc theo mức độ cần chú ý, không theo bảng chữ cái. */
const FILTERS = [
  { label: "Chờ duyệt", value: "PENDING_REVIEW" },
  { label: "Báo chưa nhận", value: "DISPUTED" },
  { label: "Chờ chuyển tiền", value: "APPROVED" },
  { label: "Chờ người rút xác nhận", value: "PAID" },
  { label: "Đã xong", value: "COMPLETED" },
  { label: "Đã huỷ", value: "REJECTED" },
  { label: "Tất cả", value: "ALL" },
] as const;

const xu = new Intl.NumberFormat("vi-VN");

function moment(value: string | null) {
  if (!value) return "—";
  const parsed = new Date(value.replace(" ", "T"));
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString("vi-VN", {
      day: "2-digit", hour: "2-digit", minute: "2-digit", month: "2-digit", year: "numeric",
    });
}

export default function AdminWithdrawalsPage() {
  const [rows, setRows] = useState<Withdrawal[]>([]);
  const [state, setState] = useState<string>("PENDING_REVIEW");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState<OperationOutcome | null>(null);
  const [ask, setAsk] = useState<DialogRequest | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    const response = await authedFetch(`${API_BASE_URL}/admin/withdrawals?state=${state}`)
      .catch(() => null);
    if (response?.ok) setRows((await response.json()) as Withdrawal[]);
    setLoading(false);
  }, [state]);

  useEffect(() => {
    void load();
  }, [load]);

  /** Mọi thao tác đi qua đây, nên một lỗi luôn tới được mắt quản trị viên. */
  async function run(label: string, path: string, body: unknown, details: string[]) {
    setBusy(true);
    try {
      const response = await authedFetch(`${API_BASE_URL}/admin/withdrawals/${path}`, {
        body: JSON.stringify(body ?? {}),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        setOutcome(await failureOutcome(`${label} thất bại`, response));
        await load();
        return;
      }
      await load();
      setOutcome({ details, kind: "success", title: `${label} thành công` });
    } finally {
      setBusy(false);
    }
  }

  function approve(row: Withdrawal) {
    setAsk({
      fields: [{
        hint: "Không bắt buộc. Người rút đọc được câu này.",
        kind: "textarea",
        label: "Ghi chú",
        maxLength: 500,
        name: "note",
        placeholder: "Ví dụ: sẽ chuyển trong hôm nay.",
      }],
      intro: `Chấp nhận yêu cầu rút ${xu.format(row.grossAmountXu)} Xu của ${row.userName ?? row.userEmail}.`,
      lines: [
        `Chuyển tới: ${row.accountName} · ${row.bankName} · ${row.accountNumber}`,
        `Số tiền thực nhận: ${xu.format(row.netAmountXu)} Xu`,
        "Xu đã bị trừ khỏi ví từ lúc người dùng gửi yêu cầu, nên bước này không trừ thêm.",
      ],
      onSubmit: (values) => run("Duyệt yêu cầu", `${row.id}/approve`, { note: values.note },
        ["Yêu cầu chuyển sang “Đã duyệt”. Chuyển tiền xong thì bấm “Đã chuyển tiền”."]),
      submitLabel: "Duyệt",
      title: "Duyệt yêu cầu rút tiền",
    });
  }

  function markPaid(row: Withdrawal) {
    setAsk({
      fields: [
        {
          hint: "Người rút dùng mã này để đối chiếu với sao kê ngân hàng của họ.",
          label: "Mã giao dịch ngân hàng",
          maxLength: 160,
          name: "transferReference",
          placeholder: "Ví dụ: FT26082212345678",
        },
        {
          kind: "textarea",
          label: "Ghi chú",
          maxLength: 500,
          name: "note",
        },
      ],
      intro: `Xác nhận đã chuyển ${xu.format(row.netAmountXu)} Xu tới ${row.accountName}.`,
      lines: [
        `${row.bankName} · ${row.accountNumber}`,
        "Người rút sẽ thấy nút xác nhận đã nhận tiền, hoặc báo lại nếu chưa nhận.",
      ],
      onSubmit: (values) => run("Đánh dấu đã chuyển", `${row.id}/paid`, values,
        ["Đang chờ người rút xác nhận."]),
      submitLabel: "Đã chuyển tiền",
      title: "Đánh dấu đã chuyển tiền",
      warning: row.state === "DISPUTED"
        ? "Người rút đã báo chưa nhận được lần trước. Kiểm tra lại số tài khoản trước khi chuyển."
        : undefined,
    });
  }

  function reject(row: Withdrawal) {
    setAsk({
      fields: [{
        hint: "Người rút đọc được nguyên văn. Nêu rõ vì sao, vì họ vừa bị lấy lại một khoản đã yêu cầu.",
        kind: "textarea",
        label: "Lý do huỷ",
        maxLength: 500,
        name: "note",
        placeholder: "Ví dụ: tên tài khoản không khớp với chủ tài khoản đã đăng ký.",
        required: true,
        requiredMessage: "Huỷ mà không nói lý do là một khiếu nại chắc chắn sẽ tới.",
      }],
      intro: `Huỷ yêu cầu rút ${xu.format(row.grossAmountXu)} Xu của ${row.userName ?? row.userEmail}.`,
      lines: [
        `${xu.format(row.grossAmountXu)} Xu sẽ được hoàn lại vào ví ngay khi bấm.`,
        "Yêu cầu đã huỷ không mở lại được; người dùng phải gửi yêu cầu mới.",
      ],
      onSubmit: (values) => run("Huỷ yêu cầu", `${row.id}/reject`, { note: values.note },
        [`Đã hoàn ${xu.format(row.grossAmountXu)} Xu về ví người dùng.`]),
      submitLabel: "Huỷ và hoàn xu",
      title: "Huỷ yêu cầu rút tiền",
      tone: "danger",
      warning: row.state === "PAID"
        ? "Yêu cầu này đã được đánh dấu là đã chuyển tiền. Chỉ huỷ nếu khoản chuyển thật sự không đi."
        : undefined,
    });
  }

  return (
    <>
      {outcome ? <OperationDialog onClose={() => setOutcome(null)} outcome={outcome} /> : null}
      <FormDialog onClose={() => setAsk(null)} request={ask} />

      <header className="adminTopbar">
        <div>
          <h1 style={{ margin: 0 }}>Rút tiền</h1>
          <p style={{ color: "var(--text-secondary)", fontSize: "0.82rem", margin: "0.2rem 0 0" }}>
            Xu đã bị trừ khỏi ví từ lúc người dùng gửi yêu cầu. Huỷ một yêu cầu sẽ hoàn xu lại ngay.
          </p>
        </div>
        <select
          onChange={(event) => setState(event.target.value)}
          style={{ borderRadius: "6px", padding: "0.35rem 0.6rem" }}
          value={state}
        >
          {FILTERS.map((item) => (
            <option key={item.value} value={item.value}>{item.label}</option>
          ))}
        </select>
      </header>

      {loading ? <p className="adminEmptyState">Đang tải…</p> : null}
      {!loading && rows.length === 0 ? (
        <p className="adminEmptyState">Không có yêu cầu nào ở trạng thái này.</p>
      ) : null}

      <div style={{ display: "grid", gap: "0.85rem" }}>
        {rows.map((row) => (
          <article className="adminWithdrawal" key={row.id}>
            <header>
              <span className="adminWithdrawalState" data-state={row.state}>
                {STATE_LABELS[row.state] ?? row.state}
              </span>
              <strong>{xu.format(row.grossAmountXu)} Xu</strong>
              <span className="adminWithdrawalMeta">
                phí {xu.format(row.feeXu)} · thực nhận {xu.format(row.netAmountXu)} Xu
              </span>
            </header>

            <dl className="adminWithdrawalFacts">
              <div>
                <dt>Người rút</dt>
                <dd>
                  {row.userName ?? "—"}
                  <small>{row.userEmail}</small>
                </dd>
              </div>
              <div>
                <dt>Nhóm</dt>
                <dd>{row.teamName ?? "—"}</dd>
              </div>
              <div>
                <dt>Tài khoản nhận</dt>
                {/* Số đầy đủ, chọn được để dán sang giao diện ngân hàng. */}
                <dd className="adminWithdrawalAccount">
                  {row.accountName}
                  <small>{row.bankName} · {row.accountNumber}</small>
                </dd>
              </div>
              <div>
                <dt>Gửi lúc</dt>
                <dd>{moment(row.createdAt)}</dd>
              </div>
              {row.paidAt ? (
                <div>
                  <dt>Đã chuyển lúc</dt>
                  <dd>{moment(row.paidAt)}{row.transferReference ? <small>Mã: {row.transferReference}</small> : null}</dd>
                </div>
              ) : null}
              {row.confirmedAt ? (
                <div>
                  <dt>Người rút xác nhận</dt>
                  <dd>{moment(row.confirmedAt)}</dd>
                </div>
              ) : null}
            </dl>

            {row.adminNote ? (
              <p className="adminWithdrawalNote"><strong>Ghi chú của bạn:</strong> {row.adminNote}</p>
            ) : null}
            {row.confirmNote ? (
              <p className="adminWithdrawalNote isAlert">
                <strong>Người rút báo:</strong> {row.confirmNote}
              </p>
            ) : null}

            <div className="adminWithdrawalActions">
              {row.state === "PENDING_REVIEW" ? (
                <button className="pubPrimaryBtn" disabled={busy} onClick={() => approve(row)} type="button">
                  Duyệt
                </button>
              ) : null}
              {["PENDING_REVIEW", "APPROVED", "PROCESSING", "DISPUTED"].includes(row.state) ? (
                <button className="pubPrimaryBtn" disabled={busy} onClick={() => markPaid(row)} type="button">
                  Đã chuyển tiền
                </button>
              ) : null}
              {["PENDING_REVIEW", "APPROVED", "PROCESSING", "PAID", "DISPUTED"].includes(row.state) ? (
                <button className="pubDangerBtn" disabled={busy} onClick={() => reject(row)} type="button">
                  Huỷ và hoàn xu
                </button>
              ) : null}
              {row.state === "COMPLETED" ? (
                <span className="adminWithdrawalDone">Xong — người rút đã xác nhận nhận được tiền.</span>
              ) : null}
              {row.state === "REJECTED" ? (
                <span className="adminWithdrawalDone">Đã huỷ và hoàn {xu.format(row.grossAmountXu)} Xu.</span>
              ) : null}
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
