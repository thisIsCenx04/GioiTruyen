import { describe, expect, it } from "vitest";

import { coverThumbUrl, coverUrl } from "./story-cover";

describe("coverThumbUrl", () => {
  // The stored URL includes the API context path. An earlier version anchored
  // the pattern at "/uploads", matched nothing, and every card went on loading
  // the multi-megabyte original with no visible sign anything was wrong.
  it("rewrites a stored cover path that carries the API context path", () => {
    expect(coverThumbUrl("/api/v1/uploads/stories/abc-123.png"))
      .toBe("/api/v1/uploads/stories/thumb/abc-123.jpg");
  });

  it("rewrites a path served without a context path", () => {
    expect(coverThumbUrl("/uploads/stories/abc-123.jpeg"))
      .toBe("/uploads/stories/thumb/abc-123.jpg");
  });

  // The server only writes JPEG thumbnails, whatever the source format.
  it("always points at a .jpg", () => {
    expect(coverThumbUrl("/uploads/stories/abc-123.PNG"))
      .toBe("/uploads/stories/thumb/abc-123.jpg");
  });

  // ImageIO cannot read these, so no thumbnail exists to point at.
  it("leaves formats the server cannot resize alone", () => {
    expect(coverThumbUrl("/uploads/stories/abc.webp")).toBe("/uploads/stories/abc.webp");
    expect(coverThumbUrl("/uploads/stories/abc.gif")).toBe("/uploads/stories/abc.gif");
  });

  it("leaves covers hosted elsewhere alone", () => {
    expect(coverThumbUrl("https://cdn.example.com/a.jpg")).toBe("https://cdn.example.com/a.jpg");
  });

  it("returns nothing for a story with no cover", () => {
    expect(coverThumbUrl(null)).toBe("");
    expect(coverThumbUrl("  ")).toBe("");
  });

  it("does not disturb how the original is resolved", () => {
    expect(coverUrl("abc.png")).toBe("/uploads/stories/abc.png");
    expect(coverUrl("/api/v1/uploads/stories/abc.png")).toBe("/api/v1/uploads/stories/abc.png");
  });
});
