import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

const uuid = "[0-9a-fA-F-]{36}";

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function allowed(method: string, path: string) {
  if (path === "admin/configuration/monetization-kill-switches") {
    return method === "GET";
  }
  if (new RegExp(`^admin/topups/${uuid}/(approve|reject)$`, "u").test(path)) {
    return method === "POST";
  }
  return (
    method === "POST" &&
    new RegExp(`^admin/withdrawals/${uuid}/(approve|reject)$`, "u").test(path)
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
  for (const name of [
    "idempotency-key",
    "scoped-reauthentication",
  ]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  let body: string | undefined;
  if (!["GET", "HEAD"].includes(request.method)) {
    body = await request.text();
    if (body) headers.set("Content-Type", "application/json");
  }
  try {
    const backend = await fetch(
      `${backendBaseUrl}/${path}${request.nextUrl.search}`,
      {
        ...(body === undefined ? {} : { body }),
        cache: "no-store",
        headers,
        method: request.method,
      },
    );
    return new NextResponse((await backend.text()) || null, {
      headers: {
        "Content-Type":
          backend.headers.get("content-type") ?? "application/json",
      },
      status: backend.status,
    });
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

export function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
