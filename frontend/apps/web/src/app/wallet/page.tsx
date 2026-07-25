import type { Metadata } from "next";

import { WalletJourney } from "../../components/wallet-journey";

export const metadata: Metadata = {
  description: "Theo dõi số dư XU và nạp ví an toàn bằng VietQR.",
  title: "Ví XU",
};

export default function WalletPage() {
  return <WalletJourney />;
}
