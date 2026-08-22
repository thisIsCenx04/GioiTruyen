"use client";

import { Check, Clock, Plus, Trash2, Video, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

import { failureOutcome } from "@/components/api-problem";
import { FormDialog, type DialogRequest } from "@/components/form-dialog";
import { OperationDialog, type OperationOutcome } from "@/components/operation-dialog";
import { PublisherTabs } from "@/components/publisher-tabs";
import { PublicShell } from "@/components/site-chrome";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { formatXu } from "@/lib/format";

/**
 * PR campaigns: the team pays readers to promote a story off the platform.
 *
 * <p>The screen has two jobs and they belong to different moments. Writing a
 * campaign happens once; judging what comes back happens every day. So the
 * review queue is what opens first, and the campaign list sits below it.
 */

type Quest = {
  id: string;
  questKind: "OPEN" | "APPLY";
  platform: string;
  title: string;
  requirement: string;
  storyId: string | null;
  storyTitle: string | null;
  rewardXu: number;
  slotCount: number;
  claimedCount: number;
  approvedCount: number;
  pendingCount: number;
  awaitingReview: number;
  escrowXu: number;
  paidXu: number;
  publishFeeXu: number;
  status: "DRAFT" | "OPEN" | "FULL" | "CLOSED" | "CANCELLED";
  registrationEndsAt: string | null;
  publishedAt: string | null;
  createdAt: string | null;
};

type Claim = {
  id: string;
  status: string;
  applyNote: string | null;
  submissionUrl: string | null;
  submissionNote: string | null;
  submittedAt: string | null;
  submitDueAt: string | null;
  reviewDueAt: string | null;
  autoApproved: boolean;
  rejectReason: string | null;
  paidXu: number;
  userId: string;
  userName: string;
  userAvatarUrl: string | null;
  files: string[];
};

type StoryOption = { id: string; title: string };

const PLATFORMS = [
  { label: "TikTok", value: "TIKTOK" },
  { label: "YouTube", value: "YOUTUBE" },
  { label: "Facebook", value: "FACEBOOK" },
  { label: "Khác", value: "OTHER" },
] as const;

/* Hai lựa chọn, đều ngắn. Nhiệm vụ treo hàng tháng giữ ngân sách bị ký quỹ và
   suất bị khoá trong khi truyện nó định quảng bá đã trôi qua từ lâu. */
const DURATIONS = [5, 10] as const;

const STATUS_LABELS: Record<string, string> = {
  CANCELLED: "Đã dừng",
  CLOSED: "Đã kết thúc",
  DRAFT: "Nháp",
  FULL: "Đã đủ người",
  OPEN: "Đang mở",
};

const CLAIM_LABELS: Record<string, string> = {
  APPROVED: "Đã duyệt",
  CANCELLED: "Đã huỷ",
  CLAIMED: "Đang làm",
  DECLINED: "Không được chọn",
  EXPIRED: "Quá hạn nộp",
  PENDING: "Chờ duyệt đơn",
  REJECTED: "Bị từ chối",
  SUBMITTED: "Chờ duyệt bài",
};

const EMPTY_FORM = {
  contactChannel: "ZALO",
  contactHandle: "",
  platform: "TIKTOK",
  questKind: "OPEN" as "OPEN" | "APPLY",
  registrationDays: 10,
  requirement: "",
  rewardXu: 50_000,
  slotCount: 1,
  storyId: "",
  title: "",
};

/** Days between now and a deadline, for the countdown on a pending review. */
function daysLeft(iso: string | null): number | null {
  if (!iso) return null;
  const at = Date.parse(iso);
  if (Number.isNaN(at)) return null;
  return Math.ceil((at - Date.now()) / 86_400_000);
}

export function PrQuestWorkspace({ teamId }: Readonly<{ teamId: string }>) {
  const [quests, setQuests] = useState<Quest[]>([]);
  const [stories, setStories] = useState<StoryOption[]>([]);
  const [claims, setClaims] = useState<Record<string, Claim[]>>({});
  const [openQuest, setOpenQuest] = useState<string | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "forbidden" | "error">("loading");
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [editingId, setEditingId] = useState<string | null>(null);
  const [composing, setComposing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState<OperationOutcome | null>(null);
  const [ask, setAsk] = useState<DialogRequest | null>(null);

  const base = `${API_BASE_URL}/teams/${encodeURIComponent(teamId)}`;

  const load = useCallback(async () => {
    const response = await authedFetch(`${base}/pr-quests`);
    if (response.status === 403) {
      setState("forbidden");
      return;
    }
    if (!response.ok) {
      setState("error");
      return;
    }
    setQuests((await response.json()) as Quest[]);
    setState("ready");

    const storyRes = await authedFetch(`${base}/stories`).catch(() => null);
    if (storyRes?.ok) {
      const rows = (await storyRes.json()) as { id: string; title: string }[];
      setStories(rows.map((row) => ({ id: row.id, title: row.title })));
    }
  }, [base]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadClaims = useCallback(async (questId: string) => {
    const response = await authedFetch(`${base}/pr-quests/${questId}/claims`);
    if (!response.ok) return;
    const rows = (await response.json()) as Claim[];
    setClaims((current) => ({ ...current, [questId]: rows }));
  }, [base]);

  /** Every write goes through here so a failure always reaches the publisher. */
  async function run(
    label: string,
    request: () => Promise<Response>,
    details: string[] = [],
  ) {
    setBusy(true);
    try {
      const response = await request();
      if (!response.ok) {
        const failure = await failureOutcome(label + " thất bại", response);
        setOutcome({
          ...failure,
          details: [...failure.details, "Không có thay đổi nào được lưu."],
        });
        // Trạng thái phía máy chủ có thể đã khác với thứ đang hiện trên màn hình
        // - đó chính là lý do thao tác vừa bị từ chối. Tải lại để khớp.
        await load();
        return false;
      }
      await load();
      if (openQuest) await loadClaims(openQuest);
      setOutcome({ details, kind: "success", title: label + " thành công" });
      return true;
    } catch {
      setOutcome({
        details: ["Mất kết nối tới máy chủ."],
        kind: "error",
        title: label + " thất bại",
      });
      return false;
    } finally {
      setBusy(false);
    }
  }

  const budget = form.rewardXu * form.slotCount;
  /* Một khoản phí duy nhất, cố định. Không có phần trăm: mọi Xu trong ngân sách
     hoặc đến tay người làm, hoặc quay về ví nhóm. */
  const publishFee = 10_000;
  const total = budget + publishFee;

  function startNew() {
    setForm({ ...EMPTY_FORM });
    setEditingId(null);
    setComposing(true);
  }

  function startEdit(quest: Quest) {
    setForm({
      contactChannel: "ZALO",
      contactHandle: "",
      platform: quest.platform,
      questKind: quest.questKind,
      registrationDays: 10,
      requirement: quest.requirement,
      rewardXu: quest.rewardXu,
      slotCount: quest.slotCount,
      storyId: quest.storyId ?? "",
      title: quest.title,
    });
    setEditingId(quest.id);
    setComposing(true);
  }

  async function save() {
    const body = JSON.stringify(form);
    const done = await run(
      editingId ? "Cập nhật nhiệm vụ" : "Tạo nhiệm vụ nháp",
      () => authedFetch(
        editingId ? `${base}/pr-quests/${editingId}` : `${base}/pr-quests`,
        { body, headers: { "Content-Type": "application/json" }, method: editingId ? "PUT" : "POST" },
      ),
      [
        "Nhiệm vụ đang ở trạng thái Nháp — chưa trừ Xu, chưa ai thấy.",
        `Bấm “Publish” khi sẵn sàng: sẽ trừ ${formatXu(total)} Xu khỏi ví.`,
      ],
    );
    if (done) setComposing(false);
  }

  function publish(quest: Quest) {
    const cost = quest.rewardXu * quest.slotCount;
    setAsk({
      intro: `Nhiệm vụ sẽ hiện trên bảng nhiệm vụ công khai và Xu bị trừ ngay lúc này.`,
      lines: [
        `Ngân sách: ${formatXu(cost)} Xu — ${quest.slotCount} suất × ${formatXu(quest.rewardXu)} Xu`,
        `Phí đăng: ${formatXu(publishFee)} Xu`,
        `Tổng trừ ngay: ${formatXu(cost + publishFee)} Xu`,
        "Xu chưa trả cho ai sẽ hoàn đủ về ví khi nhiệm vụ kết thúc.",
      ],
      onSubmit: () => run("Publish nhiệm vụ", () =>
        authedFetch(`${base}/pr-quests/${quest.id}/publish`, { method: "POST" }),
        ["Nhiệm vụ đã lên bảng nhiệm vụ. Xu đã được ký quỹ."]),
      submitLabel: `Publish và trừ ${formatXu(cost + publishFee)} Xu`,
      title: `Publish “${quest.title}”?`,
      warning: `Phí đăng ${formatXu(publishFee)} Xu không hoàn lại, kể cả khi không ai nhận nhiệm vụ.`,
    });
  }

  function stop(quest: Quest) {
    setAsk({
      lines: [
        "Suất đã có người nhận vẫn được giữ và vẫn phải duyệt — tiền đó đã cam kết với họ.",
        "Chỉ phần suất chưa ai nhận được hoàn về ví.",
        "Nhiệm vụ biến mất khỏi bảng ngay, không ai nhận thêm được nữa.",
      ],
      onSubmit: () => run("Dừng nhiệm vụ", () =>
        authedFetch(`${base}/pr-quests/${quest.id}/stop`, { method: "POST" })),
      submitLabel: "Dừng nhiệm vụ",
      title: `Dừng “${quest.title}”?`,
      tone: "danger",
    });
  }

  function remove(quest: Quest) {
    setAsk({
      lines: [
        "Nhiệm vụ này còn ở dạng nháp: chưa trừ Xu, chưa ai nhìn thấy.",
        "Xoá xong không khôi phục lại được.",
      ],
      onSubmit: () => run("Xoá nhiệm vụ", () =>
        authedFetch(`${base}/pr-quests/${quest.id}`, { method: "DELETE" })),
      submitLabel: "Xoá nháp",
      title: `Xoá nhiệm vụ nháp “${quest.title}”?`,
      tone: "danger",
    });
  }

  async function decide(
    claimId: string,
    action: "accept" | "decline" | "approve" | "reject",
    rewardXu = 0,
  ) {
    if (action === "reject") {
      // Một hộp thoại hỏi cả hai thứ. Trước đây là hai prompt nối nhau, ai bấm
      // Cancel ở hộp kênh liên hệ là mất trắng lý do vừa gõ ở hộp trước.
      setAsk({
        fields: [
          {
            hint: "Người nhận đọc được nguyên văn câu này, và quản trị viên cũng đọc nếu có khiếu nại.",
            kind: "textarea",
            label: "Lý do từ chối",
            maxLength: 2000,
            name: "reason",
            placeholder: "Nêu rõ chỗ chưa đạt so với yêu cầu đã đăng. "
              + "Ví dụ: video mới 320 view, yêu cầu là 1.000 view.",
            required: true,
            requiredMessage: "Từ chối mà không nói lý do là căn cứ để người nhận khiếu nại thắng.",
          },
          {
            kind: "select",
            label: "Kênh liên hệ",
            name: "contactChannel",
            options: [
              { label: "Zalo", value: "ZALO" },
              { label: "Facebook", value: "FACEBOOK" },
              { label: "Số điện thoại", value: "PHONE" },
              { label: "Email", value: "EMAIL" },
            ],
          },
          {
            hint: "Người nhận cần liên hệ lại được với nhóm để sửa và nộp lại.",
            label: "Số / link liên hệ",
            maxLength: 200,
            name: "contactHandle",
            placeholder: "0900000000 hoặc fb.com/tennhom",
            required: true,
            requiredMessage: "Thiếu kênh liên hệ thì người nhận không trao đổi lại được với nhóm.",
          },
        ],
        intro: "Người nhận sẽ được sửa lại và nộp bài lần nữa, chừng nào chưa hết hạn nộp.",
        onSubmit: (values) => run("Từ chối bài", () =>
          authedFetch(`${base}/pr-quests/claims/${claimId}/reject`, {
            body: JSON.stringify({
              contactChannel: values.contactChannel,
              contactHandle: values.contactHandle,
              reason: values.reason,
            }),
            headers: { "Content-Type": "application/json" },
            method: "POST",
          })),
        submitLabel: "Từ chối bài",
        title: "Từ chối bài nộp",
        tone: "danger",
      });
      return;
    }
    if (action === "decline") {
      setAsk({
        fields: [{
          hint: "Không bắt buộc, nhưng một câu ngắn giúp họ biết nên sửa gì ở lần sau.",
          kind: "textarea",
          label: "Lý do không chọn",
          maxLength: 1000,
          name: "reason",
          placeholder: "Ví dụ: kênh chưa đủ lượng theo dõi cho yêu cầu lần này.",
        }],
        intro: "Người gửi đơn sẽ thấy đơn chuyển sang “Không được chọn”. Suất vẫn còn cho người khác.",
        onSubmit: (values) => run("Từ chối đơn", () =>
          authedFetch(`${base}/pr-quests/claims/${claimId}/decline`, {
            body: JSON.stringify({ reason: values.reason }),
            headers: { "Content-Type": "application/json" },
            method: "POST",
          })),
        submitLabel: "Không chọn",
        title: "Từ chối đơn ứng tuyển",
      });
      return;
    }
    if (action === "approve") {
      // Duyệt là chuyển Xu ra khỏi ký quỹ sang ví người khác. Không có nút hoàn
      // tác nào cho việc đó, nên nó phải đi qua một lần xác nhận.
      setAsk({
        intro: "Xu rời khỏi ký quỹ và vào ví người thực hiện ngay khi bạn bấm.",
        lines: [
          `Trả cho người thực hiện: ${formatXu(rewardXu)} Xu`,
          "Hãy mở link bài đăng và kiểm tra đủ yêu cầu trước khi duyệt.",
        ],
        onSubmit: () => run("Duyệt bài", () =>
          authedFetch(`${base}/pr-quests/claims/${claimId}/approve`, { method: "POST" }),
          [`Đã trả ${formatXu(rewardXu)} Xu cho người thực hiện.`]),
        submitLabel: `Duyệt và trả ${formatXu(rewardXu)} Xu`,
        title: "Duyệt bài nộp?",
        warning: "Đã trả rồi thì không thu lại được.",
      });
      return;
    }
    await run("Chọn người này", () =>
      authedFetch(`${base}/pr-quests/claims/${claimId}/accept`, { method: "POST" }),
      ["Người này đã được chọn. Xu vẫn nằm trong ký quỹ cho tới khi bạn duyệt bài."]);
  }

  if (state === "forbidden") {
    return (
      <PublicShell>
        <main className="publisherShell">
          <div className="publisherContainer">
            <p className="pubEmpty">Chỉ chủ nhóm hoặc quản lý mới quản lý được chiến dịch PR.</p>
          </div>
        </main>
      </PublicShell>
    );
  }

  const reviewQueue = quests.filter((quest) => quest.awaitingReview > 0 || quest.pendingCount > 0);

  return (
    <PublicShell>
      <main className="publisherShell">
        <div className="publisherContainer">
          {outcome ? (
            <OperationDialog onClose={() => setOutcome(null)} outcome={outcome} />
          ) : null}
          <FormDialog onClose={() => setAsk(null)} request={ask} />

          <header className="publisherHeading">
            <div>
              <h1>CHIẾN DỊCH PR</h1>
              <span>Thuê độc giả quảng bá truyện lên TikTok, YouTube, Facebook</span>
            </div>
          </header>

          <PublisherTabs active="pr" teamId={teamId} />

          {/* What needs answering today, before anything else on the page. A
              submission left unanswered for seven days is paid automatically,
              so this queue is the one thing that costs money to ignore. */}
          {reviewQueue.length > 0 ? (
            <section className="publisherCard prQueue">
              <h2>Cần bạn xử lý</h2>
              <ul>
                {reviewQueue.map((quest) => (
                  <li key={quest.id}>
                    <strong>{quest.title}</strong>
                    <span>
                      {quest.awaitingReview > 0 ? `${quest.awaitingReview} bài chờ duyệt` : ""}
                      {quest.awaitingReview > 0 && quest.pendingCount > 0 ? " · " : ""}
                      {quest.pendingCount > 0 ? `${quest.pendingCount} đơn chờ chọn` : ""}
                    </span>
                    <button
                      className="pubGhostBtn"
                      onClick={() => {
                        setOpenQuest(quest.id);
                        void loadClaims(quest.id);
                      }}
                      type="button"
                    >
                      Mở
                    </button>
                  </li>
                ))}
              </ul>
              <p className="prQueueWarn">
                Bài nộp không được trả lời trong <strong>7 ngày</strong> sẽ được hệ thống tự động
                duyệt và trả Xu cho người thực hiện.
              </p>
            </section>
          ) : null}

          {composing ? (
            <section className="publisherCard prForm">
              <header className="publisherCardHeader">
                <h2>{editingId ? "Sửa nhiệm vụ nháp" : "Nhiệm vụ PR mới"}</h2>
                <button className="pubGhostBtn" onClick={() => setComposing(false)} type="button">
                  Đóng
                </button>
              </header>

              <div className="prFormGrid">
                <label className="pubField">
                  <span>Loại nhiệm vụ</span>
                  <select
                    onChange={(event) => setForm({ ...form, questKind: event.target.value as "OPEN" | "APPLY" })}
                    value={form.questKind}
                  >
                    <option value="OPEN">Mở — ai nhận trước được trước</option>
                    <option value="APPLY">Xét duyệt — người nhận gửi đơn, bạn chọn</option>
                  </select>
                </label>

                <label className="pubField">
                  <span>Nền tảng</span>
                  <select
                    onChange={(event) => setForm({ ...form, platform: event.target.value })}
                    value={form.platform}
                  >
                    {PLATFORMS.map((item) => (
                      <option key={item.value} value={item.value}>{item.label}</option>
                    ))}
                  </select>
                </label>

                <label className="pubField">
                  <span>Truyện cần quảng bá</span>
                  <select
                    onChange={(event) => setForm({ ...form, storyId: event.target.value })}
                    value={form.storyId}
                  >
                    <option value="">— Quảng bá cả nhóm —</option>
                    {stories.map((story) => (
                      <option key={story.id} value={story.id}>{story.title}</option>
                    ))}
                  </select>
                </label>

                <label className="pubField">
                  <span>Hạn đăng ký</span>
                  <select
                    onChange={(event) => setForm({ ...form, registrationDays: Number(event.target.value) })}
                    value={form.registrationDays}
                  >
                    {DURATIONS.map((days) => (
                      <option key={days} value={days}>{days} ngày</option>
                    ))}
                  </select>
                </label>

                <label className="pubField pubFieldWide">
                  <span>Tiêu đề nhiệm vụ</span>
                  <input
                    maxLength={180}
                    onChange={(event) => setForm({ ...form, title: event.target.value })}
                    placeholder="Ví dụ: Làm video TikTok giới thiệu truyện"
                    value={form.title}
                  />
                </label>

                <label className="pubField pubFieldWide">
                  <span>
                    Yêu cầu cụ thể (KPI)
                    <small className="pubFieldCount">Người nhận sẽ bị đánh giá theo đúng những gì bạn viết ở đây</small>
                  </span>
                  <textarea
                    onChange={(event) => setForm({ ...form, requirement: event.target.value })}
                    placeholder={"Ví dụ:\n- Video TikTok tối thiểu 30 giây\n- Đạt tối thiểu 1.000 view sau 7 ngày\n- Gắn link truyện trong bio hoặc phần bình luận ghim\n- Giữ bài tối thiểu 30 ngày"}
                    rows={8}
                    value={form.requirement}
                  />
                </label>

                <label className="pubField">
                  <span>Thưởng mỗi người (Xu)</span>
                  <input
                    min={1000}
                    onChange={(event) => setForm({ ...form, rewardXu: Math.max(0, Number(event.target.value)) })}
                    type="number"
                    value={form.rewardXu}
                  />
                </label>

                <label className="pubField">
                  <span>Số suất</span>
                  <input
                    max={100}
                    min={1}
                    onChange={(event) => setForm({ ...form, slotCount: Math.max(1, Number(event.target.value)) })}
                    type="number"
                    value={form.slotCount}
                  />
                </label>
              </div>

              {/* The bill, before any money moves. The publish fee is called out
                  separately because it is the one part that never comes back. */}
              <div className="prCost">
                <div><span>Ngân sách</span><b>{formatXu(budget)} Xu</b></div>
                <div><span>Phí đăng nhiệm vụ</span><b>{formatXu(publishFee)} Xu</b></div>
                <div className="prCostTotal"><span>Trừ ngay khi publish</span><b>{formatXu(total)} Xu</b></div>
              </div>
              <p className="prCostNote">
                Phí đăng <strong>không hoàn lại</strong> kể cả khi không ai nhận. Ngoài ra không
                thu thêm phần trăm nào: Xu chưa trả cho ai sẽ hoàn đủ về ví khi nhiệm vụ kết thúc.
              </p>

              <div className="prFormActions">
                <button className="pubPrimaryBtn" disabled={busy} onClick={() => void save()} type="button">
                  {editingId ? "Lưu nháp" : "Tạo nháp"}
                </button>
              </div>
            </section>
          ) : (
            <div className="prToolbar">
              <button className="pubPrimaryBtn" onClick={startNew} type="button">
                <Plus aria-hidden="true" size={15} /> Tạo nhiệm vụ PR
              </button>
            </div>
          )}

          {state === "loading" ? <p className="pubEmpty">Đang tải…</p> : null}
          {state === "error" ? <p className="pubEmpty">Không tải được danh sách nhiệm vụ.</p> : null}

          {state === "ready" && quests.length === 0 && !composing ? (
            <p className="pubEmpty">
              Chưa có chiến dịch PR nào. Tạo một nhiệm vụ để thuê độc giả quảng bá truyện của nhóm
              lên TikTok, YouTube hoặc Facebook.
            </p>
          ) : null}

          {quests.map((quest) => {
            const rows = claims[quest.id] ?? [];
            const isOpen = openQuest === quest.id;
            return (
              <section className="publisherCard prQuest" key={quest.id}>
                <header className="prQuestHead">
                  <div>
                    <span className={`prBadge is${quest.status}`}>{STATUS_LABELS[quest.status]}</span>
                    <span className="prBadge isKind">
                      {quest.questKind === "APPLY" ? "Xét duyệt" : "Nhận trước"}
                    </span>
                    <span className="prBadge isPlatform">{quest.platform}</span>
                    <h3>{quest.title}</h3>
                    {quest.storyTitle ? <small>Truyện: {quest.storyTitle}</small> : null}
                  </div>
                  <div className="prQuestActions">
                    {quest.status === "DRAFT" ? (
                      <>
                        <button className="pubGhostBtn" onClick={() => startEdit(quest)} type="button">Sửa</button>
                        <button className="pubPrimaryBtn" disabled={busy} onClick={() => void publish(quest)} type="button">
                          Publish
                        </button>
                        <button className="pubDangerBtn" disabled={busy} onClick={() => void remove(quest)} type="button">
                          <Trash2 aria-hidden="true" size={14} />
                        </button>
                      </>
                    ) : null}
                    {quest.status === "OPEN" || quest.status === "FULL" ? (
                      <button className="pubDangerBtn" disabled={busy} onClick={() => void stop(quest)} type="button">
                        Dừng nhiệm vụ
                      </button>
                    ) : null}
                    <button
                      className="pubGhostBtn"
                      onClick={() => {
                        const next = isOpen ? null : quest.id;
                        setOpenQuest(next);
                        if (next) void loadClaims(next);
                      }}
                      type="button"
                    >
                      {isOpen ? "Thu gọn" : `Người nhận (${quest.claimedCount + quest.pendingCount})`}
                    </button>
                  </div>
                </header>

                <div className="prStats">
                  <div><span>Thưởng/người</span><b>{formatXu(quest.rewardXu)} Xu</b></div>
                  <div><span>Suất</span><b>{quest.claimedCount}/{quest.slotCount}</b></div>
                  <div><span>Đã duyệt</span><b>{quest.approvedCount}</b></div>
                  <div><span>Còn ký quỹ</span><b>{formatXu(quest.escrowXu)} Xu</b></div>
                  <div><span>Đã trả</span><b>{formatXu(quest.paidXu)} Xu</b></div>
                  <div><span>Phí đăng</span><b>{formatXu(quest.publishFeeXu)} Xu</b></div>
                </div>

                {isOpen ? (
                  <div className="prClaims">
                    {rows.length === 0 ? (
                      <p className="pubEmpty">Chưa có ai nhận nhiệm vụ này.</p>
                    ) : null}
                    {rows.map((claim) => {
                      const left = daysLeft(claim.reviewDueAt);
                      return (
                        <article className="prClaim" key={claim.id}>
                          <header>
                            <strong>{claim.userName}</strong>
                            <span className={`prBadge isClaim${claim.status}`}>
                              {CLAIM_LABELS[claim.status] ?? claim.status}
                            </span>
                            {claim.status === "SUBMITTED" && left != null ? (
                              <span className={left <= 2 ? "prDue isUrgent" : "prDue"}>
                                <Clock aria-hidden="true" size={13} />
                                {left <= 0 ? "Quá hạn — sẽ tự duyệt" : `Còn ${left} ngày phải duyệt`}
                              </span>
                            ) : null}
                            {claim.autoApproved ? <span className="prDue">Tự động duyệt</span> : null}
                          </header>

                          {claim.applyNote ? <p className="prNote">Đơn: {claim.applyNote}</p> : null}

                          {claim.submissionUrl ? (
                            <p className="prNote">
                              Link:{" "}
                              <a href={claim.submissionUrl} rel="noreferrer noopener" target="_blank">
                                {claim.submissionUrl}
                              </a>
                            </p>
                          ) : null}
                          {claim.submissionNote ? <p className="prNote">Ghi chú: {claim.submissionNote}</p> : null}

                          {claim.files.length > 0 ? (
                            <div className="prShots">
                              {claim.files.map((file) => (
                                <a href={file} key={file} rel="noreferrer noopener" target="_blank">
                                  <img alt="Ảnh chụp bài đăng" loading="lazy" src={file} />
                                </a>
                              ))}
                            </div>
                          ) : null}

                          {claim.rejectReason ? (
                            <p className="prNote isReject">Lý do từ chối: {claim.rejectReason}</p>
                          ) : null}

                          <div className="prClaimActions">
                            {claim.status === "PENDING" ? (
                              <>
                                <button className="pubPrimaryBtn" disabled={busy} onClick={() => void decide(claim.id, "accept")} type="button">
                                  <Check aria-hidden="true" size={14} /> Chọn
                                </button>
                                <button className="pubGhostBtn" disabled={busy} onClick={() => void decide(claim.id, "decline")} type="button">
                                  <X aria-hidden="true" size={14} /> Không chọn
                                </button>
                              </>
                            ) : null}
                            {claim.status === "SUBMITTED" ? (
                              <>
                                <button className="pubPrimaryBtn" disabled={busy} onClick={() => void decide(claim.id, "approve", quest.rewardXu)} type="button">
                                  <Check aria-hidden="true" size={14} /> Duyệt & trả {formatXu(quest.rewardXu)} Xu
                                </button>
                                <button className="pubDangerBtn" disabled={busy} onClick={() => void decide(claim.id, "reject")} type="button">
                                  <X aria-hidden="true" size={14} /> Từ chối
                                </button>
                              </>
                            ) : null}
                            {claim.status === "APPROVED" ? (
                              <span className="prPaid">Đã trả {formatXu(claim.paidXu)} Xu</span>
                            ) : null}
                          </div>
                        </article>
                      );
                    })}
                  </div>
                ) : null}
              </section>
            );
          })}

          <section className="publisherCard prHelp">
            <h2><Video aria-hidden="true" size={16} /> Luật của chiến dịch PR</h2>
            <ul>
              <li>Xu bị trừ ngay khi publish và được <strong>ký quỹ</strong> — người nhận thấy được tiền đã có sẵn.</li>
              <li>Người nhận có <strong>7 ngày</strong> để nộp bài. Quá hạn thì suất được trả về cho người khác.</li>
              <li>Bạn có <strong>7 ngày</strong> để duyệt bài. Quá hạn hệ thống <strong>tự duyệt và trả Xu</strong>.</li>
              <li>Từ chối bắt buộc ghi lý do và để lại kênh liên hệ, để hai bên trao đổi tiếp.</li>
              <li>Dừng chiến dịch chỉ hoàn phần suất chưa ai nhận. Suất đã nhận vẫn phải duyệt.</li>
              <li>Phí đăng <strong>10.000 Xu</strong> không hoàn lại trong mọi trường hợp. Ngoài ra không thu thêm phần trăm.</li>
            </ul>
          </section>
        </div>
      </main>
    </PublicShell>
  );
}
