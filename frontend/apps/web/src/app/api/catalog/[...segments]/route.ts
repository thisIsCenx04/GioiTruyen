import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function allowed(path: string) {
  return path === "search/suggestions";
}

export async function GET(
  request: NextRequest,
  context: RouteContext,
) {
  const path = (await context.params).segments.join("/");
  if (!allowed(path)) {
    return NextResponse.json(
      {
        code: "ROUTE_NOT_ALLOWED",
        status: 404,
        title: "Route not found",
        type: "about:blank",
      },
      { status: 404 },
    );
  }
  try {
    const backend = await fetch(
      `${backendBaseUrl}/${path}${request.nextUrl.search}`,
      {
        cache: "no-store",
        headers: {
          Accept: "application/json",
        },
      },
    );
    const response = new NextResponse(await backend.text(), {
      headers: {
        "Content-Type":
          backend.headers.get("content-type") ?? "application/json",
      },
      status: backend.status,
    });
    const retryAfter = backend.headers.get("retry-after");
    if (retryAfter) response.headers.set("Retry-After", retryAfter);
    return response;
  } catch {
    return NextResponse.json(
      {
        code: "BACKEND_UNAVAILABLE",
        status: 503,
        title: "Service unavailable",
        type: "about:blank",
      },
      { status: 503 },
    );
  }
}
