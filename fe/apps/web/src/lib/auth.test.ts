import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  clearTokens,
  getPostLoginDestination,
  getUserRolesFromToken,
  isAdminUser,
  refreshAccessToken,
} from "./auth";

// jsdom is not installed, so the handful of browser globals these helpers touch
// are stubbed here. Keeping the shim local keeps the suite fast and dependency
// free; swap it for `environment: "jsdom"` if the suite ever needs real DOM.
function installBrowserGlobals() {
  const cookies = new Map<string, string>();
  const store = new Map<string, string>();

  vi.stubGlobal("document", {
    get cookie() {
      return [...cookies].map(([name, value]) => `${name}=${value}`).join("; ");
    },
    set cookie(value: string) {
      const [pair, ...attributes] = value.split(";");
      const separator = pair.indexOf("=");
      const name = pair.slice(0, separator).trim();
      const expired = attributes.some((attribute) =>
        /max-age=0|expires=Thu, 01 Jan 1970/i.test(attribute));
      if (expired) {
        cookies.delete(name);
      } else {
        cookies.set(name, pair.slice(separator + 1).trim());
      }
    },
  });

  vi.stubGlobal("localStorage", {
    getItem: (key: string) => store.get(key) ?? null,
    removeItem: (key: string) => void store.delete(key),
    setItem: (key: string, value: string) => void store.set(key, value),
  });

  vi.stubGlobal("window", globalThis);
}

function tokenWithPayload(payload: Record<string, unknown>) {
  const encoded = btoa(JSON.stringify(payload))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  return `header.${encoded}.signature`;
}

beforeEach(() => {
  installBrowserGlobals();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("authentication role helpers", () => {
  it("recognizes the backend singular role claim", () => {
    expect(getUserRolesFromToken(tokenWithPayload({ role: "ADMIN" }))).toContain("ADMIN");
  });

  it("reads the scope claim the backend actually sends", () => {
    expect(getUserRolesFromToken(tokenWithPayload({ scope: "ADMIN" }))).toContain("ADMIN");
  });

  it("sends an admin to the dashboard when no destination was requested", () => {
    expect(getPostLoginDestination(tokenWithPayload({ role: "ADMIN" }), null)).toBe("/dashboard");
  });

  it("honours an explicit destination even for an admin", () => {
    expect(getPostLoginDestination(tokenWithPayload({ role: "ADMIN" }), "/library")).toBe("/library");
  });

  it("exposes dashboard access for a logged-in admin session", () => {
    const token = tokenWithPayload({ role: "ADMIN" });
    document.cookie = "logged_in=true; path=/";
    document.cookie = `access_token=${encodeURIComponent(token)}; path=/`;

    expect(isAdminUser()).toBe(true);
  });

  it("ignores an unsigned legacy admin flag", () => {
    document.cookie = "logged_in=true; path=/";
    document.cookie = "is_admin=true; path=/";
    localStorage.setItem("is_admin", "true");

    expect(isAdminUser()).toBe(false);
  });

  it("keeps a safe requested destination for non-admin users", () => {
    const token = tokenWithPayload({ role: "READER" });

    expect(getPostLoginDestination(token, "/library")).toBe("/library");
    expect(getPostLoginDestination(token, "//external.test")).toBe("/");
  });
});

describe("refreshAccessToken", () => {
  it("does nothing when there is no refresh token to spend", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    expect(await refreshAccessToken()).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("stores and returns the renewed access token", async () => {
    localStorage.setItem("refresh_token", "stored-refresh-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      json: async () => ({ accessToken: "new-access", refreshToken: "new-refresh" }),
      ok: true,
      status: 200,
    }));

    expect(await refreshAccessToken()).toBe("new-access");
    expect(localStorage.getItem("access_token")).toBe("new-access");
    // The server revokes the presented token, so the rotated one must replace it.
    expect(localStorage.getItem("refresh_token")).toBe("new-refresh");
  });

  it("clears the session when the refresh token is rejected", async () => {
    localStorage.setItem("refresh_token", "revoked-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      json: async () => ({}),
      ok: false,
      status: 401,
    }));

    expect(await refreshAccessToken()).toBeNull();
    expect(localStorage.getItem("refresh_token")).toBeNull();
  });

  it("returns null instead of throwing when the network fails", async () => {
    localStorage.setItem("refresh_token", "stored-refresh-token");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));

    expect(await refreshAccessToken()).toBeNull();
    // A transient outage must not sign the reader out.
    expect(localStorage.getItem("refresh_token")).toBe("stored-refresh-token");
  });

  it("shares one exchange between concurrent callers", async () => {
    localStorage.setItem("refresh_token", "stored-refresh-token");
    const fetchMock = vi.fn().mockResolvedValue({
      json: async () => ({ accessToken: "new-access" }),
      ok: true,
      status: 200,
    });
    vi.stubGlobal("fetch", fetchMock);

    const [first, second] = await Promise.all([refreshAccessToken(), refreshAccessToken()]);

    expect(first).toBe("new-access");
    expect(second).toBe("new-access");
    // Two round trips would burn the rotated token and log the user out.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("clearTokens", () => {
  it("removes both tokens from every store", () => {
    localStorage.setItem("access_token", "a");
    localStorage.setItem("refresh_token", "b");
    document.cookie = "access_token=a; path=/";

    clearTokens();

    expect(localStorage.getItem("access_token")).toBeNull();
    expect(localStorage.getItem("refresh_token")).toBeNull();
    expect(document.cookie).not.toContain("access_token=a");
  });
});
