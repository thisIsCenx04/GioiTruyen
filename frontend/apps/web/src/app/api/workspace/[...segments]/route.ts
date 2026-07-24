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
  if (new RegExp(`^teams/${uuid}/stories$`, "u").test(path)) {
    return method === "GET" || method === "POST";
  }
  if (new RegExp(`^teams/${uuid}/analytics/views$`, "u").test(path)) {
    return method === "GET";
  }
  if (new RegExp(`^teams/${uuid}/stories/${uuid}$`, "u").test(path)) {
    return method === "GET" || method === "PATCH";
  }
  if (
    new RegExp(`^teams/${uuid}/stories/${uuid}/chapters$`, "u").test(
      path,
    )
  ) {
    return method === "GET" || method === "POST";
  }
  if (
    new RegExp(
      `^teams/${uuid}/stories/${uuid}/chapters/${uuid}$`,
      "u",
    ).test(path)
  ) {
    return method === "PATCH";
  }
  if (new RegExp(`^teams/${uuid}/stories/${uuid}/submit$`, "u").test(path)) {
    return method === "POST";
  }
  if (
    new RegExp(`^teams/${uuid}/stories/${uuid}/schedule$`, "u").test(
      path,
    )
  ) {
    return ["DELETE", "PATCH", "POST"].includes(method);
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
  if (
    new RegExp(`^stories/${uuid}/(favorite|follow)$`, "u").test(path)
  ) {
    return ["DELETE", "GET", "PUT"].includes(method);
  }
  if (path === "comments") {
    return method === "POST";
  }
  if (new RegExp(`^comments/${uuid}$`, "u").test(path)) {
    return method === "DELETE" || method === "PATCH";
  }
  if (
    new RegExp(`^reactions/(story|chapter|comment)/${uuid}$`, "u")
      .test(path)
  ) {
    return ["DELETE", "GET", "PUT"].includes(method);
  }
  if (path === "reports") {
    return method === "POST";
  }
  if (
    new RegExp(`^moderation/cases/${uuid}/appeals$`, "u").test(path)
  ) {
    return method === "POST";
  }
  if (path === "copyright/cases") {
    return method === "POST";
  }
  if (
    new RegExp(`^copyright/cases/${uuid}/appeals$`, "u").test(path)
  ) {
    return method === "POST";
  }
  if (path === "notifications") {
    return method === "GET";
  }
  if (path === "notifications/read-all") {
    return method === "POST";
  }
  if (new RegExp(`^notifications/${uuid}/read$`, "u").test(path)) {
    return method === "POST";
  }
  if (path === "notification-preferences") {
    return method === "GET" || method === "PATCH";
  }
  if (path === "notification-push-subscriptions") {
    return method === "POST";
  }
  if (
    new RegExp(`^notification-push-subscriptions/${uuid}$`, "u").test(
      path,
    )
  ) {
    return method === "DELETE";
  }
  if (
    new RegExp(`^me/reading-progress/${uuid}$`, "u").test(path)
  ) {
    return method === "GET" || method === "PUT";
  }
  if (path === "me/reading-history") {
    return method === "GET";
  }
  if (new RegExp(`^me/reading-history/${uuid}$`, "u").test(path)) {
    return method === "DELETE";
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
  const ifMatch = request.headers.get("if-match");
  if (ifMatch) {
    headers.set("If-Match", ifMatch);
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
    const etag = backend.headers.get("etag");
    if (etag) {
      response.headers.set("ETag", etag);
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
