import { describe, expect, it } from "vitest";

import { mergeSectionsByTitle } from "./catalog";

const story = (id: string) => ({ id }) as never;

const section = (title: string, ids: string[]) => ({ title, stories: ids.map(story) });

describe("mergeSectionsByTitle", () => {
  // The home page opened with the same six covers twice, under "TRUYỆN MỚI
  // CẬP NHẬT" and then "Truyện vừa cập nhật". Whatever produces a repeated
  // heading, the reader must meet it once.
  it("folds two shelves with the same heading into one", () => {
    const merged = mergeSectionsByTitle([
      section("Truyện mới cập nhật", ["a", "b"]),
      section("Truyện mới cập nhật", ["c"]),
    ]);
    expect(merged).toHaveLength(1);
    expect(merged[0]?.stories.map((s: { id: string }) => s.id)).toEqual(["a", "b", "c"]);
  });

  // Merging must grow a shelf, never duplicate a card inside it.
  it("keeps one card per story when the shelves overlap", () => {
    const merged = mergeSectionsByTitle([
      section("Đề cử", ["a", "b"]),
      section("Đề cử", ["b", "c"]),
    ]);
    expect(merged[0]?.stories.map((s: { id: string }) => s.id)).toEqual(["a", "b", "c"]);
  });

  // Headings differing only in case or padding are the same heading to a reader.
  it("treats casing and surrounding space as the same heading", () => {
    expect(mergeSectionsByTitle([
      section("TRUYỆN MỚI CẬP NHẬT", ["a"]),
      section("  Truyện mới cập nhật ", ["b"]),
    ])).toHaveLength(1);
  });

  it("leaves distinct shelves alone and keeps their order", () => {
    const merged = mergeSectionsByTitle([
      section("Truyện độc quyền", ["a"]),
      section("Truyện đề cử", ["b"]),
      section("Đã hoàn thành", ["c"]),
    ]);
    expect(merged.map((s) => s.title))
      .toEqual(["Truyện độc quyền", "Truyện đề cử", "Đã hoàn thành"]);
  });

  // The merged shelf keeps the position of the first occurrence, so folding a
  // duplicate does not reshuffle the page.
  it("keeps the position of the first occurrence", () => {
    const merged = mergeSectionsByTitle([
      section("Mới", ["a"]),
      section("Khác", ["b"]),
      section("Mới", ["c"]),
    ]);
    expect(merged.map((s) => s.title)).toEqual(["Mới", "Khác"]);
    expect(merged[0]?.stories.map((s: { id: string }) => s.id)).toEqual(["a", "c"]);
  });

  it("does not mutate the sections it was given", () => {
    const first = section("Mới", ["a"]);
    mergeSectionsByTitle([first, section("Mới", ["b"])]);
    expect(first.stories).toHaveLength(1);
  });

  it("handles an empty list", () => {
    expect(mergeSectionsByTitle([])).toEqual([]);
  });
});
