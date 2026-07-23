import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

const publicOperations = new Set([
  "POST login",
  "POST register",
  "POST email/verify",
  "POST password/forgot",
  "POST password/reset",
  "POST refresh",
]);

const protectedOperations = new Set([
  "POST logout",
  "POST mfa/challenge",
  "POST mfa/verify",
  "POST reauth/grants",
  "GET sessions",
  "DELETE sessions",
]);

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function secureCookie(name: string, value: string, maxAge: number) {
  return {
    name,
    value,
    httpOnly: true,
    maxAge,
    path: "/",
    sameSite: "strict" as const,
    secure: process.env.NODE_ENV === "production",
  };
}

function allowed(method: string, path: string) {
  if (publicOperations.has(`${method} ${path}`)) {
    return true;
  }
  if (protectedOperations.has(`${method} ${path}`)) {
    return true;
  }
  return method === "DELETE" && /^sessions\/[0-9a-f-]{36}$/u.test(path);
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

  const headers = new Headers({ Accept: "application/json" });
  const accessToken = request.cookies.get("access_token")?.value;
  if (!publicOperations.has(`${request.method} ${path}`)) {
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
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  let body: string | undefined;
  if (!["GET", "HEAD"].includes(request.method)) {
    body = await request.text();
    if (body) {
      headers.set("Content-Type", "application/json");
    }
  }
  if (path === "refresh") {
    const refreshToken = request.cookies.get("refresh_token")?.value;
    if (!refreshToken) {
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
    body = JSON.stringify({ refreshToken });
    headers.set("Content-Type", "application/json");
  }

  const backend = await fetch(`${backendBaseUrl}/auth/${path}`, {
    ...(body === undefined ? {} : { body }),
    cache: "no-store",
    headers,
    method: request.method,
  });
  const responseBody = await backend.text();

  if ((path === "login" || path === "refresh") && backend.ok) {
    const tokens = JSON.parse(responseBody) as {
      accessToken: string;
      expiresIn: number;
      refreshToken: string;
    };
    const response = NextResponse.json({ status: "AUTHENTICATED" });
    response.cookies.set(
      secureCookie("access_token", tokens.accessToken, tokens.expiresIn),
    );
    response.cookies.set(
      secureCookie("refresh_token", tokens.refreshToken, 60 * 60 * 24 * 30),
    );
    return response;
  }

  const response = new NextResponse(responseBody || null, {
    headers: {
      "Content-Type":
        backend.headers.get("content-type") ?? "application/json",
    },
    status: backend.status,
  });
  if (
    backend.ok &&
    (path === "logout" ||
      (path === "sessions" && request.method === "DELETE"))
  ) {
    response.cookies.set(secureCookie("access_token", "", 0));
    response.cookies.set(secureCookie("refresh_token", "", 0));
  }
  const retryAfter = backend.headers.get("retry-after");
  if (retryAfter) {
    response.headers.set("Retry-After", retryAfter);
  }
  return response;
}

export function GET(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function DELETE(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
