import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

const uuid = "[0-9a-fA-F-]{36}";

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function allowed(method: string, path: string) {
  if (path === "cases") return method === "GET";
  if (new RegExp(`^cases/${uuid}$`, "u").test(path)) {
    return method === "GET" || method === "PATCH";
  }
  if (
    new RegExp(
      `^cases/${uuid}/appeals/${uuid}/decisions$`,
      "u",
    ).test(path)
  ) {
    return method === "POST";
  }
  if (
    new RegExp(`^copyright/cases/${uuid}/decisions$`, "u").test(path)
  ) {
    return method === "POST";
  }
  return (
    method === "POST" &&
    new RegExp(`^cases/${uuid}/decisions$`, "u").test(path)
  );
}

async function proxy(request: NextRequest, context: RouteContext) {
  const { segments } = await context.params;
  const path = segments.join("/");
  if (!allowed(request.method, path)) {
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
  const accessToken = request.cookies.get("access_token")?.value;
  if (!accessToken) {
    return NextResponse.json(
      {
        code: "AUTHENTICATION_REQUIRED",
        status: 401,
        title: "Authentication required",
        type: "about:blank",
      },
      { status: 401 },
    );
  }
  const headers = new Headers({
    Accept: "application/json",
    Authorization: `Bearer ${accessToken}`,
  });
  const ifMatch = request.headers.get("if-match");
  if (ifMatch) headers.set("If-Match", ifMatch);
  let body: string | undefined;
  if (!["GET", "HEAD"].includes(request.method)) {
    body = await request.text();
    if (body) headers.set("Content-Type", "application/json");
  }
  try {
    const backendPath = path.startsWith("copyright/")
      ? path
      : `moderation/${path}`;
    const backend = await fetch(
      `${backendBaseUrl}/${backendPath}${request.nextUrl.search}`,
      {
        ...(body === undefined ? {} : { body }),
        cache: "no-store",
        headers,
        method: request.method,
      },
    );
    const response = new NextResponse((await backend.text()) || null, {
      headers: {
        "Content-Type":
          backend.headers.get("content-type") ?? "application/json",
      },
      status: backend.status,
    });
    const etag = backend.headers.get("etag");
    if (etag) response.headers.set("ETag", etag);
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

export function GET(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function PATCH(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
