import "@testing-library/jest-dom/vitest";

import { HttpResponse, http } from "msw";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { TeamWorkspace } from "./team-workspace";

const team = {
  description: "Nhóm biên tập truyện dài.",
  id: "team-01",
  name: "Bàn viết phương Nam",
  slug: "ban-viet-phuong-nam",
  state: "ACTIVE",
  version: 2,
};

function commonHandlers() {
  return [
    http.get("/api/workspace/teams/team-01", () => HttpResponse.json(team)),
    http.get("/api/workspace/teams/team-01/follow", () =>
      HttpResponse.json({
        followerCount: 18,
        following: false,
        teamId: "team-01",
      }),
    ),
  ];
}

describe("team workspace", () => {
  it("lets an owner inspect members and update versioned permissions", async () => {
    apiMockServer.use(
      ...commonHandlers(),
      http.get("/api/workspace/teams/team-01/members", () =>
        HttpResponse.json([
          {
            joinedAt: "2026-07-24T00:00:00Z",
            permissions: ["story:create"],
            role: "MEMBER",
            state: "ACTIVE",
            teamId: "team-01",
            userId: "user-02",
            version: 3,
          },
        ]),
      ),
      http.patch(
        "/api/workspace/teams/team-01/members/user-02/permissions",
        async ({ request }) => {
          await expect(request.json()).resolves.toMatchObject({ version: 3 });
          return HttpResponse.json({
            joinedAt: "2026-07-24T00:00:00Z",
            permissions: ["story:create", "story:edit"],
            role: "MEMBER",
            state: "ACTIVE",
            teamId: "team-01",
            userId: "user-02",
            version: 4,
          });
        },
      ),
    );
    const user = userEvent.setup();
    render(createElement(TeamWorkspace, { teamId: "team-01" }));

    expect(
      await screen.findByRole("heading", { name: "Bàn viết phương Nam" }),
    ).toBeInTheDocument();
    const memberRow = screen.getByText("user-02").closest("article");
    expect(memberRow).not.toBeNull();
    await user.click(
      within(memberRow!).getByRole("checkbox", { name: "Sửa nội dung" }),
    );
    await user.click(
      within(memberRow!).getByRole("button", { name: "Lưu quyền" }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Đã cập nhật quyền thành viên.",
    );
    expect(screen.getByText(/phiên bản 4/u)).toBeInTheDocument();
  });

  it("keeps management controls hidden for a non-owner", async () => {
    apiMockServer.use(
      ...commonHandlers(),
      http.get("/api/workspace/teams/team-01/members", () =>
        HttpResponse.json(
          {
            code: "TEAM_OWNER_REQUIRED",
            status: 403,
            title: "Owner required",
            type: "about:blank",
          },
          { status: 403 },
        ),
      ),
    );
    render(createElement(TeamWorkspace, { teamId: "team-01" }));

    expect(
      await screen.findByRole("heading", { name: "Bàn viết phương Nam" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/chế độ thành viên hoặc người theo dõi/u),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: /Mời một cộng sự/u }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Lưu thông tin" }),
    ).not.toBeInTheDocument();
  });

  it("follows a team from the workspace", async () => {
    apiMockServer.use(
      ...commonHandlers(),
      http.get("/api/workspace/teams/team-01/members", () =>
        HttpResponse.json([]),
      ),
      http.put("/api/workspace/teams/team-01/follow", () =>
        HttpResponse.json({
          followerCount: 19,
          following: true,
          teamId: "team-01",
        }),
      ),
    );
    const user = userEvent.setup();
    render(createElement(TeamWorkspace, { teamId: "team-01" }));

    await user.click(
      await screen.findByRole("button", { name: /Theo dõi nhóm/u }),
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Đã theo dõi nhóm.",
    );
    expect(
      screen.getByRole("button", { name: /Đang theo dõi/u }),
    ).toBeInTheDocument();
  });
});
