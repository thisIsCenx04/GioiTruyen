import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function cookie(name: string, value: string, maxAge: number) {
  return {
    httpOnly: true,
    maxAge,
    name,
    path: "/",
    sameSite: "strict" as const,
    secure: process.env.NODE_ENV === "production",
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
      response.cookies.set(
        cookie("access_token", tokens.accessToken, tokens.expiresIn),
      );
      response.cookies.set(
        cookie("refresh_token", tokens.refreshToken, 60 * 60 * 24 * 30),
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
      response.cookies.set(cookie("access_token", "", 0));
      response.cookies.set(cookie("refresh_token", "", 0));
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
