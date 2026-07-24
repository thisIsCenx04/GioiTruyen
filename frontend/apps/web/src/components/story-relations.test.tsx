import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { StoryRelations } from "./story-relations";

const storyId = "20000000-0000-4000-8000-000000000001";

describe("story relations", () => {
  it("loads independent counts and toggles favorite idempotently", async () => {
    apiMockServer.use(
      http.get(`/api/workspace/stories/${storyId}/favorite`, () =>
        HttpResponse.json({
          active: false,
          count: 7,
          storyId,
          type: "FAVORITE",
        })),
      http.get(`/api/workspace/stories/${storyId}/follow`, () =>
        HttpResponse.json({
          active: true,
          count: 3,
          storyId,
          type: "FOLLOW",
        })),
      http.put(`/api/workspace/stories/${storyId}/favorite`, () =>
        HttpResponse.json({
          active: true,
          count: 8,
          storyId,
          type: "FAVORITE",
        })),
    );
    const user = userEvent.setup();
    render(<StoryRelations storyId={storyId} />);

    const favorite = await screen.findByRole("button", {
      name: /Yêu thích\s*7/u,
    });
    expect(screen.getByRole("button", { name: /Đang theo dõi\s*3/u }))
      .toHaveAttribute("aria-pressed", "true");
    await user.click(favorite);

    expect(await screen.findByRole("button", {
      name: /Đã yêu thích\s*8/u,
    })).toHaveAttribute("aria-pressed", "true");
  });
});
