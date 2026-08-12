import { clearTokens, getAccessToken, refreshAccessToken } from "@/lib/auth";

/**
 * Base path for every private API call made from the browser.
 *
 * The generated client defaults to "/api/workspace", a path this deployment does
 * not serve: nginx routes only "/api/v1" to the backend and sends everything
 * else to the SPA. Requests to the default therefore came back as index.html,
 * failed to parse as JSON, and surfaced as "Không thể kết nối máy chủ" on the
 * wallet, team, notification and donation screens.
 *
 * Every `createBrowser*Client()` call must pass this explicitly.
 */
export const API_BASE_URL = "/api/v1";

/** Legacy prefixes baked into the generated client, all served by /api/v1 here. */
const LEGACY_PREFIXES = ["/api/workspace", "/api/catalog", "/api/auth"];

/**
 * A `fetch` that rewrites the generated client's hardcoded prefixes onto the
 * path this deployment actually serves.
 *
 * Some client factories (notably `createBrowserCommentClient`) build their base
 * URL internally and accept no `baseUrl` option, so the only place left to
 * correct them is the fetch they are handed. Patching the generated package is
 * not an option: it lives under node_modules and a reinstall would revert it.
 */
export const apiFetch: typeof fetch = (input, init) => {
  if (typeof input === "string") {
    return fetch(rewrite(input), init);
  }
  if (input instanceof URL) {
    return fetch(new URL(rewrite(input.pathname) + input.search, input.origin), init);
  }
  if (input instanceof Request) {
    const url = new URL(input.url);
    const rewritten = rewrite(url.pathname);
    if (rewritten === url.pathname) {
      return fetch(input, init);
    }
    return fetch(new Request(new URL(rewritten + url.search, url.origin), input), init);
  }
  return fetch(input, init);
};

/**
 * `apiFetch` plus the reader's bearer token, retrying once after a refresh.
 *
 * The generated clients send no Authorization header, so every call needing an
 * account came back 401. Components read that as "not signed in" and rendered a
 * login prompt to readers who were, in fact, signed in.
 */
export const authedFetch: typeof fetch = async (input, init) => {
  const send = (token: string | null) => {
    if (!token) return apiFetch(input, init);
    const headers = new Headers(init?.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return apiFetch(input, { ...init, credentials: "same-origin", headers });
  };

  const response = await send(getAccessToken());
  if (response.status !== 401) return response;

  const renewed = await refreshAccessToken();
  if (renewed) return send(renewed);

  // Neither token works any more. `logged_in` is a plain cookie with no
  // expiry tied to the session, so leaving it set makes the UI keep insisting
  // the reader is signed in while every request comes back 401.
  clearTokens();
  return response;
};

function rewrite(path: string): string {
  for (const prefix of LEGACY_PREFIXES) {
    if (path.startsWith(`${prefix}/`) || path === prefix) {
      return API_BASE_URL + path.slice(prefix.length);
    }
  }
  return path;
}
