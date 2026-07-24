import { timingSafeEqual } from "node:crypto";

export function authorizeRevalidation(
  authorization: string | null,
  expectedToken: string | undefined,
): boolean {
  if (!expectedToken || expectedToken.length < 32) {
    return false;
  }
  const prefix = "Bearer ";
  if (!authorization?.startsWith(prefix)) {
    return false;
  }
  const supplied = Buffer.from(authorization.slice(prefix.length), "utf8");
  const expected = Buffer.from(expectedToken, "utf8");
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

export function validRevalidationBody(
  value: unknown,
): value is { eventId: string; targets: string[] } {
  if (!value || typeof value !== "object") {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.eventId === "string" &&
    /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(candidate.eventId) &&
    Array.isArray(candidate.targets) &&
    candidate.targets.length > 0 &&
    candidate.targets.length <= 100 &&
    candidate.targets.every(
      (target) =>
        typeof target === "string" &&
        /^[a-z]+:[A-Za-z0-9][A-Za-z0-9._:-]{0,255}$/.test(target),
    )
  );
}
