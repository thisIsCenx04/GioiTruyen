import { afterEach, describe, expect, it, vi } from "vitest";

import { API_BASE_URL, apiFetch } from "./api-base";

/** Captures the URL apiFetch actually requested. */
function stubFetch() {
  const spy = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
  vi.stubGlobal("fetch", spy);
  return spy;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("apiFetch", () => {
  it("rewrites the generated client's workspace prefix onto the served base", async () => {
    const spy = stubFetch();

    await apiFetch("/api/workspace/wallets/me");

    expect(spy).toHaveBeenCalledWith(`${API_BASE_URL}/wallets/me`, undefined);
  });

  it("rewrites the catalog prefix as well", async () => {
    const spy = stubFetch();

    await apiFetch("/api/catalog/comments?targetId=1");

    expect(spy).toHaveBeenCalledWith(`${API_BASE_URL}/comments?targetId=1`, undefined);
  });

  it("rewrites the auth prefix", async () => {
    const spy = stubFetch();

    await apiFetch("/api/auth/refresh");

    expect(spy).toHaveBeenCalledWith(`${API_BASE_URL}/refresh`, undefined);
  });

  it("leaves an already-correct path untouched", async () => {
    const spy = stubFetch();

    await apiFetch("/api/v1/quests/me");

    expect(spy).toHaveBeenCalledWith("/api/v1/quests/me", undefined);
  });

  it("does not rewrite an unrelated path that merely starts similarly", async () => {
    const spy = stubFetch();

    // "/api/workspaces" is not "/api/workspace/..." and must be left alone.
    await apiFetch("/api/workspaces/list");

    expect(spy).toHaveBeenCalledWith("/api/workspaces/list", undefined);
  });

  it("preserves the request init it was given", async () => {
    const spy = stubFetch();
    const init = { method: "POST" };

    await apiFetch("/api/workspace/topups", init);

    expect(spy).toHaveBeenCalledWith(`${API_BASE_URL}/topups`, init);
  });

  it("rewrites a URL object while keeping its query string", async () => {
    const spy = stubFetch();

    await apiFetch(new URL("https://gioitruyen.com/api/workspace/wallets/me?x=1"));

    const called = spy.mock.calls[0]![0] as URL;
    expect(called.pathname).toBe(`${API_BASE_URL}/wallets/me`);
    expect(called.search).toBe("?x=1");
  });
});
