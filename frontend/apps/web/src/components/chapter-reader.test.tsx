import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { ChapterReader } from "./chapter-reader";

const chapter = {
  contentHtml: "<p>Đêm xuống trên thành cổ.</p><p>Ngọn đèn vẫn sáng.</p>",
  etag: "a".repeat(64),
  id: "30000000-0000-4000-8000-000000000001",
  next: {
    id: "30000000-0000-4000-8000-000000000002",
    number: 2,
    slug: "chuong-hai",
    title: "Qua miền sương",
  },
  number: 1,
  previous: null,
  publishedAt: "2026-07-24T00:00:00Z",
  revisionId: "70000000-0000-4000-8000-000000000001",
  revisionNo: 3,
  slug: "dem-thanh-co",
  storyId: "20000000-0000-4000-8000-000000000001",
  title: "Đêm thành cổ",
  version: 1,
  wordCount: 1250,
} as const;

describe("chapter reader", () => {
  it("renders readable content, navigation and persistent controls", async () => {
    apiMockServer.use(
      http.post("/api/reading-sessions", () =>
        HttpResponse.json({
          expiresAt: "2026-07-24T00:30:00Z",
          heartbeatIntervalSeconds: 15,
          sessionId: "40000000-0000-4000-8000-000000000001",
          sessionToken: "signed-token",
        }, { status: 201 })),
      http.get("/api/catalog/comments", () =>
        HttpResponse.json({
          hasMore: false,
          items: [],
          nextCursor: null,
        })),
    );
    const user = userEvent.setup();
    render(<ChapterReader chapter={chapter} />);

    expect(screen.getByRole("heading", { name: "Đêm thành cổ" }))
      .toBeInTheDocument();
    expect(screen.getByText("Đêm xuống trên thành cổ."))
      .toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: "Điều hướng chương" }))
      .toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Tăng cỡ chữ" }));
    expect(screen.getByRole("status", { name: "Cỡ chữ" }))
      .toHaveTextContent("21");
    await user.click(screen.getByRole("button", { name: "Ban đêm" }));
    expect(screen.getByRole("button", { name: "Nền giấy" }))
      .toHaveAttribute("aria-pressed", "true");
  });
});
