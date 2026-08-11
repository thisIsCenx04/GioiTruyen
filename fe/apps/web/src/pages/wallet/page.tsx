import { Link } from "react-router-dom";

import { PublicShell } from "@/components/site-chrome";
import { TopupWorkspace } from "@/components/topup-workspace";
import { WalletBalance } from "@/components/wallet-balance";

export default function WalletPage() {
  return (
    <PublicShell>
      <main className="walletPageShell">
        <header className="walletHeader">
          <div>
            <p className="detailEyebrow">Tài khoản đọc · Ví cá nhân</p>
            <h1>Nạp xu, giữ nhịp đọc</h1>
          </div>
          <Link to="/library">Trở lại kệ truyện</Link>
        </header>

        <WalletBalance />
        <TopupWorkspace />
      </main>
    </PublicShell>
  );
}
