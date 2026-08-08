import "@testing-library/jest-dom/vitest";

import { HttpResponse, http } from "msw";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { describe, expect, it } from "vitest";

import { apiMockServer } from "../../../../test/msw/server";
import {
  ForgotPasswordJourney,
  RegisterJourney,
} from "./auth-journeys";

describe("authentication journeys", () => {
  it("keeps password recovery responses uniform", async () => {
    apiMockServer.use(
      http.post("/api/auth/password/forgot", () =>
        HttpResponse.json(
          {
            code: "BACKEND_UNAVAILABLE",
            status: 503,
            title: "Unavailable",
            type: "about:blank",
          },
          { status: 503 },
        ),
      ),
    );
    const user = userEvent.setup();
    render(createElement(ForgotPasswordJourney));

    await user.type(
      screen.getByRole("textbox", { name: "Email đã đăng ký" }),
      "unknown@example.test",
    );
    await user.click(screen.getByRole("button", { name: "Gửi hướng dẫn" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Nếu email tồn tại",
    );
  });

  it("requires explicit terms consent during registration", async () => {
    render(createElement(RegisterJourney));

    expect(
      screen.getByRole("checkbox", { name: /Tôi đồng ý/u }),
    ).toBeRequired();
    expect(screen.getByLabelText("Mật khẩu")).toHaveAttribute(
      "minlength",
      "12",
    );
  });
});
