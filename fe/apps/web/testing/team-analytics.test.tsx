import "@testing-library/jest-dom/vitest";

import { HttpResponse, http } from "msw";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { TeamAnalytics } from "./team-analytics";

const report = {
  from: "2026-07-19T00:00:00Z",
  period: "7D",
  reasons: [
    { code: "DUPLICATE", count: 3 },
    { code: "BOT_SIGNAL", count: 1 },
  ],
  series: [
    {
      completedViews: 12,
      invalidViews: 3,
      qualityRate: 0.75,
      rawEvents: 20,
      start: "2026-07-24T00:00:00Z",
      validViews: 9,
    },
  ],
  teamId: "team-01",
  to: "2026-07-25T12:00:00Z",
  totals: {
    completedViews: 12,
    invalidViews: 3,
    qualityRate: 0.75,
    rawEvents: 20,
    validViews: 9,
  },
};

describe("team analytics", () => {
  it("renders quality totals, chart semantics, and reason summary", async () => {
    apiMockServer.use(
      http.get(
        "/api/workspace/teams/team-01/analytics/views",
        ({ request }) => {
          expect(new URL(request.url).searchParams.get("period")).toBe("30D");
          return HttpResponse.json({ ...report, period: "30D" });
        },
      ),
    );

    render(createElement(TeamAnalytics, { teamId: "team-01" }));

    expect(await screen.findByText("75%")).toBeInTheDocument();
    expect(
      screen.getByRole("list", {
        name: "Biểu đồ lượt đọc hợp lệ và bị loại theo ngày",
      }),
    ).toHaveTextContent("9 hợp lệ, 3 bị loại");
    expect(screen.getByText("Lượt đọc trùng")).toBeInTheDocument();
    expect(screen.getByText("Dấu hiệu bot")).toBeInTheDocument();
  });

  it("changes the bounded reporting period", async () => {
    let selected = "";
    apiMockServer.use(
      http.get(
        "/api/workspace/teams/team-01/analytics/views",
        ({ request }) => {
          selected = new URL(request.url).searchParams.get("period") ?? "";
          return HttpResponse.json({ ...report, period: selected });
        },
      ),
    );
    const user = userEvent.setup();
    render(createElement(TeamAnalytics, { teamId: "team-01" }));
    await screen.findByText("75%");

    await user.click(screen.getByRole("button", { name: "7 ngày" }));

    expect(await screen.findByText("Lượt đọc trùng")).toBeInTheDocument();
    expect(selected).toBe("7D");
    expect(screen.getByRole("button", { name: "7 ngày" }))
      .toHaveAttribute("aria-pressed", "true");
  });

  it("shows permission guidance without leaking analytics", async () => {
    apiMockServer.use(
      http.get(
        "/api/workspace/teams/team-01/analytics/views",
        () => HttpResponse.json(
          {
            code: "TEAM_ANALYTICS_FORBIDDEN",
            status: 403,
            title: "Forbidden",
            type: "about:blank",
          },
          { status: 403 },
        ),
      ),
    );

    render(createElement(TeamAnalytics, { teamId: "team-01" }));

    expect(
      await screen.findByRole("heading", {
        name: "Bạn chưa được xem số liệu.",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText("75%")).not.toBeInTheDocument();
    expect(screen.getByText(/Xem phân tích/u)).toBeInTheDocument();
  });
});
