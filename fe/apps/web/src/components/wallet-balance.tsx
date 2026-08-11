"use client";

import { Gem, Coins } from "lucide-react";
import { useEffect, useState } from "react";

import { getAccessToken, isLoggedIn, refreshAccessToken } from "@/lib/auth";

const money = new Intl.NumberFormat("vi-VN");

type Wallet = { coinBalance: number; gemBalance: number };

/** Current balance. Shows nothing for a guest rather than a misleading zero. */
export function WalletBalance() {
  const [wallet, setWallet] = useState<Wallet | null>(null);

  useEffect(() => {
    if (!isLoggedIn()) return;
    const load = async () => {
      const send = (token: string | null) =>
        fetch("/api/v1/wallets/me", {
          headers: token
            ? { Accept: "application/json", Authorization: `Bearer ${token}` }
            : { Accept: "application/json" },
        });
      let response = await send(getAccessToken());
      if (response.status === 401) {
        const renewed = await refreshAccessToken();
        if (renewed) response = await send(renewed);
      }
      if (response.ok) setWallet((await response.json()) as Wallet);
    };
    void load();
  }, []);

  if (!isLoggedIn()) return null;

  return (
    <div className="walletBalanceRow">
      <article>
        <Coins aria-hidden="true" size={18} />
        <div>
          <strong>{wallet ? money.format(wallet.coinBalance) : "—"}</strong>
          <span>xu khả dụng</span>
        </div>
      </article>
      <article>
        <Gem aria-hidden="true" size={18} />
        <div>
          <strong>{wallet ? money.format(wallet.gemBalance) : "—"}</strong>
          <span>ngọc khả dụng</span>
        </div>
      </article>
    </div>
  );
}
