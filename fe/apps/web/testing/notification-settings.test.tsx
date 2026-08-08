import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import { NotificationSettings } from "./notification-settings";

describe("notification settings", () => {
  it("requires an explicit choice and saves versioned consent", async () => {
    apiMockServer.use(
      http.get("/api/workspace/notification-preferences", () =>
        HttpResponse.json({
          categories: [],
          consentVersion: "notifications-2026.1",
          consentedAt: null,
          emailEnabled: false,
          pushEnabled: false,
          updatedAt: "2026-07-24T00:00:00Z",
          userId: "10000000-0000-4000-8000-000000000001",
          version: 0,
        })),
      http.patch(
        "/api/workspace/notification-preferences",
        async ({ request }) => {
          expect(request.headers.get("If-Match")).toBe('"0"');
          expect(await request.json()).toEqual({
            categories: ["STORY_UPDATES"],
            consentGranted: true,
            emailEnabled: true,
            pushEnabled: false,
          });
          return HttpResponse.json({
            categories: ["STORY_UPDATES"],
            consentVersion: "notifications-2026.1",
            consentedAt: "2026-07-24T01:00:00Z",
            emailEnabled: true,
            pushEnabled: false,
            updatedAt: "2026-07-24T01:00:00Z",
            userId: "10000000-0000-4000-8000-000000000001",
            version: 1,
          });
        },
      ),
    );
    const user = userEvent.setup();
    render(<NotificationSettings />);

    const choices = await screen.findAllByRole("checkbox");
    expect(choices).toHaveLength(6);
    const [emailChoice, pushChoice, storyChoice] = choices;
    if (!emailChoice || !pushChoice || !storyChoice) {
      throw new Error("Notification choices did not render");
    }
    expect(emailChoice).not.toBeChecked();
    expect(pushChoice).not.toBeChecked();

    await user.click(emailChoice);
    await user.click(storyChoice);
    await user.click(screen.getByRole("button"));

    expect(await screen.findByRole("status")).toBeInTheDocument();
  });
});
