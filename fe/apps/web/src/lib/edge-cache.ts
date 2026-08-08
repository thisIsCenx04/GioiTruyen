export const PRIVATE_CACHE_CONTROL = "private, no-store, max-age=0";
export const PUBLIC_EDGE_CACHE_CONTROL =
  "public, s-maxage=120, stale-while-revalidate=600";

const PUBLIC_PATHS = [
  /^\/$/,
  /^\/home$/,
  /^\/search(?:\/|$)/,
  /^\/stories(?:\/|$)/,
];

export type EdgeCacheRequest = {
  hasAuthorization: boolean;
  hasCookie: boolean;
  method: string;
  pathname: string;
};

export type EdgeCachePolicy =
  | { kind: "private"; value: typeof PRIVATE_CACHE_CONTROL }
  | { kind: "public"; value: typeof PUBLIC_EDGE_CACHE_CONTROL };

export function edgeCachePolicy(
  request: EdgeCacheRequest,
): EdgeCachePolicy {
  const method = request.method.toUpperCase();
  const cacheableMethod = method === "GET" || method === "HEAD";
  const cacheablePath = PUBLIC_PATHS.some((pattern) =>
    pattern.test(request.pathname),
  );

  if (
    !cacheableMethod ||
    !cacheablePath ||
    request.hasAuthorization ||
    request.hasCookie
  ) {
    return { kind: "private", value: PRIVATE_CACHE_CONTROL };
  }

  return { kind: "public", value: PUBLIC_EDGE_CACHE_CONTROL };
}
