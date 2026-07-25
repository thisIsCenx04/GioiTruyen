import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { WalletJourney } from "./wallet-journey";

vi.mock("qrcode", () => ({
  default: { toCanvas: vi.fn(() => Promise.resolve()) },
}));

const awaiting = {
  amountVnd: 100_000,
  createdAt: "2026-07-25T01:00:00Z",
  creditedXu: 110_000,
  discountPercent: 10,
  discountVersion: 2,
  expiresAt: "2099-07-25T01:15:00Z",
  id: "10000000-0000-4000-8000-000000000001",
  qrPayload: "0002010102123857VIETQR",
  status: "AWAITING_PAYMENT",
  transferReference: "GT20260725ABCD",
} as const;

describe("wallet journey", () => {
  it("shows private balance, VietQR receipt and recent top-ups", async () => {
    apiMockServer.use(
      http.get("/api/workspace/wallets/me", () =>
        HttpResponse.json({
          asOf: "2026-07-25T01:00:00Z",
          availableXu: 125_000,
          currency: "XU",
          reservedXu: 5_000,
          version: 3,
        })),
      http.get("/api/workspace/wallets/me/topups", () =>
        HttpResponse.json([awaiting])),
      http.get(
        `/api/workspace/wallets/me/topups/${awaiting.id}`,
        () => HttpResponse.json(awaiting),
      ),
    );

    render(<WalletJourney />);

    expect(await screen.findByText("125.000")).toBeInTheDocument();
    expect(screen.getByText("GT20260725ABCD")).toBeInTheDocument();
    expect(screen.getByRole("img", {
      name: "Mã VietQR cho yêu cầu nạp tiền",
    })).toBeInTheDocument();
    expect(screen.getAllByText("Chờ thanh toán")).toHaveLength(2);
  });

  it("creates an idempotent top-up and replaces the active receipt", async () => {
    let suppliedKey = "";
    apiMockServer.use(
      http.get("/api/workspace/wallets/me", () =>
        HttpResponse.json({
          asOf: "2026-07-25T01:00:00Z",
          availableXu: 0,
          currency: "XU",
          reservedXu: 0,
          version: 0,
        })),
      http.get("/api/workspace/wallets/me/topups", () =>
        HttpResponse.json([])),
      http.post("/api/workspace/wallets/me/topups", async ({ request }) => {
        suppliedKey = request.headers.get("idempotency-key") ?? "";
        await expect(request.json()).resolves.toEqual({ amountVnd: 200_000 });
        return HttpResponse.json({
          ...awaiting,
          amountVnd: 200_000,
          creditedXu: 220_000,
          id: "10000000-0000-4000-8000-000000000002",
          transferReference: "GT20260725EFGH",
        }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render(<WalletJourney />);

    await screen.findByText("Chưa có yêu cầu nạp tiền nào.");
    await user.click(screen.getByRole("button", { name: "200.000 ₫" }));
    await user.click(screen.getByRole("button", { name: "Tạo mã nạp tiền" }));

    expect(await screen.findByText("GT20260725EFGH")).toBeInTheDocument();
    expect(suppliedKey).toMatch(/^[0-9a-f-]{36}$/u);
    expect(screen.getAllByText(/200\.000/u).length).toBeGreaterThan(0);
  });
});
