import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../test/msw/server";
import {
  createBrowserAuthClient,
  createStoryApiClient,
  StoryApiError,
} from "./index";

const API_URL = "https://api.gioitruyen.test";

describe("story API client", () => {
  it("serializes a JSON request and returns the response", async () => {
    apiMockServer.use(
      http.post(`${API_URL}/v1/stories`, async ({ request }) => {
        expect(request.headers.get("accept")).toBe("application/json");
        expect(request.headers.get("content-type")).toBe("application/json");
        await expect(request.json()).resolves.toEqual({ title: "Đèn khuya" });

        return HttpResponse.json(
          { id: "story-01", title: "Đèn khuya" },
          { status: 201 },
        );
      }),
    );
    const client = createStoryApiClient({ baseUrl: `${API_URL}/` });

    await expect(
      client.request("/v1/stories", {
        body: { title: "Đèn khuya" },
        method: "POST",
      }),
    ).resolves.toEqual({ id: "story-01", title: "Đèn khuya" });
  });

  it("returns undefined for a successful response without content", async () => {
    apiMockServer.use(
      http.delete(`${API_URL}/v1/drafts/draft-01`, () => {
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const client = createStoryApiClient({ baseUrl: API_URL });

    await expect(
      client.request("/v1/drafts/draft-01", { method: "DELETE" }),
    ).resolves.toBeUndefined();
  });

  it("raises a typed RFC 9457 problem for an API failure", async () => {
    apiMockServer.use(
      http.get(`${API_URL}/v1/stories/missing`, () => {
        return HttpResponse.json(
          {
            code: "STORY_NOT_FOUND",
            detail: "Không tìm thấy truyện.",
            status: 404,
            title: "Story not found",
            traceId: "trace-01",
            type: "https://gioitruyen.test/problems/story-not-found",
          },
          { status: 404 },
        );
      }),
    );
    const client = createStoryApiClient({ baseUrl: API_URL });

    const request = client.request("/v1/stories/missing");

    await expect(request).rejects.toBeInstanceOf(StoryApiError);
    await expect(request).rejects.toMatchObject({
      problem: {
        code: "STORY_NOT_FOUND",
        status: 404,
        traceId: "trace-01",
      },
    });
  });
});

describe("browser authentication client", () => {
  it("sends login credentials only to the same-origin BFF", async () => {
    apiMockServer.use(
      http.post("/api/auth/login", async ({ request }) => {
        await expect(request.json()).resolves.toEqual({
          email: "reader@gioitruyen.vn",
          password: "correct horse battery",
        });
        return HttpResponse.json({ status: "AUTHENTICATED" });
      }),
    );

    await expect(
      createBrowserAuthClient().login(
        "reader@gioitruyen.vn",
        "correct horse battery",
      ),
    ).resolves.toEqual({ status: "AUTHENTICATED" });
  });

  it("refreshes an expired access cookie once before retrying", async () => {
    let attempts = 0;
    apiMockServer.use(
      http.get("/api/auth/sessions", () => {
        attempts += 1;
        if (attempts === 1) {
          return HttpResponse.json(
            {
              code: "AUTHENTICATION_REQUIRED",
              status: 401,
              title: "Authentication required",
              type: "about:blank",
            },
            { status: 401 },
          );
        }
        return HttpResponse.json({ sessions: [] });
      }),
      http.post("/api/auth/refresh", () =>
        HttpResponse.json({ status: "AUTHENTICATED" }),
      ),
    );

    await expect(
      createBrowserAuthClient().listSessions(),
    ).resolves.toEqual({ sessions: [] });
    expect(attempts).toBe(2);
  });

  it("does not refresh public authentication failures", async () => {
    let refreshAttempts = 0;
    apiMockServer.use(
      http.post("/api/auth/login", () =>
        HttpResponse.json(
          {
            code: "MFA_CODE_REQUIRED",
            status: 401,
            title: "Login rejected",
            type: "about:blank",
          },
          { status: 401 },
        ),
      ),
      http.post("/api/auth/refresh", () => {
        refreshAttempts += 1;
        return HttpResponse.json({ status: "AUTHENTICATED" });
      }),
    );

    await expect(
      createBrowserAuthClient().login("reader@example.test", "password"),
    ).rejects.toMatchObject({
      problem: { code: "MFA_CODE_REQUIRED" },
    });
    expect(refreshAttempts).toBe(0);
  });

  it("maps the remaining identity journeys to the BFF contract", async () => {
    apiMockServer.use(
      http.post("/api/auth/register", () =>
        HttpResponse.json({ status: "PENDING_VERIFICATION" }),
      ),
      http.post("/api/auth/password/forgot", () =>
        HttpResponse.json({ status: "ACCEPTED" }),
      ),
      http.post("/api/auth/password/reset", () =>
        new HttpResponse(null, { status: 204 }),
      ),
      http.post("/api/auth/email/verify", () =>
        new HttpResponse(null, { status: 204 }),
      ),
      http.post("/api/auth/mfa/challenge", () =>
        HttpResponse.json({
          algorithm: "SHA1",
          digits: 6,
          periodSeconds: 30,
          provisioningSecret: "BASE32",
        }),
      ),
      http.post("/api/auth/mfa/verify", () =>
        HttpResponse.json({ recoveryCodes: ["recovery-01"] }),
      ),
      http.post("/api/auth/logout", () =>
        new HttpResponse(null, { status: 204 }),
      ),
      http.delete("/api/auth/sessions", () =>
        new HttpResponse(null, { status: 204 }),
      ),
      http.delete("/api/auth/sessions/session-01", () =>
        new HttpResponse(null, { status: 204 }),
      ),
    );
    const client = createBrowserAuthClient();

    await expect(
      client.register("reader@example.test", "a long safe password", true),
    ).resolves.toEqual({ status: "PENDING_VERIFICATION" });
    await expect(
      client.forgotPassword("reader@example.test"),
    ).resolves.toEqual({ status: "ACCEPTED" });
    await expect(
      client.resetPassword("reset-token", "a new safe password"),
    ).resolves.toBeUndefined();
    await expect(client.verifyEmail("verify-token")).resolves.toBeUndefined();
    await expect(client.beginMfa()).resolves.toMatchObject({
      provisioningSecret: "BASE32",
    });
    await expect(client.verifyMfa("123456")).resolves.toEqual({
      recoveryCodes: ["recovery-01"],
    });
    await expect(client.revokeSession("session-01")).resolves.toBeUndefined();
    await expect(client.revokeAllSessions()).resolves.toBeUndefined();
    await expect(client.logout()).resolves.toBeUndefined();
  });
});
