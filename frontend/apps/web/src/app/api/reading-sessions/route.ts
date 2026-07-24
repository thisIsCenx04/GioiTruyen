import { type NextRequest, NextResponse } from "next/server";

const endpoint = `${
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
}`.replace(/\/+$/u, "");

export async function POST(request: NextRequest) {
  const headers = new Headers({
    Accept: "application/json",
    "Content-Type": "application/json",
  });
  const accessToken = request.cookies.get("access_token")?.value;
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  try {
    const backend = await fetch(`${endpoint}/reading-sessions`, {
      body: await request.text(),
      cache: "no-store",
      headers,
      method: "POST",
    });
    const response = new NextResponse((await backend.text()) || null, {
      headers: {
        "Cache-Control": "no-store",
        "Content-Type":
          backend.headers.get("content-type") ?? "application/json",
      },
      status: backend.status,
    });
    const retryAfter = backend.headers.get("retry-after");
    if (retryAfter) response.headers.set("Retry-After", retryAfter);
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
