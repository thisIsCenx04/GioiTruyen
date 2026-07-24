import { type NextRequest, NextResponse } from "next/server";

const endpoint = `${
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
}`.replace(/\/+$/u, "");
const uuid = /^[0-9a-fA-F-]{36}$/u;

type RouteContext = Readonly<{
  params: Promise<{ sessionId: string }>;
}>;

export async function POST(request: NextRequest, context: RouteContext) {
  const { sessionId } = await context.params;
  const token = request.headers.get("x-reading-session-token");
  if (!uuid.test(sessionId) || !token || token.length > 1024) {
    return NextResponse.json(
      {
        code: "READING_COMPLETION_INVALID",
        status: 400,
        title: "Reading completion rejected",
        type: "about:blank",
      },
      { status: 400 },
    );
  }
  try {
    const backend = await fetch(
      `${endpoint}/reading-sessions/${encodeURIComponent(
        sessionId,
      )}/complete`,
      {
        body: await request.text(),
        cache: "no-store",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-Reading-Session-Token": token,
        },
        method: "POST",
      },
    );
    return new NextResponse((await backend.text()) || null, {
      headers: {
        "Cache-Control": "no-store",
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
