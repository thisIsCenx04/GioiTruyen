import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

const uuid = "[0-9a-fA-F-]{36}";
const token = "[A-Za-z0-9._-]{43,128}";

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function allowed(method: string, path: string) {
  if (/^teams$/u.test(path)) {
    return method === "GET" || method === "POST";
  }
  if (new RegExp(`^teams/${uuid}$`, "u").test(path)) {
    return method === "GET" || method === "PATCH";
  }
  if (new RegExp(`^teams/${uuid}/members$`, "u").test(path)) {
    return method === "GET" || method === "POST";
  }
  if (new RegExp(`^teams/${uuid}/members/${uuid}$`, "u").test(path)) {
    return method === "DELETE";
  }
  if (
    new RegExp(`^teams/${uuid}/members/${uuid}/permissions$`, "u").test(
      path,
    )
  ) {
    return method === "PATCH";
  }
  if (new RegExp(`^teams/${uuid}/follow$`, "u").test(path)) {
    return ["DELETE", "GET", "PUT"].includes(method);
  }
  return (
    method === "POST" &&
    new RegExp(`^team-invitations/${token}/accept$`, "u").test(path)
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
  const idempotencyKey = request.headers.get("idempotency-key");
  if (idempotencyKey) {
    headers.set("Idempotency-Key", idempotencyKey);
  }

  let body: string | undefined;
  if (!["GET", "HEAD"].includes(request.method)) {
    body = await request.text();
    if (body) {
      headers.set("Content-Type", "application/json");
    }
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
    const responseBody = await backend.text();
    const response = new NextResponse(responseBody || null, {
      headers: {
        "Content-Type":
          backend.headers.get("content-type") ?? "application/json",
      },
      status: backend.status,
    });
    const retryAfter = backend.headers.get("retry-after");
    if (retryAfter) {
      response.headers.set("Retry-After", retryAfter);
    }
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

export function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function PATCH(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function PUT(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function DELETE(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
