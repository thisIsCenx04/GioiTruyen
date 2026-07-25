import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../test/msw/server";
import {
  createBrowserAuthClient,
  createBrowserPublishingClient,
  createBrowserReadingClient,
  createBrowserReadingSessionClient,
  createBrowserTeamClient,
  createBrowserWalletClient,
  createPublicCatalogClient,
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

describe("browser reading client", () => {
  it("loads the caller's private reading progress", async () => {
    apiMockServer.use(
      http.get(
        "/api/workspace/me/reading-progress/story-01",
        () => HttpResponse.json({
          chapterId: "chapter-03",
          deviceUpdatedAt: "2026-07-24T00:00:00Z",
          position: 62.5,
          storyId: "story-01",
          updatedAt: "2026-07-24T00:00:01Z",
          version: 5,
        }),
      ),
    );

    await expect(
      createBrowserReadingClient().progress("story-01"),
    ).resolves.toMatchObject({ position: 62.5, version: 5 });
  });

  it("synchronizes cross-device progress with If-Match", async () => {
    apiMockServer.use(
      http.put(
        "/api/workspace/me/reading-progress/story-01",
        async ({ request }) => {
          expect(request.headers.get("if-match")).toBe('"4"');
          await expect(request.json()).resolves.toMatchObject({
            chapterId: "chapter-03",
            position: 62.5,
          });
          return HttpResponse.json({
            chapterId: "chapter-03",
            deviceUpdatedAt: "2026-07-24T00:00:00Z",
            position: 62.5,
            storyId: "story-01",
            updatedAt: "2026-07-24T00:00:01Z",
            version: 5,
          });
        },
      ),
    );

    await expect(
      createBrowserReadingClient().synchronize(
        "story-01",
        {
          chapterId: "chapter-03",
          deviceUpdatedAt: "2026-07-24T00:00:00Z",
          position: 62.5,
        },
        4,
      ),
    ).resolves.toMatchObject({ position: 62.5, version: 5 });
  });

  it("paginates and deletes private reading history", async () => {
    apiMockServer.use(
      http.get("/api/workspace/me/reading-history", ({ request }) => {
        const url = new URL(request.url);
        expect(url.searchParams.get("cursor")).toBe("signed-cursor");
        expect(url.searchParams.get("limit")).toBe("25");
        return HttpResponse.json({
          hasMore: false,
          items: [],
          nextCursor: null,
        });
      }),
      http.delete(
        "/api/workspace/me/reading-history/story-01",
        () => new HttpResponse(null, { status: 204 }),
      ),
    );
    const client = createBrowserReadingClient();

    await expect(
      client.history({ cursor: "signed-cursor", limit: 25 }),
    ).resolves.toMatchObject({ hasMore: false, items: [] });
    await expect(client.deleteHistory("story-01")).resolves.toBeUndefined();
  });
});

describe("browser wallet client", () => {
  it("maps balance, history and idempotent top-up requests", async () => {
    apiMockServer.use(
      http.get("/api/workspace/wallets/me", () =>
        HttpResponse.json({
          asOf: "2026-07-25T00:00:00Z",
          availableXu: 20_000,
          currency: "XU",
          reservedXu: 0,
          version: 1,
        })),
      http.get("/api/workspace/wallets/me/topups", () =>
        HttpResponse.json([])),
      http.get(
        "/api/workspace/wallets/me/topups/10000000-0000-4000-8000-000000000001",
        () => HttpResponse.json({
          amountVnd: 50_000,
          createdAt: "2026-07-25T00:00:00Z",
          creditedXu: 50_000,
          discountPercent: 0,
          discountVersion: 1,
          expiresAt: "2026-07-25T00:15:00Z",
          id: "10000000-0000-4000-8000-000000000001",
          qrPayload: "VIETQR",
          status: "CREDITED",
          transferReference: "GT20260725ABCD",
        })),
      http.post("/api/workspace/wallets/me/topups", async ({ request }) => {
        expect(request.headers.get("idempotency-key")).toBe(
          "topup-20260725-reader-01",
        );
        await expect(request.json()).resolves.toEqual({ amountVnd: 50_000 });
        return HttpResponse.json({
          amountVnd: 50_000,
          createdAt: "2026-07-25T00:00:00Z",
          creditedXu: 50_000,
          discountPercent: 0,
          discountVersion: 1,
          expiresAt: "2026-07-25T00:15:00Z",
          id: "10000000-0000-4000-8000-000000000001",
          qrPayload: "VIETQR",
          status: "AWAITING_PAYMENT",
          transferReference: "GT20260725ABCD",
        }, { status: 201 });
      }),
    );
    const client = createBrowserWalletClient();

    await expect(client.balance()).resolves.toMatchObject({
      availableXu: 20_000,
      currency: "XU",
    });
    await expect(client.topupHistory()).resolves.toEqual([]);
    await expect(
      client.createTopup(50_000, "topup-20260725-reader-01"),
    ).resolves.toMatchObject({ status: "AWAITING_PAYMENT" });
    await expect(
      client.getTopup("10000000-0000-4000-8000-000000000001"),
    ).resolves.toMatchObject({ status: "CREDITED" });
  });

  it("refreshes once before retrying an expired wallet request", async () => {
    let attempts = 0;
    let refreshes = 0;
    apiMockServer.use(
      http.get("/api/workspace/wallets/me", () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json({
              code: "AUTHENTICATION_REQUIRED",
              status: 401,
              title: "Authentication required",
              type: "about:blank",
            }, { status: 401 })
          : HttpResponse.json({
              asOf: "2026-07-25T00:00:00Z",
              availableXu: 0,
              currency: "XU",
              reservedXu: 0,
              version: 0,
            });
      }),
      http.post("/api/auth/refresh", () => {
        refreshes += 1;
        return HttpResponse.json({ status: "AUTHENTICATED" });
      }),
    );

    await expect(createBrowserWalletClient().balance()).resolves.toMatchObject({
      currency: "XU",
    });
    expect(attempts).toBe(2);
    expect(refreshes).toBe(1);
  });
});

describe("browser Team analytics client", () => {
  it("requests a bounded period from the private workspace", async () => {
    apiMockServer.use(
      http.get(
        "/api/workspace/teams/team-01/analytics/views",
        ({ request }) => {
          expect(new URL(request.url).searchParams.get("period")).toBe("90D");
          return HttpResponse.json({
            from: "2026-05-01T00:00:00Z",
            period: "90D",
            reasons: [],
            series: [],
            teamId: "team-01",
            to: "2026-07-25T00:00:00Z",
            totals: {
              completedViews: 0,
              invalidViews: 0,
              qualityRate: 0,
              rawEvents: 0,
              validViews: 0,
            },
          });
        },
      ),
    );

    await expect(
      createBrowserTeamClient().analytics("team-01", "90D"),
    ).resolves.toMatchObject({ period: "90D", teamId: "team-01" });
  });
});

describe("browser reading session client", () => {
  it("starts an anonymous privacy-bounded session", async () => {
    apiMockServer.use(
      http.post("/api/reading-sessions", async ({ request }) => {
        await expect(request.json()).resolves.toMatchObject({
          anonymousId: "anonymous-01",
          chapterId: "chapter-01",
          storyId: "story-01",
        });
        return HttpResponse.json(
          {
            expiresAt: "2026-07-24T00:30:00Z",
            heartbeatIntervalSeconds: 15,
            sessionId: "session-01",
            sessionToken: "signed-token",
          },
          { status: 201 },
        );
      }),
    );

    await expect(
      createBrowserReadingSessionClient().start({
        anonymousId: "anonymous-01",
        chapterId: "chapter-01",
        storyId: "story-01",
      }),
    ).resolves.toMatchObject({
      heartbeatIntervalSeconds: 15,
      sessionToken: "signed-token",
    });
  });

  it("sends signed sequenced heartbeat batches", async () => {
    apiMockServer.use(
      http.post(
        "/api/reading-sessions/session-01/heartbeats",
        async ({ request }) => {
          expect(request.headers.get("x-reading-session-token")).toBe(
            "signed-token",
          );
          await expect(request.json()).resolves.toMatchObject({
            batchId: "batch-01",
            heartbeats: [{ sequence: 1 }],
          });
          return HttpResponse.json(
            {
              batchId: "batch-01",
              duplicate: false,
              nextSequence: 2,
            },
            { status: 202 },
          );
        },
      ),
    );

    await expect(
      createBrowserReadingSessionClient().heartbeat(
        "session-01",
        "signed-token",
        {
          batchId: "batch-01",
          heartbeats: [
            {
              activeSeconds: 15,
              occurredAt: "2026-07-24T00:00:00Z",
              position: 25,
              sequence: 1,
            },
          ],
        },
      ),
    ).resolves.toMatchObject({ duplicate: false, nextSequence: 2 });
  });

  it("queues idempotent completion without incrementing views", async () => {
    apiMockServer.use(
      http.post(
        "/api/reading-sessions/session-01/complete",
        async ({ request }) => {
          expect(request.headers.get("x-reading-session-token")).toBe(
            "signed-token",
          );
          await expect(request.json()).resolves.toMatchObject({
            completionId: "completion-01",
            finalSequence: 1,
          });
          return HttpResponse.json(
            {
              completionId: "completion-01",
              duplicate: false,
              status: "COMPLETION_PENDING",
            },
            { status: 202 },
          );
        },
      ),
    );

    await expect(
      createBrowserReadingSessionClient().complete(
        "session-01",
        "signed-token",
        {
          completionId: "completion-01",
          finalSequence: 1,
          occurredAt: "2026-07-24T00:00:00Z",
          position: 100,
        },
      ),
    ).resolves.toMatchObject({ status: "COMPLETION_PENDING" });
  });
});

describe("browser publishing client", () => {
  it("uses If-Match for chapter autosave and preserves editor content", async () => {
    apiMockServer.use(
      http.patch(
        "/api/workspace/teams/team-01/stories/story-01/chapters/chapter-01",
        async ({ request }) => {
          expect(request.headers.get("if-match")).toBe('"3"');
          await expect(request.json()).resolves.toEqual({
            contentHtml: "<p>Revision four</p>",
            title: "Chapter",
          });
          return HttpResponse.json({
            currentRevision: "revision-04",
            id: "chapter-01",
            number: 1,
            revisionNo: 4,
            storyId: "story-01",
            teamId: "team-01",
            title: "Chapter",
            version: 4,
            wordCount: 2,
            workflowStatus: "DRAFT",
          });
        },
      ),
    );

    await expect(
      createBrowserPublishingClient().updateChapter(
        "team-01",
        "story-01",
        "chapter-01",
        3,
        { contentHtml: "<p>Revision four</p>", title: "Chapter" },
      ),
    ).resolves.toMatchObject({
      contentHtml: "<p>Revision four</p>",
      revisionNo: 4,
      version: 4,
    });
  });

  it("carries an idempotency key when submitting a frozen revision", async () => {
    apiMockServer.use(
      http.post(
        "/api/workspace/teams/team-01/stories/story-01/submit",
        ({ request }) => {
          expect(request.headers.get("idempotency-key")).toBe("submit-01");
          return HttpResponse.json(
            {
              reviewId: "review-01",
              state: "PRECHECK_PENDING",
              storyId: "story-01",
              version: 1,
            },
            { status: 202 },
          );
        },
      ),
    );

    await expect(
      createBrowserPublishingClient().submit(
        "team-01",
        "story-01",
        "submit-01",
      ),
    ).resolves.toMatchObject({ reviewId: "review-01" });
  });
});

describe("public catalog client", () => {
  it("maps public catalog and revisioned chapter routes", async () => {
    apiMockServer.use(
      http.get(`${API_URL}/home`, () =>
        HttpResponse.json({
          generatedAt: "2026-07-24T00:00:00Z",
          locale: "vi-VN",
          sections: [],
          version: "v1",
        }),
      ),
      http.get(`${API_URL}/stories/story`, () =>
        HttpResponse.json({ id: "story-01", title: "Story" }),
      ),
      http.get(`${API_URL}/stories/story/chapters`, () =>
        HttpResponse.json({
          hasMore: false,
          items: [],
          nextCursor: null,
        }),
      ),
      http.get(`${API_URL}/chapters/chapter-01`, () =>
        HttpResponse.json({
          contentHtml: "<p>Published</p>",
          etag: "a".repeat(64),
          id: "chapter-01",
          revisionNo: 2,
        }),
      ),
      http.get(`${API_URL}/search`, () =>
        HttpResponse.json({
          facets: {},
          hasMore: false,
          items: [],
          nextCursor: null,
          tookMs: 2,
        }),
      ),
      http.get(`${API_URL}/search/suggestions`, () =>
        HttpResponse.json({
          hasMore: false,
          items: [],
          nextCursor: null,
        }),
      ),
    );
    const client = createPublicCatalogClient({ baseUrl: API_URL });

    await expect(client.home()).resolves.toMatchObject({ version: "v1" });
    await expect(client.story("story")).resolves.toMatchObject({
      title: "Story",
    });
    await expect(client.chapters("story")).resolves.toMatchObject({
      items: [],
    });
    await expect(client.chapter("chapter-01")).resolves.toMatchObject({
      contentHtml: "<p>Published</p>",
      revisionNo: 2,
    });
    await expect(client.search("kiếm hiệp")).resolves.toMatchObject({
      tookMs: 2,
    });
    await expect(client.suggestions("kiếm")).resolves.toMatchObject({
      items: [],
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

describe("browser team client", () => {
  it("maps membership permission updates to the workspace BFF", async () => {
    apiMockServer.use(
      http.patch(
        "/api/workspace/teams/team-01/members/user-02/permissions",
        async ({ request }) => {
          await expect(request.json()).resolves.toEqual({
            permissions: ["story:edit"],
            version: 3,
          });
          return HttpResponse.json({
            joinedAt: "2026-07-24T00:00:00Z",
            permissions: ["story:edit"],
            role: "MEMBER",
            state: "ACTIVE",
            teamId: "team-01",
            userId: "user-02",
            version: 4,
          });
        },
      ),
    );

    await expect(
      createBrowserTeamClient().updateMemberPermissions(
        "team-01",
        "user-02",
        3,
        ["story:edit"],
      ),
    ).resolves.toMatchObject({ permissions: ["story:edit"], version: 4 });
  });

  it("carries an idempotency key when inviting a member", async () => {
    apiMockServer.use(
      http.post(
        "/api/workspace/teams/team-01/members",
        async ({ request }) => {
          expect(request.headers.get("idempotency-key")).toBe("invite-01");
          return HttpResponse.json(
            {
              joinedAt: "2026-07-24T00:00:00Z",
              permissions: ["story:create"],
              role: "MEMBER",
              state: "INVITED",
              teamId: "team-01",
              userId: "user-02",
              version: 1,
            },
            { status: 201 },
          );
        },
      ),
    );

    await expect(
      createBrowserTeamClient().inviteMember(
        "team-01",
        { permissions: ["story:create"], userId: "user-02" },
        "invite-01",
      ),
    ).resolves.toMatchObject({ state: "INVITED" });
  });

  it("refreshes once and retries a protected team request", async () => {
    let attempts = 0;
    let refreshes = 0;
    apiMockServer.use(
      http.get("/api/workspace/teams", () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json(
              {
                code: "AUTHENTICATION_REQUIRED",
                status: 401,
                title: "Authentication required",
                type: "about:blank",
              },
              { status: 401 },
            )
          : HttpResponse.json([]);
      }),
      http.post("/api/auth/refresh", () => {
        refreshes += 1;
        return HttpResponse.json({ status: "AUTHENTICATED" });
      }),
    );

    await expect(createBrowserTeamClient().listTeams()).resolves.toEqual([]);
    expect(attempts).toBe(2);
    expect(refreshes).toBe(1);
  });
});
