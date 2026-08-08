import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { DonationJourney } from "./donation-journey";

const teamId = "30000000-0000-4000-8000-000000000001";

describe("donation journey", () => {
  it("confirms and posts an idempotent XU-only donation", async () => {
    let idempotencyKey = "";
    apiMockServer.use(
      http.get("/api/workspace/wallets/me", () =>
        HttpResponse.json({
          asOf: "2026-07-25T00:00:00Z",
          availableXu: 2_000,
          currency: "XU",
          reservedXu: 0,
          version: 2,
        })),
      http.post("/api/workspace/donations", async ({ request }) => {
        idempotencyKey = request.headers.get("idempotency-key") ?? "";
        await expect(request.json()).resolves.toEqual({
          amountXu: 1_000,
          message: "Mong chờ chương mới!",
          teamId,
        });
        return HttpResponse.json({
          amountXu: 1_000,
          createdAt: "2026-07-25T01:00:00Z",
          donationId: "40000000-0000-4000-8000-000000000001",
          ledgerTransactionId: "50000000-0000-4000-8000-000000000001",
          message: "Mong chờ chương mới!",
          replayed: false,
          status: "POSTED",
          teamId,
        }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render(<DonationJourney storyTitle="Đèn khuya" teamId={teamId} />);

    await user.click(screen.getByRole("button", {
      name: "Ủng hộ đội ngũ bằng XU",
    }));
    expect(await screen.findByText("2.000 XU")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "1.000" }));
    await user.type(
      screen.getByPlaceholderText("Cảm ơn vì câu chuyện này…"),
      "Mong chờ chương mới!",
    );
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(screen.getByText(/không thể hoàn tác/u)).toBeInTheDocument();
    await user.click(screen.getByRole("button", {
      name: "Xác nhận 1.000 XU",
    }));

    expect(await screen.findByText("1.000 XU đã được gửi.")).toBeInTheDocument();
    expect(screen.getByText(/Đèn khuya/u)).toBeInTheDocument();
    expect(idempotencyKey).toMatch(/^[0-9a-f-]{36}$/u);
  });

  it("offers a wallet top-up after an insufficient-balance rejection", async () => {
    apiMockServer.use(
      http.get("/api/workspace/wallets/me", () =>
        HttpResponse.json({
          asOf: "2026-07-25T00:00:00Z",
          availableXu: 1_000,
          currency: "XU",
          reservedXu: 0,
          version: 2,
        })),
      http.post("/api/workspace/donations", () =>
        HttpResponse.json({
          code: "DONATION_INSUFFICIENT_BALANCE",
          detail: "Insufficient available XU.",
          status: 422,
          title: "Donation rejected",
          type: "about:blank",
        }, { status: 422 })),
    );
    const user = userEvent.setup();
    render(<DonationJourney storyTitle="Đèn khuya" teamId={teamId} />);

    await user.click(screen.getByRole("button", {
      name: "Ủng hộ đội ngũ bằng XU",
    }));
    await screen.findByText("1.000 XU");
    await user.click(screen.getByRole("button", { name: "500" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    await user.click(screen.getByRole("button", {
      name: "Xác nhận 500 XU",
    }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Số dư khả dụng chưa đủ",
    );
    expect(screen.getByRole("link", { name: "Nạp thêm XU" }))
      .toHaveAttribute("href", "/wallet");
  });
});
