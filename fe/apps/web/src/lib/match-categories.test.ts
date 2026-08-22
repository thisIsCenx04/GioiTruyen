import { describe, expect, it } from "vitest";

import { matchCategoryNames, type Category } from "./match-categories";

const categories: Category[] = [
  { id: "1", name: "Ngôn Tình", slug: "ngon-tinh" },
  { id: "2", name: "Huyền Huyễn", slug: "huyen-huyen" },
  { id: "3", name: "Vô Hạn Lưu", slug: "vo-han-luu" },
  { id: "4", name: "Đam Mỹ", slug: "dam-my" },
];

describe("matchCategoryNames", () => {
  it("matches the names a file lists", () => {
    const result = matchCategoryNames(["Ngôn Tình", "Vô Hạn Lưu"], categories);
    expect(result.ids).toEqual(["1", "3"]);
    expect(result.unmatched).toEqual([]);
  });

  // A file is typed by hand, so the accents and capitals are never dependable.
  it("ignores case and accents", () => {
    expect(matchCategoryNames(["ngon tinh", "HUYỀN HUYỄN"], categories).ids)
      .toEqual(["1", "2"]);
  });

  // Vietnamese "đ" does not decompose under NFD, so it needs its own rule.
  it("matches names containing đ", () => {
    expect(matchCategoryNames(["dam my"], categories).ids).toEqual(["4"]);
  });

  it("accepts a slug where a file wrote one", () => {
    expect(matchCategoryNames(["vo-han-luu"], categories).ids).toEqual(["3"]);
  });

  /**
   * A genre the site does not have is reported, never guessed at. Ticking the
   * nearest-looking genre would file the story under something the publisher
   * did not choose, which is worse than leaving it out.
   */
  it("reports names it has no genre for", () => {
    const result = matchCategoryNames(["Ngôn Tình", "Thể Loại Lạ"], categories);
    expect(result.ids).toEqual(["1"]);
    expect(result.unmatched).toEqual(["Thể Loại Lạ"]);
  });

  it("ticks a genre once even when the file repeats it", () => {
    expect(matchCategoryNames(["Ngôn Tình", "ngon tinh"], categories).ids).toEqual(["1"]);
  });

  it("skips blank entries left by a trailing comma", () => {
    const result = matchCategoryNames(["Ngôn Tình", "", "   "], categories);
    expect(result.ids).toEqual(["1"]);
    expect(result.unmatched).toEqual([]);
  });

  it("handles a file that lists none", () => {
    expect(matchCategoryNames([], categories)).toEqual({ ids: [], matched: [], unmatched: [] });
  });
});
