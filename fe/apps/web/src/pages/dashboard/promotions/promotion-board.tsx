"use client";

import { useCallback, useEffect, useState } from "react";

import { getAccessToken } from "@/lib/auth";

/** Mirrors the home page's bố cáo strip, which shows twelve. */
const SLOT_COUNT = 12;

type BoardSlot = {
  position: number;
  promotionId: string | null;
  storyId: string | null;
  storyTitle: string | null;
  storySlug: string | null;
  coverUrl: string | null;
  teamName: string | null;
  startsAt: string | null;
  endsAt: string | null;
  durationDays: number;
};

type PendingBooking = {
  id: string;
  storyTitle: string;
  teamName: string;
  durationDays: number;
  coinPaid: number;
  createdAt: string;
  purchasedByEmail: string;
};

type Board = { slots: BoardSlot[]; pending: PendingBooking[] };

type PromotablePackage = {
  id: string;
  name: string;
  durationDays: number;
  priceCoin: number;
};
type PromotableStory = {
  storyId: string;
  title: string;
  teamName: string;
  /** Set while the story already holds a live slot. */
  activeUntil: string | null;
};

const money = new Intl.NumberFormat("vi-VN");

async function adminFetch(path: string, init?: RequestInit) {
  const token = getAccessToken();
  return fetch(`/api/v1${path}`, {
    ...init,
    headers: {
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
}

/** "18/08/2026 01:07" - the form the rest of the admin screens use. */
function when(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

/** Hours left, because a 3-day booking runs 72 hours from approval, not to a date. */
function remaining(endsAt: string | null) {
  if (!endsAt) return "";
  const ms = new Date(endsAt).getTime() - Date.now();
  if (Number.isNaN(ms) || ms <= 0) return "đã hết hạn";
  const hours = Math.floor(ms / 3_600_000);
  return hours >= 24 ? `còn ${Math.floor(hours / 24)} ngày ${hours % 24}h` : `còn ${hours}h`;
}

/**
 * The bố cáo board.
 *
 * <p>Two columns on purpose: the twelve slots on the left are the home page as
 * the reader will see it, and the queue on the right is what is asking to get
 * in. Approving moves a card from right to left; dragging rearranges the left.
 *
 * <p>Drag-and-drop is the browser's own, with no library: the payload is a slot
 * index, and the whole arrangement is sent on drop so positions can never end
 * up doubled halfway through a move.
 */
export function PromotionBoard({ onChanged }: Readonly<{ onChanged?: () => void }>) {
  const [board, setBoard] = useState<Board | null>(null);
  const [dragging, setDragging] = useState<number | null>(null);
  const [over, setOver] = useState<number | null>(null);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  // Set when approving hits a full board; carries the booking being scheduled.
  const [scheduling, setScheduling] = useState<PendingBooking | null>(null);
  const [scheduleAt, setScheduleAt] = useState("");
  const [stories, setStories] = useState<PromotableStory[]>([]);
  const [packages, setPackages] = useState<PromotablePackage[]>([]);
  const [newStory, setNewStory] = useState("");
  const [newPackage, setNewPackage] = useState("");

  const refresh = useCallback(async () => {
    const response = await adminFetch("/admin/promotions/board");
    if (response.ok) setBoard((await response.json()) as Board);
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  // The admin-create form offers the same stories and packages the buyer's page
  // does, so the two stay in step. /promotions/me returns every published story
  // when the caller is an admin, which is exactly the pick-list wanted here.
  useEffect(() => {
    void adminFetch("/promotions/me").then(async (response) => {
      if (!response.ok) {
        setError("Không tải được danh sách truyện và gói bố cáo.");
        return;
      }
      const data = await response.json() as {
        packages: PromotablePackage[]; stories: PromotableStory[];
      };
      setPackages(data.packages ?? []);
      setStories(data.stories ?? []);
    }).catch(() => setError("Không tải được danh sách truyện và gói bố cáo."));
  }, []);

  async function send(path: string, body?: unknown, label = "") {
    setBusy(label);
    setError("");
    setNotice("");
    try {
      const response = await adminFetch(path, {
        method: "POST",
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        // The board-full case is not a failure to report and forget: it is the
        // moment the admin is offered the scheduling form instead.
        if ((payload as { code?: string }).code === "promotion.board_full") {
          return { ok: false, full: true };
        }
        setError((payload as { detail?: string }).detail ?? "Không thực hiện được.");
        return { ok: false, full: false };
      }
      await refresh();
      onChanged?.();
      return { ok: true, full: false };
    } finally {
      setBusy("");
    }
  }

  async function approve(row: PendingBooking) {
    const result = await send(`/admin/promotions/${row.id}/approve`, { note: "" }, row.id);
    if (result.full) {
      setScheduling(row);
      setScheduleAt("");
      setError(`Kho bố cáo đã đầy ${SLOT_COUNT} vị trí. Hãy hẹn lịch cho yêu cầu này, `
        + "hoặc gỡ bớt một vị trí rồi duyệt lại.");
    } else if (result.ok) {
      setNotice(`Đã duyệt "${row.storyTitle}" và xếp vào vị trí trống.`);
    }
  }

  async function confirmSchedule() {
    if (!scheduling) return;
    // hh:dd/mm/yyyy, the format the operator asked for.
    if (!/^\d{1,2}:\d{1,2}\/\d{1,2}\/\d{4}$/u.test(scheduleAt.trim())) {
      setError("Nhập lịch theo dạng hh:dd/mm/yyyy, ví dụ 09:24/08/2026.");
      return;
    }
    const result = await send(
      `/admin/promotions/${scheduling.id}/schedule`,
      { scheduledAt: scheduleAt.trim() },
      scheduling.id,
    );
    if (result.ok) {
      setNotice(`Đã báo lịch cho "${scheduling.storyTitle}". Người đăng nhận được thông báo.`);
      setScheduling(null);
    }
  }

  async function drop(target: number) {
    setOver(null);
    if (board === null || dragging === null || dragging === target) {
      setDragging(null);
      return;
    }
    // Swap locally, then send the whole arrangement.
    const next = board.slots.map((slot) => ({ ...slot }));
    const from = next.find((slot) => slot.position === dragging);
    const to = next.find((slot) => slot.position === target);
    if (!from || !to) return;
    const moved = from.promotionId;
    from.promotionId = to.promotionId;
    to.promotionId = moved;

    const slots: Record<number, string> = {};
    for (const slot of next) {
      if (slot.promotionId) slots[slot.position] = slot.promotionId;
    }
    setDragging(null);
    await send("/admin/promotions/board/reorder", { slots }, "reorder");
  }

  if (board === null) {
    return <p className="pubHint">Đang tải kho bố cáo…</p>;
  }

  const filled = board.slots.filter((slot) => slot.promotionId).length;

  return (
    <section className="promoBoard">
      <header className="promoBoardHead">
        <div>
          <h2>Kho bố cáo trang chủ</h2>
          <p className="pubHint">
            {filled}/{SLOT_COUNT} vị trí đang chạy · kéo thả để đổi thứ tự hiển thị
          </p>
        </div>
      </header>

      {error ? <div className="promoBoardError" role="alert">{error}</div> : null}
      {notice ? <div className="promoBoardNotice" role="status">{notice}</div> : null}

      <div className="promoBoardGrid">
        {/* Left: the twelve slots, in the order readers will see them. */}
        <div className="promoSlots">
          {board.slots.map((slot) => (
            <article
              className={`promoSlot${slot.promotionId ? " isFilled" : ""}${over === slot.position ? " isOver" : ""}`}
              draggable={Boolean(slot.promotionId)}
              key={slot.position}
              onDragEnd={() => { setDragging(null); setOver(null); }}
              onDragLeave={() => setOver((current) => (current === slot.position ? null : current))}
              onDragOver={(event) => { event.preventDefault(); setOver(slot.position); }}
              onDragStart={() => setDragging(slot.position)}
              onDrop={(event) => { event.preventDefault(); void drop(slot.position); }}
            >
              <span className="promoSlotNo">{slot.position}</span>
              {slot.promotionId ? (
                <>
                  {slot.coverUrl
                    ? <img alt="" className="promoSlotCover" src={slot.coverUrl}  decoding="async" loading="lazy" />
                    : <span className="promoSlotCover promoSlotCoverEmpty" />}
                  <div className="promoSlotBody">
                    <strong>{slot.storyTitle}</strong>
                    <small>{slot.teamName}</small>
                    <small>{slot.durationDays} ngày · {remaining(slot.endsAt)}</small>
                  </div>
                  <button
                    className="promoSlotClear"
                    disabled={busy !== ""}
                    onClick={() => void send(`/admin/promotions/board/${slot.promotionId}/clear`, undefined, slot.promotionId ?? "")}
                    title="Gỡ khỏi kho bố cáo"
                    type="button"
                  >
                    ×
                  </button>
                </>
              ) : (
                <span className="promoSlotEmpty">Trống</span>
              )}
            </article>
          ))}
        </div>

        {/* Right: what is waiting to get in. */}
        <aside className="promoQueue">
          <h3>Chờ duyệt ({board.pending.length})</h3>

          {board.pending.length === 0 ? (
            <p className="pubHint">Không có yêu cầu nào đang chờ.</p>
          ) : (
            <ul>
              {board.pending.map((row) => (
                <li key={row.id}>
                  <div>
                    <strong>{row.storyTitle}</strong>
                    <small>{row.teamName} · {row.durationDays} ngày · {money.format(row.coinPaid)} xu</small>
                    <small>Gửi lúc {when(row.createdAt)}</small>
                  </div>
                  <button
                    className="promoApprove"
                    disabled={busy !== ""}
                    onClick={() => void approve(row)}
                    type="button"
                  >
                    Duyệt
                  </button>
                </li>
              ))}
            </ul>
          )}

          {/* Offered when approving finds the board full. */}
          {scheduling ? (
            <div className="promoSchedule">
              <strong>Hẹn lịch cho “{scheduling.storyTitle}”</strong>
              <p className="pubHint">
                Người đăng sẽ nhận: “Bạn đã đăng ký bố cáo thành công. Truyện sẽ được
                bố cáo vào …”. Xu đã trả được giữ nguyên, yêu cầu vẫn nằm trong hàng chờ.
              </p>
              <input
                onChange={(event) => setScheduleAt(event.target.value)}
                placeholder="hh:dd/mm/yyyy — ví dụ 09:24/08/2026"
                value={scheduleAt}
              />
              <div>
                <button disabled={busy !== ""} onClick={() => void confirmSchedule()} type="button">
                  Gửi lịch
                </button>
                <button onClick={() => { setScheduling(null); setError(""); }} type="button">
                  Huỷ
                </button>
              </div>
            </div>
          ) : null}

          {/* House picks: a slot the admin fills without charging anyone. */}
          <div className="promoDirect">
            <h3>Tự thêm bố cáo</h3>
            <p className="pubHint">Không trừ xu của nhóm. Dùng khi kho đang trống.</p>
            <select onChange={(event) => setNewStory(event.target.value)} value={newStory}>
              <option value="">— Chọn truyện ({stories.length}) —</option>
              {stories.map((story) => (
                <option key={story.storyId} value={story.storyId}>
                  {story.title} — {story.teamName}
                  {/* Flagged rather than hidden: stacking a second slot onto a
                      story is legitimate, but doing it unknowingly is not. */}
                  {story.activeUntil ? " (đang có bố cáo)" : ""}
                </option>
              ))}
            </select>
            {/* Same packages, same prices as the buyer's page - both read the
                promotion_packages table, so an admin never places a slot on a
                length or price the price list does not offer. */}
            <select onChange={(event) => setNewPackage(event.target.value)} value={newPackage}>
              <option value="">— Chọn gói —</option>
              {packages.map((pkg) => (
                <option key={pkg.id} value={pkg.id}>
                  {pkg.name} · {pkg.durationDays} ngày · {money.format(pkg.priceCoin)} xu
                </option>
              ))}
            </select>
            <button
              disabled={busy !== "" || !newStory || !newPackage}
              onClick={() => void send("/admin/promotions/board", { packageId: newPackage, storyId: newStory }, "create")
                .then((result) => {
                  if (result.ok) {
                    setNotice("Đã thêm vào kho bố cáo.");
                    setNewStory("");
                    setNewPackage("");
                  }
                })}
              type="button"
            >
              Thêm vào kho
            </button>
          </div>
        </aside>
      </div>
    </section>
  );
}
