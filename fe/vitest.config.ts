import { defineConfig } from "vitest/config";

export default defineConfig({
  oxc: {
    jsx: {
      runtime: "automatic",
    },
  },
  test: {
    clearMocks: true,
    environment: "jsdom",
    globals: false,
    hookTimeout: 5_000,
    include: [
      "{apps,packages}/**/*.test.{ts,tsx}",
      "test/policy/**/*.test.{ts,tsx}",
    ],
    mockReset: true,
    reporters: [
      "default",
      ["junit", { outputFile: "test-results/frontend-unit.xml" }],
    ],
    restoreMocks: true,
    retry: 0,
    sequence: {
      shuffle: false,
    },
    setupFiles: ["./test/setup.ts"],
    testTimeout: 5_000,
    coverage: {
      exclude: [
        "**/*.d.ts",
        "packages/ui/src/index.ts",
      ],
      include: [
        "packages/api-client/src/**/*.ts",
        "packages/ui/src/**/*.{ts,tsx}",
      ],
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      reportsDirectory: "coverage",
      thresholds: {
        branches: 75,
        functions: 80,
        lines: 80,
        statements: 80,
      },
    },
  },
});
