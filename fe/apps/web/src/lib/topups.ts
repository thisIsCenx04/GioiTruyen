import { getAccessToken, refreshAccessToken } from "./auth";

const API = "/api/v1";

export type DepositPackage = {
  id: string;
  name: string;
  priceVnd: number;
  coinAmount: number;
  gemAmount: number;
  bonusCoin: number;
  bonusGem: number;
};

export type PaymentMethodView = {
  id: string;
  name: string;
  type: "BANK_TRANSFER" | "OTHER" | "PAYPAL" | "QR";
  config: Record<string, string>;
  instructions: string | null;
};

export type TopupInstruction = {
  paymentId: string;
  transactionCode: string;
  amountVnd: number;
  coinAmount: number;
  gemAmount: number;
  methodName: string;
  methodType: string;
  accountName: string;
  accountNumber: string;
  bankName: string;
  transferNote: string;
  qrImageUrl: string | null;
  qrPayload: string | null;
  paypalLink: string;
  instructions: string | null;
  status: string;
};

export type TopupHistoryRow = {
  id: string;
  transactionCode: string;
  amountVnd: number;
  coinReceived: number;
  gemReceived: number;
  methodName: string | null;
  status: string;
  createdAt: string;
  paidAt: string | null;
};

async function authed(path: string, init?: RequestInit) {
  const send = (token: string | null) => {
    const headers = new Headers(init?.headers);
    headers.set("Accept", "application/json");
    if (init?.body) headers.set("Content-Type", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${API}${path}`, { ...init, headers });
  };
  let response = await send(getAccessToken());
  if (response.status === 401) {
    const renewed = await refreshAccessToken();
    if (renewed) response = await send(renewed);
  }
  return response;
}

/** Both lists are public, so the price table renders for signed-out visitors. */
export async function loadDepositPackages(): Promise<DepositPackage[]> {
  const response = await fetch(`${API}/deposit-packages`, { headers: { Accept: "application/json" } });
  return response.ok ? ((await response.json()) as DepositPackage[]) : [];
}

export async function loadPaymentMethods(): Promise<PaymentMethodView[]> {
  const response = await fetch(`${API}/payment-methods`, { headers: { Accept: "application/json" } });
  return response.ok ? ((await response.json()) as PaymentMethodView[]) : [];
}

export async function createTopup(packageId: string, methodId: string): Promise<TopupInstruction> {
  const response = await authed("/topups", {
    body: JSON.stringify({ methodId, packageId }),
    method: "POST",
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(problem?.detail ?? "Không tạo được yêu cầu nạp. Vui lòng thử lại.");
  }
  return (await response.json()) as TopupInstruction;
}

/** Polled while the reader waits for an admin to confirm the transfer. */
export async function loadTopup(paymentId: string): Promise<TopupInstruction | null> {
  const response = await authed(`/topups/${paymentId}`);
  return response.ok ? ((await response.json()) as TopupInstruction) : null;
}

export async function loadTopupHistory(): Promise<TopupHistoryRow[]> {
  const response = await authed("/topups");
  return response.ok ? ((await response.json()) as TopupHistoryRow[]) : [];
}
