"use client";

import { Clock, ExternalLink, ShieldAlert, Upload, Users } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { failureOutcome } from "@/components/api-problem";
import { FormDialog, type DialogRequest } from "@/components/form-dialog";
import { OperationDialog, type OperationOutcome } from "@/components/operation-dialog";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { isLoggedIn, loginHref } from "@/lib/auth";
import { formatXu } from "@/lib/format";

/**
 * The PR quest board a reader sees.
 *
 * <p>Somebody deciding whether to spend an evening filming needs three things
 * before they commit: what is being asked, what it pays, and whether this team
 * actually pays. All three are on the card, and the escrow figure is there
 * because it is the difference between an offer and a promise.
 */

export type PrQuest = {
  id: string;
  questKind: "OPEN" | "APPLY";
  platform: string;
  title: string;
  requirement: string;
  rewardXu: number;
  slotCount: number;
  claimedCount: number;
  escrowXu: number;
  status: string;
  registrationEndsAt: string | null;
  submitWindowDays: number;
  reviewWindowDays: number;
  contentHoldDays: number;
  teamId: string;
  teamName: string;
  teamSlug: string | null;
  teamAvatarUrl: string | null;
  storyTitle: string | null;
  storySlug: string | null;
  teamQuests: number;
  teamApprovalRate: number | null;
  /** Present only on "my quests". */
  claimId?: string;
  claimStatus?: string;
  submissionUrl?: string | null;
  submitDueAt?: string | null;
  reviewDueAt?: string | null;
  reviewedAt?: string | null;
  autoApproved?: boolean;
  rejectReason?: string | null;
  rejectContact?: string | null;
  paidXu?: number;
};

const PLATFORM_LABELS: Record<string, string> = {
  FACEBOOK: "Facebook",
  OTHER: "Khác",
  TIKTOK: "TikTok",
  YOUTUBE: "YouTube",
};

const CLAIM_LABELS: Record<string, string> = {
  APPROVED: "Đã nhận thưởng",
  CANCELLED: "Đã huỷ",
  CLAIMED: "Đang làm",
  DECLINED: "Không được chọn",
  EXPIRED: "Quá hạn gửi",
  PENDING: "Chờ nhóm duyệt đơn",
  REJECTED: "Bị từ chối — sửa và nộp lại",
  SUBMITTED: "Chờ nhóm duyệt bài",
};

const SORTS = [
  { label: "Mới nhất", value: "newest" },
  { label: "Thưởng cao nhất", value: "reward" },
  { label: "Sắp hết hạn", value: "ending" },
  { label: "Còn ít suất", value: "slots" },
] as const;

const DONE_STATES = new Set(["APPROVED", "DECLINED", "EXPIRED", "CANCELLED"]);

function daysLeft(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const at = Date.parse(iso);
  if (Number.isNaN(at)) return null;
  return Math.ceil((at - Date.now()) / 86_400_000);
}

export function PrQuestBoard({ view }: Readonly<{ view: "board" | "mine" | "history" }>) {
  const [quests, setQuests] = useState<PrQuest[]>([]);
  const [mine, setMine] = useState<PrQuest[]>([]);
  const [sort, setSort] = useState<string>("newest");
  const [platform, setPlatform] = useState("");
  const [availableOnly, setAvailableOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [outcome, setOutcome] = useState<OperationOutcome | null>(null);
  const [ask, setAsk] = useState<DialogRequest | null>(null);
  const [submitting, setSubmitting] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const signedIn = isLoggedIn();

  const load = useCallback(async () => {
    setLoading(true);
    if (view === "board") {
      const params = new URLSearchParams({ sort });
      if (platform) params.set("platform", platform);
      if (availableOnly) params.set("availableOnly", "true");
      const response = await authedFetch(`${API_BASE_URL}/pr-quests?${params}`).catch(() => null);
      if (response?.ok) setQuests((await response.json()) as PrQuest[]);
    }
    if (signedIn) {
      const response = await authedFetch(`${API_BASE_URL}/pr-quests/mine`).catch(() => null);
      if (response?.ok) setMine((await response.json()) as PrQuest[]);
    }
    setLoading(false);
  }, [availableOnly, platform, signedIn, sort, view]);

  useEffect(() => {
    void load();
  }, [load]);

  const claimedIds = new Set(mine.map((row) => row.id));

  function claim(quest: PrQuest) {
    if (!signedIn) {
      window.location.href = loginHref("/quests");
      return;
    }
    // Nhiệm vụ OPEN nhận được ngay - hỏi thêm chỉ làm chậm. Nhiệm vụ APPLY thì
    // nhóm phải chọn người, nên lời giới thiệu chính là thứ họ dựa vào để chọn.
    if (quest.questKind !== "APPLY") {
      void sendClaim(quest, "");
      return;
    }
    setAsk({
      fields: [{
        hint: "Nhóm đọc phần này để quyết định chọn ai, nên nói thẳng con số thật.",
        kind: "textarea",
        label: "Giới thiệu kênh của bạn",
        maxLength: 1000,
        name: "note",
        placeholder: "Link kênh + lượng view trung bình mỗi bài. Ví dụ:\n"
          + "tiktok.com/@tenkenh — 12.000 view/video, 4.500 follow",
        required: true,
        requiredMessage: "Nhóm cần biết kênh của bạn mới chọn được. Ít nhất dán link kênh.",
      }],
      intro: `Nhóm ${quest.teamName} sẽ xem đơn rồi chọn người thực hiện. `
        + "Được chọn thì Xu mới bắt đầu tính; chưa được chọn thì bạn không mất gì.",
      onSubmit: (values) => sendClaim(quest, values.note),
      submitLabel: "Gửi đơn",
      title: `Gửi đơn: ${quest.title}`,
    });
  }

  async function sendClaim(quest: PrQuest, note: string) {
    setBusy(true);
    try {
      const response = await authedFetch(`${API_BASE_URL}/pr-quests/${quest.id}/claim`, {
        body: JSON.stringify({ note }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        setOutcome(await failureOutcome(
          quest.questKind === "APPLY" ? "Không gửi được đơn" : "Không nhận được nhiệm vụ",
          response,
        ));
        await load();
        return;
      }
      await load();
      setOutcome({
        details: quest.questKind === "APPLY"
          ? ["Đơn của bạn đã gửi. Nhóm sẽ xem và chọn người thực hiện."]
          : [
            `Bạn có ${quest.submitWindowDays} ngày để hoàn thành và nộp link.`,
            "Quá hạn không nộp thì suất sẽ được trả lại cho người khác.",
            `Sau khi nộp, nhóm có ${quest.reviewWindowDays} ngày để duyệt — quá hạn hệ thống tự duyệt và trả Xu cho bạn.`,
          ],
        kind: "success",
        title: quest.questKind === "APPLY" ? "Đã gửi đơn" : "Đã nhận nhiệm vụ",
      });
    } finally {
      setBusy(false);
    }
  }

  if (view === "board") {
    return (
      <>
        {outcome ? <OperationDialog onClose={() => setOutcome(null)} outcome={outcome} /> : null}
        <FormDialog onClose={() => setAsk(null)} request={ask} />

        <div className="prBoardFilters">
          <select onChange={(event) => setSort(event.target.value)} value={sort}>
            {SORTS.map((item) => (
              <option key={item.value} value={item.value}>{item.label}</option>
            ))}
          </select>
          <select onChange={(event) => setPlatform(event.target.value)} value={platform}>
            <option value="">Mọi nền tảng</option>
            {Object.entries(PLATFORM_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <label className="prFilterToggle">
            <input
              checked={availableOnly}
              onChange={(event) => setAvailableOnly(event.target.checked)}
              type="checkbox"
            />
            Chỉ nhiệm vụ còn suất
          </label>
        </div>

        {loading ? <p className="pubEmpty">Đang tải nhiệm vụ PR…</p> : null}

        {!loading && quests.length === 0 ? (
          <p className="pubEmpty">
            Chưa có nhiệm vụ PR nào đang mở. Các nhóm xuất bản đăng nhiệm vụ ở đây khi cần
            quảng bá truyện lên TikTok, YouTube hoặc Facebook.
          </p>
        ) : null}

        <ul className="prBoardList">
          {quests.map((quest) => {
            const left = quest.slotCount - quest.claimedCount;
            const days = daysLeft(quest.registrationEndsAt);
            const taken = claimedIds.has(quest.id);
            return (
              <li className="prCard" key={quest.id}>
                {/* Tiền là thứ người đọc quyết định dựa vào, nên nó đứng đầu và
                    to nhất. Mọi thứ khác chỉ trả lời "đổi lại phải làm gì". */}
                <div className="prCardTop">
                  <div className="prCardHeadings">
                    <div className="prCardBadges">
                      <span className="prBadge isPlatform">
                        {PLATFORM_LABELS[quest.platform] ?? quest.platform}
                      </span>
                      {quest.questKind === "APPLY" ? (
                        <span className="prBadge isKind">Xét duyệt</span>
                      ) : null}
                      {quest.teamApprovalRate != null ? (
                        <span className="prBadge isRate">Duyệt {quest.teamApprovalRate}%</span>
                      ) : null}
                    </div>
                    <h3>{quest.title}</h3>
                    <p className="prCardTeam">
                      <Link to={`/teams/${quest.teamSlug ?? quest.teamId}`}>{quest.teamName}</Link>
                      {quest.storyTitle ? <> · {quest.storyTitle}</> : null}
                    </p>
                  </div>
                  <div className="prCardPrice">
                    <b>{formatXu(quest.rewardXu)}</b>
                    <span>Xu / người</span>
                  </div>
                </div>

                <pre className="prCardKpi">{quest.requirement}</pre>

                <div className="prCardMeta">
                  <span><Users aria-hidden="true" size={13} /> {quest.claimedCount}/{quest.slotCount} suất</span>
                  <span><Clock aria-hidden="true" size={13} /> còn {days == null ? "—" : Math.max(0, days)} ngày</span>
                  {/* Bằng chứng tiền có thật, gói trong ba chữ. */}
                  <span className="prCardEscrowChip">Đã ký quỹ</span>
                </div>

                <button
                  className="pubPrimaryBtn prCardCta"
                  disabled={busy || taken || left <= 0}
                  onClick={() => void claim(quest)}
                  type="button"
                >
                  {taken ? "Đã nhận" : left <= 0 ? "Đã đủ người" : quest.questKind === "APPLY" ? "Gửi đơn" : "Nhận nhiệm vụ"}
                </button>
              </li>
            );
          })}
        </ul>
      </>
    );
  }

  const rows = view === "mine"
    ? mine.filter((row) => !DONE_STATES.has(row.claimStatus ?? ""))
    : mine.filter((row) => DONE_STATES.has(row.claimStatus ?? "") || row.claimStatus === "REJECTED");

  return (
    <>
      {outcome ? <OperationDialog onClose={() => setOutcome(null)} outcome={outcome} /> : null}
        <FormDialog onClose={() => setAsk(null)} request={ask} />

      {!signedIn ? (
        <p className="pubEmpty">
          <Link to={loginHref("/quests")}>Đăng nhập</Link> để nhận nhiệm vụ PR.
        </p>
      ) : null}

      {signedIn && rows.length === 0 ? (
        <p className="pubEmpty">
          {view === "mine"
            ? "Bạn chưa nhận nhiệm vụ PR nào."
            : "Chưa có nhiệm vụ PR nào hoàn tất."}
        </p>
      ) : null}

      <ul className="prMineList">
        {rows.map((row) => (
          <PrClaimCard
            key={row.claimId}
            onDone={() => void load()}
            onOutcome={setOutcome}
            quest={row}
            submitting={submitting === row.claimId}
            setSubmitting={(value) => setSubmitting(value ? row.claimId ?? null : null)}
          />
        ))}
      </ul>
    </>
  );
}

/** One quest the reader has taken: what to do next, and the way to do it. */
function PrClaimCard({
  onDone,
  onOutcome,
  quest,
  setSubmitting,
  submitting,
}: Readonly<{
  onDone: () => void;
  onOutcome: (outcome: OperationOutcome) => void;
  quest: PrQuest;
  setSubmitting: (value: boolean) => void;
  submitting: boolean;
}>) {
  const [url, setUrl] = useState(quest.submissionUrl ?? "");
  const [note, setNote] = useState("");
  const [files, setFiles] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const [ask, setAsk] = useState<DialogRequest | null>(null);

  const submitLeft = daysLeft(quest.submitDueAt);
  const reviewLeft = daysLeft(quest.reviewDueAt);
  const canSubmit = quest.claimStatus === "CLAIMED" || quest.claimStatus === "REJECTED";

  async function upload(picked: FileList | null) {
    if (!picked || picked.length === 0) return;
    setUploading(true);
    try {
      for (const file of Array.from(picked)) {
        const body = new FormData();
        body.append("file", file);
        const response = await authedFetch(`${API_BASE_URL}/pr-quests/proof`, { body, method: "POST" });
        if (!response.ok) {
          onOutcome(await failureOutcome(`Không tải được ảnh “${file.name}”`, response));
          return;
        }
        const result = (await response.json()) as { url: string };
        setFiles((current) => [...current, result.url]);
      }
    } finally {
      setUploading(false);
    }
  }

  function submit() {
    // Encouraged, not required. A screenshot is the only evidence that survives
    // the post being deleted after approval, so it is worth one confirmation -
    // but not worth blocking payment for work that was actually done.
    if (files.length > 0) {
      void sendSubmission();
      return;
    }
    setAsk({
      intro: "Bạn chưa kèm ảnh chụp màn hình bài đăng.",
      lines: [
        "Ảnh là bằng chứng duy nhất còn lại nếu bài đăng bị xoá hoặc chuyển sang riêng tư sau khi nhóm đã duyệt.",
        "Không có ảnh thì khi khiếu nại, quản trị viên chỉ còn cái link đã hỏng để nhìn.",
      ],
      onSubmit: () => sendSubmission(),
      submitLabel: "Vẫn gửi",
      title: "Gửi kết quả mà không có ảnh?",
    });
  }

  async function sendSubmission() {
    setSubmitting(true);
    try {
      const response = await authedFetch(`${API_BASE_URL}/pr-quests/claims/${quest.claimId}/submit`, {
        body: JSON.stringify({ fileUrls: files, note, url }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        onOutcome(await failureOutcome("Gửi kết quả thất bại", response));
        return;
      }
      onOutcome({
        details: [
          `Nhóm có ${quest.reviewWindowDays} ngày để duyệt.`,
          "Quá hạn mà nhóm không phản hồi, hệ thống sẽ tự duyệt và trả Xu cho bạn.",
        ],
        kind: "success",
        title: "Đã gửi kết quả",
      });
      setFiles([]);
      onDone();
    } finally {
      setSubmitting(false);
    }
  }

  function dispute() {
    setAsk({
      fields: [{
        hint: "Nhóm cũng đọc được phần này. Nêu việc đã làm và mốc thời gian, đừng chỉ nói “bị xử ép”.",
        kind: "textarea",
        label: "Chuyện gì đã xảy ra",
        maxLength: 2000,
        name: "reason",
        placeholder: "Ví dụ: đã đăng video ngày 12/8 đúng yêu cầu 1000 view, "
          + "nhóm từ chối ngày 14/8 với lý do “không đạt” nhưng video lúc đó đã 3.200 view.",
        required: true,
        requiredMessage: "Quản trị viên chỉ có phần mô tả này để phân xử, nên không thể bỏ trống.",
      }],
      intro: quest.rejectReason
        ? `Nhóm đã từ chối với lý do: “${quest.rejectReason}”. Nếu bạn cho là không thoả đáng, mô tả lại sự việc để quản trị viên phân xử.`
        : "Quản trị viên sẽ đọc mô tả của bạn, xem bằng chứng và quyết định.",
      lines: files.length > 0
        ? [`${files.length} ảnh bạn đã tải lên sẽ được gửi kèm làm bằng chứng.`]
        : ["Bạn chưa tải ảnh nào lên — hãy tải ảnh trước khi gửi nếu có, vì đó là bằng chứng mạnh nhất."],
      onSubmit: (values) => sendDispute(values.reason),
      submitLabel: "Gửi khiếu nại",
      title: "Khiếu nại lên quản trị viên",
    });
  }

  async function sendDispute(reason: string) {
    const response = await authedFetch(`${API_BASE_URL}/pr-quests/claims/${quest.claimId}/dispute`, {
      body: JSON.stringify({ evidenceUrls: files, reason }),
      headers: { "Content-Type": "application/json" },
      method: "POST",
    });
    if (!response.ok) {
      onOutcome(await failureOutcome("Không gửi được khiếu nại", response));
      return;
    }
    onOutcome({
      details: [
        "Quản trị viên sẽ xem xét và liên hệ hai bên.",
        "Xu của suất này bị giữ lại cho tới khi có kết luận.",
      ],
      kind: "success",
      title: "Đã gửi khiếu nại",
    });
    onDone();
  }

  return (
    <li className="prMineCard">
      <FormDialog onClose={() => setAsk(null)} request={ask} />
      <header>
        <span className={`prBadge isClaim${quest.claimStatus}`}>
          {CLAIM_LABELS[quest.claimStatus ?? ""] ?? quest.claimStatus}
        </span>
        <strong>{quest.title}</strong>
        <span className="prMineReward">{formatXu(quest.rewardXu)} Xu</span>
      </header>

      <p className="prCardTeam">
        <Link to={`/teams/${quest.teamSlug ?? quest.teamId}`}>{quest.teamName}</Link>
        {quest.storyTitle ? <> · {quest.storyTitle}</> : null}
      </p>

      {quest.claimStatus === "CLAIMED" && submitLeft != null ? (
        <p className={submitLeft <= 2 ? "prDue isUrgent" : "prDue"}>
          <Clock aria-hidden="true" size={13} />
          {submitLeft <= 0 ? "Đã quá hạn gửi" : `Còn ${submitLeft} ngày để gửi kết quả`}
        </p>
      ) : null}

      {quest.claimStatus === "SUBMITTED" && reviewLeft != null ? (
        <p className="prDue">
          <Clock aria-hidden="true" size={13} />
          {reviewLeft <= 0
            ? "Quá hạn duyệt — hệ thống sẽ tự duyệt và trả Xu cho bạn"
            : `Nhóm còn ${reviewLeft} ngày để duyệt`}
        </p>
      ) : null}

      {quest.claimStatus === "APPROVED" ? (
        <p className="prPaid">
          Đã nhận {formatXu(quest.paidXu ?? 0)} Xu
          {quest.autoApproved ? " (tự động duyệt do nhóm không phản hồi)" : ""}
        </p>
      ) : null}

      {quest.rejectReason ? (
        <div className="prRejectBox">
          <strong>Lý do từ chối:</strong> {quest.rejectReason}
          {quest.rejectContact ? <p>Liên hệ nhóm: {quest.rejectContact}</p> : null}
        </div>
      ) : null}

      <details className="prKpiDetails">
        <summary>Xem yêu cầu</summary>
        <pre className="prCardKpi">{quest.requirement}</pre>
      </details>

      {canSubmit ? (
        <div className="prSubmitForm">
          <label className="pubField">
            <span>Link bài đăng</span>
            <input
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://www.tiktok.com/@ban/video/..."
              value={url}
            />
          </label>
          <label className="pubField">
            <span>Ghi chú cho nhóm (không bắt buộc)</span>
            <input onChange={(event) => setNote(event.target.value)} value={note} />
          </label>

          <div className="prUpload">
            <label className="pubGhostBtn">
              <Upload aria-hidden="true" size={14} />
              {uploading ? "Đang tải…" : "Thêm ảnh chụp"}
              <input
                accept="image/*"
                multiple
                onChange={(event) => {
                  void upload(event.currentTarget.files);
                  event.currentTarget.value = "";
                }}
                type="file"
              />
            </label>
            <small>Nên có — link có thể bị xoá sau khi duyệt, ảnh chụp là bằng chứng duy nhất còn lại.</small>
          </div>

          {files.length > 0 ? (
            <div className="prShots">
              {files.map((file) => (
                <img alt="Ảnh chụp bài đăng" key={file} src={file} />
              ))}
            </div>
          ) : null}

          <button
            className="pubPrimaryBtn"
            disabled={submitting || uploading || !url.trim()}
            onClick={() => void submit()}
            type="button"
          >
            {submitting ? "Đang gửi…" : "Gửi kết quả"}
          </button>
        </div>
      ) : null}

      {quest.submissionUrl ? (
        <p className="prNote">
          <a href={quest.submissionUrl} rel="noreferrer noopener" target="_blank">
            <ExternalLink aria-hidden="true" size={13} /> Bài đã nộp
          </a>
        </p>
      ) : null}

      {/* Available once a decision has been made, in either direction. */}
      {quest.claimStatus === "REJECTED" || quest.claimStatus === "APPROVED" ? (
        <button className="prDisputeBtn" onClick={() => void dispute()} type="button">
          <ShieldAlert aria-hidden="true" size={13} /> Khiếu nại tới quản trị viên
        </button>
      ) : null}
    </li>
  );
}
