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

function secureCookie(request: NextRequest, name: string, value: string, maxAge: number) {
  const isHttps =
    request.nextUrl.protocol === "https:" ||
    request.headers.get("x-forwarded-proto") === "https";
  return {
    name,
    value,
    httpOnly: true,
    maxAge,
    path: "/",
    sameSite: "lax" as const,
    secure: isHttps,
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

function oauthProvider(path: string) {
  const match = /^oauth2\/(google|facebook)\/authorize$/u.exec(path);
  return match?.[1] ?? null;
}

async function proxy(request: NextRequest, context: RouteContext) {
  const { segments } = await context.params;
  const path = segments.join("/");
  const provider = oauthProvider(path);
  if (request.method === "GET" && provider) {
    const returnTo = request.nextUrl.searchParams.get("returnTo") ?? "/account/sessions";
    const publicAuthBaseUrl = process.env.NEXT_PUBLIC_OAUTH_AUTH_BASE_URL?.replace(/\/+$/u, "");
    if (publicAuthBaseUrl) {
      const target = new URL(`/auth/oauth2/${provider}/authorize`, publicAuthBaseUrl);
      target.searchParams.set("returnTo", returnTo);
      return NextResponse.redirect(target);
    }
    const target = new URL("/auth/login", request.nextUrl.origin);
    target.searchParams.set("oauth", provider);
    target.searchParams.set("status", "unconfigured");
    target.searchParams.set("returnTo", returnTo);
    return NextResponse.redirect(target);
  }

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
    let roles: string[] = [];
    try {
      const parts = tokens.accessToken.split(".");
      const tokenSegment = parts[1];
      if (tokenSegment) {
        const payload = JSON.parse(
          Buffer.from(tokenSegment, "base64url").toString("utf-8"),
        ) as { roles?: string[] };
        if (Array.isArray(payload.roles)) {
          roles = payload.roles;
        }
      }
    } catch {
      // Ignore token decode errors
    }
    const response = NextResponse.json({ status: "AUTHENTICATED", roles });
    const THIRTY_DAYS = 30 * 24 * 60 * 60;
    response.cookies.set(
      secureCookie(request, "access_token", tokens.accessToken, THIRTY_DAYS),
    );
    response.cookies.set(
      secureCookie(request, "refresh_token", tokens.refreshToken, THIRTY_DAYS),
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
    response.cookies.set(secureCookie(request, "access_token", "", 0));
    response.cookies.set(secureCookie(request, "refresh_token", "", 0));
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
