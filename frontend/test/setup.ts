import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";

import { apiMockServer } from "./msw/server";

beforeAll(() => {
  apiMockServer.listen({ onUnhandledRequest: "error" });
});

afterEach(() => {
  apiMockServer.resetHandlers();
  cleanup();
});

afterAll(() => {
  apiMockServer.close();
});
