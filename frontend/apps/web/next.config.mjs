import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const workspaceRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

/** @type {import("next").NextConfig} */
const nextConfig = {
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
  transpilePackages: ["@gioitruyen/api-client", "@gioitruyen/ui"],
  turbopack: {
    root: workspaceRoot,
  },
  typedRoutes: true,
};

export default nextConfig;
