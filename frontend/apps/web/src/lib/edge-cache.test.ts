import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";

import { proxy } from "../proxy";
import {
  edgeCachePolicy,
  PRIVATE_CACHE_CONTROL,
  PUBLIC_EDGE_CACHE_CONTROL,
} from "./edge-cache";

describe("edge cache policy", () => {
  it.each(["access_token", "refresh_token"])(
    "never shares a public page carrying the %s cookie",
    (cookieName) => {
      const request = new NextRequest("https://gioitruyen.example/stories/demo", {
        headers: { cookie: `${cookieName}=sensitive-value` },
      });

      const response = proxy(request);

      expect(response.headers.get("cache-control")).toBe(
        PRIVATE_CACHE_CONTROL,
      );
      expect(response.headers.get("cloudflare-cdn-cache-control")).toBe(
        PRIVATE_CACHE_CONTROL,
      );
      expect(response.headers.get("cdn-cache-control")).toBe(
        PRIVATE_CACHE_CONTROL,
      );
      expect([...response.headers.values()].join(" ")).not.toContain(
        "sensitive-value",
      );
    },
  );

  it("allows an anonymous versioned story page in the shared edge cache", () => {
    const request = new NextRequest(
      "https://gioitruyen.example/stories/demo?_rsc=version-42",
    );

    const response = proxy(request);

    expect(response.headers.get("cache-control")).toBeNull();
    expect(response.headers.get("cloudflare-cdn-cache-control")).toBe(
      PUBLIC_EDGE_CACHE_CONTROL,
    );
  });

  it.each([
    { method: "POST", pathname: "/stories" },
    { method: "GET", pathname: "/api/workspace/teams" },
    { method: "GET", pathname: "/account/sessions" },
  ])("keeps non-public requests private: $method $pathname", (request) => {
    expect(
      edgeCachePolicy({
        ...request,
        hasAuthorization: false,
        hasCookie: false,
      }),
    ).toEqual({ kind: "private", value: PRIVATE_CACHE_CONTROL });
  });

  it("rejects shared caching whenever Authorization is present", () => {
    expect(
      edgeCachePolicy({
        hasAuthorization: true,
        hasCookie: false,
        method: "GET",
        pathname: "/search",
      }),
    ).toEqual({ kind: "private", value: PRIVATE_CACHE_CONTROL });
  });
});
