"use client";

import { useCallback, useEffect, useState } from "react";

import { FormDialog, type DialogRequest } from "@/components/form-dialog";
import { OperationDialog, type OperationOutcome } from "@/components/operation-dialog";
import { formatXu } from "@/lib/format";
import { adminFetch } from "../admin-data";

/**
 * Where PR disputes are settled.
 *
 * <p>Both sides arrive here for opposite reasons: a creator who did the work
 * and was refused, or a team whose creator took the coins and then deleted the
 * post. The screen shows the same evidence either way - the link, the
 * screenshots taken at submission time, what the quest asked for, and what each
 * side wrote - because deciding needs all four.
 */

type Dispute = {
  id: string;
  raisedRole: "CREATOR" | "TEAM";
  reason: string;
  evidenceUrls: string[];
  status: string;
  penaltyXu: number;
  createdAt: string | null;
  claimId: string;
  claimStatus: string;
  submissionUrl: string | null;
  rejectReason: string | null;
  paidXu: number;
  questTitle: string;
  requirement: string;
  platform: string;
  rewardXu: number;
  teamName: string;
  raiserName: string;
  creatorName: string;
  files: string[];
};

export default function AdminPrDisputesPage() {
  const [rows, setRows] = useState<Dispute[]>([]);
  const [status, setStatus] = useState("OPEN");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState<OperationOutcome | null>(null);
  const [ask, setAsk] = useState<DialogRequest | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    const data = await adminFetch<Dispute[]>(`/admin/pr-disputes?status=${status}`).catch(() => []);
    setRows(data);
    setLoading(false);
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  function resolve(dispute: Dispute, upheld: boolean) {
    const loser = dispute.raisedRole === "CREATOR"
      ? `nhóm ${dispute.teamName}`
      : `người làm ${dispute.creatorName}`;

    setAsk({
      fields: [
        {
          hint: "Cả hai bên đọc được nguyên văn. Nêu căn cứ, vì đây là hồ sơ nếu họ hỏi lại.",
          kind: "textarea",
          label: upheld ? "Kết luận" : "Lý do bác khiếu nại",
          maxLength: 2000,
          name: "note",
          required: true,
          requiredMessage: "Kết luận không có lý do thì hai bên không biết dựa vào đâu.",
        },
        ...(upheld
          ? [{
            hint: `Trừ của ${loser} và chuyển cho bên kia. Để 0 nếu chỉ kết luận, không phạt.`,
            kind: "number" as const,
            label: "Xu bồi thường",
            max: dispute.rewardXu * 2,
            min: 0,
            name: "penalty",
            value: String(dispute.rewardXu),
          }]
          : []),
      ],
      intro: upheld
        ? `Chấp nhận khiếu nại. Bên bị kết luận là sai: ${loser}.`
        : "Bác khiếu nại. Không có Xu nào chuyển đi.",
      lines: [
        `Thưởng của nhiệm vụ: ${formatXu(dispute.rewardXu)} Xu`,
        "Khiếu nại đóng lại sau khi xử lý, không mở lại được.",
      ],
      onSubmit: (values) => send(dispute, upheld, values.note, Math.max(0, Number(values.penalty) || 0)),
      submitLabel: upheld ? "Chấp nhận khiếu nại" : "Bác khiếu nại",
      title: upheld ? "Chấp nhận khiếu nại" : "Bác khiếu nại",
      tone: upheld ? "default" : "danger",
    });
  }

  async function send(dispute: Dispute, upheld: boolean, note: string, penalty: number) {
    setBusy(true);
    try {
      const response = await adminFetch<void>(`/admin/pr-disputes/${dispute.id}/resolve`, {
        body: JSON.stringify({ note, penaltyXu: penalty, upheld }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      void response;
      await load();
      setOutcome({
        details: [
          upheld ? "Khiếu nại được chấp nhận." : "Khiếu nại bị bác.",
          penalty > 0 ? `Đã chuyển ${formatXu(penalty)} Xu bồi thường.` : "Không phạt Xu.",
          "Cả hai bên đã được thông báo.",
        ],
        kind: "success",
        title: "Đã xử lý khiếu nại",
      });
    } catch (cause) {
      setOutcome({
        details: [cause instanceof Error ? cause.message : "Không xử lý được khiếu nại."],
        kind: "error",
        title: "Xử lý thất bại",
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {outcome ? <OperationDialog onClose={() => setOutcome(null)} outcome={outcome} /> : null}
      <FormDialog onClose={() => setAsk(null)} request={ask} />

      <header className="adminTopbar">
        <div>
          <h1 style={{ margin: 0 }}>Khiếu nại PR</h1>
          <p style={{ color: "var(--text-secondary)", fontSize: "0.82rem", margin: "0.2rem 0 0" }}>
            Tranh chấp giữa nhóm xuất bản và người nhận nhiệm vụ PR. Phán quyết có thể chuyển Xu
            giữa hai bên.
          </p>
        </div>
        <select
          onChange={(event) => setStatus(event.target.value)}
          style={{ borderRadius: "6px", padding: "0.35rem 0.6rem" }}
          value={status}
        >
          <option value="OPEN">Đang chờ xử lý</option>
          <option value="RESOLVED">Đã chấp nhận</option>
          <option value="DISMISSED">Đã bác</option>
        </select>
      </header>

      {loading ? <p className="adminEmptyState">Đang tải…</p> : null}
      {!loading && rows.length === 0 ? (
        <p className="adminEmptyState">Không có khiếu nại nào ở trạng thái này.</p>
      ) : null}

      <div style={{ display: "grid", gap: "0.85rem" }}>
        {rows.map((dispute) => (
          <article className="prDispute" key={dispute.id}>
            <header>
              <span className={dispute.raisedRole === "CREATOR" ? "prBadge isClaimPENDING" : "prBadge isKind"}>
                {dispute.raisedRole === "CREATOR" ? "Người làm khiếu nại" : "Nhóm khiếu nại"}
              </span>
              <strong>{dispute.questTitle}</strong>
              <span className="prDisputeMeta">
                {dispute.teamName} ↔ {dispute.creatorName} · {formatXu(dispute.rewardXu)} Xu
              </span>
            </header>

            <p className="prNote"><strong>Nội dung khiếu nại:</strong> {dispute.reason}</p>

            {dispute.rejectReason ? (
              <p className="prNote isReject">
                <strong>Lý do nhóm từ chối:</strong> {dispute.rejectReason}
              </p>
            ) : null}

            <details className="prKpiDetails">
              <summary>Yêu cầu của nhiệm vụ ({dispute.platform})</summary>
              <pre className="prCardKpi">{dispute.requirement}</pre>
            </details>

            {dispute.submissionUrl ? (
              <p className="prNote">
                Bài đã nộp:{" "}
                <a href={dispute.submissionUrl} rel="noreferrer noopener" target="_blank">
                  {dispute.submissionUrl}
                </a>
              </p>
            ) : null}

            {/* Screenshots taken when the work was submitted. The link may be
                dead by now, which is exactly why these are kept. */}
            {dispute.files.length > 0 ? (
              <div className="prShots">
                {dispute.files.map((file) => (
                  <a href={file} key={file} rel="noreferrer noopener" target="_blank">
                    <img alt="Ảnh chụp bài đăng lúc nộp" loading="lazy" src={file} />
                  </a>
                ))}
              </div>
            ) : null}

            {dispute.evidenceUrls.length > 0 ? (
              <p className="prNote">
                Bằng chứng kèm khiếu nại: {dispute.evidenceUrls.map((url) => (
                  <a href={url} key={url} rel="noreferrer noopener" target="_blank">{url} </a>
                ))}
              </p>
            ) : null}

            {dispute.status === "OPEN" ? (
              <div className="prClaimActions">
                <button className="pubPrimaryBtn" disabled={busy} onClick={() => void resolve(dispute, true)} type="button">
                  Chấp nhận khiếu nại
                </button>
                <button className="pubGhostBtn" disabled={busy} onClick={() => void resolve(dispute, false)} type="button">
                  Bác khiếu nại
                </button>
              </div>
            ) : (
              <p className="prNote">
                Đã xử lý{dispute.penaltyXu > 0 ? ` · bồi thường ${formatXu(dispute.penaltyXu)} Xu` : ""}
              </p>
            )}
          </article>
        ))}
      </div>
    </>
  );
}
