import { afterEach, describe, expect, it } from "vitest";

import {
  getPostLoginDestination,
  getUserRolesFromToken,
  isAdminUser,
} from "./auth";

function tokenWithPayload(payload: Record<string, unknown>) {
  const encoded = btoa(JSON.stringify(payload))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  return `header.${encoded}.signature`;
}

afterEach(() => {
  document.cookie = "logged_in=; path=/; max-age=0";
  document.cookie = "access_token=; path=/; max-age=0";
});

describe("authentication role helpers", () => {
  it("recognizes the backend singular role claim", () => {
    const token = tokenWithPayload({ role: "ADMIN" });

    expect(getUserRolesFromToken(token)).toContain("ADMIN");
    expect(getPostLoginDestination(token, "/library")).toBe("/dashboard");
  });

  it("exposes dashboard access for a logged-in admin session", () => {
    const token = tokenWithPayload({ role: "ADMIN" });
    document.cookie = "logged_in=true; path=/";
    document.cookie = `access_token=${encodeURIComponent(token)}; path=/`;

    expect(isAdminUser()).toBe(true);
  });

  it("keeps a safe requested destination for non-admin users", () => {
    const token = tokenWithPayload({ role: "READER" });

    expect(getPostLoginDestination(token, "/library")).toBe("/library");
    expect(getPostLoginDestination(token, "//external.test")).toBe("/");
  });
});
