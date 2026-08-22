import { describe, expect, it } from "vitest";

import { chapterIdentity, mergeImportedChapters } from "./publishing-workspace";

describe("publisher file chapter merge", () => {
  it("matches the same chapter when only the chapter separator changes", () => {
    expect(chapterIdentity("Chuong 1 - Mo dau", 1)).toBe(chapterIdentity("Chuong 1: Mo dau", 1));
    expect(chapterIdentity("Mo dau", 1)).toBe(chapterIdentity("Chuong 1: Mo dau", 1));
  });

  it("updates matched chapters while keeping their ids, prices and access", () => {
    const result = mergeImportedChapters(
      [
        { accessType: "PAID", coinPrice: 15, content: "old", id: "chapter-1", title: "Chuong 1: Mo dau" },
      ],
      [{ chapterNumber: 1, title: "Chuong 1: Mo dau" }],
      [
        { accessType: "FREE", coinPrice: 0, content: "new", title: "Chuong 1 - Mo dau" },
      ],
    );

    expect(result.updated).toHaveLength(1);
    expect(result.added).toHaveLength(0);
    expect(result.chapters[0]).toMatchObject({
      accessType: "PAID",
      coinPrice: 15,
      content: "new",
      id: "chapter-1",
      title: "Chuong 1 - Mo dau",
    });
  });

  it("adds a chapter when the same number has a different title", () => {
    const result = mergeImportedChapters(
      [
        { accessType: "FREE", coinPrice: 0, content: "old", id: "chapter-1", title: "Chuong 1: Mo dau" },
      ],
      [{ chapterNumber: 1, title: "Chuong 1: Mo dau" }],
      [
        { accessType: "FREE", coinPrice: 0, content: "branch", title: "Chuong 1: Ngoai truyen" },
      ],
    );

    expect(result.updated).toHaveLength(0);
    expect(result.added).toHaveLength(1);
    expect(result.chapters).toHaveLength(2);
  });
});
