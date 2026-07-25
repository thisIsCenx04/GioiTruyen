import type { Metadata } from "next";

import { MonetizationReviewConsole } from "../../components/monetization-review-console";

export const metadata: Metadata = {
  description: "Bàn kiểm soát nạp XU và yêu cầu rút tiền.",
  title: "Kiểm soát tài chính",
};

export default function FinancePage() {
  return <MonetizationReviewConsole />;
}
