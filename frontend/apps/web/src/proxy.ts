import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { edgeCachePolicy } from "./lib/edge-cache";

export function proxy(request: NextRequest) {
  const response = NextResponse.next();
  const policy = edgeCachePolicy({
    hasAuthorization: request.headers.has("authorization"),
    hasCookie: request.headers.has("cookie"),
    method: request.method,
    pathname: request.nextUrl.pathname,
  });

  response.headers.set("Cloudflare-CDN-Cache-Control", policy.value);
  response.headers.set("CDN-Cache-Control", policy.value);

  if (policy.kind === "private") {
    response.headers.set("Cache-Control", policy.value);
  }

  return response;
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)",
  ],
};
