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

function rewrite(path: string): string {
  for (const prefix of LEGACY_PREFIXES) {
    if (path.startsWith(`${prefix}/`) || path === prefix) {
      return API_BASE_URL + path.slice(prefix.length);
    }
  }
  return path;
}
