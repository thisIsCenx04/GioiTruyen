import "@testing-library/jest-dom/vitest";

import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { BrandMark } from "./brand-mark";
import { StatusPill } from "./status-pill";
import { StoryCard } from "./story-card";

describe("shared reader components", () => {
  it("renders the complete and compact brand variants", () => {
    const { rerender } = render(<BrandMark inverse />);

    expect(screen.getByText("Giới Truyện")).toBeVisible();

    rerender(<BrandMark compact />);

    expect(screen.queryByText("Giới Truyện")).not.toBeInTheDocument();
    expect(screen.getByText("G")).toHaveAttribute("aria-hidden", "true");
  });

  it("exposes the semantic status and visual tone", () => {
    render(<StatusPill tone="attention">Chờ duyệt</StatusPill>);

    expect(screen.getByText("Chờ duyệt")).toHaveAttribute(
      "data-tone",
      "attention",
    );
  });

  it("links a story card to its reading destination", () => {
    render(
      <StoryCard
        author="Mộc Miên"
        coverTone="jade"
        eyebrow="Huyền huyễn"
        href="/truyen/muc-chua-kho"
        latestChapter="Ch. 40"
        title="Mực chưa khô"
      />,
    );

    expect(
      screen.getByRole("link", { name: "Đọc Mực chưa khô" }),
    ).toHaveAttribute("href", "/truyen/muc-chua-kho");
    expect(
      screen.getByRole("heading", { name: "Mực chưa khô" }),
    ).toBeVisible();
    expect(screen.getByText("Mộc Miên")).toBeVisible();
    expect(screen.getByText("Huyền huyễn")).toBeVisible();
    expect(screen.getByText("Ch. 40")).toBeVisible();
  });
});
