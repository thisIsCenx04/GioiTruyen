/** @type {import("next").NextConfig} */
const nextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  transpilePackages: ["@gioitruyen/api-client", "@gioitruyen/ui"],
  typedRoutes: true,
};

export default nextConfig;
