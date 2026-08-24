"use client";

import { useEffect } from "react";

import { isLoggedIn } from "@/lib/auth";
import { reportQuestProgress, type QuestType } from "@/lib/quests";

/**
 * Nhịp đo. Không phải nhịp gửi.
 *
 * Bản cũ đặt `setInterval` 60 giây và mỗi lần nổ thì cộng đúng 1 phút. Cách đó
 * đo số lần bộ đếm nổ chứ không đo thời gian thật: trình duyệt bóp nhịp timer ở
 * tab nền, và một lần nổ trễ ba phút vẫn chỉ được tính một phút. Ở đây mỗi nhịp
 * chỉ cộng đúng khoảng thời gian vừa trôi qua, đo bằng `Date.now()`.
 */
const TICK_MS = 5_000;

/**
 * Khoảng nhảy lớn hơn mức này thì bỏ, không cộng.
 *
 * Máy ngủ, khoá màn hình, hoặc tab bị treo sẽ làm hai nhịp cách nhau hàng giờ.
 * Người dùng không đọc trong khoảng đó, nên nó không được tính.
 */
const MAX_TICK_GAP_MS = TICK_MS * 3;

/**
 * Bao lâu không thao tác thì coi là rời đi.
 *
 * Ba phút, không phải chín mươi giây như bản cũ. Đọc truyện là việc ít thao tác
 * nhất trên trang: đọc hết một màn hình rồi mới cuộn một lần là chuyện thường,
 * và ngưỡng chín mươi giây khiến chính người đọc chăm chú nhất bị coi là rảnh.
 */
const IDLE_AFTER_MS = 180_000;

/** Gửi lên máy chủ mỗi 30 giây, nếu có đủ phút tròn để gửi. */
const FLUSH_EVERY_MS = 30_000;

/** Máy chủ chặn mỗi lần gửi tối đa 5 phút (MAX_INCREMENT). Phần dư gửi lần sau. */
const MAX_MINUTES_PER_REQUEST = 5;

const MS_PER_MINUTE = 60_000;

/** Tab nào đang đếm. Bản ghi cũ hơn mức này coi như tab đó đã đóng. */
const OWNER_STALE_MS = 20_000;

/** Múi giờ mà máy chủ dùng để cắt ngày nhiệm vụ (QuestService.QUEST_ZONE). */
const QUEST_ZONE = "Asia/Ho_Chi_Minh";

const questDayFormat = new Intl.DateTimeFormat("en-CA", {
  day: "2-digit",
  month: "2-digit",
  timeZone: QUEST_ZONE,
  year: "numeric",
});

/** Ngày nhiệm vụ hiện tại theo giờ Việt Nam, dạng YYYY-MM-DD. */
function questDay(): string {
  return questDayFormat.format(new Date());
}

/**
 * Mili giây đã tích được nhưng chưa gửi, giữ trong localStorage.
 *
 * Giữ ngoài React là điều bắt buộc, không phải để cho tiện. Bộ đếm cũ sống
 * trong `useEffect` của trang đọc chương, nên mỗi lần sang chương mới là nó bị
 * huỷ và đếm lại từ 0 — ai đọc mỗi chương dưới một phút thì vĩnh viễn không
 * được tính phút nào. Trong localStorage thì nó sống qua chuyển trang, qua tải
 * lại, và qua cả việc đóng tab.
 */
function bucketKey(questType: QuestType): string {
  return `quest-ms:${questType}:${questDay()}`;
}

function readBucket(questType: QuestType): number {
  try {
    const raw = window.localStorage.getItem(bucketKey(questType));
    const value = raw === null ? 0 : Number.parseInt(raw, 10);
    return Number.isFinite(value) && value > 0 ? value : 0;
  } catch {
    // Chế độ riêng tư hoặc trình duyệt chặn lưu trữ: đếm trong phiên này thôi.
    return 0;
  }
}

function writeBucket(questType: QuestType, ms: number): void {
  try {
    window.localStorage.setItem(bucketKey(questType), String(Math.max(0, Math.round(ms))));
  } catch {
    // Không lưu được thì thôi, không được để vỡ trang đọc.
  }
}

/**
 * Chỉ một tab được đếm cho mỗi loại nhiệm vụ.
 *
 * Mở hai tab là hai bộ đếm cùng chạy, nên nhiệm vụ "Online 30 phút" xong sau 15
 * phút. Một người ngồi trước máy vẫn là một người, dù họ mở bao nhiêu tab.
 */
function ownerKey(questType: QuestType): string {
  return `quest-owner:${questType}`;
}

function claimOwnership(questType: QuestType, tabId: string): boolean {
  try {
    const raw = window.localStorage.getItem(ownerKey(questType));
    if (raw) {
      const [holder, stampText] = raw.split("|");
      const stamp = Number.parseInt(stampText ?? "", 10);
      const fresh = Number.isFinite(stamp) && Date.now() - stamp < OWNER_STALE_MS;
      if (fresh && holder !== tabId) return false;
    }
    window.localStorage.setItem(ownerKey(questType), `${tabId}|${Date.now()}`);
    return true;
  } catch {
    // Không đọc được lưu trữ thì cứ đếm: thà tính hơi rộng còn hơn không tính.
    return true;
  }
}

function releaseOwnership(questType: QuestType, tabId: string): void {
  try {
    const raw = window.localStorage.getItem(ownerKey(questType));
    if (raw && raw.startsWith(`${tabId}|`)) {
      window.localStorage.removeItem(ownerKey(questType));
    }
  } catch {
    // Không sao: bản ghi sẽ tự cũ đi và tab khác giành được quyền đếm.
  }
}

/**
 * Đo thời gian thật để các nhiệm vụ tính theo phút chạy đúng.
 *
 * <p>Gắn `questType="READ_MINUTES"` ở trang đọc chương và `"ONLINE_MINUTES"`
 * một lần ở khung trang. Hai bộ đếm độc lập nhau và không cộng chéo.
 */
export function QuestTimeTracker({ questType }: Readonly<{ questType: QuestType }>) {
  useEffect(() => {
    if (!isLoggedIn()) return undefined;

    const tabId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
    let lastActivity = Date.now();
    let lastTick = Date.now();
    let sinceFlush = 0;
    let sending = false;
    let stopped = false;

    const markActive = () => {
      lastActivity = Date.now();
    };

    // Đủ rộng để việc đọc yên lặng vẫn được ghi nhận: cuộn, lăn chuột, chạm,
    // gõ phím, và cả lúc quay lại tab đều tính là còn ở đây.
    const events: Array<keyof WindowEventMap> = [
      "pointerdown",
      "pointermove",
      "keydown",
      "scroll",
      "wheel",
      "touchmove",
      "focus",
    ];
    for (const event of events) {
      window.addEventListener(event, markActive, { passive: true });
    }
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        // Quay lại tab là một hành động. Nhưng mốc nhịp cũng phải đặt lại, kẻo
        // khoảng thời gian tab bị ẩn lại bị cộng vào.
        lastActivity = Date.now();
        lastTick = Date.now();
      }
    };
    document.addEventListener("visibilitychange", onVisible);

    /** Gửi các phút tròn đã tích được, giữ lại phần lẻ cho lần sau. */
    async function flush(): Promise<void> {
      if (sending || stopped) return;
      const pending = readBucket(questType);
      const minutes = Math.floor(pending / MS_PER_MINUTE);
      if (minutes < 1) return;

      const sendNow = Math.min(minutes, MAX_MINUTES_PER_REQUEST);
      sending = true;
      try {
        const result = await reportQuestProgress(questType, sendNow);
        if (result === null) return; // Máy chủ không nhận: giữ nguyên, gửi lại sau.
        // Trừ đúng phần đã gửi, đọc lại từ đầu phòng khi nhịp khác vừa cộng thêm.
        writeBucket(questType, Math.max(0, readBucket(questType) - sendNow * MS_PER_MINUTE));
      } finally {
        sending = false;
      }
    }

    const timer = window.setInterval(() => {
      const now = Date.now();
      const gap = now - lastTick;
      lastTick = now;

      if (document.visibilityState !== "visible") return;
      if (!claimOwnership(questType, tabId)) return;
      if (now - lastActivity > IDLE_AFTER_MS) return;
      // Máy vừa ngủ dậy: khoảng nhảy không phải thời gian đọc.
      if (gap <= 0 || gap > MAX_TICK_GAP_MS) return;

      writeBucket(questType, readBucket(questType) + gap);

      sinceFlush += gap;
      if (sinceFlush >= FLUSH_EVERY_MS) {
        sinceFlush = 0;
        void flush();
      }
    }, TICK_MS);

    // Phần chưa gửi của lần trước vẫn nằm trong localStorage - gửi ngay khi mở.
    void flush();

    return () => {
      stopped = true;
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
      for (const event of events) {
        window.removeEventListener(event, markActive);
      }
      releaseOwnership(questType, tabId);
    };
  }, [questType]);

  return null;
}

/**
 * Nhiệm vụ "Đọc 3 chương".
 *
 * <p>Không phải viết thêm cho đủ bộ: loại {@code READ_CHAPTERS} có trong danh
 * mục nhiệm vụ và hiện trên trang, nhưng không chỗ nào trong giao diện báo
 * tiến độ cho nó cả — nên nó đứng yên ở 0/3 dù người dùng đọc bao nhiêu
 * chương đi nữa.
 *
 * <p>Mỗi chương chỉ được tính một lần trong ngày. Tải lại cùng một chương ba
 * lần không phải là đọc ba chương.
 */
export function QuestChapterCounter({ chapterKey }: Readonly<{ chapterKey: string }>) {
  useEffect(() => {
    if (!isLoggedIn() || !chapterKey) return;

    const storeKey = `quest-chapters:${questDay()}`;
    let seen: string[] = [];
    try {
      const raw = window.localStorage.getItem(storeKey);
      const parsed: unknown = raw === null ? [] : JSON.parse(raw);
      seen = Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === "string") : [];
    } catch {
      seen = [];
    }
    if (seen.includes(chapterKey)) return;

    void reportQuestProgress("READ_CHAPTERS", 1).then((result) => {
      // Chỉ ghi nhớ khi máy chủ nhận. Ghi trước rồi gửi hỏng thì chương đó mất
      // luôn, không bao giờ được tính lại trong ngày.
      if (result === null) return;
      try {
        window.localStorage.setItem(storeKey, JSON.stringify([...seen, chapterKey].slice(-200)));
      } catch {
        // Không lưu được thì cùng lắm là đếm rộng tay một chút.
      }
    });
  }, [chapterKey]);

  return null;
}
