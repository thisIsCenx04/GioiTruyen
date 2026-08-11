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

/** Same-site absolute paths only, so a crafted returnTo cannot bounce off-site. */
export function safeReturnPath(value: string | null | undefined): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return null;
  }
  return value;
}

/**
 * Builds a login URL that remembers the page the reader came from. Without this
 * every login link drops them on the home page after signing in.
 */
export function loginHref(returnTo?: string | null): string {
  const target = safeReturnPath(returnTo)
    ?? (typeof window === "undefined"
      ? null
      : safeReturnPath(window.location.pathname + window.location.search));

  return target && target !== "/"
    ? `/login?returnTo=${encodeURIComponent(target)}`
    : "/login";
}

export function getPostLoginDestination(
  accessToken: string | null,
  returnTo: string | null,
): string {
  // An explicit returnTo wins even for admins; they asked for that page.
  const target = safeReturnPath(returnTo);
  if (target) {
    return target;
  }
  if (getUserRolesFromToken(accessToken).includes("ADMIN")) {
    return "/dashboard";
  }
  return "/";
}

function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  const match = document.cookie.match(/(?:^|; )refresh_token=([^;]*)/);
  if (match?.[1]) {
    return decodeURIComponent(match[1]);
  }
  return localStorage.getItem("refresh_token");
}

function storeTokens(accessToken: string, refreshToken?: string): void {
  localStorage.setItem("access_token", accessToken);
  document.cookie = `access_token=${encodeURIComponent(accessToken)}; path=/; max-age=1800; SameSite=Lax`;
  if (refreshToken) {
    localStorage.setItem("refresh_token", refreshToken);
    document.cookie = `refresh_token=${encodeURIComponent(refreshToken)}; path=/; max-age=2592000; SameSite=Lax`;
  }
}

export function clearTokens(): void {
  localStorage.removeItem("access_token");
  localStorage.removeItem("refresh_token");
  document.cookie = "access_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  document.cookie = "refresh_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
}

/** Concurrent 401s share one exchange; the server revokes a refresh token on use. */
let inFlightRefresh: Promise<string | null> | null = null;

/**
 * Trades the stored refresh token for a new access token. Access tokens last 30
 * minutes, so without this every session ends mid-read and the reader is bounced
 * to the login page. Returns null when the session cannot be renewed.
 */
export async function refreshAccessToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  if (inFlightRefresh) return inFlightRefresh;

  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  inFlightRefresh = (async () => {
    try {
      const response = await fetch("/api/v1/auth/refresh", {
        body: JSON.stringify({ refreshToken }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        // The token is spent or revoked; drop it so we stop retrying.
        if (response.status === 401) clearTokens();
        return null;
      }
      const data = (await response.json()) as {
        accessToken?: string;
        refreshToken?: string;
      };
      if (!data.accessToken) return null;
      storeTokens(data.accessToken, data.refreshToken);
      return data.accessToken;
    } catch {
      return null;
    } finally {
      inFlightRefresh = null;
    }
  })();

  return inFlightRefresh;
}

export function isLoggedIn(): boolean {
  if (typeof window === "undefined") return false;
  return document.cookie.includes("logged_in=true") || Boolean(getAccessToken());
}

export function isAdminUser(): boolean {
  if (typeof window === "undefined") return false;
  if (!isLoggedIn()) return false;

  // UI visibility follows the same signed JWT role used by Spring Security.
  // Never trust the old is_admin cookie/localStorage flag: either can be set
  // manually and must not be enough to expose the dashboard shell.
  return getUserRolesFromToken().includes("ADMIN");
}
