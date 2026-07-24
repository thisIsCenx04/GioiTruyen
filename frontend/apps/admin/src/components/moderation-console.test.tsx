import "@testing-library/jest-dom/vitest";

import { HttpResponse, http } from "msw";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { ModerationConsole } from "./moderation-console";

const review = {
  assigneeId: null,
  checks: [
    {
      code: "FROZEN_EVIDENCE_VALID",
      outcome: "PASS",
      policyVersion: "publishing-precheck-v1",
      rule: "SCHEMA",
    },
  ],
  id: "80000000-0000-4000-8000-000000000001",
  leaseUntil: null,
  manualFallback: false,
  priority: 90,
  state: "OPEN",
  submittedAt: "2026-07-24T00:00:00Z",
  targetId: "40000000-0000-4000-8000-000000000001",
  targetType: "STORY",
  teamId: "20000000-0000-4000-8000-000000000001",
  version: 1,
};

const detail = {
  chapters: [
    {
      chapterId: "60000000-0000-4000-8000-000000000001",
      checksum: "b".repeat(64),
      contentHtml: "<p>Nội dung đóng băng.</p>",
      number: 1,
      plainText: "Nội dung đóng băng.",
      revisionId: "70000000-0000-4000-8000-000000000001",
      revisionNo: 2,
    },
  ],
  review,
  story: {
    categoryIds: [],
    checksum: "a".repeat(64),
    coverAssetId: null,
    language: "vi",
    origin: "ORIGINAL",
    revisionId: "50000000-0000-4000-8000-000000000001",
    revisionNo: 3,
    synopsis: "Bản truyện cần được kiểm duyệt.",
    title: "Mưa trên thành cũ",
  },
};

function handlers() {
  return [
    http.get("/api/moderation/cases", () =>
      HttpResponse.json({ items: [review], nextCursor: null }),
    ),
    http.get(`/api/moderation/cases/${review.id}`, () =>
      HttpResponse.json(detail),
    ),
  ];
}

describe("moderation console", () => {
  it("shows frozen story, chapter and precheck evidence", async () => {
    apiMockServer.use(...handlers());
    render(createElement(ModerationConsole));

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Mưa trên thành cũ",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("Nội dung đóng băng.")).toBeInTheDocument();
    expect(screen.getByText("FROZEN_EVIDENCE_VALID")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Nhận hồ sơ trong 15 phút" }),
    ).toBeInTheDocument();
  });

  it("claims with If-Match then writes an immutable decision audit", async () => {
    apiMockServer.use(
      ...handlers(),
      http.patch(`/api/moderation/cases/${review.id}`, ({ request }) => {
        expect(request.headers.get("if-match")).toBe('"1"');
        return HttpResponse.json({
          ...review,
          assigneeId: "10000000-0000-4000-8000-000000000001",
          leaseUntil: "2026-07-24T00:15:00Z",
          state: "CLAIMED",
          version: 2,
        });
      }),
      http.post(
        `/api/moderation/cases/${review.id}/decisions`,
        async ({ request }) => {
          expect(request.headers.get("if-match")).toBe('"2"');
          await expect(request.json()).resolves.toMatchObject({
            decision: "APPROVE",
            policyVersion: "publishing-policy-v1",
            reasonCode: "CONTENT_ACCEPTED",
          });
          return HttpResponse.json({
            decidedAt: "2026-07-24T00:01:00Z",
            decision: "APPROVE",
            policyVersion: "publishing-policy-v1",
            reasonCode: "CONTENT_ACCEPTED",
            reviewId: review.id,
            reviewerId: "10000000-0000-4000-8000-000000000001",
            state: "APPROVED",
            version: 3,
          });
        },
      ),
    );
    const user = userEvent.setup();
    render(createElement(ModerationConsole));

    await user.click(
      await screen.findByRole("button", {
        name: "Nhận hồ sơ trong 15 phút",
      }),
    );
    await user.click(
      await screen.findByRole("button", {
        name: "Ghi quyết định và audit",
      }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Audit đã ghi bất biến",
    );
    expect(screen.getByRole("status")).toHaveTextContent("APPROVE");
  });
});
