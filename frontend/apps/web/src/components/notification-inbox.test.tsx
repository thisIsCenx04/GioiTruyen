import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { NotificationInbox } from "./notification-inbox";

const first = "20000000-0000-4000-8000-000000000001";
const second = "20000000-0000-4000-8000-000000000002";

describe("notification inbox", () => {
  it("paginates and marks one or all notifications read", async () => {
    apiMockServer.use(
      http.get("/api/workspace/notifications", ({ request }) => {
        const cursor = new URL(request.url).searchParams.get("cursor");
        if (cursor) {
          return HttpResponse.json({
            hasMore: false,
            items: [{
              body: "Chương mới đã sẵn sàng.",
              createdAt: "2026-07-23T00:00:00Z",
              data: {},
              id: second,
              readAt: null,
              title: "Tân Thế Toàn",
              type: "CHAPTER_PUBLISHED",
            }],
            nextCursor: null,
            unreadCount: 2,
          });
        }
        return HttpResponse.json({
          hasMore: true,
          items: [{
            body: "Truyện vừa được xuất bản.",
            createdAt: "2026-07-24T00:00:00Z",
            data: {},
            id: first,
            readAt: null,
            title: "Một thế giới mới",
            type: "STORY_PUBLISHED",
          }],
          nextCursor: "signed-cursor",
          unreadCount: 2,
        });
      }),
      http.post(`/api/workspace/notifications/${first}/read`, () =>
        HttpResponse.json({
          body: "Truyện vừa được xuất bản.",
          createdAt: "2026-07-24T00:00:00Z",
          data: {},
          id: first,
          readAt: "2026-07-24T01:00:00Z",
          title: "Một thế giới mới",
          type: "STORY_PUBLISHED",
        })),
      http.post("/api/workspace/notifications/read-all", () =>
        HttpResponse.json({
          readBefore: "2026-07-24T02:00:00Z",
          unreadCount: 0,
        })),
    );
    const user = userEvent.setup();
    render(<NotificationInbox />);

    const firstNotice = await screen.findByRole("button", {
      name: "Đánh dấu đã đọc: Một thế giới mới",
    });
    expect(screen.getByText("02")).toBeInTheDocument();
    await user.click(firstNotice);
    expect(await screen.findByText("Đã đọc")).toBeInTheDocument();
    expect(screen.getByText("01")).toBeInTheDocument();

    await user.click(screen.getByRole("button", {
      name: "Mở trang cũ hơn",
    }));
    expect(await screen.findByText("Tân Thế Toàn")).toBeInTheDocument();
    await user.click(screen.getByRole("button", {
      name: "Đánh dấu tất cả đã đọc",
    }));
    expect(await screen.findByText("00")).toBeInTheDocument();
  });
});
