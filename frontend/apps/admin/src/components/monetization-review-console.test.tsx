import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { MonetizationReviewConsole } from "./monetization-review-console";

const topupId = "10000000-0000-4000-8000-000000000001";
const withdrawalId = "70000000-0000-4000-8000-000000000001";
const grantToken = "g".repeat(43);

function killSwitchHandler() {
  return http.get(
    "/api/monetization/admin/configuration/monetization-kill-switches",
    () => HttpResponse.json([
      {
        changedAt: "2026-07-25T00:00:00Z",
        changedBy: "system",
        engaged: false,
        operation: "TOPUP_CREDIT",
        version: 0,
      },
      {
        changedAt: "2026-07-25T00:00:00Z",
        changedBy: "system",
        engaged: false,
        operation: "WITHDRAWAL_REQUEST",
        version: 0,
      },
      {
        changedAt: "2026-07-25T00:00:00Z",
        changedBy: "system",
        engaged: true,
        operation: "WITHDRAWAL_PAYOUT",
        version: 1,
      },
    ]),
  );
}

describe("monetization review console", () => {
  it("re-authenticates and approves a top-up with masked evidence", async () => {
    apiMockServer.use(
      killSwitchHandler(),
      http.post("/api/auth/reauth/grants", async ({ request }) => {
        await expect(request.json()).resolves.toMatchObject({
          password: "correct password",
          scope: "TOPUP_MANUAL_APPROVAL",
          targetId: topupId,
          targetType: "topup",
        });
        return HttpResponse.json({
          expiresAt: "2026-07-25T00:05:00Z",
          expiresIn: 300,
          grantToken,
          grantType: "Scoped-Reauthentication",
        });
      }),
      http.post(
        `/api/monetization/admin/topups/${topupId}/approve`,
        async ({ request }) => {
          expect(request.headers.get("scoped-reauthentication")).toBe(grantToken);
          await expect(request.json()).resolves.toEqual({
            evidenceReference: "reconciliation/case-0001",
            reason: "Provider evidence and amount were verified.",
          });
          return HttpResponse.json({
            decidedAt: "2026-07-25T00:01:00Z",
            ledgerTransactionId: "20000000-0000-4000-8000-000000000001",
            paymentEventId: "30000000-0000-4000-8000-000000000001",
            status: "CREDITED",
            topupId,
          });
        },
      ),
    );
    const user = userEvent.setup();
    render(<MonetizationReviewConsole />);

    await screen.findByText("Chi trả withdrawal");
    expect(screen.getByText("Đang khóa")).toBeInTheDocument();
    await user.type(screen.getByLabelText("ID hồ sơ nội bộ"), topupId);
    await user.type(
      screen.getByLabelText("Tham chiếu bằng chứng đối soát"),
      "reconciliation/case-0001",
    );
    await user.type(
      screen.getByLabelText("Lý do quyết định"),
      "Provider evidence and amount were verified.",
    );
    await user.type(
      screen.getByLabelText("Mật khẩu hiện tại"),
      "correct password",
    );
    await user.click(screen.getByRole("button", {
      name: "Ghi quyết định có kiểm soát",
    }));

    expect(await screen.findByRole("status")).toHaveTextContent("CREDITED");
  });

  it("uses a one-time grant and idempotency key to reject a withdrawal", async () => {
    let suppliedKey = "";
    apiMockServer.use(
      killSwitchHandler(),
      http.post("/api/auth/reauth/grants", async ({ request }) => {
        await expect(request.json()).resolves.toMatchObject({
          scope: "WITHDRAWAL_APPROVAL",
          targetId: withdrawalId,
          targetType: "withdrawal",
        });
        return HttpResponse.json({
          expiresAt: "2026-07-25T00:05:00Z",
          expiresIn: 300,
          grantToken,
          grantType: "Scoped-Reauthentication",
        });
      }),
      http.post(
        `/api/monetization/admin/withdrawals/${withdrawalId}/reject`,
        async ({ request }) => {
          suppliedKey = request.headers.get("idempotency-key") ?? "";
          expect(request.headers.get("scoped-reauthentication")).toBe(grantToken);
          await expect(request.json()).resolves.toEqual({
            reason: "Destination ownership could not be verified.",
          });
          return HttpResponse.json({
            reason: "Destination ownership could not be verified.",
            replayed: false,
            reviewedAt: "2026-07-25T00:01:00Z",
            reviewerId: "90000000-0000-4000-8000-000000000001",
            riskLevel: "STANDARD",
            riskRuleVersion: "risk-v1",
            state: "REJECTED",
            withdrawalId,
          });
        },
      ),
    );
    const user = userEvent.setup();
    render(<MonetizationReviewConsole />);

    await user.click(await screen.findByRole("button", {
      name: "Withdrawal chờ duyệt",
    }));
    await user.click(screen.getByLabelText("Từ chối"));
    await user.type(screen.getByLabelText("ID hồ sơ nội bộ"), withdrawalId);
    await user.type(
      screen.getByLabelText("Lý do quyết định"),
      "Destination ownership could not be verified.",
    );
    await user.type(screen.getByLabelText("Mật khẩu hiện tại"), "password");
    await user.click(screen.getByRole("button", {
      name: "Ghi quyết định có kiểm soát",
    }));

    expect(await screen.findByRole("status")).toHaveTextContent("REJECTED");
    expect(suppliedKey).toMatch(/^[0-9a-f-]{36}$/u);
  });

  it("rejects an unmatched top-up with a coded reconciliation reason", async () => {
    apiMockServer.use(
      killSwitchHandler(),
      http.post(
        `/api/monetization/admin/topups/${topupId}/reject`,
        async ({ request }) => {
          await expect(request.json()).resolves.toEqual({
            evidenceReference: "reconciliation/case-0002",
            reason: "The bank reference could not be verified.",
            reasonCode: "REFERENCE_UNVERIFIABLE",
          });
          return HttpResponse.json({
            paymentEventId: "30000000-0000-4000-8000-000000000002",
            replayed: false,
            status: "REJECTED",
            topupId,
          });
        },
      ),
    );
    const user = userEvent.setup();
    render(<MonetizationReviewConsole />);

    await user.click(screen.getByLabelText("Từ chối"));
    await user.type(screen.getByLabelText("ID hồ sơ nội bộ"), topupId);
    await user.type(
      screen.getByLabelText("Tham chiếu bằng chứng đối soát"),
      "reconciliation/case-0002",
    );
    await user.selectOptions(
      screen.getByLabelText("Mã lý do"),
      "REFERENCE_UNVERIFIABLE",
    );
    await user.type(
      screen.getByLabelText("Lý do quyết định"),
      "The bank reference could not be verified.",
    );
    expect(screen.queryByLabelText("Mật khẩu hiện tại")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", {
      name: "Ghi quyết định có kiểm soát",
    }));

    expect(await screen.findByRole("status")).toHaveTextContent("REJECTED");
  });
});
