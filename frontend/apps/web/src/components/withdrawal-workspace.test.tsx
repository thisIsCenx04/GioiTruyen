import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { WithdrawalWorkspace } from "./withdrawal-workspace";

const teamId = "30000000-0000-4000-8000-000000000001";
const destinationId = "60000000-0000-4000-8000-000000000001";

const paid = {
  createdAt: "2026-07-20T01:00:00Z",
  destinationMasked: "VCB ···· 1234",
  feeRuleVersion: "fee-v1",
  feeXu: 20_000,
  grossAmountXu: 200_000,
  id: "70000000-0000-4000-8000-000000000001",
  netAmountXu: 180_000,
  replayed: false,
  state: "PAID",
  teamId,
} as const;

describe("withdrawal workspace", () => {
  it("paginates history and creates an idempotent review request", async () => {
    let key = "";
    apiMockServer.use(
      http.get(`/api/workspace/teams/${teamId}/withdrawals`, ({ request }) => {
        const cursor = new URL(request.url).searchParams.get("cursor");
        return cursor
          ? HttpResponse.json({
              items: [{
                ...paid,
                id: "70000000-0000-4000-8000-000000000002",
              }],
            })
          : HttpResponse.json({ items: [paid], nextCursor: "signed-cursor" });
      }),
      http.post(
        `/api/workspace/teams/${teamId}/withdrawals`,
        async ({ request }) => {
          key = request.headers.get("idempotency-key") ?? "";
          await expect(request.json()).resolves.toEqual({
            destinationId,
            grossAmountXu: 300_000,
          });
          return HttpResponse.json({
            createdAt: "2026-07-25T01:00:00Z",
            destinationMasked: "VCB ···· 1234",
            feeRuleVersion: "fee-v1",
            feeXu: 20_000,
            grossAmountXu: 300_000,
            id: "70000000-0000-4000-8000-000000000003",
            netAmountXu: 280_000,
            replayed: false,
            state: "PENDING_REVIEW",
            teamId,
          }, { status: 201 });
        },
      ),
    );
    const user = userEvent.setup();
    render(<WithdrawalWorkspace teamId={teamId} />);

    expect(await screen.findByText("200.000 XU")).toBeInTheDocument();
    await user.click(screen.getByRole("button", {
      name: "Mở giao dịch cũ hơn",
    }));
    expect(await screen.findAllByText("200.000 XU")).toHaveLength(2);

    await user.clear(screen.getByLabelText("Tổng XU cần rút"));
    await user.type(screen.getByLabelText("Tổng XU cần rút"), "300000");
    await user.type(
      screen.getByLabelText("Mã điểm nhận đã xác minh"),
      destinationId,
    );
    await user.click(screen.getByRole("button", {
      name: "Kiểm tra yêu cầu",
    }));
    expect(screen.getByText(/Xác nhận giữ/u)).toBeInTheDocument();
    await user.click(screen.getByRole("button", {
      name: "Xác nhận yêu cầu",
    }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Đã giữ 300.000 XU",
    );
    expect(screen.getByText("Chờ duyệt")).toBeInTheDocument();
    expect(key).toMatch(/^[0-9a-f-]{36}$/u);
  });

  it("does not expose private finance controls without permission", async () => {
    apiMockServer.use(
      http.get(`/api/workspace/teams/${teamId}/withdrawals`, () =>
        HttpResponse.json({
          code: "WITHDRAWAL_FORBIDDEN",
          status: 403,
          title: "Withdrawal request rejected",
          type: "about:blank",
        }, { status: 403 })),
    );

    render(<WithdrawalWorkspace teamId={teamId} />);

    expect(await screen.findByText(
      "Quyền finance:request là bắt buộc.",
    )).toBeInTheDocument();
    expect(screen.queryByLabelText("Tổng XU cần rút")).not.toBeInTheDocument();
  });
});
