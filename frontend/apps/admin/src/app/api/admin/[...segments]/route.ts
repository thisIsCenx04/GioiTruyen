import { type NextRequest, NextResponse } from "next/server";

const backendBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

const uuid = "[0-9a-fA-F-]{36}";

type RouteContext = Readonly<{
  params: Promise<{ segments: string[] }>;
}>;

function allowed(method: string, path: string) {
  if (
    ["content/stories", "content/categories", "content/teams", "content/users"]
      .includes(path)
  ) {
    return method === "POST";
  }
  if (
    new RegExp(`^content/(stories|categories|teams|users)/${uuid}$`, "u")
      .test(path)
  ) {
    return method === "PUT" || method === "DELETE";
  }
  if (path === "finance/cash-flow") {
    return method === "POST";
  }
  return method === "POST"
    && new RegExp(`^finance/cash-flow/${uuid}/reverse$`, "u").test(path);
}

async function proxy(request: NextRequest, context: RouteContext) {
  const path = (await context.params).segments.join("/");
  if (!allowed(request.method, path)) {
    return NextResponse.json(
      { code: "ROUTE_NOT_ALLOWED", status: 404, title: "Route not found", type: "about:blank" },
      { status: 404 },
    );
  }
  const accessToken = request.cookies.get("access_token")?.value;
  if (!accessToken) {
    return NextResponse.json(
      {
        code: "AUTHENTICATION_REQUIRED",
        status: 401,
        title: "Vui lòng đăng nhập quản trị",
        type: "about:blank",
      },
      { status: 401 },
    );
  }
  const body = await request.text();
  try {
    const backend = await fetch(`${backendBaseUrl}/admin/${path}`, {
      ...(body ? { body } : {}),
      cache: "no-store",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${accessToken}`,
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      method: request.method,
    });
    return new NextResponse((await backend.text()) || null, {
      headers: {
        "Content-Type": backend.headers.get("content-type") ?? "application/json",
      },
      status: backend.status,
    });
  } catch {
    return NextResponse.json(
      { code: "BACKEND_UNAVAILABLE", status: 503, title: "Backend chưa sẵn sàng", type: "about:blank" },
      { status: 503 },
    );
  }
}

export function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function PUT(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function DELETE(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
