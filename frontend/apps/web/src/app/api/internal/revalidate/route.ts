import { revalidatePath } from "next/cache";
import { NextResponse } from "next/server";
import {
  authorizeRevalidation,
  validRevalidationBody,
} from "../../../../lib/isr-revalidation";

export async function POST(request: Request) {
  if (
    !authorizeRevalidation(
      request.headers.get("authorization"),
      process.env.NEXT_ISR_REVALIDATE_TOKEN,
    )
  ) {
    return NextResponse.json({ error: "forbidden" }, { status: 403 });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid body" }, { status: 400 });
  }
  if (!validRevalidationBody(body)) {
    return NextResponse.json({ error: "invalid body" }, { status: 400 });
  }

  revalidatePath("/", "layout");
  revalidatePath("/stories", "layout");
  return NextResponse.json({
    eventId: body.eventId,
    revalidated: true,
  });
}
