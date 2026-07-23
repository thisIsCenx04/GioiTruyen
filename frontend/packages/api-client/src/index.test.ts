import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../test/msw/server";
import { createStoryApiClient, StoryApiError } from "./index";

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
