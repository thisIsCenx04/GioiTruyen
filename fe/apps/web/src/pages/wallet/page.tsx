"use client";

import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";
import { TopupWorkspace } from "@/components/topup-workspace";
import { WalletBalance } from "@/components/wallet-balance";
import { WalletTransactionHistory } from "@/components/wallet-transaction-history";
import { WithdrawalWorkspace } from "@/components/withdrawal-workspace";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";
import { isAdminUser, isLoggedIn } from "@/lib/auth";

type WalletTab = "topup" | "withdraw" | "ledger";
type PublishingAccess = {
  memberRole: string | null;
};

export default function WalletPage() {
  const [activeTab, setActiveTab] = useState<WalletTab>("topup");
  const [ownerTools, setOwnerTools] = useState(false);

  useEffect(() => {
    if (!isLoggedIn()) {
      setOwnerTools(false);
      return;
    }
    if (isAdminUser()) {
      setOwnerTools(true);
      return;
    }
    let active = true;
    void authedFetch(`${API_BASE_URL}/me/publishing`)
      .then(async (response) => {
        if (!active || !response.ok) return;
        const access = (await response.json()) as PublishingAccess;
        setOwnerTools(access.memberRole === "OWNER");
      })
      .catch(() => {
        if (active) setOwnerTools(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!ownerTools && activeTab !== "topup") {
      setActiveTab("topup");
    }
  }, [activeTab, ownerTools]);

  return (
    <PublicShell>
      <main className="walletPageShell">
        <header className="walletHeader">
          <div>
            <p className="detailEyebrow">Tài khoản đọc · Ví cá nhân</p>
            <h1>Nạp xu, rút tiền và theo dõi thu chi</h1>
          </div>
          <Link to="/library">Trở lại kệ truyện</Link>
        </header>

        <WalletBalance />

        <div className="walletTabs pubTabs" role="tablist" aria-label="Ví tiền">
          <button
            aria-selected={activeTab === "topup"}
            className={activeTab === "topup" ? "pubTab isActive" : "pubTab"}
            onClick={() => setActiveTab("topup")}
            role="tab"
            type="button"
          >
            Nạp xu
          </button>
          {ownerTools ? (
            <>
              <button
                aria-selected={activeTab === "withdraw"}
                className={activeTab === "withdraw" ? "pubTab isActive" : "pubTab"}
                onClick={() => setActiveTab("withdraw")}
                role="tab"
                type="button"
              >
                Rút tiền
              </button>
              <button
                aria-selected={activeTab === "ledger"}
                className={activeTab === "ledger" ? "pubTab isActive" : "pubTab"}
                onClick={() => setActiveTab("ledger")}
                role="tab"
                type="button"
              >
                Lịch sử thu chi
              </button>
            </>
          ) : null}
        </div>

        {activeTab === "topup" ? <TopupWorkspace /> : null}
        {activeTab === "withdraw" && ownerTools ? <WithdrawalWorkspace /> : null}
        {activeTab === "ledger" && ownerTools ? <WalletTransactionHistory /> : null}
      </main>
    </PublicShell>
  );
}
