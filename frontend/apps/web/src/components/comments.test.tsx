import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { Comments } from "./comments";

const targetId = "20000000-0000-4000-8000-000000000001";
const commentId = "30000000-0000-4000-8000-000000000001";
const createdAt = "2026-07-25T05:00:00Z";

describe("comments", () => {
  it("loads public comments and creates a sanitized discussion entry", async () => {
    apiMockServer.use(
      http.get("/api/catalog/comments", () =>
        HttpResponse.json({
          hasMore: false,
          items: [{
            author: {
              avatarMediaId: null,
              displayName: "Minh",
              id: "10000000-0000-4000-8000-000000000001",
            },
            body: "Chương này rất hay.",
            createdAt,
            depth: 0,
            id: commentId,
            parentId: null,
            rootId: commentId,
            status: "VISIBLE",
            targetId,
            targetType: "STORY",
            updatedAt: createdAt,
            version: 1,
          }],
          nextCursor: null,
        })),
      http.get("/api/workspace/reactions/comment/:commentId", ({ params }) =>
        HttpResponse.json({
          active: false,
          count: 4,
          targetId: params.commentId,
          targetType: "COMMENT",
        })),
      http.put("/api/workspace/reactions/comment/:commentId", ({ params }) =>
        HttpResponse.json({
          active: true,
          count: 5,
          targetId: params.commentId,
          targetType: "COMMENT",
        })),
      http.post("/api/workspace/comments", async ({ request }) => {
        const input = await request.json() as { body: string };
        return HttpResponse.json({
          author: {
            avatarMediaId: null,
            displayName: "Tôi",
            id: "10000000-0000-4000-8000-000000000002",
          },
          body: input.body,
          createdAt,
          depth: 0,
          id: "30000000-0000-4000-8000-000000000002",
          parentId: null,
          rootId: "30000000-0000-4000-8000-000000000002",
          status: "VISIBLE",
          targetId,
          targetType: "STORY",
          updatedAt: createdAt,
          version: 1,
        }, { status: 201 });
      }),
      http.patch(
        "/api/workspace/comments/30000000-0000-4000-8000-000000000002",
        async ({ request }) => {
          const input = await request.json() as { body: string };
          return HttpResponse.json({
            author: {
              avatarMediaId: null,
              displayName: "Tôi",
              id: "10000000-0000-4000-8000-000000000002",
            },
            body: input.body,
            createdAt,
            depth: 0,
            id: "30000000-0000-4000-8000-000000000002",
            parentId: null,
            rootId: "30000000-0000-4000-8000-000000000002",
            status: "VISIBLE",
            targetId,
            targetType: "STORY",
            updatedAt: createdAt,
            version: 2,
          });
        },
      ),
      http.delete(
        "/api/workspace/comments/30000000-0000-4000-8000-000000000002",
        () => HttpResponse.json({
          author: {
            avatarMediaId: null,
            displayName: "Tôi",
            id: "10000000-0000-4000-8000-000000000002",
          },
          body: "Bình luận đã được xóa.",
          createdAt,
          depth: 0,
          id: "30000000-0000-4000-8000-000000000002",
          parentId: null,
          rootId: "30000000-0000-4000-8000-000000000002",
          status: "DELETED",
          targetId,
          targetType: "STORY",
          updatedAt: createdAt,
          version: 3,
        }),
      ),
    );
    const user = userEvent.setup();
    render(<Comments targetId={targetId} targetType="STORY" />);

    expect(await screen.findByText("Chương này rất hay.")).toBeVisible();
    const reaction = await screen.findByRole("button", {
      name: "Thích · 4",
    });
    await user.click(reaction);
    expect(await screen.findByRole("button", { name: "Bỏ thích · 5" }))
      .toHaveAttribute("aria-pressed", "true");
    await user.type(
      screen.getByLabelText("Chia sẻ cảm nhận"),
      "Mình cũng thích chương này.",
    );
    await user.click(screen.getByRole("button", { name: "Gửi bình luận" }));

    expect(await screen.findByText("Mình cũng thích chương này."))
      .toBeVisible();
    expect(screen.getByRole("button", { name: "Xóa" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Sửa" }));
    const editor = screen.getByLabelText("Sửa bình luận");
    await user.clear(editor);
    await user.type(editor, "Nội dung đã sửa.");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(await screen.findByText("Nội dung đã sửa.")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Xóa" }));
    expect(await screen.findByText("Bình luận đã được xóa.")).toBeVisible();
  });

  it("offers login when an anonymous mutation is rejected", async () => {
    apiMockServer.use(
      http.get("/api/catalog/comments", () =>
        HttpResponse.json({
          hasMore: false,
          items: [],
          nextCursor: null,
        })),
      http.post("/api/workspace/comments", () =>
        HttpResponse.json({
          code: "AUTHENTICATION_REQUIRED",
          status: 401,
          title: "Authentication required",
          type: "about:blank",
        }, { status: 401 })),
    );
    const user = userEvent.setup();
    render(<Comments targetId={targetId} targetType="STORY" />);

    await user.type(
      screen.getByLabelText("Chia sẻ cảm nhận"),
      "Bình luận",
    );
    await user.click(screen.getByRole("button", { name: "Gửi bình luận" }));

    expect(await screen.findByRole("link", { name: "Đăng nhập" }))
      .toHaveAttribute("href", "/auth/login");
  });
});
