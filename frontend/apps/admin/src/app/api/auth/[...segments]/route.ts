import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function cookie(request: NextRequest, name: string, value: string, maxAge: number) {
  const isHttps =
    request.nextUrl.protocol === "https:" ||
    request.headers.get("x-forwarded-proto") === "https";
  return {
    httpOnly: true,
    maxAge,
    name,
    path: "/",
    sameSite: "lax" as const,
    secure: isHttps,
    value,
  };
}

async function proxy(request: NextRequest, context: RouteContext) {
  const { segments } = await context.params;
  const path = segments.join("/");
  if (!["login", "logout", "refresh", "reauth/grants"].includes(path)) {
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
  let body = await request.text();
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
  } else if (path === "logout" || path === "reauth/grants") {
    const accessToken = request.cookies.get("access_token")?.value;
    if (!accessToken) {
      if (path === "reauth/grants") {
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
      return new NextResponse(null, { status: 204 });
    }
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (body) headers.set("Content-Type", "application/json");
  try {
    const backend = await fetch(`${backendBaseUrl}/auth/${path}`, {
      ...(body ? { body } : {}),
      cache: "no-store",
      headers,
      method: "POST",
    });
    const responseBody = await backend.text();
    if ((path === "login" || path === "refresh") && backend.ok) {
      const tokens = JSON.parse(responseBody) as {
        accessToken: string;
        expiresIn: number;
        refreshToken: string;
      };
      const response = NextResponse.json({ status: "AUTHENTICATED" });
      const THIRTY_DAYS = 30 * 24 * 60 * 60;
      response.cookies.set(
        cookie(request, "access_token", tokens.accessToken, THIRTY_DAYS),
      );
      response.cookies.set(
        cookie(request, "refresh_token", tokens.refreshToken, THIRTY_DAYS),
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
    if (path === "logout" && backend.ok) {
      response.cookies.set(cookie(request, "access_token", "", 0));
      response.cookies.set(cookie(request, "refresh_token", "", 0));
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

export function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
