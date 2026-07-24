import "@testing-library/jest-dom/vitest";

import { HttpResponse, http } from "msw";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { PublishingWorkspace } from "./publishing-workspace";

const story = {
  categoryIds: ["category-01"],
  completionStatus: "ONGOING",
  coverAssetId: null,
  currentRevision: "story-revision-01",
  id: "story-01",
  language: "vi",
  origin: "ORIGINAL",
  revisionNo: 1,
  slug: "mua-dem",
  synopsis: "Một câu chuyện trong mưa.",
  teamId: "team-01",
  title: "Mưa đêm",
  updatedAt: "2026-07-24T00:00:00Z",
  version: 1,
  workflowStatus: "DRAFT",
};

const chapter = {
  contentHtml: "<p>Trang đầu tiên.</p>",
  currentRevision: "chapter-revision-01",
  id: "chapter-01",
  number: 1,
  revisionNo: 1,
  slug: "chapter-1",
  storyId: "story-01",
  teamId: "team-01",
  title: "Khởi đầu",
  updatedAt: "2026-07-24T00:00:00Z",
  version: 1,
  wordCount: 3,
  workflowStatus: "DRAFT",
};

function workspaceHandlers() {
  return [
    http.get("/api/workspace/teams/team-01/stories", () =>
      HttpResponse.json([story]),
    ),
    http.get(
      "/api/workspace/teams/team-01/stories/story-01/chapters",
      () => HttpResponse.json([chapter]),
    ),
    http.get("/api/catalog/categories", () =>
      HttpResponse.json({
        groups: [
          {
            categories: [{ id: "category-01", name: "Kỳ ảo", slug: "ky-ao" }],
            group: "genre",
            label: "Thể loại",
          },
        ],
        version: "v1",
      }),
    ),
  ];
}

describe("publishing workspace", () => {
  it("loads the story, current chapter revision and workflow status", async () => {
    apiMockServer.use(...workspaceHandlers());

    render(createElement(PublishingWorkspace, { teamId: "team-01" }));

    expect(
      await screen.findByRole("heading", { name: "Mưa đêm" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Tên chương")).toHaveValue("Khởi đầu");
    expect(screen.getByLabelText("Nội dung HTML")).toHaveValue(
      "<p>Trang đầu tiên.</p>",
    );
    expect(screen.getByText("Story v1")).toBeInTheDocument();
  });

  it("autosaves a chapter with optimistic concurrency", async () => {
    let autosaved = false;
    apiMockServer.use(
      ...workspaceHandlers(),
      http.patch(
        "/api/workspace/teams/team-01/stories/story-01/chapters/chapter-01",
        async ({ request }) => {
          expect(request.headers.get("if-match")).toBe('"1"');
          await expect(request.json()).resolves.toMatchObject({
            title: "Khởi đầu mới",
          });
          autosaved = true;
          return HttpResponse.json({
            ...chapter,
            currentRevision: "chapter-revision-02",
            revisionNo: 2,
            title: "Khởi đầu mới",
            version: 2,
          });
        },
      ),
    );
    const user = userEvent.setup();
    render(createElement(PublishingWorkspace, { teamId: "team-01" }));

    const title = await screen.findByLabelText("Tên chương");
    await user.clear(title);
    await user.type(title, "Khởi đầu mới");

    await waitFor(
      () => {
        expect(autosaved).toBe(true);
        expect(screen.getByText("Đã lưu an toàn")).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });
});
