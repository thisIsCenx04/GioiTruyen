"use client";

import { Check, Gem, Gift, Loader2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { XuIcon, NgocIcon } from "@/components/currency-icons";
import { PublicShell } from "@/components/site-chrome";
import { isLoggedIn } from "@/lib/auth";
import {
  claimQuestReward,
  loadQuestBoard,
  loadQuestPreview,
  type QuestBoard,
  type QuestProgress,
} from "@/lib/quests";

export default function QuestsPage() {
  const navigate = useNavigate();
  const [board, setBoard] = useState<QuestBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [claiming, setClaiming] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [showLoginPrompt, setShowLoginPrompt] = useState(false);
  const signedIn = isLoggedIn();

  const refresh = useCallback(async () => {
    const next = await loadQuestBoard();
    if (!next) {
      // The board is per-account, so an unusable session means signing in again.
      navigate(`/login?returnTo=${encodeURIComponent("/quests")}`, { replace: true });
      return;
    }
    setBoard(next);
    setLoading(false);
  }, [navigate]);

  useEffect(() => {
    // A guest still sees the quest list - they just cannot claim anything, so
    // the page is a preview rather than a locked door.
    if (!isLoggedIn()) {
      void loadQuestPreview().then((preview) => {
        setBoard(preview);
        setLoading(false);
      });
      return;
    }
    void refresh();
  }, [refresh]);

  async function claim(quest: QuestProgress) {
    // Rewards belong to an account, so an anonymous reader is asked to sign in
    // at the moment it matters rather than being blocked from the page.
    if (!isLoggedIn()) {
      setShowLoginPrompt(true);
      return;
    }
    setClaiming(quest.questId);
    setError("");
    setNotice("");
    try {
      const result = await claimQuestReward(quest.questId);
      const parts = [
        result.rewardCoin > 0 ? `${result.rewardCoin} xu` : "",
        result.rewardGem > 0 ? `${result.rewardGem} ngọc` : "",
      ].filter(Boolean);
      setNotice(`Đã nhận ${parts.join(" và ")} từ "${quest.title}".`);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không nhận được thưởng.");
    } finally {
      setClaiming("");
    }
  }

  if (loading) {
    return (
      <PublicShell>
        <main className="questPageShell">
          <p className="questLoading"><Loader2 aria-hidden="true" /> Đang tải nhiệm vụ…</p>
        </main>
      </PublicShell>
    );
  }

  const quests = board?.quests ?? [];
  const claimable = quests.filter((quest) => quest.completed && !quest.claimed).length;

  return (
    <PublicShell>
      <main className="questPageShell">
        <header className="questHeader">
          <div>
            <p className="detailEyebrow">Nhiệm vụ hằng ngày</p>
            <h1>Nhiệm vụ của tôi</h1>
            <p className="questHeaderNote">
              Nhiệm vụ làm mới mỗi ngày. Đọc truyện và tương tác để nhận xu và ngọc.
            </p>
          </div>
          <div className="questSummary">
            <strong>{board?.completedCount ?? 0}/{board?.totalCount ?? 0}</strong>
            <span>đã hoàn thành</span>
            {claimable > 0 ? <em>{claimable} phần thưởng chờ nhận</em> : null}
          </div>
        </header>

        {!signedIn ? (
          <p className="questGuestBanner">
            Bạn đang xem trước. <Link to={`/login?returnTo=${encodeURIComponent("/quests")}`}>Đăng nhập</Link>
            {" "}để bắt đầu tích tiến độ và nhận thưởng.
          </p>
        ) : null}
        {notice ? <p className="questNotice" role="status">{notice}</p> : null}
        {error ? <p className="questError" role="alert">{error}</p> : null}

        <ul className="questList">
          {quests.map((quest) => {
            const percent = Math.min(
              100,
              Math.round((quest.progressValue / Math.max(1, quest.targetValue)) * 100),
            );
            return (
              <li className={quest.claimed ? "questCard isClaimed" : "questCard"} key={quest.questId}>
                <div className="questCardMain">
                  <strong>{quest.title}</strong>
                  {quest.description ? <small>{quest.description}</small> : null}
                  <div className="questProgressBar" aria-hidden="true">
                    <span style={{ width: `${percent}%` }} />
                  </div>
                  <span className="questProgressText">
                    {quest.progressValue}/{quest.targetValue}
                  </span>
                </div>

                <div className="questCardSide">
                  <span className="questReward" style={{ display: "inline-flex", alignItems: "center", gap: "0.4rem" }}>
                    {quest.rewardCoin > 0 ? <b style={{ display: "inline-flex", alignItems: "center", gap: "0.2rem" }}>{quest.rewardCoin} <XuIcon size={16} /></b> : null}
                    {quest.rewardGem > 0
                      ? <b style={{ display: "inline-flex", alignItems: "center", gap: "0.2rem" }}>{quest.rewardGem} <NgocIcon size={16} /></b>
                      : null}
                  </span>
                  {quest.claimed ? (
                    <span className="questClaimed"><Check aria-hidden="true" size={14} /> Đã nhận</span>
                  ) : (
                    <button
                      // A guest can always press it; the prompt explains why.
                      disabled={signedIn && (!quest.completed || claiming === quest.questId)}
                      onClick={() => void claim(quest)}
                      type="button"
                    >
                      <Gift aria-hidden="true" size={14} />
                      {claiming === quest.questId ? "Đang nhận…" : "Nhận thưởng"}
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>

        {showLoginPrompt ? (
          <div className="questLoginPrompt" role="dialog" aria-modal="true" aria-labelledby="quest-login-title">
            <button
              aria-label="Đóng"
              className="questLoginScrim"
              onClick={() => setShowLoginPrompt(false)}
              type="button"
            />
            <div className="questLoginCard">
              <h2 id="quest-login-title">Đăng nhập để nhận thưởng</h2>
              <p>
                Bạn vẫn đọc truyện miễn phí thoải mái. Nhưng để tích tiến độ nhiệm vụ
                và nhận xu, ngọc thì cần một tài khoản.
              </p>
              <div className="questLoginActions">
                <Link
                  className="questLoginPrimary"
                  to={`/login?returnTo=${encodeURIComponent("/quests")}`}
                >
                  Đăng nhập
                </Link>
                <Link
                  className="questLoginSecondary"
                  to={`/register?returnTo=${encodeURIComponent("/quests")}`}
                >
                  Đăng ký
                </Link>
                <button onClick={() => setShowLoginPrompt(false)} type="button">Để sau</button>
              </div>
            </div>
          </div>
        ) : null}
      </main>
    </PublicShell>
  );
}
