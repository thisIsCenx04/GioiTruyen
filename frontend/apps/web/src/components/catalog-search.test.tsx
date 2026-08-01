import "@testing-library/jest-dom/vitest";

import { HttpResponse, http } from "msw";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

import { apiMockServer } from "../../../../test/msw/server";
import { CatalogSearch } from "./catalog-search";

describe("catalog search", () => {
  it("shows bounded suggestions and keeps a standard search form", async () => {
    apiMockServer.use(
      http.get("/api/catalog/search/suggestions", () =>
        HttpResponse.json({
          hasMore: false,
          items: [
            {
              coverAssetId: null,
              id: "story-01",
              slug: "nguoi-chep-su",
              title: "Người Chép Sử",
            },
          ],
          nextCursor: null,
        }),
      ),
    );
    const user = userEvent.setup();
    render(<CatalogSearch />);

    await user.type(
      screen.getByRole("searchbox", { name: "Tìm theo tên truyện" }),
      "người",
    );

    expect(
      await screen.findByRole("link", { name: /Người Chép Sử/u }),
    ).toHaveAttribute("href", "/truyen/nguoi-chep-su");
    expect(
      screen.getByRole("button", { name: "Tìm truyện" }),
    ).toHaveAttribute("type", "submit");
  });
});
