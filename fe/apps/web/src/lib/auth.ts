export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  const match = document.cookie.match(/(?:^|; )access_token=([^;]*)/);
  if (match?.[1]) {
    return decodeURIComponent(match[1]);
  }
  return localStorage.getItem("access_token");
}

export function getUserRolesFromToken(token = getAccessToken()): string[] {
  if (!token) return [];
  try {
    const parts = token.split(".");
    if (parts.length < 2) return [];
    const base64Url = parts[1]!;
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    );
    const parsed = JSON.parse(jsonPayload) as {
      role?: unknown;
      roles?: unknown;
      scope?: unknown;
    };
    const roles = Array.isArray(parsed.roles)
      ? parsed.roles.filter((role): role is string => typeof role === "string")
      : [];

    if (typeof parsed.role === "string") {
      roles.push(parsed.role);
    }
    if (typeof parsed.scope === "string") {
      roles.push(...parsed.scope.split(/\s+/));
    }

    return [...new Set(roles.map((role) => role.toUpperCase()))];
  } catch {
    return [];
  }
}

export function getPostLoginDestination(
  accessToken: string | null,
  returnTo: string | null,
): string {
  if (getUserRolesFromToken(accessToken).includes("ADMIN")) {
    return "/dashboard";
  }

  return returnTo?.startsWith("/") && !returnTo.startsWith("//")
    ? returnTo
    : "/";
}

export function isLoggedIn(): boolean {
  if (typeof window === "undefined") return false;
  return document.cookie.includes("logged_in=true") || Boolean(getAccessToken());
}

export function isAdminUser(): boolean {
  if (typeof window === "undefined") return false;
  if (!isLoggedIn()) return false;

  const roles = getUserRolesFromToken();
  if (roles.includes("ADMIN")) return true;

  if (document.cookie.includes("is_admin=true")) return true;
  if (localStorage.getItem("is_admin") === "true") return true;

  return false;
}
